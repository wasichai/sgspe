package sgspe.dashboard

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.File

// especificación de F8 §2.2.16: la tabla por proyecto admite hasta sgspe.dashboards.executive.max-projects (25)
class DashboardsPropertiesTest {
    @Test
    fun `el tope por defecto es 25, el de application yml tambien, y no puede ser menor que 1`() {
        assertEquals(25, DashboardsProperties().executive.maxProjects)
        val yml = File("src/main/resources/application.yml").readText()
        // entre executive: y max-projects va la línea de comentario de application.yml
        assertEquals(
            true,
            Regex("""dashboards:\s+executive:\s+(#[^\n]*\n\s*)*max-projects: 25""").containsMatchIn(yml),
            "falta sgspe.dashboards.executive.max-projects"
        )
        assertThrows<IllegalArgumentException> { DashboardsProperties.Executive(maxProjects = 0) }
    }

    // cierre de F8 (decisión 8 del plan 8.2a-bis): los KPI del tablero executive siempre cuentan en la base, sin el interruptor
    @Test
    fun `sin el interruptor de los conteos en la base`() {
        val yml = File("src/main/resources/application.yml").readText()
        assertEquals(
            false,
            Regex("""dashboards:(\n {4}\S[^\n]*|\n {6,}[^\n]*|\n\s*#[^\n]*)*\n {4}aggregates:""").containsMatchIn(yml),
            "sobra sgspe.dashboards.aggregates"
        )
    }
}
