package sgspe.dashboard

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

// los KPI-COM (dashboards.md §4.4) con filas de prueba, sin base
class CommitmentKpisTest {
    private val start = LocalDate.parse("2026-09-01")
    private val end = LocalDate.parse("2026-09-30")
    private var seq = 0

    private fun row(
        state: String,
        deadline: String? = null,
        due: String? = null,
        fulfilled: String? = null,
        agreed: String? = "2026-01-15",
        cancelled: String? = null,
        owner: String? = "a@sgspe.test",
        theme: String? = "SOCIAL",
        formality: String? = "FORMAL",
        amount: String? = "100",
        currency: String? = "PEN",
        executed: String? = null,
        project: String? = "p1",
        locality: String? = "l1",
        topic: String? = "t1"
    ) = CommitmentRow(
        "c${++seq}",
        project,
        state,
        owner,
        locality,
        null,
        topic,
        theme,
        formality,
        agreed?.let(LocalDate::parse),
        due?.let(LocalDate::parse),
        fulfilled?.let(LocalDate::parse),
        cancelled?.let(LocalDate::parse),
        deadline,
        amount?.let(::BigDecimal),
        currency,
        executed?.let(::BigDecimal)
    )

    @Test
    fun `registrados por estado en el orden de WF-D, abiertos y bloqueados`() {
        val rows = listOf(row("closed"), row("draft"), row("blocked"), row("blocked"), row("cancelled"))
        val registered = CommitmentKpis.registered(rows)
        assertEquals(5, registered.value)
        assertEquals(
            listOf("draft", "blocked", "closed", "cancelled"),
            registered.breakdowns
                .single()
                .rows
                .map { it["key"] }
        )
        assertEquals(3, CommitmentKpis.open(rows).value)
        assertEquals(2, CommitmentKpis.blocked(rows).value)
    }

    @Test
    fun `vencidos por dias de atraso con los umbrales de su proyecto, y por vencer`() {
        val today = LocalDate.parse("2026-10-09")
        val rows =
            listOf(
                row("in_progress", "VENCIDO", "2026-10-05"),
                row("blocked", "VENCIDO", "2026-09-30"),
                row("approved", "VENCIDO", "2026-09-01"),
                row("fulfilled", "VENCIDO", "2026-09-01"),
                row("in_progress", "POR VENCER", "2026-10-12")
            )
        val overdue = CommitmentKpis.overdue(rows, today) { Escalation(7, 15) }
        assertEquals(3, overdue.value)
        assertEquals(
            listOf("1-7" to 1, "8-15" to 1, "más de 15" to 1),
            overdue.breakdowns
                .single()
                .rows
                .map { it["key"] to it["value"] }
        )
        assertEquals(1, CommitmentKpis.dueSoon(rows).value)
    }

    @Test
    fun `cumplidos en el periodo y a tiempo frente al plazo vigente y al original`() {
        val onTime = row("fulfilled", due = "2026-09-20", fulfilled = "2026-09-15")
        val late = row("verified", due = "2026-09-10", fulfilled = "2026-09-12")
        val rescheduled = row("closed", due = "2026-09-25", fulfilled = "2026-09-20")
        val outside = row("fulfilled", due = "2026-08-20", fulfilled = "2026-08-15")
        val rows = listOf(onTime, late, rescheduled, outside)
        assertEquals(3, CommitmentKpis.fulfilled(rows, start, end).value)
        val reschedules =
            listOf(
                RescheduleRow(rescheduled.id, LocalDate.parse("2026-09-10"), LocalDate.parse("2026-09-18"), LocalDate.parse("2026-09-05")),
                RescheduleRow(rescheduled.id, LocalDate.parse("2026-09-18"), LocalDate.parse("2026-09-25"), LocalDate.parse("2026-09-15"))
            )
        val result = CommitmentKpis.onTime(rows, reschedules, start, end)
        assertEquals(BigDecimal("66.7"), result.value)
        assertEquals(2 to 3, result.numerator to result.denominator)
        val original =
            result.breakdowns
                .single()
                .rows
                .first { it["key"] == "original" }
        assertEquals(1, original["numerator"])
    }

