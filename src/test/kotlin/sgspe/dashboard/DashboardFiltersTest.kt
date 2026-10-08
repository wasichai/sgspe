package sgspe.dashboard

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.util.LinkedMultiValueMap
import sgspe.common.ModelCatalog
import sgspe.common.UnprocessableContentException
import wasichai.core.common.ValidationException
import java.time.LocalDate
import java.util.UUID

// dashboards.md §3.1: el mes en curso de Lima por defecto, los proyectos por defecto, 422 ante un parámetro desconocido
class DashboardFiltersTest {
    private val catalog = ModelCatalog.parse("""{"enums": {"stakeholder_scope": ["AIDS", "AIIS", "REGIONAL", "NACIONAL"]}}""", """{"roles": []}""")
    private val today = LocalDate.parse("2026-05-20")
    private val assigned = UUID.randomUUID()

    private fun parse(vararg pairs: Pair<String, String>): DashboardFilters =
        runBlocking {
            val params = LinkedMultiValueMap<String, String>()
            pairs.forEach { (k, v) -> params.add(k, v) }
            DashboardFilters.parse(params, DashboardFilters.COMMON, catalog, today) { listOf(assigned) }.resolve()
        }

    @Test
    fun `sin filtros, el mes en curso y los proyectos asignados`() {
        val filters = parse()
        assertEquals(LocalDate.parse("2026-05-01"), filters.periodStart)
        assertEquals(LocalDate.parse("2026-05-31"), filters.periodEnd)
        assertEquals(listOf(assigned), filters.projects)
    }

    @Test
    fun `project se repite y manda sobre las asignaciones`() {
        val a = UUID.randomUUID()
        val b = UUID.randomUUID()
        assertEquals(listOf(a, b), parse("project" to a.toString(), "project" to b.toString()).projects)
    }

    @Test
    fun `un parametro desconocido es 422 y un valor invalido es 400`() {
        assertThrows<UnprocessableContentException> { parse("projectId" to "x") }
        val error =
            assertThrows<ValidationException> { parse("periodStart" to "2026-06-01", "periodEnd" to "2026-05-01", "scope" to "LOCAL", "project" to "x") }
        assertEquals(setOf("periodStart", "scope", "project"), error.violations.map { it.field }.toSet())
    }
}
