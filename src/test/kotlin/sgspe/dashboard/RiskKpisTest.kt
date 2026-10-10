package sgspe.dashboard

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import sgspe.risk.RiskThresholds
import java.math.BigDecimal
import java.time.LocalDate

// los KPI-RSK (dashboards.md §4.6), sin base
class RiskKpisTest {
    private fun risk(
        id: String,
        probability: Int? = null,
        impact: Int? = null,
        level: String? = null,
        category: String? = "AMBIENTAL",
        locality: String? = "loc-1"
    ) = RiskRow(id, "p1", "RS-$id", category, probability, impact, level, null, null, null, locality, null, null)

    private fun date(text: String) = LocalDate.parse(text)

    @Test
    fun `KPI-RSK-01 cuenta los riesgos altos y criticos por categoria`() {
        val rows = listOf(risk("1", level = "ALTO"), risk("2", level = "CRITICO", category = "OTRO"), risk("3", level = "MEDIO"))
        val result = RiskKpis.high(rows)
        assertEquals(2, result.value)
        assertEquals(
            setOf("AMBIENTAL", "OTRO"),
            result.breakdowns
                .single()
                .rows
                .map { it["key"] }
                .toSet()
        )
    }

    @Test
    fun `KPI-RSK-02 llena la matriz 5 x 5, aparta los sin evaluar y con un proyecto da el nivel de cada celda`() {
        val rows = listOf(risk("1", 3, 4, "ALTO"), risk("2", 3, 4, "ALTO"), risk("3"))
        val result = RiskKpis.heatmap(rows, RiskThresholds(5, 10, 15))
        assertEquals(2, result.value)
        val matrix = result.breakdowns.first { it.by == "matrix" }.rows
        assertEquals(25, matrix.size)
        assertEquals(2, matrix.first { it["key"] == "3-4" }["value"])
        assertEquals("CRITICO", matrix.first { it["key"] == "5-5" }["level"])
        assertEquals(
            1,
            result.breakdowns
                .first { it.by == "assessment" }
                .rows
                .single()["value"]
        )
        assertTrue(
            RiskKpis
                .heatmap(rows, null)
                .breakdowns
                .first()
                .rows
                .none { "level" in it }
        )
    }

    @Test
    fun `KPI-RSK-03 apila por nivel cada territorio`() {
        val rows = listOf(risk("1", level = "ALTO"), risk("2", level = "ALTO"), risk("3", locality = "loc-2"))
        val locality =
            RiskKpis
                .distribution(rows, mapOf("loc-1" to "Localidad Ejemplo"))
                .breakdowns
                .first { it.by == "locality" }
                .rows
        assertEquals("Localidad Ejemplo", locality.first()["label"])
        assertEquals(2, locality.first()["ALTO"])
        assertEquals(1, locality.last()["SIN NIVEL"])
    }

    @Test
    fun `KPI-RSK-04 da doce semanas con el nivel de cada riesgo activo y las tendencias`() {
        val end = date("2026-10-09")
        val points =
            listOf(
                RiskPoint("1", date("2026-09-01"), false, "MEDIO", null, 1),
                RiskPoint("1", date("2026-10-06"), false, "ALTO", "AUMENTA", 2),
                RiskPoint("1", date("2026-10-07"), true, "BAJO", null, 3),
                RiskPoint("9", date("2026-10-06"), false, "CRITICO", null, 4)
            )
        val result = RiskKpis.evolution(listOf(risk("1")), points, end)
        val series = result.series!!
        assertEquals(12, series.size)
        assertEquals("2026-10-05", series.last()["period"])
        assertEquals(1, series.last()["ALTO"])
        assertEquals(1, series.last()["AUMENTA"])
        assertEquals(1, series.first { it["period"] == "2026-08-31" }["MEDIO"])
        // el riesgo 9 no está activo en el filtro: no cuenta
        assertEquals(0, series.last()["CRITICO"])
        assertEquals(3, result.value)
    }

    @Test
    fun `KPI-RSK-05 es el porcentaje de medidas completadas y lista las atrasadas`() {
        val measures =
            listOf(
                MeasureRow("m1", "1", "Mesa de trabajo", "a@sgspe.test", date("2026-10-01"), false),
                MeasureRow("m2", "1", "Taller", "a@sgspe.test", date("2026-10-01"), true),
                MeasureRow("m3", "1", "Visita", null, date("2026-12-01"), false)
            )
        val result = RiskKpis.mitigation(measures, mapOf("1" to "RS-1"), date("2026-10-09"))
        assertEquals(BigDecimal("33.3"), result.value)
        val late = result.breakdowns.first { it.by == "overdue" }.rows
        assertEquals(listOf("m1"), late.map { it["key"] })
        assertEquals("RS-1", late.single()["label"])
        assertEquals(
            setOf("a@sgspe.test", "SIN RESPONSABLE"),
            result.breakdowns
                .first { it.by == "owner" }
                .rows
                .map { it["key"] }
                .toSet()
        )
    }

    @Test
    fun `el tablero risks trae los cinco KPI-RSK en el orden de sus zonas`() {
        assertEquals(listOf("KPI-RSK-02", "KPI-RSK-01", "KPI-RSK-03", "KPI-RSK-04", "KPI-RSK-05"), Kpis.DASHBOARDS.getValue("risks"))
        assertTrue((1..5).all { Kpis.BY_CODE.getValue("KPI-RSK-%02d".format(it)).dashboard == "risks" })
        assertTrue((1..5).none { Kpis.confidentialSensitive("KPI-RSK-%02d".format(it)) })
    }
}
