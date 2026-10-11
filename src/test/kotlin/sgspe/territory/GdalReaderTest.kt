package sgspe.territory

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import wasichai.core.common.ValidationException
import java.io.File
import java.math.BigDecimal
import java.nio.file.Files

// la lectura con GDAL (gis.md §4.1, especificación de F6 §3.4) sobre fixtures sintéticos en UTM 17S cerca de
// SyntheticGeo.ORIGIN (-78.50, -7.90 ≈ E 775 649, N 9 125 930): sin reproyectar, con el CRS detectado como propuesta y la
// capa elegida. códigos y nombres ficticios
class GdalReaderTest {
    @TempDir
    lateinit var dir: File

    private fun reader(properties: ImportProperties = ImportProperties()) =
        GdalReader(GdalTools(GdalProperties(path = GdalFixtures.requireGdal().toString())), properties)

    private fun upload(
        workspace: ImportWorkspace,
        file: File
    ): File = Files.copy(file.toPath(), workspace.resolveEntry("upload-${file.name}")).toFile()

    private val parcels =
        GdalFixtures.collection(
            listOf(
                mapOf("codigo" to "PRD-01", "tenencia" to "PROPIEDAD") to GdalFixtures.utmSquare(776_000.0, 9_126_000.0, 100.0),
                mapOf("codigo" to "PRD-02", "tenencia" to "COMUNAL") to GdalFixtures.utmSquare(776_200.0, 9_126_000.0, 100.0)
            )
        )

    @Suppress("UNCHECKED_CAST")
    private fun firstX(row: SourceRow): Double = ((row.geometry!!["coordinates"] as List<List<List<BigDecimal>>>)[0][0][0]).toDouble()

    @Test
    fun `un Shapefile UTM 17S se lee sin reproyectar y propone EPSG 32717`() {
        val reader = reader()
        val zip = GdalFixtures.shapefileZip(dir, "predios", parcels, 32717)
        ImportWorkspace.create().use { workspace ->
            val source = reader.read(ImportFormat.SHP, upload(workspace, zip), workspace, null)
            assertEquals("predios", source.layer.name)
            assertEquals("EPSG 32717", source.layer.detectedCrs)
            val rows = ImportReaders.geojson(source.geojson, 10)
            assertEquals(listOf("PRD-01", "PRD-02"), rows.map { it.values["codigo"] })
            assertEquals(776_000.0, firstX(rows.first()), 0.001)
        }
    }

    @Test
    fun `un GeoPackage con dos capas pide elegir y con la capa la convierte`() {
        val reader = reader()
        val communities =
            GdalFixtures.collection(
                listOf(mapOf("codigo" to "COM-01", "nombre" to "Comunidad Ejemplo") to GdalFixtures.utmSquare(776_000.0, 9_126_500.0, 300.0))
            )
        val gpkg = GdalFixtures.geopackage(dir, "capas", linkedMapOf("comunidades" to communities, "predios" to parcels), 32717)
        ImportWorkspace.create().use { workspace ->
            val file = upload(workspace, gpkg)
            val e = assertThrows<ImportChoiceRequired> { reader.read(ImportFormat.GPKG, file, workspace, null) }
            assertEquals("layer" to "LAYER_REQUIRED", e.violations.single().let { it.field to it.message })
            assertEquals(setOf("comunidades", "predios"), e.layers.map { it.name }.toSet())
            assertEquals(setOf("EPSG 32717"), e.layers.map { it.detectedCrs }.toSet())
            val source = reader.read(ImportFormat.GPKG, file, workspace, "comunidades")
            assertEquals("Comunidad Ejemplo", ImportReaders.geojson(source.geojson, 10).single().values["nombre"])
        }
    }

    @Test
    fun `una capa pedida que no esta en el archivo es 400 sobre layer`() {
        val gpkg = GdalFixtures.geopackage(dir, "una", mapOf("predios" to parcels), 32717)
        ImportWorkspace.create().use { workspace ->
            val e = assertThrows<ValidationException> { reader().read(ImportFormat.GPKG, upload(workspace, gpkg), workspace, "-sql") }
            assertEquals("layer", e.violations.single().field)
        }
    }

