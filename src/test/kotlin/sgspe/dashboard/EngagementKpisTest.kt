package sgspe.dashboard

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import sgspe.engagement.AttendanceCounts
import sgspe.engagement.EngagementWriteGuard
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

// los KPI-ENG de F2 (dashboards.md §4.3) con datos ficticios
class EngagementKpisTest {
    private fun engagement(
        id: String,
        type: String,
        date: String,
        total: Long = 0,
        female: Long = 0
    ) = EngagementRow(id, "p", type, "Reunión $id", LocalDate.parse(date), mapOf("attendees_total_count" to total, "attendees_female_count" to female))

    private val end = LocalDate.parse("2026-05-31")

    @Test
    fun `KPI-ENG-01 cuenta las realizadas por tipo y por mes`() {
        val may = listOf(engagement("1", "REUNION", "2026-05-02"), engagement("2", "LLAMADA", "2026-05-03"))
        val window = may + engagement("3", "TALLER", "2026-02-10")
        val result = EngagementKpis.held(may, window, end)
        assertEquals(2, result.value)
        assertEquals(1, result.series!!.first { it["period"] == "2026-02" }["value"])
        assertEquals(12, result.series.size)
    }

    @Test
    fun `KPI-ENG-02 cuenta solo reuniones y suma la meta de los meses del periodo`() {
        val may = listOf(engagement("1", "REUNION", "2026-05-02"), engagement("2", "LLAMADA", "2026-05-03"), engagement("3", "ASAMBLEA", "2026-05-20"))
        val targets = EngagementKpis.months(end).map { MonthlyTarget(it, if (it == YearMonth.of(2026, 5)) 3 else 2) }
        val result = EngagementKpis.meetings(may, may, targets, LocalDate.parse("2026-05-01"), end)
        assertEquals(2, result.value)
        assertEquals(3L, result.denominator)
        assertEquals(3L, result.series!!.last()["target"])
    }

    // un periodo de 18 meses: la meta cubre todos sus meses, no solo los 12 de la serie (revisión de la Tarea 22, I1)
    @Test
    fun `KPI-ENG-02 con un periodo de 18 meses suma la meta de los 18`() {
        val start = LocalDate.parse("2025-01-01")
        val last = LocalDate.parse("2026-06-30")
        val months = EngagementKpis.targetMonths(start, last)
        assertEquals(18, months.size)
        assertEquals(YearMonth.of(2025, 1), months.first())
        assertEquals(YearMonth.of(2026, 6), months.last())
        val targets = EngagementKpis.monthlyTargets(months, mapOf("p" to 2L, "q" to 3L), emptyList())
        assertEquals(18, targets.size)
        assertTrue(targets.all { it.target == 5L })
        val held =
            (1..18).map {
                engagement(
                    "$it",
                    "REUNION",
                    YearMonth
                        .of(2025, 1)
                        .plusMonths(it - 1L)
                        .atDay(10)
                        .toString()
                )
            }
        val result = EngagementKpis.meetings(held, held.filter { !it.heldDate.isBefore(LocalDate.parse("2025-07-01")) }, targets, start, last)
        assertEquals(18, result.value)
        assertEquals(90L, result.denominator)
        assertEquals(12, result.series!!.size)
        assertEquals(5L, result.series.first()["target"])
    }

    // un periodo corto pide igual los 12 meses de la serie; un periodo parcial cuenta la meta de su mes entero
    @Test
    fun `los meses de la meta son la union de la serie y el periodo`() {
        val months = EngagementKpis.targetMonths(LocalDate.parse("2026-05-15"), LocalDate.parse("2026-05-20"))
        assertEquals(EngagementKpis.months(LocalDate.parse("2026-05-20")), months)
    }

    // la meta del plan vigente en el mes; con dos planes vigentes del mismo proyecto, la del de start_date más reciente;
    // sin plan, la del proyecto
    @Test
    fun `con dos planes en el mismo mes manda el de inicio mas reciente`() {
        val plans =
            listOf(
                EngagementKpis.PlanTarget("a", "p", LocalDate.parse("2026-01-01"), null, 4),
                EngagementKpis.PlanTarget("b", "p", LocalDate.parse("2026-05-10"), LocalDate.parse("2026-12-31"), 6),
                EngagementKpis.PlanTarget("c", "p", null, LocalDate.parse("2026-03-31"), 9)
            )
        val targets =
            EngagementKpis.monthlyTargets(listOf(YearMonth.of(2026, 2), YearMonth.of(2026, 4), YearMonth.of(2026, 5)), mapOf("p" to 2L, "q" to 2L), plans)
        assertEquals(listOf(4L + 2L, 4L + 2L, 6L + 2L), targets.map { it.target })
    }

