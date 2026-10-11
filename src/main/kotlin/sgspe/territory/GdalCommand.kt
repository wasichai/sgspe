package sgspe.territory

import org.springframework.http.HttpStatus
import sgspe.common.Finding
import sgspe.common.toCodedException
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import wasichai.core.common.ValidationException
import wasichai.core.common.WasichaiException
import java.nio.file.Path

// una capa de un archivo según ogrinfo: su nombre, el tipo de su primera geometría, cuántos elementos tiene (null si el driver
// no lo sabe sin recorrerla) y su CRS como valor del catálogo «CRS de origen» («EPSG 32717»), o null si no tiene uno del
// catálogo. el CRS detectado es una propuesta: quien importa lo confirma (CON-024, especificación de F6 §2.2.6)
data class SourceLayer(
    val name: String,
    val geometryType: String?,
    val featureCount: Long?,
    val detectedCrs: String?,
    // cuántos campos tiene y cuántos bytes ocupan sus nombres: el tamaño del GeoJSON crece con ambos, no solo con -limit
    val fieldCount: Int = 0,
    val fieldNameBytes: Long = 0
)

// los comandos de GDAL (ASM-127, especificación de F6 §3.4): argumentos fijos más rutas absolutas del workspace o de la
// carpeta de una exportación, nunca una URL, una ruta ni una opción del cliente; el único texto que viene de quien sube es el
// nombre de una capa, y solo después de que ogrinfo la listó. el entorno del proceso empieza vacío: sin HOME (ningún gdalrc),
// sin GDAL_HTTP_* ni credenciales, con /vsicurl/ cerrado (CPL_VSIL_CURL_ALLOWED_EXTENSIONS vacío no admite ninguna extensión)
// y sin listar carpetas al abrir. los KML los lee el driver KML, que no sigue NetworkLink (LIBKML sí). puro
object GdalCommand {
    private val VERSION = Regex("""GDAL (\d+)\.(\d+)""")
    private val DRIVER = Regex("""^\s+(.+?) -[a-z,]*vector[a-z,]*- \(""", RegexOption.MULTILINE)
    private val PASSTHROUGH = setOf("GDAL_DATA", "PROJ_DATA", "PROJ_LIB", "TMPDIR")

    fun info(
        ogrinfo: Path,
        driver: String,
        source: Path
    ): List<String> = listOf(ogrinfo.toString(), "-json", "-so", "-if", driver, absolute(source))

    fun toGeoJson(
        ogr2ogr: Path,
        driver: String,
        source: Path,
        layer: String,
        target: Path,
        limit: Int
    ): List<String> {
        requireSafeLayer(layer)
        return listOf(
            ogr2ogr.toString(),
            "-f",
            "GeoJSON",
            "-if",
            driver,
            "-dim",
            "XY",
            "-limit",
            limit.toString(),
            "-lco",
            "RFC7946=NO",
            absolute(target),
            absolute(source),
            layer
        )
    }

    // un GeoJSON EPSG:4326 hacia otro formato; reproyecta solo con targetSrid (GeoJSON se lee siempre como 4326)
    fun fromGeoJson(
        ogr2ogr: Path,
        driver: String,
        source: Path,
        target: Path,
        layerName: String,
        targetSrid: Int?,
        layerOptions: List<String>
    ): List<String> =
        listOf(ogr2ogr.toString(), "-f", driver, "-if", "GeoJSON", "-nln", layerName) +
            (targetSrid?.let { listOf("-t_srs", "EPSG:$it") } ?: emptyList()) +
            layerOptions.flatMap { listOf("-lco", it) } +
            listOf(absolute(target), absolute(source))

    fun environment(server: Map<String, String>): Map<String, String> =
        server.filterKeys { it in PASSTHROUGH } +
            mapOf(
                "CPL_VSIL_CURL_ALLOWED_EXTENSIONS" to "",
                "GDAL_DISABLE_READDIR_ON_OPEN" to "EMPTY_DIR",
                "GDAL_PAM_ENABLED" to "NO",
                "CPL_DEBUG" to "OFF"
            )

    fun version(output: String): Pair<Int, Int>? = VERSION.find(output)?.let { it.groupValues[1].toInt() to it.groupValues[2].toInt() }

    fun atLeast(
        version: Pair<Int, Int>,
        minimum: String
    ): Boolean {
        val (major, minor) = minimum.split('.').map(String::toInt)
        return version.first > major || (version.first == major && version.second >= minor)
    }

    fun drivers(output: String): Set<String> = DRIVER.findAll(output).map { it.groupValues[1].trim() }.toSet()

    fun formatUnavailable(
        format: String,
        field: String = "file"
    ): WasichaiException =
        Finding(
            "FORMAT_UNAVAILABLE",
            "Este servidor no puede leer ni escribir $format: falta GDAL o su driver",
            field,
            status = HttpStatus.valueOf(422)
        ).toCodedException()

    // el nombre de una capa sale de ogrinfo; aun así, uno que GDAL leería como opción se rechaza
    private fun requireSafeLayer(layer: String) {
        if (layer.isEmpty() || layer.startsWith("-")) throw ValidationException("Capa inválida", "layer", "no es una capa que GDAL pueda leer de este archivo")
    }

    private fun absolute(path: Path): String {
        require(path.isAbsolute) { "GDAL solo recibe rutas absolutas del workspace" }
        return path.toString()
    }
}

// la salida de ogrinfo -json -so: las capas, el tipo de su primera geometría, featureCount y el CRS (projjson.id o el ID
// final del WKT, que es el del CRS del archivo y no el de su CRS base). un CRS fuera del catálogo «CRS de origen» es null
object GdalInfo {
    private val JSON: JsonMapper = JsonMapper.builder().build()
    private val WKT_ID = Regex("""ID\["EPSG",\s*(\d+)]]\s*$""")

    fun layers(json: String): List<SourceLayer> =
        JSON.readTree(json).path("layers").values().map { layer ->
            val geometry = layer.path("geometryFields").values().firstOrNull()
            val count = layer.path("featureCount")
            val fields = layer.path("fields").values().toList()
            SourceLayer(
                name = layer.path("name").asString(),
                geometryType = geometry?.path("type")?.takeIf { it.isString }?.asString(),
                featureCount = if (count.isNumber) count.asLong() else null,
                detectedCrs = geometry?.let { epsg(it.path("coordinateSystem")) }?.takeIf(Crs::isCatalogued)?.let(Crs::value),
                fieldCount = fields.size,
                fieldNameBytes =
                    fields.sumOf {
                        it
                            .path("name")
                            .asString()
                            .toByteArray(Charsets.UTF_8)
                            .size
                            .toLong()
                    }
            )
        }

    fun epsg(coordinateSystem: JsonNode): Int? {
        val id = coordinateSystem.path("projjson").path("id")
        val authority = id.path("authority")
        if (authority.isString && authority.asString() == "EPSG" && id.path("code").isNumber) return id.path("code").asInt()
        val wkt = coordinateSystem.path("wkt").takeIf { it.isString }?.asString() ?: return null
        return WKT_ID
            .find(wkt)
            ?.groupValues
            ?.get(1)
            ?.toInt()
    }
}
