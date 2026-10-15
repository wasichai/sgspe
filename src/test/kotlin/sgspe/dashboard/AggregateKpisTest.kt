package sgspe.dashboard

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import sgspe.commitment.Commitments
import sgspe.common.Attention
import sgspe.common.AttentionCounting
import java.time.LocalDate
import java.time.YearMonth
import kotlin.random.Random

// plan 8.2a-bis: cada KPI desde los conteos de la base da lo mismo que su gemela sobre filas, con filas al azar (semillas fijas)
// agrupadas como las agrupa AggregateSql (las funciones `group…` de abajo son el GROUP BY en Kotlin). la paridad del SQL con
// esas agrupaciones la prueba ExecutiveAggregatesParityApiTest contra la base
class AggregateKpisTest {
    private val end = LocalDate.parse("2026-09-30")
    private val start = LocalDate.parse("2026-09-01")
    private val today = LocalDate.parse("2026-10-14")
    private val months = EngagementKpis.months(end)
    private val seeds = listOf(1, 7, 42, 2026)

    private fun <T> Random.pick(list: List<T>): T = list[nextInt(list.size)]

    private fun day(random: Random): LocalDate = LocalDate.parse("2025-06-01").plusDays(random.nextLong(520))

    // ---- QCS ----

    private val states: Map<String, List<String?>> =
        mapOf(
            Attention.GRIEVANCE to listOf("received", "in_review", "resolved", "closed", "rejected", "withdrawn", null),
            Attention.INQUIRY to listOf("received", "answered", "closed"),
            Attention.REQUEST to listOf("received", "in_evaluation", "closed")
        )