    @Test
    fun `formales por tema con agreed_date en el periodo, sin los cancelados`() {
        val rows =
            listOf(
                row("approved", agreed = "2026-09-02", theme = "AMBIENTAL"),
                row("draft", agreed = "2026-09-03", theme = "SOCIAL"),
                row("cancelled", agreed = "2026-09-03"),
                row("approved", agreed = "2026-09-04", formality = "VERBAL"),
                row("approved", agreed = "2026-08-31")
            )
        val result = CommitmentKpis.formalByTheme(rows, start, end)
        assertEquals(2, result.value)
        assertEquals(
            setOf("AMBIENTAL", "SOCIAL"),
            result.breakdowns
                .single()
                .rows
                .map { it["key"] }
                .toSet()
        )
    }

    @Test
    fun `montos por moneda de los comprometidos, con campana, la otra moneda es una advertencia`() {
        val rows =
            listOf(
                row("approved", amount = "100", executed = "40"),
                row("closed", amount = "50.5", executed = "50.5"),
                row("in_progress", amount = "10", currency = "USD"),
                row("draft", amount = "999")
            )
        val all = CommitmentKpis.amounts(rows, null)
        assertEquals(0, BigDecimal("150.5").compareTo(all.value as BigDecimal))
        assertEquals(
            listOf("PEN", "USD"),
            all.breakdowns
                .single()
                .rows
                .map { it["key"] }
        )
        val campaign = CommitmentKpis.amounts(rows, "PEN")
        assertEquals(
            listOf("PEN"),
            campaign.breakdowns
                .single()
                .rows
                .map { it["key"] }
        )
        assertEquals(1, campaign.warnings.size)
    }

    @Test
    fun `desgloses de abiertos y vencidos, los 10 primeros y Otros`() {
        val rows = (1..12).map { row("in_progress", "VENCIDO", "2026-09-01", owner = "o$it@sgspe.test") } + row("closed", owner = "o1@sgspe.test")
        val result = CommitmentKpis.breakdowns(rows, mapOf("p1" to "PRY-1"), mapOf("l1" to "Localidad"), mapOf("t1" to "Agua"))
        val owners = result.breakdowns.first { it.by == "owner" }.rows
        assertEquals(11, owners.size)
        assertEquals("Otros" to 2, owners.last()["key"] to owners.last()["value"])
        assertEquals(
            "PRY-1",
            result.breakdowns
                .first { it.by == "project" }
                .rows
                .single()["label"]
        )
        assertEquals(
            12,
            result.breakdowns
                .first { it.by == "topic" }
                .rows
                .single()["overdue"]
        )
    }

    @Test
    fun `la serie de vencidos al cierre de cada mes usa la fecha vigente en ese mes`() {
        // vence el 2026-08-10, se reprograma el 2026-08-20 al 2026-09-30 y se cumple el 2026-10-05
        val rescheduled = row("fulfilled", due = "2026-09-30", fulfilled = "2026-10-05", agreed = "2026-07-01")
        // aprobado, vencido desde el 2026-07-15 y nunca cumplido
        val open = row("in_progress", due = "2026-07-15", agreed = "2026-07-01")
        // cancelado el 2026-09-05 tras vencer el 2026-07-20
        val cancelled = row("cancelled", due = "2026-07-20", agreed = "2026-07-01", cancelled = "2026-09-05")
        val reschedules = listOf(RescheduleRow(rescheduled.id, LocalDate.parse("2026-08-10"), LocalDate.parse("2026-09-30"), LocalDate.parse("2026-08-20")))
        val series = CommitmentKpis.overdueSeries(listOf(rescheduled, open, cancelled), reschedules, LocalDate.parse("2026-10-31")).series!!
        val byMonth = series.associate { it["period"] to it["value"] }
        assertEquals(2, byMonth["2026-07"])
        // en agosto la fecha vigente del reprogramado ya es el 2026-09-30; el cancelado cuenta hasta el mes de su cancelación
        assertEquals(2, byMonth["2026-08"])
        assertEquals(1, byMonth["2026-09"])
        assertEquals(1, byMonth["2026-10"])
        assertEquals(12, series.size)
    }
}