    @Test
    fun `un GPX con solo waypoints no pide capa`() {
        val points =
            GdalFixtures.collection(
                listOf(mapOf("name" to "DDH-01") to GdalFixtures.point(-78.50, -7.90), mapOf("name" to "DDH-02") to GdalFixtures.point(-78.49, -7.90))
            )
        val gpx = GdalFixtures.gpx(dir, "sondajes", points)
        ImportWorkspace.create().use { workspace ->
            val source = reader().read(ImportFormat.GPX, upload(workspace, gpx), workspace, null)
            assertEquals("waypoints", source.layer.name)
            assertEquals(listOf("DDH-01", "DDH-02"), ImportReaders.geojson(source.geojson, 10).map { it.values["name"] })
        }
    }

    @Test
    fun `un KMZ se descomprime y su kml se lee en grados`() {
        val kml =
            GdalFixtures.collection(
                listOf(mapOf("Name" to "COM-KMZ-01", "Description" to "Comunidad Ejemplo") to GdalFixtures.lonLatSquare(-78.50, -7.90, 0.01))
            )
        val kmz = GdalFixtures.kmz(dir, "comunidades", kml)
        ImportWorkspace.create().use { workspace ->
            val source = reader().read(ImportFormat.KMZ, upload(workspace, kmz), workspace, null)
            val row = ImportReaders.geojson(source.geojson, 10).single()
            assertEquals("COM-KMZ-01", row.values["Name"])
            assertEquals(-78.50, firstX(row), 0.000001)
        }
    }

    @Test
    fun `una FileGDB en ZIP se lee con OpenFileGDB`() {
        val features =
            GdalFixtures.collection(
                listOf(mapOf("codigo" to "AMB-01", "nombre" to "Zona de protección Ejemplo") to GdalFixtures.utmSquare(776_000.0, 9_127_000.0, 200.0))
            )
        val zip = GdalFixtures.fileGdbZip(dir, "elementos", features, 32717)
        ImportWorkspace.create().use { workspace ->
            val source = reader().read(ImportFormat.GDB, upload(workspace, zip), workspace, null)
            assertEquals("EPSG 32717", source.layer.detectedCrs)
            assertEquals("AMB-01", ImportReaders.geojson(source.geojson, 10).single().values["codigo"])
        }
    }

    @Test
    fun `una capa con mas elementos que el maximo es 400 sin convertirla y un archivo roto es 400 sobre file`() {
        val gpkg = GdalFixtures.geopackage(dir, "grande", mapOf("predios" to parcels), 32717)
        ImportWorkspace.create().use { workspace ->
            val e =
                assertThrows<ValidationException> { reader(ImportProperties(maxRows = 1)).read(ImportFormat.GPKG, upload(workspace, gpkg), workspace, null) }
            assertEquals("file", e.violations.single().field)
            val broken = File(dir, "roto.gpkg").apply { writeText("no es un GeoPackage") }
            assertEquals(
                "file",
                assertThrows<ValidationException> {
                    reader().read(ImportFormat.GPKG, upload(workspace, broken), workspace, null)
                }.violations.single().field
            )
            assertNull(Files.list(workspace.dir).use { list -> list.filter { it.fileName.toString().endsWith(".geojson") }.findFirst().orElse(null) })
        }
    }

    @Test
    fun `un listado de capas demasiado grande es 400 sobre file`() {
        val gpkg = GdalFixtures.geopackage(dir, "listado", mapOf("predios" to parcels), 32717)
        val small = GdalReader(GdalTools(GdalProperties(path = GdalFixtures.requireGdal().toString())), ImportProperties(), maxListingBytes = 10)
        ImportWorkspace.create().use { workspace ->
            val e = assertThrows<ValidationException> { small.read(ImportFormat.GPKG, upload(workspace, gpkg), workspace, null) }
            assertEquals("file", e.violations.single().field)
        }
    }
}