    private fun qcs(
        random: Random,
        count: Int,
        prefix: String = "q"
    ): List<AttentionRow> {
        val rows = mutableListOf<AttentionRow>()
        repeat(count) { i ->
            val type = random.pick(Attention.OBJECTS)
            val state = random.pick(states.getValue(type))
            // un tercio apunta a una QCS anterior de otro tipo: la reclasificó
            val previous = rows.filter { it.type != type }.takeIf { it.isNotEmpty() && random.nextInt(3) == 0 }?.let { random.pick(it) }
            val reclassified = previous?.let { mapOf(Attention.reclassifiedField(type, it.type) to it.id) } ?: emptyMap()
            rows +=
                AttentionRow(
                    type,
                    "$prefix$i",
                    state,
                    day(random).takeIf { random.nextInt(10) > 0 },
                    null,
                    null,
                    day(random).takeIf { random.nextBoolean() },
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
        }
        return rows
    }

    // el GROUP BY de AggregateSql.attention para un proyecto, sin ámbito (tile y own verdaderos)
    private fun groupAttention(
        project: String,
        rows: List<AttentionRow>
    ): List<AttentionCount> {
        val gone = AttentionCounting.originals(rows.map { it.raised() })
        return rows
            .groupingBy { r ->
                val grievance = r.type == Attention.GRIEVANCE
                val received = r.received?.let(YearMonth::from)?.takeIf { grievance && it in months }
                val closed = r.closed?.let(YearMonth::from)?.takeIf { grievance && r.state == AttentionKpis.CLOSED && it in months }
                val out = (r.type to r.id) in gone
                AttentionCount(project, r.type, r.state, true, true, out, out, received, closed, 0)
            }.eachCount()
            .map { (key, n) -> key.copy(n = n) }
    }

    @Test
    fun `KPI-GRV-02 y las quejas de KPI-EXE-03 desde los conteos son las de las filas`() {
        for (seed in seeds) {
            val rows = qcs(Random(seed), 300)
            val counts = groupAttention("p", rows)
            assertEquals(AttentionKpis.open(rows), AggregateKpis.open(counts), "semilla $seed")
            val engagements = IndicatorResult(0, series = months.map { mapOf("period" to it.toString(), "value" to it.monthValue) })
            val overdue = IndicatorResult(0, series = months.map { mapOf("period" to it.toString(), "value" to it.year % 7) })
            assertEquals(ExecutiveKpis.trend(engagements, rows, overdue, end), AggregateKpis.trend(engagements, counts, overdue, end), "semilla $seed")
        }
    }

    @Test
    fun `la fila de un proyecto cuenta su reclasificacion solo si el registro nuevo es de ese proyecto`() {
        val random = Random(3)
        val a = qcs(random, 120, "a")
        val b = qcs(random, 120, "b")
        // b reclasificó un original de a: en las tiles el original no cuenta; en la fila de a, sí (como el filtro de a solo)
        val reclassifiedInA = AttentionCounting.originals(a.map { it.raised() })
        val original = a.first { it.type == Attention.GRIEVANCE && it.open && (it.type to it.id) !in reclassifiedInA }
        val successor = b.first { it.type == Attention.INQUIRY }.let { it.copy(reclassified = mapOf("reclassified_from_grievance" to original.id)) }
        val bRows = b.map { if (it.id == successor.id) successor else it }
        val all = a + bRows
        val gone = AttentionCounting.originals(all.map { it.raised() })
        // gone: apuntado por cualquier fila de las tiles; goneOwn: por una de su proyecto (lo que da el GROUP BY de la base)
        val marked =
            all.map { r ->
                val project = if (r in a) "a" else "b"
                val own = AttentionCounting.originals((if (project == "a") a else bRows).map { it.raised() })
                AttentionCount(project, r.type, r.state, true, true, (r.type to r.id) in gone, (r.type to r.id) in own, null, null, 1)
            }
        assertEquals(AttentionKpis.open(all).value, AggregateKpis.open(marked).value)
        val split = AggregateKpis.openByProject(marked, listOf("a", "b"))
        assertEquals(AttentionKpis.open(a).value, split.getValue("a").value)
        assertEquals(AttentionKpis.open(bRows).value, split.getValue("b").value)
        assertEquals(AttentionKpis.open(all).value, split.getValue("a").value as Int + split.getValue("b").value as Int - 1)
    }

    // ---- interacciones ----

    @Test
    fun `KPI-ENG-01 desde los conteos es el de las filas`() {
        for (seed in seeds) {
            val random = Random(seed)
            val periodStart = LocalDate.parse("2025-08-15")
            val windowStart = months.first().atDay(1)
            val rows =
                (0 until 400)
                    .map { i ->
                        EngagementRow("e$i", "p", random.pick(listOf("REUNION", "TALLER", "REUNION INFORMATIVA", null)), null, day(random), emptyMap())
                    }.filter { !it.heldDate.isBefore(minOf(periodStart, windowStart)) && !it.heldDate.isAfter(end) }
            val inPeriod = rows.filter { !it.heldDate.isBefore(periodStart) }
            val window = rows.filter { !it.heldDate.isBefore(windowStart) }
            val counts =
                rows
                    .groupingBy { HeldCount("p", it.type, YearMonth.from(it.heldDate), !it.heldDate.isBefore(periodStart), true, true, 0) }
                    .eachCount()
                    .map { (key, n) -> key.copy(n = n) }
            assertEquals(EngagementKpis.held(inPeriod, window, end), AggregateKpis.held(counts, end), "semilla $seed")
            assertEquals(inPeriod.size, AggregateKpis.heldByProject(counts, listOf("p", "q")).getValue("p").value)
            assertEquals(0, AggregateKpis.heldByProject(counts, listOf("p", "q")).getValue("q").value)
        }
    }

    // ---- compromisos ----

    private fun commitment(
        random: Random,
        i: Int
    ) = CommitmentRow(
        "c$i",
        random.pick(listOf("p1", "p2", "p3")),
        random.pick(CommitmentKpis.STATES + listOf<String?>(null)),
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        day(random).takeIf { random.nextInt(8) > 0 },
        null,
        null,
        random.pick(listOf(CommitmentKpis.OVERDUE, CommitmentKpis.DUE_SOON, null)),
        null,
        null,
        null
    )

    @Test
    fun `KPI-COM-02 y KPI-COM-03 desde los conteos son los de las filas, tambien con umbrales distintos por proyecto`() {
        for (seed in seeds) {
            val random = Random(seed)
            val rows = (0 until 300).map { commitment(random, it) }
            val open =
                rows
                    .filter { it.state !in Commitments.CLOSED }
                    .groupingBy { it.project!! }
                    .eachCount()
                    .map { (p, n) -> GroupCount(p, emptyList(), true, true, n) }
            assertEquals(CommitmentKpis.open(rows), AggregateKpis.openCommitments(open), "semilla $seed")
            val overdue =
                rows
                    .filter { it.state in Commitments.PENDING && it.deadlineStatus == CommitmentKpis.OVERDUE }
                    .groupingBy { it.project!! to it.dueDate }
                    .eachCount()
                    .map { (key, n) -> GroupCount(key.first, listOf(key.second), true, true, n) }
            for (thresholds in listOf(mapOf<String, Escalation>(), mapOf("p2" to Escalation(3, 30)))) {
                val escalation: (String?) -> Escalation = { thresholds[it] ?: Escalation(7, 15) }
                assertEquals(CommitmentKpis.overdue(rows, today, escalation), AggregateKpis.overdueCommitments(overdue, today, escalation), "semilla $seed")
            }
            val split = AggregateKpis.groupedByProject(overdue, listOf("p1", "p2", "p3", "p4"))
            for (p in listOf("p1", "p2", "p3", "p4")) {
                assertEquals(CommitmentKpis.overdue(rows.filter { it.project == p }, today) { Escalation(7, 15) }.value, split.getValue(p).value)
            }
        }
    }

    // ---- riesgos y alertas ----

    @Test
    fun `KPI-RSK-01 y KPI-EXE-02 desde los conteos son los de las filas`() {
        for (seed in seeds) {
            val random = Random(seed)
            val risks =
                (0 until 200).map {
                    RiskRow(
                        "r$it",
                        "p",
                        null,
                        random.pick(listOf("EXPECTATIVAS Y BENEFICIOS", "AGUA", null)),
                        null,
                        null,
                        random.pick(listOf("BAJO", "MEDIO", "ALTO", "CRITICO", null)),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                    )
                }
            val high =
                risks
                    .filter { it.level == "ALTO" || it.level == "CRITICO" }
                    .groupingBy { it.category }
                    .eachCount()
                    .map { (category, n) -> GroupCount("p", listOf(category), true, true, n) }
            assertEquals(RiskKpis.high(risks), AggregateKpis.highRisks(high), "semilla $seed")
            val warnings =
                (0 until 200).map {
                    WarningRow("w$it", random.pick(ExecutiveKpis.ACTIVE_WARNING_STATES.toList() + "closed"), random.pick(listOf("ALTO", "MEDIO", null)))
                }
            val active =
                warnings
                    .filter { it.state in ExecutiveKpis.ACTIVE_WARNING_STATES }
                    .groupingBy { it.severity to it.state }
                    .eachCount()
                    .map { (key, n) -> GroupCount("p", listOf(key.first, key.second), true, true, n) }
            assertEquals(ExecutiveKpis.activeWarnings(warnings), AggregateKpis.activeWarnings(active), "semilla $seed")
        }
    }

    @Test
    fun `las tiles cuentan las filas tile y cada proyecto las suyas own`() {
        val rows =
            listOf(
                GroupCount("a", emptyList(), tile = true, own = true, n = 2),
                GroupCount("a", emptyList(), tile = true, own = false, n = 3),
                GroupCount("b", emptyList(), tile = false, own = true, n = 5)
            )
        assertEquals(5, AggregateKpis.openCommitments(rows).value)
        val split = AggregateKpis.groupedByProject(rows, listOf("a", "b", "c"))
        assertEquals(listOf(2, 5, 0), listOf("a", "b", "c").map { split.getValue(it).value })
    }

    @Test
    fun `tally ordena como counts`() {
        val keys = listOf("b", "a", "c", "a", "b", "a", "d")
        assertEquals(Numbers.counts(keys), Numbers.tally(keys.groupingBy { it }.eachCount() + ("z" to 0)))
    }
}
