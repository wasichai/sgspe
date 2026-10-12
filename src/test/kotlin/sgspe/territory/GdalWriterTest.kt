package sgspe.territory

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import wasichai.core.common.WasichaiException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.zip.ZipFile

// la escritura de KML, GeoPackage y Shapefile desde un GeoJSON EPSG:4326 (especificación de F6 §3.5): KML siempre en grados,
// GeoPackage y Shapefile en el CRS pedido, el Shapefile en ZIP con .cpg UTF-8. predios ficticios con tildes
class GdalWriterTest {
    @TempDir
    lateinit var dir: Path

    private val writer by lazy { GdalWriter(GdalTools(GdalProperties(path = GdalFixtures.requireGdal().toString()))) }

    private fun source(): Path =
        dir.resolve("capa.geojson").also {
            Files.writeString(
                it,
                GdalFixtures.collection(
                    listOf(
                        mapOf("code" to "PRD-01", "name" to "Predio Añaño") to GdalFixtures.lonLatSquare(-78.50, -7.90, 0.001),
                        mapOf("code" to "PRD-02", "name" to "Predio Ñuñoa") to GdalFixtures.lonLatSquare(-78.49, -7.90, 0.001)
                    )
                )
            )
        }

    @Test
    fun `un GeoPackage en UTM 17S con sus dos registros`() {
        val out = dir.resolve("salida.gpkg")
        writer.write(GeoExportFormat.GPKG, source(), "land_parcel", 32717, out)
        val layer = GdalFixtures.info(out.toFile(), "GPKG").single()
        assertEquals("land_parcel", layer.name)
        assertEquals(2L, layer.featureCount)
        assertEquals("EPSG 32717", layer.detectedCrs)
    }

    @Test
    fun `un KML siempre en grados aunque se pida UTM`() {
        val out = dir.resolve("salida.kml")
        writer.write(GeoExportFormat.KML, source(), "land_parcel", 32717, out)
        val layer = GdalFixtures.info(out.toFile(), "KML").single()
        assertEquals(2L, layer.featureCount)
        assertEquals("EPSG 4326", layer.detectedCrs)
    }

    @Test
    fun `un Shapefile sale como ZIP con cpg UTF-8 y prj`() {
        val out = dir.resolve("salida.zip")
        writer.write(GeoExportFormat.SHP, source(), "land_parcel", 32717, out)
        val names =
            ZipFile(out.toFile()).use { zip ->
                zip
                    .entries()
                    .toList()
                    .map { it.name }
                    .toSet()
            }
        assertEquals(setOf("land_parcel.shp", "land_parcel.shx", "land_parcel.dbf", "land_parcel.prj", "land_parcel.cpg"), names)
        val cpg = ZipFile(out.toFile()).use { zip -> zip.getInputStream(zip.getEntry("land_parcel.cpg")).readAllBytes().toString(Charsets.UTF_8) }
        assertEquals("UTF-8", cpg.trim())
        val unzipped = dir.resolve("shp").also { Files.createDirectories(it) }
        ZipFile(out.toFile()).use { zip -> zip.entries().toList().forEach { e -> Files.copy(zip.getInputStream(e), unzipped.resolve(e.name)) } }
        val layer = GdalFixtures.info(unzipped.resolve("land_parcel.shp").toFile(), "ESRI Shapefile").single()
        assertEquals(2L, layer.featureCount)
        assertEquals("EPSG 32717", layer.detectedCrs)
    }

    @Test
    fun `la carpeta de trabajo y el error de GDAL no quedan junto a la salida`() {
        val out = dir.resolve("salida.zip")
        writer.write(GeoExportFormat.SHP, source(), "land_parcel", 32717, out)
        val left = Files.list(dir).use { files -> files.map { it.fileName.toString() }.toList() }
        assertFalse(left.any { it.startsWith("gdal-") }, left.toString())
        assertTrue("salida.zip" in left)
    }

    @Test
    fun `con plazo vencido ogr2ogr se interrumpe y no queda carpeta de trabajo`() {
        val slow = dir.resolve("lento").also { Files.createDirectories(it) }
        val fake = dir.resolve("bin").also { Files.createDirectories(it) }
        val real = GdalFixtures.requireGdal()
        Files.createSymbolicLink(fake.resolve("ogrinfo"), real.resolve("ogrinfo"))
        Files.writeString(fake.resolve("ogr2ogr"), "#!/bin/sh\nexec sleep 30\n")
        fake.resolve("ogr2ogr").toFile().setExecutable(true)
        val hanging = GdalWriter(GdalTools(GdalProperties(path = fake.toString())))
        val started = System.nanoTime()
        assertThrows<kotlinx.coroutines.TimeoutCancellationException> {
            runBlocking { hanging.writeWithin(GeoExportFormat.GPKG, source(), "land_parcel", null, slow.resolve("x.gpkg"), Duration.ofMillis(500)) }
        }
        assertTrue(Duration.ofNanos(System.nanoTime() - started) < Duration.ofSeconds(10))
        assertEquals(0L, Files.list(slow).use { it.count() })
    }

    @Test
    fun `sin GDAL es FORMAT_UNAVAILABLE`() {
        val missing = GdalWriter(GdalTools(GdalProperties(path = dir.resolve("sin-gdal").toString())))
        val e = assertThrows<WasichaiException> { missing.write(GeoExportFormat.GPKG, source(), "land_parcel", null, dir.resolve("x.gpkg")) }
        assertEquals("FORMAT_UNAVAILABLE", e.violations.single().message)
    }
}
