package sgspe.dashboard

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

// KPI-EXE-01 a 03 (dashboards.md §4.1) sin base: proyectos en active por etapa y tipo, alertas activas por severidad y estado,
// y la tendencia de 12 meses sin valor escalar, con las quejas contadas una vez (un original reclasificado no cuenta)
class ExecutiveKpisTest {
    private val end = LocalDate.parse("2026-09-30")

    private fun qcs(
        type: String,
        id: String,
        state: String,
        received: String?,
        closed: String? = null,
        reclassified: Map<String, String> = emptyMap()
    ) = AttentionRow(
        type,
        id,
        state,
        received?.let(LocalDate::parse),
        null,
        null,
        closed?.let(LocalDate::parse),
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        false,
        reclassified
    )

    @Test
    fun `KPI-EXE-01 cuenta los proyectos en active con su etapa y su tipo`() {
        val rows =
            listOf(
                ProjectRow("a", "active", "EXPLORACION", "MINERO"),
                ProjectRow("b", "active", null, "MINERO"),
                ProjectRow("c", "draft", "EXPLORACION", "MINERO"),
                ProjectRow("d", "suspended", "EXPLORACION", "MINERO")
            )
        val result = ExecutiveKpis.activeProjects(rows)
        assertEquals(2, result.value)
        assertEquals(listOf("stage", "project_type"), result.breakdowns.map { it.by })
        assertEquals(
            setOf("EXPLORACION", "SIN DATO"),
            result.breakdowns[0]
                .rows
                .map { it["key"] }
                .toSet()
        )
        assertEquals(listOf(mapOf("key" to "MINERO", "value" to 2)), result.breakdowns[1].rows)
    }

    @Test
    fun `KPI-EXE-02 cuenta las alertas activas por severidad y estado`() {
        val rows =
            listOf(
                WarningRow("1", "detected", "ALTO"),
                WarningRow("2", "responding", "ALTO"),
                WarningRow("3", "confirmed", null),
                WarningRow("4", "dismissed", "ALTO"),
                WarningRow("5", "closed", "MEDIO")
            )
        val result = ExecutiveKpis.activeWarnings(rows)
        assertEquals(3, result.value)
        assertEquals(listOf(mapOf("key" to "ALTO", "value" to 2), mapOf("key" to "SIN DATO", "value" to 1)), result.breakdowns[0].rows)
        assertEquals(3, result.breakdowns[1].rows.size)
    }

    @Test
    fun `KPI-EXE-03 son tres medidas por mes sin valor escalar y una queja reclasificada no cuenta dos veces`() {
        val held =
            EngagementKpis.held(emptyList(), emptyList(), end).copy(
                series = EngagementKpis.months(end).map { mapOf("period" to it.toString(), "value" to if (it.toString() == "2026-09") 4 else 0) }
            )
        val overdue = IndicatorResult(2, series = EngagementKpis.months(end).map { mapOf("period" to it.toString(), "value" to 2) })
        val attention =
            listOf(
                qcs("grievance", "q1", "closed", "2026-09-02", "2026-09-20"),
                qcs("grievance", "q2", "received", "2026-09-05"),
                // el original reclasificado a consulta: no cuenta como queja recibida
                qcs("grievance", "q3", "closed", "2026-09-06", "2026-09-07"),
                qcs("inquiry", "c1", "received", "2026-09-06", reclassified = mapOf("reclassified_from_grievance" to "q3")),
                qcs("inquiry", "c2", "received", "2026-09-08")
            )
        val result = ExecutiveKpis.trend(held, attention, overdue, end)
        assertNull(result.value)
        val series = result.series!!
        assertEquals(12, series.size)
        assertEquals(
            mapOf("period" to "2026-09", "engagements" to 4, "grievancesReceived" to 2, "grievancesClosed" to 1, "overdueCommitments" to 2),
            series.last()
        )
        assertEquals("2025-10", series.first()["period"])
    }

    @Test
    fun `los KPI-EXE estan en el catalogo, en el tablero ejecutivo, y KPI-EXE-03 lleva la marca de las QCS`() {
        for (code in listOf("KPI-EXE-01", "KPI-EXE-02", "KPI-EXE-03")) assertEquals(Kpis.EXECUTIVE, Kpis.BY_CODE.getValue(code).dashboard, code)
        assertTrue(Kpis.confidentialSensitive("KPI-EXE-03"))
        assertEquals(ExecutiveKpis.TILES - ExecutiveKpis.TREND, ExecutiveKpis.BY_PROJECT)
        assertTrue("periodStart" in Kpis.BY_CODE.getValue("KPI-EXE-01").ignores)
    }
}
