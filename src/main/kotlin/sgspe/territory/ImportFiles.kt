package sgspe.territory

import org.apache.poi.openxml4j.opc.OPCPackage
import org.apache.poi.openxml4j.opc.PackageAccess
import org.apache.poi.openxml4j.util.ZipSecureFile
import org.apache.poi.util.XMLHelper
import org.apache.poi.xssf.eventusermodel.ReadOnlySharedStringsTable
import org.apache.poi.xssf.eventusermodel.XSSFReader
import org.springframework.boot.context.properties.ConfigurationProperties
import org.xml.sax.Attributes
import org.xml.sax.EntityResolver
import org.xml.sax.ErrorHandler
import org.xml.sax.InputSource
import org.xml.sax.SAXParseException
import org.xml.sax.helpers.DefaultHandler
import tools.jackson.core.JacksonException
import tools.jackson.core.JsonParser
import tools.jackson.core.JsonToken
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.json.JsonMapper
import wasichai.core.common.FieldViolation
import wasichai.core.common.ValidationException
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.Reader
import java.io.StringReader
import java.math.BigDecimal
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.time.Duration

// los límites de una importación (RQ-SEC-020, ASM-050): tamaño del archivo, filas, tiempo de lectura y validación,
// importaciones a la vez, la tolerancia de «sin cambio» (gis.md §4.4: 1 cm) y la envolvente del Perú para marcar lo que
// cae fuera de la región. maxConcurrent acota los hilos de E/S y las conexiones que ocupan las importaciones en curso; la
// que espera más que `timeout` por un lugar es un 503
@ConfigurationProperties("sgspe.territory.import")
data class ImportProperties(
    val maxBytes: Long = 20L * 1024 * 1024,
    val maxRows: Int = 10_000,
    val timeout: Duration = Duration.ofSeconds(60),
    val maxConcurrent: Int = 4,
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
        // el nombre lo escribe quien sube el archivo: no se repite en el mensaje, y un NUL es un 400 y no un 500
        val outside = ValidationException("Archivo inválido", "file", "una entrada del archivo apunta fuera de su carpeta")
        val resolved =
            try {
                if (Path.of(name).isAbsolute) throw outside
                dir.resolve(name).normalize()
            } catch (_: InvalidPathException) {
                throw outside
            }
        if (!resolved.startsWith(dir)) throw outside
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
// encabezado; un CSV con «;», «,» o tabulador, con comillas, y con coma decimal (las hojas del Perú suelen traerla).
// la memoria no depende del tamaño del archivo: los tres formatos se leen en flujo y se cortan en la fila maxRows + 1
// (RQ-SEC-020), así que una hoja enorme pero legítima se rechaza sin cargarla
object ImportReaders {
    // tope de bytes inflados de una entrada del ZIP de un XLSX. ZipSecureFile.setMaxEntrySize es un estático global de
    // POI, y aquí es seguro: POI solo lee en este archivo (las exportaciones escriben, y ZipSecureFile solo vigila lecturas)
    // y se fija una sola vez, al cargar este objeto. una hoja legítima de 10 000 filas pesa mucho menos de 64 MB de XML
    const val MAX_ENTRY_BYTES: Long = 64L * 1024 * 1024
    private const val MAX_COLUMNS = 16_384
    private const val DETECTION_CHARS = 65_536
    private const val INTERRUPT_EVERY = 1024
    private const val BOM = 0xFEFF

    // un número de un archivo cabe en 40 cifras significativas y un exponente de a lo más 40 (una coordenada UTM tiene 7 u 8
    // cifras): lo demás no es un dato, y expandir «1E+999999999» con toPlainString agota el heap con un archivo de 3 KB
    private const val MAX_DIGITS = 40
    private const val MAX_EXPONENT = 40

    // BigDecimal(String) es cuadrático en las cifras: una celda de 1,6 MB de dígitos tarda un minuto. por eso nada más largo que
    // esto se convierte: si solo tiene caracteres de número es un número fuera de rango, y si no, no es un número
    private const val MAX_NUMBER_CHARS = MAX_DIGITS + 12

    // el costo cuadrático está en la racha de dígitos, no en el largo total: una lista de cifras cortas pegada en una columna
    // de notas (`1,2,3,…`) es texto (parseDecimal ya no la convierte por su largo). solo una racha de más de MAX_DIGITS es un
    // número fuera de rango
    private fun tooLongNumber(text: String): Boolean =
        text.length > MAX_NUMBER_CHARS && text.all { it.isDigit() || it in "+-.,eE" } && longestDigitRun(text) > MAX_DIGITS

    private fun longestDigitRun(text: String): Int {
        var longest = 0
        var run = 0
        for (c in text) {
            run = if (c.isDigit()) run + 1 else 0
            if (run > longest) longest = run
        }
        return longest
    }

    private fun inBounds(value: BigDecimal): Boolean =
        value.precision() <= MAX_DIGITS && kotlin.math.abs(value.precision().toLong() - value.scale() - 1) <= MAX_EXPONENT

    private fun outOfRange(where: String) = ValidationException("Número fuera de rango", "file", "un número de $where está fuera de rango")

    private val JSON: JsonMapper = JsonMapper.builder().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).build()

    init {
        ZipSecureFile.setMaxEntrySize(MAX_ENTRY_BYTES)
    }

    fun read(
        file: File,
        filename: String,
        maxRows: Int
    ): List<SourceRow> {
        val name = filename.lowercase()
        return when {
            name.endsWith(".xlsx") -> xlsx(file, maxRows)
            name.endsWith(".csv") -> csv(file, maxRows)
            name.endsWith(".geojson") || name.endsWith(".json") -> geojson(file, maxRows)
            else -> throw ValidationException("Formato no admitido", "file", "en F2 se importan XLSX, CSV y GeoJSON")
        }
    }

    private fun tooManyRows(maxRows: Int) = ValidationException("Archivo demasiado grande", "file", "tiene más de $maxRows filas; el máximo es $maxRows")

    // el tiempo de la importación lo corta quien la llama interrumpiendo el hilo (runInterruptible): se atiende cada tanto
    private fun checkInterrupted() {
        if (Thread.interrupted()) throw InterruptedException("importación cancelada")
    }

    // arma las filas de una hoja o un CSV: la primera con datos es el encabezado; una fila vacía se salta y no cuenta para el
    // tope; un encabezado repetido, o una columna con datos y sin encabezado, es un 400 que la nombra (si no, una columna
    // taparía a otra en silencio)
    private class RowCollector(
        private val maxRows: Int
    ) {
        val rows = ArrayList<SourceRow>()
        private var header: List<String>? = null
        private var seen = 0

        fun accept(
            row: Int,
            cells: List<String?>
        ) {
            if (++seen % INTERRUPT_EVERY == 0) checkInterrupted()
            if (cells.all { it == null }) return
            val titles = header
            if (titles == null) {
                val names = cells.map { it?.trim().orEmpty() }
                names.filter { it.isNotEmpty() }.groupingBy { it }.eachCount().entries.firstOrNull { it.value > 1 }?.let {
                    throw ValidationException("Encabezado inválido", "file", "el encabezado «${it.key}» está repetido")
                }
                header = names
                return
            }
            cells.withIndex().firstOrNull { (column, value) -> value != null && titles.getOrNull(column).isNullOrEmpty() }?.let {
                throw ValidationException("Encabezado inválido", "file", "la columna ${it.index + 1} tiene datos y no tiene encabezado (fila $row)")
            }
            if (rows.size >= maxRows) throw tooManyRows(maxRows)
            rows += SourceRow(row, titles.withIndex().filter { it.value.isNotEmpty() }.associate { (column, title) -> title to cells.getOrNull(column) })
        }
    }

    // ---- XLSX, en flujo -------------------------------------------------------------------------------------------

    fun xlsx(
        file: File,
        maxRows: Int = Int.MAX_VALUE
    ): List<SourceRow> {
        val collector = RowCollector(maxRows)
        try {
            OPCPackage.open(file, PackageAccess.READ).use { pkg ->
                val strings = ReadOnlySharedStringsTable(pkg, false)
                val sheets = XSSFReader(pkg).sheetsData
                if (!sheets.hasNext()) return emptyList()
                sheets.next().use { sheet ->
                    val reader = XMLHelper.newXMLReader()
                    // XMLHelper ya rechaza el DOCTYPE (XXE); aquí además nada se resuelve y nada se imprime
                    reader.entityResolver = EntityResolver { _, _ -> InputSource(StringReader("")) }
                    reader.errorHandler = QuietErrors
                    reader.contentHandler = SheetHandler(strings, collector)
                    reader.parse(InputSource(sheet))
                }
            }
        } catch (e: ValidationException) {
            throw e
        } catch (e: InterruptedException) {
            throw e
        } catch (e: Exception) {
            // el lector SAX envuelve la interrupción de un hilo cancelado: se devuelve tal cual, para que la cancelación se
            // propague y no se confunda con un archivo inválido
            generateSequence<Throwable>(e) { it.cause }.filterIsInstance<InterruptedException>().firstOrNull()?.let { throw it }
            throw ValidationException("Archivo inválido", "file", xlsxDetail(e))
        }
        return collector.rows
    }

    // un XLSX roto es un archivo inválido, no un 500. una bomba zip (la razón de compresión o el tamaño de la entrada que
    // vigila ZipSecureFile) y un DOCTYPE se nombran, para que el 400 diga qué se rechazó (RQ-SEC-020). nunca se repite el
    // mensaje de POI: puede traer rutas
    private fun xlsxDetail(e: Exception): String {
        val chain = generateSequence<Throwable>(e) { it.cause }.toList()
        val messages = chain.mapNotNull { it.message }
        return when {
            messages.any { "Zip bomb" in it || "ZipSecureFile" in it } ->
                "el XLSX supera los límites de descompresión: su contenido es mucho mayor que el archivo"
            chain.any { it is SAXParseException && it.message?.contains("DOCTYPE") == true } ->
                "el XLSX trae una declaración DOCTYPE, que no se admite"
            else -> "no se pudo leer el XLSX: ${e.javaClass.simpleName}"
        }
    }

    private object QuietErrors : ErrorHandler {
        override fun warning(e: SAXParseException) = Unit

        override fun error(e: SAXParseException): Unit = throw e

        override fun fatalError(e: SAXParseException): Unit = throw e
    }

    // las celdas de la hoja: los números tal como están escritos (sin notación científica), las cadenas compartidas o en
    // línea, y de una fórmula su valor guardado, nunca la fórmula ni una evaluación
    private class SheetHandler(
        private val strings: ReadOnlySharedStringsTable,
        private val collector: RowCollector
    ) : DefaultHandler() {
        private val cells = ArrayList<String?>()
        private val text = StringBuilder()
        private var rowNumber = 0
        private var lastRow = 0
        private var column = 0
        private var type: String? = null
        private var collecting = false
        private var inInline = false
        private var inPhonetic = false
        private var value: String? = null

        override fun startElement(
            uri: String?,
            localName: String,
            qName: String?,
            attributes: Attributes
        ) {
            when (localName) {
                "row" -> {
                    rowNumber = attributes.getValue("r")?.toIntOrNull() ?: (lastRow + 1)
                    lastRow = rowNumber
                    cells.clear()
                }
                "c" -> {
                    column = attributes.getValue("r")?.let(::columnOf) ?: cells.size
                    if (column !in 0 until MAX_COLUMNS) throw ValidationException("Archivo inválido", "file", "la hoja tiene más de $MAX_COLUMNS columnas")
                    type = attributes.getValue("t")
                    value = null
                    text.clear()
                }
                "v" -> {
                    text.clear()
                    collecting = true
                }
                "is" -> {
                    text.clear()
                    inInline = true
                }
                "rPh" -> inPhonetic = true
                "t" -> collecting = inInline && !inPhonetic
            }
        }

        override fun characters(
            ch: CharArray,
            start: Int,
            length: Int
        ) {
            if (collecting) text.append(ch, start, length)
        }

        override fun endElement(
            uri: String?,
            localName: String,
            qName: String?
        ) {
            when (localName) {
                "v" -> {
                    value = typed(text.toString())
                    collecting = false
                }
                "t" -> collecting = false
                "rPh" -> inPhonetic = false
                "is" -> {
                    value = text.toString()
                    inInline = false
                }
                "c" -> {
                    while (cells.size <= column) cells += null
                    cells[column] = value?.trim()?.ifEmpty { null }
                }
                "row" -> collector.accept(rowNumber, cells.toList())
            }
        }

        private fun typed(raw: String): String? =
            when (type) {
                "s" -> raw.trim().toIntOrNull()?.let { strings.getItemAt(it).string }
                "str", "inlineStr", "d" -> raw
                "b" -> if (raw.trim() == "1") "TRUE" else "FALSE"
                "e" -> null
                else -> {
                    val text = raw.trim()
                    if (tooLongNumber(text)) throw outOfRange("la fila $rowNumber, columna ${column + 1}")
                    val number = if (text.length > MAX_NUMBER_CHARS) null else text.toBigDecimalOrNull()
                    if (number != null && !inBounds(number)) throw outOfRange("la fila $rowNumber, columna ${column + 1}")
                    number?.stripTrailingZeros()?.toPlainString() ?: raw
                }
            }

        // «AB12» -> 27 (A es 0)
        private fun columnOf(reference: String): Int? {
            val letters = reference.takeWhile { it.isLetter() }
            if (letters.isEmpty() || letters.length > 3) return null
            return letters.fold(0) { acc, letter -> acc * 26 + (letter.uppercaseChar() - 'A' + 1) } - 1
        }
    }

    // ---- CSV, en flujo ----------------------------------------------------------------------------------------------

    // el CSV de Excel en español sale en UTF-8 (con o sin BOM) o, si no se eligió esa opción, en Windows-1252: sin esta
    // vuelta las tildes de los encabezados («Código») se leerían como caracteres de reemplazo y el mapeo no los hallaría.
    // se lee en flujo con UTF-8 estricto y, si encuentra un byte que no lo es, se reinicia la lectura en Windows-1252
    fun csv(
        file: File,
        maxRows: Int = Int.MAX_VALUE
    ): List<SourceRow> =
        try {
            open(file, Charsets.UTF_8).use { csv(it, maxRows) }
        } catch (_: CharacterCodingException) {
            open(file, Charset.forName("windows-1252")).use { csv(it, maxRows) }
        }

    private fun open(
        file: File,
        charset: Charset
    ): Reader =
        InputStreamReader(
            java.io.FileInputStream(file), // no Files.newInputStream: su canal se cierra con la interrupción del hilo
            charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
        )

    fun csv(
        text: String,
        maxRows: Int = Int.MAX_VALUE
    ): List<SourceRow> = csv(StringReader(text), maxRows)

    // RFC 4180 con tolerancia a las hojas escritas a mano: una comilla abre un campo solo al inicio de él; dentro, «""» es una
    // comilla y la comilla cierra solo antes de un separador, un salto de línea o el fin; en cualquier otro lugar es literal
    // («tubo 2" de acero»). una comilla sin cerrar al final es un 400 y no una celda gigante
    fun csv(
        reader: Reader,
        maxRows: Int = Int.MAX_VALUE
    ): List<SourceRow> {
        val input = if (reader is BufferedReader) reader else BufferedReader(reader)
        // el BOM se salta antes de medir el separador: si no, la comilla de un encabezado citado no abriría
        input.mark(1)
        if (input.read() != BOM) input.reset()
        val separator = detectSeparator(input).code
        val collector = RowCollector(maxRows)
        val cells = ArrayList<String?>()
        val cell = StringBuilder()
        var quoted = false
        var atStart = true
        var record = 1
        var current = input.read()

        fun endCell() {
            if (cells.size >= MAX_COLUMNS) throw ValidationException("Archivo inválido", "file", "la fila $record tiene más de $MAX_COLUMNS columnas")
            val value = cell.toString().trim().ifEmpty { null }
            if (value != null && tooLongNumber(value)) throw outOfRange("la fila $record, columna ${cells.size + 1}")
            if (value != null && (value[0].isDigit() || value[0] in "+-.,")) {
                parseDecimal(value)?.let { if (!inBounds(it)) throw outOfRange("la fila $record, columna ${cells.size + 1}") }
            }
            cells += value
            cell.clear()
            atStart = true
        }

        fun endRecord() {
            endCell()
            collector.accept(record, cells.toList())
            cells.clear()
            record++
        }
        while (current >= 0) {
            val next = input.read()
            var consumed = false
            when {
                quoted ->
                    when {
                        current == '"'.code && next == '"'.code -> {
                            cell.append('"')
                            consumed = true
                        }
                        current == '"'.code && (next < 0 || next == separator || next == '\n'.code || next == '\r'.code) -> quoted = false
                        else -> cell.append(current.toChar())
                    }
                current == '"'.code && atStart -> {
                    quoted = true
                    atStart = false
                }
                current == separator -> endCell()
                current == '\n'.code || current == '\r'.code -> {
                    if (current == '\r'.code && next == '\n'.code) consumed = true
                    endRecord()
                }
                else -> {
                    cell.append(current.toChar())
                    atStart = false
                }
            }
            current = if (consumed) input.read() else next
        }
        if (quoted) throw ValidationException("Archivo inválido", "file", "una comilla sin cerrar en la fila $record")
        if (cell.isNotEmpty() || cells.isNotEmpty()) endRecord()
        return collector.rows
    }

    // el separador es el más frecuente en el encabezado fuera de comillas: «;», «,» o tabulador (Excel «Texto Unicode»). un
    // empate cae a «;». límite conocido: un encabezado con «;» y comas sin comillas («Este (m, UTM);Norte (m, UTM);Cota (m)»)
    // gana la coma; el mapeo no halla las columnas y el 400 lo hace visible
    private fun detectSeparator(input: BufferedReader): Char {
        val candidates = listOf(';', ',', '\t')
        val counts = IntArray(candidates.size)
        input.mark(DETECTION_CHARS)
        var quoted = false
        var atStart = true
        var read = 0
        while (read++ < DETECTION_CHARS) {
            val c = input.read()
            if (c < 0) break
            when {
                quoted -> if (c == '"'.code) quoted = false
                c == '"'.code && atStart -> {
                    quoted = true
                    atStart = false
                }
                c == '\n'.code || c == '\r'.code -> break
                else -> {
                    val index = candidates.indexOf(c.toChar())
                    if (index >= 0) counts[index]++
                    atStart = index >= 0
                }
            }
        }
        input.reset()
        val best = counts.max()
        return if (best == 0) ',' else candidates[counts.indexOfFirst { it == best }]
    }

    // ---- GeoJSON, en flujo --------------------------------------------------------------------------------------------

    // lee el documento por tokens: cada feature se materializa sola y la lectura se corta en el feature maxRows + 1. los
    // números decimales se leen como BigDecimal desde el texto del archivo, así que ni las propiedades ni las coordenadas
    // pasan por un Double
    @Suppress("UNCHECKED_CAST")
    fun geojson(
        file: File,
        maxRows: Int = Int.MAX_VALUE
    ): List<SourceRow> {
        val invalid = ValidationException("Archivo inválido", "file", "no es un GeoJSON válido")
        val rows = ArrayList<SourceRow>()
        var type: String? = null
        var properties: Map<String, Any?>? = null
        var geometry: Map<String, Any?>? = null
        try {
            JSON.createParser(file).use { parser ->
                if (parser.nextToken() != JsonToken.START_OBJECT) throw invalid
                while (parser.nextToken() == JsonToken.PROPERTY_NAME) {
                    val name = parser.currentName()
                    val token = parser.nextToken()
                    when {
                        name == "type" && token == JsonToken.VALUE_STRING -> type = parser.string
                        name == "features" && token == JsonToken.START_ARRAY ->
                            while (parser.nextToken() != JsonToken.END_ARRAY) {
                                if (parser.currentToken() != JsonToken.START_OBJECT) {
                                    throw ValidationException("Archivo inválido", "file", "el elemento ${rows.size + 1} de features no es un objeto")
                                }
                                if (rows.size >= maxRows) throw tooManyRows(maxRows)
                                if (rows.size % INTERRUPT_EVERY == 0) checkInterrupted()
                                rows += featureRow(rows.size + 1, parser.readValueAs(Map::class.java) as Map<String, Any?>)
                            }
                        name == "properties" && token == JsonToken.START_OBJECT -> properties = parser.readValueAs(Map::class.java) as Map<String, Any?>
                        name == "geometry" && token == JsonToken.START_OBJECT -> geometry = parser.readValueAs(Map::class.java) as Map<String, Any?>
                        else -> parser.skipChildren()
                    }
                }
                // tras el objeto raíz no debe quedar nada: un documento truncado o con basura no se lee a medias
                if (parser.nextToken() != null) throw invalid
            }
        } catch (e: JacksonException) {
            throw invalid
        } catch (e: ClassCastException) {
            throw invalid
        }
        return when (type) {
            "FeatureCollection" -> rows
            "Feature" -> listOf(featureRow(1, mapOf("properties" to properties, "geometry" to geometry)))
            else -> throw ValidationException("Archivo inválido", "file", "se espera un FeatureCollection o un Feature")
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun featureRow(
        index: Int,
        feature: Map<String, Any?>
    ): SourceRow {
        checkNumbers(feature, index)
        val properties = (feature["properties"] as? Map<String, Any?>).orEmpty()
        return SourceRow(index, properties.mapValues { text(it.value) }, feature["geometry"] as? Map<String, Any?>)
    }

    // propiedades y coordenadas dentro de las cotas de un número (MAX_DIGITS, MAX_EXPONENT): un 400 que nombra el feature
    private fun checkNumbers(
        value: Any?,
        index: Int
    ) {
        when (value) {
            is BigDecimal -> if (!inBounds(value)) throw outOfRange("el feature $index")
            is Map<*, *> -> value.values.forEach { checkNumbers(it, index) }
            is List<*> -> value.forEach { checkNumbers(it, index) }
        }
    }

    // un decimal sin notación científica; lo anidado, como texto
    private fun text(value: Any?): String? =
        when (value) {
            null -> null
            is BigDecimal -> value.stripTrailingZeros().toPlainString()
            else -> value.toString()
        }

    // un número de una celda: punto o coma decimal; con las dos presentes, la última es la decimal («1.234,56» y
    // «1,234.5»); una sola coma es decimal. null si no es un número
    fun number(raw: String?): BigDecimal? = parseDecimal(raw)?.takeIf(::inBounds)

    private fun parseDecimal(raw: String?): BigDecimal? {
        val value = raw?.trim()?.ifEmpty { null }?.takeIf { it.length <= MAX_NUMBER_CHARS } ?: return null
        val comma = value.lastIndexOf(',')
        val dot = value.lastIndexOf('.')
        val normalized =
            when {
                comma >= 0 && dot >= 0 && comma > dot -> value.replace(".", "").replace(',', '.')
                comma >= 0 && dot >= 0 -> value.replace(",", "")
                comma >= 0 -> value.replace(',', '.')
                else -> value
            }
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
