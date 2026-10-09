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
            listOf("OVERDUE_FIRST" to 1, "OVERDUE_SECOND" to 1, "OVERDUE_OVER" to 1),
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
                RescheduleRow(rescheduled.id, LocalDate.parse("2026-09-10"), LocalDate.parse("2026-09-18"), LocalDate.parse("2026-09-05"), 1),
                RescheduleRow(rescheduled.id, LocalDate.parse("2026-09-18"), LocalDate.parse("2026-09-25"), LocalDate.parse("2026-09-15"), 2)
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
        val reschedules = listOf(RescheduleRow(rescheduled.id, LocalDate.parse("2026-08-10"), LocalDate.parse("2026-09-30"), LocalDate.parse("2026-08-20"), 1))
        val series =
            CommitmentKpis
                .overdueSeries(
                    listOf(rescheduled, open, cancelled),
                    reschedules,
                    LocalDate.parse("2026-10-31"),
                    LocalDate.parse("2026-10-31")
                ).series!!
        val byMonth = series.associate { it["period"] to it["value"] }
        assertEquals(2, byMonth["2026-07"])
        // en agosto la fecha vigente del reprogramado ya es el 2026-09-30; el cancelado cuenta hasta el mes de su cancelación
        assertEquals(2, byMonth["2026-08"])
        assertEquals(1, byMonth["2026-09"])
        assertEquals(1, byMonth["2026-10"])
        assertEquals(12, series.size)
    }

    private val today = LocalDate.parse("2026-10-09")
    private val none = emptyList<CommitmentRow>()

    private fun keys(r: IndicatorResult) =
        r.breakdowns
            .single()
            .rows
            .map { it["key"] }

    @Test
    fun `lista vacia en las once funciones`() {
        assertEquals(0, CommitmentKpis.registered(none).value)
        assertEquals(0, CommitmentKpis.open(none).value)
        assertEquals(0, CommitmentKpis.overdue(none, today) { Escalation(7, 15) }.value)
        assertEquals(0, CommitmentKpis.dueSoon(none).value)
        assertEquals(0, CommitmentKpis.fulfilled(none, start, end).value)
        assertEquals(0, CommitmentKpis.blocked(none).value)
        val breakdowns = CommitmentKpis.breakdowns(none, emptyMap(), emptyMap(), emptyMap())
        assertEquals(0, breakdowns.value)
        assertEquals(4, breakdowns.breakdowns.size)
        assertEquals(true, breakdowns.breakdowns.all { it.rows.isEmpty() })
        assertEquals(0, CommitmentKpis.formalByTheme(none, start, end).value)
        val onTime = CommitmentKpis.onTime(none, emptyList(), start, end)
        assertEquals(null, onTime.value)
        assertEquals(0 to 0, onTime.numerator to onTime.denominator)
        assertEquals(null, CommitmentKpis.amounts(none, null).value)
        val series = CommitmentKpis.overdueSeries(none, emptyList(), end, today).series!!
        assertEquals(12, series.size)
        assertEquals(true, series.all { it["value"] == 0 })
    }

    @Test
    fun `cumplimiento a tiempo con denominador cero, numerador cero y redondeo half up`() {
        val outside = row("fulfilled", due = "2026-08-20", fulfilled = "2026-08-15")
        val draft = row("draft")
        assertEquals(null, CommitmentKpis.onTime(listOf(outside, draft), emptyList(), start, end).value)
        val late = listOf(row("fulfilled", due = "2026-09-01", fulfilled = "2026-09-10"))
        val zero = CommitmentKpis.onTime(late, emptyList(), start, end)
        assertEquals(BigDecimal("0.0"), zero.value)
        assertEquals(
            BigDecimal("0.0"),
            zero.breakdowns
                .single()
                .rows
                .first { it["key"] == "original" }["value"]
        )
        // sin plazo: cuenta en el denominador y no en el numerador
        val noDue = CommitmentKpis.onTime(listOf(row("fulfilled", fulfilled = "2026-09-10")), emptyList(), start, end)
        assertEquals(0 to 1, noDue.numerator to noDue.denominator)

        // 1/8 = 12.5 y 1/16 = 6.25, que sube a 6.3
        fun ratio(
            n: Int,
            d: Int
        ) = CommitmentKpis
            .onTime(
                (1..d).map { row("fulfilled", due = if (it <= n) "2026-09-20" else "2026-09-01", fulfilled = "2026-09-10") },
                emptyList(),
                start,
                end
            ).value
        assertEquals(BigDecimal("12.5"), ratio(1, 8))
        assertEquals(BigDecimal("6.3"), ratio(1, 16))
    }

    @Test
    fun `vencidos en los limites de 7, 8, 15 y 16 dias`() {
        fun daysAgo(n: Long) = row("in_progress", "VENCIDO", today.minusDays(n).toString())
        val rows = listOf(daysAgo(0), daysAgo(7), daysAgo(8), daysAgo(15), daysAgo(16), row("approved", "VENCIDO"))
        val result = CommitmentKpis.overdue(rows, today) { Escalation(7, 15) }
        assertEquals(6, result.value)
        val counts =
            result.breakdowns
                .single()
                .rows
                .associate { it["key"] to it["value"] }
        assertEquals(mapOf("OVERDUE_FIRST" to 2, "OVERDUE_SECOND" to 2, "OVERDUE_OVER" to 2), counts)
        assertEquals(
            "1-7",
            result.breakdowns
                .single()
                .rows
                .first()["label"]
        )
    }

    @Test
    fun `vencidos con umbrales distintos por proyecto suman en las mismas claves`() {
        val rows =
            listOf(
                row("in_progress", "VENCIDO", "2026-09-29", project = "p1"),
                row("in_progress", "VENCIDO", "2026-09-29", project = "p2")
            )
        val result = CommitmentKpis.overdue(rows, today) { if (it == "p1") Escalation(7, 15) else Escalation(10, 20) }
        val out = result.breakdowns.single().rows
        assertEquals(listOf("OVERDUE_FIRST", "OVERDUE_SECOND"), out.map { it["key"] })
        assertEquals("Hasta el primer umbral", out.first()["label"])
    }

    @Test
    fun `montos sin comprometidos en la moneda principal, sin ejecutado, nulos y solo USD`() {
        // solo USD sin campaña: no hay base en PEN
        val usd = CommitmentKpis.amounts(listOf(row("approved", currency = "USD", amount = "10")), null)
        assertEquals(null, usd.value)
        assertEquals(listOf("USD"), keys(usd))
        // campaña PEN sin filas PEN: null y advertencia
        val campaign = CommitmentKpis.amounts(listOf(row("approved", currency = "USD", amount = "10")), "PEN")
        assertEquals(null, campaign.value)
        assertEquals(1, campaign.warnings.size)
        // comprometido positivo sin ejecutado: ejecutado 0
        val noExec = CommitmentKpis.amounts(listOf(row("approved", amount = "10.5")), null)
        assertEquals(BigDecimal("10.5000"), noExec.value)
        assertEquals(
            BigDecimal("0.0000"),
            noExec.breakdowns
                .single()
                .rows
                .single()["executed"]
        )
        // monto o moneda nulos se omiten
        val nulls = CommitmentKpis.amounts(listOf(row("approved", amount = null), row("approved", currency = null), row("approved", amount = "5")), null)
        assertEquals(BigDecimal("5.0000"), nulls.value)
        assertEquals(null, CommitmentKpis.amounts(listOf(row("approved", amount = null)), null).value)
    }

    @Test
    fun `desglose con clave nula y Otros suman todos los abiertos`() {
        val rows = (1..11).map { row("in_progress", owner = "o$it@sgspe.test") } + row("approved", owner = null)
        val owners =
            CommitmentKpis
                .breakdowns(rows, emptyMap(), emptyMap(), emptyMap())
                .breakdowns
                .first { it.by == "owner" }
                .rows
        assertEquals(12, owners.sumOf { it["value"] as Int })
        assertEquals("Otros", owners.last()["key"])
        assertEquals(2, owners.last()["value"])
    }

    @Test
    fun `serie de vencidos, casos de borde`() {
        val series = { rows: List<CommitmentRow>, rs: List<RescheduleRow>, e: String, t: String ->
            CommitmentKpis.overdueSeries(rows, rs, LocalDate.parse(e), LocalDate.parse(t)).series!!.associate { it["period"] to it["value"] }
        }
        // cumplido dentro del mes M no cuenta en M; cuenta en los anteriores a su cumplimiento
        val inMonth = row("fulfilled", due = "2026-07-10", fulfilled = "2026-08-15", agreed = "2026-07-01")
        val m = series(listOf(inMonth), emptyList(), "2026-10-31", "2026-10-31")
        assertEquals(1, m["2026-07"])
        assertEquals(0, m["2026-08"])
        // agreed_date posterior a M
        val late = row("approved", due = "2026-07-10", agreed = "2026-09-05")
        assertEquals(0, series(listOf(late), emptyList(), "2026-10-31", "2026-10-31")["2026-08"])
        // reprogramación creada después de M: en M usa la original
        val resched = row("in_progress", due = "2026-12-01", agreed = "2026-07-01")
        val rs = listOf(RescheduleRow(resched.id, LocalDate.parse("2026-07-10"), LocalDate.parse("2026-12-01"), LocalDate.parse("2026-09-20"), 1))
        val r = series(listOf(resched), rs, "2026-10-31", "2026-10-31")
        assertEquals(1, r["2026-08"])
        assertEquals(0, r["2026-10"])
        // sin reprogramaciones usa due_date; cancelado sin cancelled_date no cuenta
        val noCancelDate = row("cancelled", due = "2026-07-10", agreed = "2026-07-01")
        assertEquals(0, series(listOf(noCancelDate), emptyList(), "2026-10-31", "2026-10-31")["2026-08"])
        // end a mitad de mes: el último punto se mide a hoy, lo que vence después aún no cuenta
        val future = row("in_progress", due = "2026-10-20", agreed = "2026-07-01")
        val mid = CommitmentKpis.overdueSeries(listOf(future), emptyList(), LocalDate.parse("2026-10-31"), LocalDate.parse("2026-10-09"))
        assertEquals(0, mid.value)
        // mismas reprogramaciones del día: gana la creada después
        val same = row("in_progress", due = "2026-09-01", agreed = "2026-07-01")
        val two =
            listOf(
                RescheduleRow(same.id, LocalDate.parse("2026-07-10"), LocalDate.parse("2026-12-01"), LocalDate.parse("2026-08-01"), 1),
                RescheduleRow(same.id, LocalDate.parse("2026-12-01"), LocalDate.parse("2026-08-05"), LocalDate.parse("2026-08-01"), 2)
            )
        assertEquals(1, series(listOf(same), two, "2026-10-31", "2026-10-31")["2026-09"])
    }
}
