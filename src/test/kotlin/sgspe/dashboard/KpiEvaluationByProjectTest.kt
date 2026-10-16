package sgspe.dashboard

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import sgspe.common.Records
import java.time.LocalDate
import java.util.UUID

// plan 8.2a-bis: la tabla por proyecto con una cifra por proyecto de una vez (Indicator.computeByProject), la misma
// disponibilidad en todas las filas y, en el KPI sin ella o de un solo proyecto, cada proyecto evaluado aparte con
// DashboardData.with
class KpiEvaluationByProjectTest {
    private val a = UUID.fromString("00000000-0000-0000-0000-00000000000a")
    private val b = UUID.fromString("00000000-0000-0000-0000-00000000000b")

    private fun data(readable: (String) -> Boolean = { true }) =
        DashboardData(
            mock(Records::class.java),
            DashboardFilters.of(listOf(a, b), LocalDate.parse("2026-09-01"), LocalDate.parse("2026-09-30")),
            readable,
            LocalDate.parse("2026-10-14"),
            aggregates = mock(KpiAggregates::class.java)
        )

    private val calls = mutableListOf<List<UUID>>()

    private fun kpi(
        singleProject: Boolean = false,
        split: (suspend (DashboardData) -> Map<String, IndicatorResult>)?
    ) = Kpi("KPI-PRUEBA", "Prueba", null, "registros", listOf("grievance.project"), singleProject = singleProject, split = split) { data ->
        calls += data.filters.projects
        IndicatorResult(data.filters.projects.size * 10, breakdowns = listOf(Breakdown("x", emptyList())))
    }

    @Test
    fun `con computeByProject una sola llamada da todas las filas`() {
        val split = kpi { data -> data.filters.projects.associate { it.toString() to IndicatorResult(if (it == a) 1 else 2) } }
        val rows = runBlocking { KpiEvaluation.evaluateByProject(split, data()) }
        assertEquals(mapOf(a.toString() to 1, b.toString() to 2), rows.mapValues { it.value.value })
        assertEquals(emptyList<List<UUID>>(), calls)
    }

    @Test
    fun `el KPI de un solo proyecto se evalua por proyecto aunque tenga computeByProject`() {
        val split = kpi(singleProject = true) { error("no se reparte un KPI de un solo proyecto") }
        val rows = runBlocking { KpiEvaluation.evaluateByProject(split, data()) }
        assertEquals(mapOf(a.toString() to 10, b.toString() to 10), rows.mapValues { it.value.value })
        assertEquals(listOf(listOf(a), listOf(b)), calls)
    }

    @Test
    fun `sin computeByProject cada proyecto se evalua solo`() {
        val rows = runBlocking { KpiEvaluation.evaluateByProject(kpi(split = null), data()) }
        assertEquals(mapOf(a.toString() to 10, b.toString() to 10), rows.mapValues { it.value.value })
        assertEquals(listOf(listOf(a), listOf(b)), calls)
    }

    @Test
    fun `sin leer lo que el KPI lee todas las filas salen no disponibles sin calcular`() {
        val rows = runBlocking { KpiEvaluation.evaluateByProject(kpi { error("no se calcula") }, data(readable = { it != "grievance.project" })) }
        assertEquals(setOf(a.toString(), b.toString()), rows.keys)
        rows.values.forEach {
            assertFalse(it.available)
            assertEquals(listOf("grievance.project"), it.missingFields)
        }
        assertEquals(emptyList<List<UUID>>(), calls)
    }
}
