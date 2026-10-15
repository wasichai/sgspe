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

    // plan 8.2a-bis: los KPI del tablero executive cuentan en la base por defecto, también en application.yml
    @Test
    fun `los conteos en la base vienen encendidos`() {
        assertEquals(true, DashboardsProperties().aggregates)
        val yml = File("src/main/resources/application.yml").readText()
        assertEquals(true, Regex("""max-projects: 25\s+(#[^\n]*\n\s*)*aggregates: true""").containsMatchIn(yml), "falta sgspe.dashboards.aggregates")
    }
}
