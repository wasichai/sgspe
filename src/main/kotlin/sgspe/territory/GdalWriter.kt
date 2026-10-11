package sgspe.territory

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withTimeout
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Duration
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

// los formatos de una exportación geográfica (especificación de F6 §3.5): geojson, csv y xlsx los escribe el código; kml,
// gpkg y shp, GDAL desde un GeoJSON EPSG:4326 (driver de la lista blanca). shp sale como ZIP con .cpg UTF-8
enum class GeoExportFormat(
    val key: String,
    val extension: String,
    val mediaType: String,
    val driver: String?,
    val layerOptions: List<String> = emptyList()
) {
    GEOJSON("geojson", "geojson", "application/geo+json", null),
    CSV("csv", "csv", "text/csv;charset=UTF-8", null),
    XLSX("xlsx", "xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", null),
    KML("kml", "kml", "application/vnd.google-earth.kml+xml", "KML"),
    GPKG("gpkg", "gpkg", "application/geopackage+sqlite3", "GPKG"),
    SHP("shp", "zip", "application/zip", "ESRI Shapefile", listOf("ENCODING=UTF-8"));

    companion object {
        fun parse(raw: Any?): GeoExportFormat? = entries.firstOrNull { it.key == raw }
    }
}

// la escritura con GDAL de una exportación: ogr2ogr en una carpeta propia junto al archivo de salida (la de ExportFile, 0700),
// con argumentos fijos (GdalCommand.fromGeoJson): -nln con el nombre del objeto, -t_srs solo para gpkg y shp en UTM, KML
// siempre en grados. el resultado se mueve al archivo de salida (o se comprime, el Shapefile) y la carpeta de trabajo, con el
// error de GDAL y todo lo demás, se borra: al ZIP solo entran las partes del Shapefile. write() es bloqueante; writeWithin()
// la corre en el despachador de E/S, interrumpible y con plazo: vencido, ogr2ogr muere con sus hijos
@Component
class GdalWriter(
    private val tools: GdalTools
) {
    suspend fun writeWithin(
        format: GeoExportFormat,
        geojson: Path,
        layerName: String,
        targetSrid: Int?,
        out: Path,
        timeout: Duration = tools.timeout
    ) = withTimeout(timeout.toMillis()) { runInterruptible(Dispatchers.IO) { write(format, geojson, layerName, targetSrid, out) } }

    fun write(
        format: GeoExportFormat,
        geojson: Path,
        layerName: String,
        targetSrid: Int?,
        out: Path
    ) {
        val driver = format.driver?.takeIf { it in DRIVERS } ?: error("$format lo escribe el código, no GDAL")
        require(LAYER.matches(layerName)) { "nombre de capa inválido" }
        val status = tools.require(driver, format.key)
        val work = Files.createTempDirectory(out.toAbsolutePath().parent, "gdal-")
        try {
            val target = if (format == GeoExportFormat.SHP) work.resolve("$layerName.shp") else work.resolve("capa.${format.extension}")
            val srid = targetSrid.takeIf { format != GeoExportFormat.KML }
            val command = GdalCommand.fromGeoJson(status.ogr2ogr!!, driver, geojson.toAbsolutePath(), target, layerName, srid, format.layerOptions)
            val exit = tools.run(command, work, work.resolve("ogr2ogr.out"))
            check(exit == 0 && Files.exists(target)) { "GDAL no pudo escribir ${format.key}" }
            if (format == GeoExportFormat.SHP) zip(work, layerName, out) else Files.move(target, out, StandardCopyOption.REPLACE_EXISTING)
        } finally {
            work.toFile().deleteRecursively()
        }
    }

    // solo las partes de un Shapefile, por nombre: ni el error de GDAL ni la salida entran al ZIP
    private fun zip(
        dir: Path,
        layer: String,
        out: Path
    ) {
        val parts = SHAPEFILE_PARTS.map { dir.resolve("$layer.$it") }.filter { Files.isRegularFile(it) }
        ZipOutputStream(Files.newOutputStream(out)).use { zip ->
            for (part in parts) {
                zip.putNextEntry(ZipEntry(part.fileName.toString()))
                Files.copy(part, zip)
                zip.closeEntry()
            }
        }
    }

    companion object {
        // la lista blanca de drivers de salida (ASM-127)
        private val DRIVERS = setOf("KML", "GPKG", "ESRI Shapefile")
        private val SHAPEFILE_PARTS = listOf("shp", "shx", "dbf", "prj", "cpg")
        private val LAYER = Regex("^[a-z][a-z0-9_]{0,62}$")
    }
}
