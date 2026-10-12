package sgspe.territory

import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Assumptions.assumeTrue
import tools.jackson.databind.json.JsonMapper
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

// GDAL para las pruebas: la carpeta con ogrinfo y ogr2ogr del PATH o de las rutas de siempre (Homebrew en la máquina de
// desarrollo, /usr/bin en la CI), y los fixtures de cada formato generados con ogr2ogr a partir de GeoJSON sintético
// (RQ-GIS-021): ningún binario versionado. sin GDAL, una prueba que lo necesita se salta en la máquina de desarrollo y falla
// en la CI, que lo instala (ci.yml, paso GDAL)
object GdalFixtures {
    private val JSON: JsonMapper = JsonMapper.builder().build()

    val bin: Path? by lazy {
        val path =
            System
                .getenv("PATH")
                .orEmpty()
                .split(File.pathSeparator)
                .filter { it.isNotBlank() }
        (path + listOf("/opt/homebrew/bin", "/usr/local/bin", "/usr/bin"))
            .map { Path.of(it) }
            .firstOrNull { Files.isExecutable(it.resolve("ogrinfo")) && Files.isExecutable(it.resolve("ogr2ogr")) }
    }

    fun requireGdal(): Path {
        val found = bin
        if (found == null) {
            if (System.getenv("CI") == "true") fail<Unit>("GDAL no está instalado: el paso GDAL de ci.yml lo instala")
            assumeTrue(false, "sin GDAL en el PATH: la prueba se salta")
        }
        return found!!
    }

    // ---- geometrías y colecciones sintéticas ----

    fun collection(features: List<Pair<Map<String, Any?>, Map<String, Any?>>>): String =
        JSON.writeValueAsString(
            mapOf(
                "type" to "FeatureCollection",
                "features" to features.map { (properties, geometry) -> mapOf("type" to "Feature", "properties" to properties, "geometry" to geometry) }
            )
        )

    // un cuadrado con su esquina en (x, y): metros en UTM o grados en 4326, según quien lo use
    private fun square(
        x: Double,
        y: Double,
        side: Double
    ): Map<String, Any?> =
        mapOf(
            "type" to "Polygon",
            "coordinates" to listOf(listOf(listOf(x, y), listOf(x + side, y), listOf(x + side, y + side), listOf(x, y + side), listOf(x, y)))
        )

    fun utmSquare(
        easting: Double,
        northing: Double,
        sideM: Double
    ) = square(easting, northing, sideM)

    fun lonLatSquare(
        longitude: Double,
        latitude: Double,
        sideDegrees: Double
    ) = square(longitude, latitude, sideDegrees)

    fun point(
        x: Double,
        y: Double
    ): Map<String, Any?> = mapOf("type" to "Point", "coordinates" to listOf(x, y))

    // ---- un archivo por formato ----

    // un ZIP con <layer>.shp, .shx, .dbf, .prj (del EPSG asignado, sin reproyectar) y .cpg UTF-8
    fun shapefileZip(
        dir: File,
        layer: String,
        geojson: String,
        epsg: Int
    ): File {
        val work = work(dir, "shp-$layer")
        val source = File(work, "fuente.geojson").apply { writeText(geojson) }
        ogr2ogr("-f", "ESRI Shapefile", "-a_srs", "EPSG:$epsg", "-lco", "ENCODING=UTF-8", File(work, "$layer.shp").path, source.path)
        return zip(File(dir, "$layer.zip"), work.listFiles()!!.filter { it.name.startsWith("$layer.") }.associate { it.name to it.readBytes() })
    }

    // un GeoPackage con una capa por entrada de `layers` (nombre de capa -> GeoJSON), todas con el mismo EPSG
    fun geopackage(
        dir: File,
        name: String,
        layers: Map<String, String>,
        epsg: Int
    ): File {
        val work = work(dir, "gpkg-$name")
        val target = File(dir, "$name.gpkg")
        layers.entries.forEachIndexed { i, (layer, geojson) ->
            val source = File(work, "capa-$i.geojson").apply { writeText(geojson) }
            val update = if (i == 0) emptyArray() else arrayOf("-update")
            ogr2ogr("-f", "GPKG", *update, "-a_srs", "EPSG:$epsg", "-nln", layer, target.path, source.path)
        }
        return target
    }

    // un ZIP con la carpeta datos.gdb (driver OpenFileGDB, que escribe desde GDAL 3.6)
    fun fileGdbZip(
        dir: File,
        layer: String,
        geojson: String,
        epsg: Int
    ): File {
        val work = work(dir, "gdb-$layer")
        val source = File(work, "fuente.geojson").apply { writeText(geojson) }
        val gdb = File(work, "datos.gdb")
        ogr2ogr("-f", "OpenFileGDB", "-a_srs", "EPSG:$epsg", "-nln", layer, gdb.path, source.path)
        return zip(File(dir, "$layer-gdb.zip"), gdb.listFiles()!!.associate { "datos.gdb/${it.name}" to it.readBytes() })
    }

    // un KMZ con doc.kml (4326); las propiedades Name y Description van a los campos del mismo nombre del driver KML
    fun kmz(
        dir: File,
        layer: String,
        geojson: String
    ): File {
        val work = work(dir, "kmz-$layer")
        val source = File(work, "fuente.geojson").apply { writeText(geojson) }
        val kml = File(work, "doc.kml")
        ogr2ogr("-f", "KML", "-nln", layer, kml.path, source.path)
        return zip(File(dir, "$layer.kmz"), mapOf("doc.kml" to kml.readBytes()))
    }

    // un GPX de puntos (4326): la propiedad name va al nombre del waypoint
    fun gpx(
        dir: File,
        name: String,
        geojson: String
    ): File {
        val work = work(dir, "gpx-$name")
        val source = File(work, "fuente.geojson").apply { writeText(geojson) }
        val target = File(dir, "$name.gpx")
        ogr2ogr("-f", "GPX", target.path, source.path)
        return target
    }

    fun zip(
        target: File,
        entries: Map<String, ByteArray>
    ): File {
        ZipOutputStream(target.outputStream()).use { out ->
            for ((entry, bytes) in entries) {
                out.putNextEntry(ZipEntry(entry))
                out.write(bytes)
                out.closeEntry()
            }
        }
        return target
    }

    // las capas de un archivo según ogrinfo, para revisar lo que escribió una exportación
    fun info(
        file: File,
        driver: String
    ): List<SourceLayer> = GdalInfo.layers(gdal("ogrinfo", "-json", "-so", "-if", driver, file.path))

    private fun work(
        dir: File,
        name: String
    ): File = File(dir, name).apply { mkdirs() }

    private fun ogr2ogr(vararg args: String) {
        gdal("ogr2ogr", *args)
    }

    private fun gdal(
        tool: String,
        vararg args: String
    ): String {
        val process = ProcessBuilder(listOf(requireGdal().resolve(tool).toString()) + args).redirectErrorStream(true).start()
        val output = process.inputStream.readAllBytes().toString(Charsets.UTF_8)
        check(process.waitFor(60, TimeUnit.SECONDS) && process.exitValue() == 0) { "$tool ${args.joinToString(" ")}: $output" }
        return output
    }
}
