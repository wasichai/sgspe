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

    private fun parseInvestment(vararg pairs: Pair<String, String>): DashboardFilters =
        runBlocking {
            val params = LinkedMultiValueMap<String, String>()
            pairs.forEach { (k, v) -> params.add(k, v) }
            DashboardFilters.parse(params, DashboardFilters.COMMON + DashboardFilters.INVESTMENT, catalog, today) { listOf(assigned) }.resolve()
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

    // la entrada 5 del Review Focus: fuera del tablero investment, program y year son desconocidos (422), nunca un filtro que
    // parece aplicado
    @Test
    fun `program y year solo los acepta quien los permite y viajan en el eco`() {
        assertThrows<UnprocessableContentException> { parse("program" to UUID.randomUUID().toString()) }
        assertThrows<UnprocessableContentException> { parse("year" to "2026") }
        val program = UUID.randomUUID()
        val filters = parseInvestment("program" to program.toString(), "year" to "2026")
        assertEquals(program, filters.program)
        assertEquals(2026, filters.year)
        assertEquals(program.toString(), filters.echo()["program"])
        assertEquals(2026, filters.echo()["year"])
        assertEquals(setOf("program", "year"), filters.given())
        assertEquals(null, parseInvestment().year)
    }

    @Test
    fun `year es un anio entero de 2000 a 2100 y program un UUID`() {
        assertEquals(2000, parseInvestment("year" to "2000").year)
        assertEquals(2100, parseInvestment("year" to "2100").year)
        for (wrong in listOf("1999", "2101", "2026.5", "dos mil")) {
            val error = assertThrows<ValidationException> { parseInvestment("year" to wrong) }
            assertEquals(listOf("year"), error.violations.map { it.field }, wrong)
        }
        assertEquals(listOf("program"), assertThrows<ValidationException> { parseInvestment("program" to "x") }.violations.map { it.field })
    }

    @Test
    fun `los KPI-INV piden program y year y los demas no`() {
        assertEquals(DashboardFilters.INVESTMENT, Kpis.extraFilters(listOf("KPI-INV-02")))
        assertEquals(DashboardFilters.INVESTMENT, Kpis.extraFilters(listOf("KPI-INV-01", "KPI-INV-04")))
        assertEquals(emptySet<String>(), Kpis.extraFilters(listOf("KPI-COM-01", "KPI-RSK-01")))
    }
}
