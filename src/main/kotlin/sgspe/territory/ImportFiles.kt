package sgspe.territory

import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.springframework.boot.context.properties.ConfigurationProperties
import tools.jackson.databind.json.JsonMapper
import wasichai.core.common.FieldViolation
import wasichai.core.common.ValidationException
import java.io.File
import java.math.BigDecimal
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

// los límites de una importación (RQ-SEC-020, ASM-050): tamaño del archivo, filas, tiempo de lectura y validación, la
// tolerancia de «sin cambio» (gis.md §4.4: 1 cm) y la envolvente del Perú para marcar lo que cae fuera de la región
@ConfigurationProperties("sgspe.territory.import")
data class ImportProperties(
    val maxBytes: Long = 20L * 1024 * 1024,
    val maxRows: Int = 10_000,
    val timeout: Duration = Duration.ofSeconds(60),
    val unchangedToleranceM: BigDecimal = BigDecimal("0.01"),
    val minLongitude: Double = -81.4,
    val maxLongitude: Double = -68.6,
    val minLatitude: Double = -18.4,
    val maxLatitude: Double = 0.1
)

// una fila del archivo: su número (el de la hoja o la posición del feature), sus valores por encabezado y, en GeoJSON, su
// geometría en el CRS de origen
data class SourceRow(
    val row: Int,
    val values: Map<String, String?>,
    val geometry: Map<String, Any?>? = null
)

// la carpeta temporal de una importación (RQ-SEC-020): todo se lee de ahí y se borra al terminar. una entrada de un ZIP
// (Shapefile y FileGDB, desde F6) solo se resuelve dentro de la carpeta: ../ o una ruta absoluta es un 400
class ImportWorkspace private constructor(
    val dir: Path
) : AutoCloseable {
    fun resolveEntry(name: String): Path {
        val resolved = dir.resolve(name).normalize()
        if (!resolved.startsWith(dir) || Path.of(name).isAbsolute) {
            throw ValidationException("Archivo inválido", "file", "una entrada del archivo apunta fuera de su carpeta: $name")
        }
        return resolved
    }

    override fun close() {
        dir.toFile().deleteRecursively()
    }

    companion object {
        fun create(): ImportWorkspace = ImportWorkspace(Files.createTempDirectory("sgspe-import-").toRealPath())
    }
}

// lectura de las hojas XLSX y CSV y de los GeoJSON de F2 (gis.md §4.1). la primera hoja, la primera fila como
// encabezado; un CSV con «;» o «,», con comillas, y con coma decimal (las hojas del Perú suelen traerla)
object ImportReaders {
    private val JSON: JsonMapper = JsonMapper.builder().build()

    fun read(
        file: File,
        filename: String,
        maxRows: Int
    ): List<SourceRow> {
        val name = filename.lowercase()
        val rows =
            when {
                name.endsWith(".xlsx") -> xlsx(file, maxRows)
                name.endsWith(".csv") -> csv(decode(file.readBytes()))
                name.endsWith(".geojson") || name.endsWith(".json") -> geojson(file)
                else -> throw ValidationException("Formato no admitido", "file", "en F2 se importan XLSX, CSV y GeoJSON")
            }
        if (rows.size > maxRows) throw tooManyRows(rows.size, maxRows)
        return rows
    }

    private fun tooManyRows(
        rows: Int,
        maxRows: Int
    ) = ValidationException("Archivo demasiado grande", "file", "tiene $rows filas; el máximo es $maxRows")

    // el CSV de Excel en español sale en UTF-8 (con o sin BOM) o, si no se eligió esa opción, en Windows-1252: sin esta
    // vuelta las tildes de los encabezados («Código») se leerían como caracteres de reemplazo y el mapeo no los hallaría
    private fun decode(bytes: ByteArray): String {
        val text =
            try {
                Charsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString()
            } catch (_: CharacterCodingException) {
                String(bytes, Charset.forName("windows-1252"))
            }
        return text.removePrefix("\uFEFF")
    }

    fun xlsx(
        file: File,
        maxRows: Int = Int.MAX_VALUE
    ): List<SourceRow> =
        try {
            WorkbookFactory.create(file, null, true).use { workbook ->
                val sheet = workbook.getSheetAt(0)
                val formatter = DataFormatter()
                // el tope se verifica antes de materializar las filas: una hoja enorme no llena la memoria
                if (sheet.lastRowNum - sheet.firstRowNum > maxRows) throw tooManyRows(sheet.lastRowNum - sheet.firstRowNum, maxRows)
                val header = sheet.getRow(sheet.firstRowNum) ?: return emptyList()
                val names = (0 until header.lastCellNum).map { formatter.formatCellValue(header.getCell(it)).trim() }
                ((sheet.firstRowNum + 1)..sheet.lastRowNum).mapNotNull { index ->
                    val row = sheet.getRow(index) ?: return@mapNotNull null
                    val values =
                        names.withIndex().associate { (column, title) ->
                            val cell = row.getCell(column)
                            val value =
                                when {
                                    cell == null -> null
                                    cell.cellType == CellType.NUMERIC -> BigDecimal(cell.numericCellValue.toString()).stripTrailingZeros().toPlainString()
                                    else -> formatter.formatCellValue(cell).trim().ifEmpty { null }
                                }
                            title to value
                        }
                    if (values.values.all { it == null }) null else SourceRow(index + 1, values)
                }
            }
        } catch (e: ValidationException) {
            throw e
        } catch (e: Exception) {
            // un XLSX roto es un archivo inválido, no un 500. una bomba zip (la razón de compresión o el tamaño de la
            // entrada que vigila ZipSecureFile de POI) se nombra, para que el 400 diga qué límite se superó (RQ-SEC-020)
            val chain = generateSequence<Throwable>(e) { it.cause }.mapNotNull { it.message }.toList()
            val detail =
                if (chain.any { "Zip bomb" in it || "ZipSecureFile" in it }) {
                    "el XLSX supera los límites de descompresión: su contenido es mucho mayor que el archivo"
                } else {
                    "no se pudo leer el XLSX: ${e.javaClass.simpleName}"
                }
            throw ValidationException("Archivo inválido", "file", detail)
        }

