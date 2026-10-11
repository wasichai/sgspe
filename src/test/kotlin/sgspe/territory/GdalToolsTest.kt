package sgspe.territory

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import wasichai.core.common.WasichaiException
import java.nio.file.Files
import java.nio.file.Path

// GdalTools: la versión mínima y los drivers se leen una vez; sin GDAL, FORMAT_UNAVAILABLE y no un 500
class GdalToolsTest {
    @TempDir
    lateinit var dir: Path

    @Test
    fun `sin GDAL en la ruta configurada no esta disponible y require es 422 FORMAT_UNAVAILABLE`() {
        val tools = GdalTools(GdalProperties(path = dir.resolve("no-existe").toString()))
        assertFalse(tools.status().available)
        val e = assertThrows<WasichaiException> { tools.require("GPKG", "gpkg") }
        assertEquals(422 to "FORMAT_UNAVAILABLE", e.status.value() to e.violations.single().message)
    }

    @Test
    fun `una version menor que la minima no esta disponible`() {
        val tools = GdalTools(GdalProperties(path = GdalFixtures.requireGdal().toString(), minVersion = "99.0"))
        assertFalse(tools.status().available)
        assertTrue(tools.status().reason!!.contains("99.0"))
    }

    @Test
    fun `con GDAL, la version y los drivers de la lista blanca, y un comando corre con el entorno limpio`() {
        val tools = GdalTools(GdalProperties(path = GdalFixtures.requireGdal().toString()))
        val status = tools.status()
        assertTrue(status.available, status.reason)
        for (driver in listOf("ESRI Shapefile", "KML", "GPKG", "OpenFileGDB", "GPX", "GeoJSON")) assertTrue(status.supports(driver), driver)
        val out = dir.resolve("version.out")
        assertEquals(0, tools.run(listOf(status.ogrinfo.toString(), "--version"), dir, out))
        assertTrue(Files.readString(out).startsWith("GDAL "))
    }
}