    @Test
    fun `KPI-ENG-03 promedia los asistentes de las reuniones informativas`() {
        val meetings =
            listOf(
                engagement("1", "REUNION INFORMATIVA", "2026-05-02", total = 10, female = 4),
                engagement("2", "TALLER", "2026-05-09", total = 20, female = 10)
            )
        val result = EngagementKpis.attendance(meetings, mapOf("1" to listOf("Ronda Ejemplo", "Ronda Ejemplo")))
        assertEquals(BigDecimal("15.00"), result.value)
        assertEquals(30L, result.numerator)
        val first =
            result.breakdowns
                .single { it.by == "engagement" }
                .rows
                .first()
        assertEquals("1", first["key"])
        assertEquals(10L, first["value"])
        assertEquals(BigDecimal("40.0"), first["femalePercent"])
        assertEquals(listOf("Ronda Ejemplo"), first["organizations"])
    }

    @Test
    fun `KPI-ENG-04 y 05 por tema y preguntas absueltas`() {
        val meetings = listOf(engagement("1", "REUNION INFORMATIVA", "2026-05-02"), engagement("2", "REUNION", "2026-05-03"))
        val questions =
            listOf(QuestionRow("1", "t-agua", true), QuestionRow("1", "t-agua", false), QuestionRow("1", null, true), QuestionRow("2", "t-agua", false))
        val topics = EngagementKpis.topics(meetings, mapOf("1" to setOf("t-agua", "t-empleo"), "2" to setOf("t-agua")), questions, mapOf("t-agua" to "Agua"))
        assertEquals(2, topics.value)
        val water =
            topics.breakdowns
                .single()
                .rows
                .first { it["key"] == "t-agua" }
        assertEquals(1, water["meetings"])
        assertEquals(2, water["questions"])
        val answered = EngagementKpis.answered(meetings, questions, mapOf("t-agua" to "Agua"))
        assertEquals(BigDecimal("66.7"), answered.value)
        assertEquals(3, answered.denominator)
    }

    @Test
    fun `las reuniones y los conteos salen de las fuentes unicas`() {
        assertEquals(EngagementWriteGuard.FORMAL_TYPES, EngagementKpis.MEETINGS)
        assertTrue(EngagementKpis.INFORMATIVE.all { it in EngagementWriteGuard.FORMAL_TYPES })
        // los siete conteos que el tablero lee son los de AttendanceCounts
        assertEquals(AttendanceCounts.FIELDS.toSet(), EngagementKpis.COUNT_FIELDS.values.toSet())
    }

    @Test
    fun `KPI-ENG-03 da los asistentes por ambito como conteos, los porcentajes desde los conteos y sin datos no inventa`() {
        val counts =
            mapOf(
                "attendees_total_count" to 7L,
                "attendees_female_count" to 2L,
                "attendees_male_count" to 5L,
                "attendees_direct_area_count" to 4L,
                "attendees_indirect_area_count" to 2L,
                "attendees_regional_count" to 1L,
                "attendees_national_count" to 0L
            )
        val rows = listOf(EngagementRow("1", "p", "TALLER", "Taller Ejemplo", LocalDate.parse("2026-05-02"), counts), engagement("2", "TALLER", "2026-05-03"))
        val points =
            EngagementKpis
                .attendance(rows, emptyMap())
                .breakdowns
                .single()
                .rows
        assertEquals(BigDecimal("28.6"), points[0]["femalePercent"])
        assertEquals(BigDecimal("71.4"), points[0]["malePercent"])
        assertEquals(4L, points[0]["directArea"])
        assertEquals(2L, points[0]["indirectArea"])
        assertEquals(1L, points[0]["regional"])
        assertEquals(0L, points[0]["national"])
        assertNull(points[1]["femalePercent"])
        assertEquals(0L, points[1]["directArea"])
    }

    @Test
    fun `KPI-ENG-03 sin reuniones informativas no tiene promedio`() {
        val result = EngagementKpis.attendance(listOf(engagement("1", "LLAMADA", "2026-05-02", total = 5)), emptyMap())
        assertNull(result.value)
        assertEquals(0, result.denominator)
    }
}