    fun csv(text: String): List<SourceRow> {
        val lines = records(text)
        if (lines.isEmpty()) return emptyList()
        val header = lines.first().map { it.trim() }
        return lines.drop(1).withIndex().mapNotNull { (index, cells) ->
            if (cells.all { it.isBlank() }) return@mapNotNull null
            SourceRow(index + 2, header.withIndex().associate { (column, title) -> title to cells.getOrNull(column)?.trim()?.ifEmpty { null } })
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun geojson(file: File): List<SourceRow> {
        val root =
            try {
                JSON.readValue(file, Map::class.java) as Map<String, Any?>
            } catch (_: Exception) {
                throw ValidationException("Archivo inválido", "file", "no es un GeoJSON válido")
            }
        val features =
            when (root["type"]) {
                "FeatureCollection" -> (root["features"] as? List<Map<String, Any?>>).orEmpty()
                "Feature" -> listOf(root)
                else -> throw ValidationException("Archivo inválido", "file", "se espera un FeatureCollection o un Feature")
            }
        return features.withIndex().map { (index, feature) ->
            val properties = (feature["properties"] as? Map<String, Any?>).orEmpty()
            SourceRow(index + 1, properties.mapValues { it.value?.toString() }, feature["geometry"] as? Map<String, Any?>)
        }
    }

    // RFC 4180 con el separador del encabezado: «;» si lo trae y no trae «,»
    private fun records(text: String): List<List<String>> {
        val firstLine = text.lineSequence().firstOrNull().orEmpty()
        val separator = if (';' in firstLine && ',' !in firstLine) ';' else ','
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                quoted && c == '"' && text.getOrNull(i + 1) == '"' -> {
                    cell.append('"')
                    i++
                }
                c == '"' -> quoted = !quoted
                !quoted && c == separator -> {
                    row += cell.toString()
                    cell.clear()
                }
                !quoted && (c == '\n' || c == '\r') -> {
                    if (c == '\r' && text.getOrNull(i + 1) == '\n') i++
                    row += cell.toString()
                    cell.clear()
                    rows += row
                    row = mutableListOf()
                }
                else -> cell.append(c)
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) {
            row += cell.toString()
            rows += row
        }
        return rows
    }

    // un número de una celda: punto o coma decimal; null si no es un número
    fun number(raw: String?): BigDecimal? {
        val value = raw?.trim()?.ifEmpty { null } ?: return null
        val normalized = if (',' in value && '.' !in value) value.replace(',', '.') else value.replace(",", "")
        return normalized.toBigDecimalOrNull()
    }
}

// ejes invertidos, detectados por sus rangos (gis.md §4.3): la fila se marca con la sugerencia de corrección
object Axes {
    fun utmSwapped(
        easting: BigDecimal,
        northing: BigDecimal
    ): String? {
        val e = easting.toDouble()
        val n = northing.toDouble()
        return if (e > 1_000_000 && n in 100_000.0..900_000.0) "el este y el norte parecen invertidos: intercambie las columnas" else null
    }

    fun latLonSwapped(
        latitude: BigDecimal,
        longitude: BigDecimal
    ): String? {
        val lat = latitude.toDouble()
        val lon = longitude.toDouble()
        val swapped = kotlin.math.abs(lat) > 90 || (lat in -82.0..-68.0 && lon in -19.0..1.0)
        return if (swapped) "la latitud y la longitud parecen invertidas: intercambie las columnas" else null
    }
}

// el mapeo de columnas de la parte `columns`: {"code": "Hole ID", "easting": "Este", "depth": "Prof"}. la clave es un
// nombre lógico (code, easting, northing, elevation, latitude, longitude) o un campo del destino, en snake_case o en el
// camelCase del portal (drillType es drill_type; depth es depth_m); el valor, el encabezado de la hoja o la propiedad del
// GeoJSON, o una constante si empieza con «=». lo que no se mapea se busca por su propio nombre
class ColumnMapping(
    private val mapping: Map<String, String>
) {
    fun value(
        row: SourceRow,
        key: String
    ): String? {
        val source = mapping[key] ?: return row.values[key]?.trim()?.ifEmpty { null }
        if (source.startsWith("=")) return source.removePrefix("=").trim().ifEmpty { null }
        return row.values[source]?.trim()?.ifEmpty { null }
    }

    companion object {
        private val JSON =
            tools.jackson.databind.json.JsonMapper
                .builder()
                .build()

        @Suppress("UNCHECKED_CAST")
        fun parse(raw: String?): ColumnMapping {
            if (raw.isNullOrBlank()) return ColumnMapping(emptyMap())
            val parsed =
                try {
                    JSON.readValue(raw, Map::class.java) as Map<String, Any?>
                } catch (_: Exception) {
                    throw ValidationException("Columnas inválidas", listOf(FieldViolation("columns", "debe ser un objeto json {campo: encabezado}")))
                }
            return ColumnMapping(parsed.entries.associate { (key, value) -> canonical(key) to value?.toString().orEmpty() })
        }

        private val ALIASES = mapOf("depth" to "depth_m")

        // drillType -> drill_type; depth -> depth_m
        fun canonical(key: String): String {
            val snake = key.replace(Regex("([a-z0-9])([A-Z])"), "$1_$2").lowercase()
            return ALIASES[snake] ?: snake
        }
    }
}
