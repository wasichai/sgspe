package sgspe.territory

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import wasichai.core.common.WasichaiException
import java.io.IOException
import java.math.BigDecimal
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

// las partes puras de las exportaciones geográficas: el CSV (RFC 4180, BOM, fórmulas neutralizadas), el mapa de fallos de
// GDAL, los filtros de origen de la capa y los proyectos que acotan la lectura
class TerritoryExportsTest {
    @TempDir
    lateinit var dir: Path

    private fun csv(vararg rows: List<Any?>): String {
        val out = dir.resolve("a.csv")
        Csv.write(listOf("id", "name"), rows.toList(), out)
        return Files.readString(out)
    }

    @Test
    fun `el CSV lleva BOM, coma y salto RFC 4180`() {
        val text = csv(listOf("1", "Predio, Ejemplo"), listOf("2", "dice \"hola\""))
        assertTrue(text.startsWith("\uFEFF"))
        assertEquals("id,name\r\n1,\"Predio, Ejemplo\"\r\n2,\"dice \"\"hola\"\"\"\r\n", text.removePrefix("\uFEFF"))
    }

    @Test
    fun `el CSV neutraliza fórmulas en el texto pero no en los números`() {
        val text = csv(listOf("=1+1", "@x"), listOf("+1", "-2"), listOf(BigDecimal("-12.50"), -3), listOf(null, "\tz")).removePrefix("\uFEFF")
        assertEquals("id,name\r\n'=1+1,'@x\r\n'+1,'-2\r\n-12.50,-3\r\n,'\tz\r\n", text)
    }

    @Test
    fun `un fallo de GDAL es un 500 con codigo y sin detalles internos`() {
        val e = TerritoryExports.gdalFailure(IllegalStateException("GDAL no pudo escribir shp /tmp/secreto"))
        assertEquals(500, e.status.value())
        assertEquals("EXPORT_FAILED", e.violations.single().message)
        assertEquals("format", e.violations.single().field)
        assertTrue(e.violations.none { "secreto" in it.message })
    }

    @Test
    fun `guardGdal mapea los fallos de GDAL pero deja pasar la cancelacion`() {
        assertEquals(
            "EXPORT_FAILED",
            assertThrows<WasichaiException> { runBlocking { TerritoryExports.guardGdal(1) { error("boom") } } }.violations.single().message
        )
        assertEquals(
            "EXPORT_FAILED",
            assertThrows<WasichaiException> {
                runBlocking { TerritoryExports.guardGdal(1) { throw IOException("x") } }
            }.violations.single().message
        )
        assertThrows<CancellationException> { runBlocking { TerritoryExports.guardGdal(1) { throw CancellationException("cliente") } } }
        val timeout = assertThrows<WasichaiException> { runBlocking { TerritoryExports.guardGdal(1) { withTimeout(1) { delay(1000) } } } }
        assertEquals(503, timeout.status.value())
        assertEquals("EXPORT_TIMEOUT", timeout.violations.single().message)
    }

    @Test
    fun `FORMAT_UNAVAILABLE de una exportacion nombra el campo format`() {
        assertEquals(
            "format",
            GdalCommand
                .formatUnavailable("shp", "format")
                .violations
                .single()
                .field
        )
        assertEquals(
            "file",
            GdalCommand
                .formatUnavailable("shp")
                .violations
                .single()
                .field
        )
    }

    @Test
    fun `la exportacion entrega la capa como se ve en todos los niveles`() {
        assertEquals(TerritoryExports.LayerFilters(excludeSuperseded = true, states = null), TerritoryExports.layerFilters("project_component"))
        assertEquals(TerritoryExports.LayerFilters(excludeSuperseded = false, states = setOf("approved")), TerritoryExports.layerFilters("area_of_influence"))
        for (objectName in ExportDisclosure.EXPORTABLE - setOf("project_component", "area_of_influence")) {
            assertEquals(TerritoryExports.LayerFilters(excludeSuperseded = false, states = null), TerritoryExports.layerFilters(objectName), objectName)
        }
    }

    @Test
    fun `las concesiones se acotan solo con filters project y los objetos sin proyecto nunca`() {
        val asked = listOf(UUID.randomUUID())
        val mine = listOf(UUID.randomUUID(), UUID.randomUUID())
        assertEquals(null, TerritoryExports.projectsFor("mining_concession", emptyList(), mine))
        assertEquals(asked, TerritoryExports.projectsFor("mining_concession", asked, asked))
        assertEquals(mine, TerritoryExports.projectsFor("project_component", emptyList(), mine))
        assertEquals(asked, TerritoryExports.projectsFor("area_of_influence", asked, asked))
        assertEquals(null, TerritoryExports.projectsFor("project", emptyList(), null))
        assertEquals(null, TerritoryExports.projectsFor("land_parcel", asked, asked))
        assertEquals(null, TerritoryExports.projectsFor("community", emptyList(), mine))
    }
}
