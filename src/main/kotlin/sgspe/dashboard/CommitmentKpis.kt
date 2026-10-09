package sgspe.dashboard

import sgspe.commitment.Commitments
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

// un compromiso como lo leen los KPI-COM, ya acotado a los filtros y a los permisos de quien mira
data class CommitmentRow(
    val id: String,
    val project: String?,
    val state: String?,
    val ownerEmail: String?,
    val locality: String?,
    val community: String?,
    val topic: String?,
    val theme: String?,
    val formality: String?,
    val agreedDate: LocalDate?,
    val dueDate: LocalDate?,
    val fulfilledDate: LocalDate?,
    val cancelledDate: LocalDate?,
    val deadlineStatus: String?,
    val amount: BigDecimal?,
    val currency: String?,
    val executed: BigDecimal?
)

// una reprogramación: la fecha anterior, la nueva y el día (en Lima) en que se registró
data class RescheduleRow(
    val commitment: String,
    val previous: LocalDate?,
    val newDate: LocalDate?,
    val created: LocalDate
)

// los umbrales de escalamiento de un proyecto (escalation_first_days y escalation_second_days, 7 y 15 por defecto)
data class Escalation(
    val first: Long,
    val second: Long
)

// los KPI-COM (dashboards.md §4.4) como funciones puras, con los conjuntos de estados de §3.2 (Commitments)
object CommitmentKpis {
    const val OVERDUE = "VENCIDO"
    const val DUE_SOON = "POR VENCER"
    const val FORMAL = "FORMAL"
    const val OTHERS = "Otros"
    const val TOP = 10
    const val DEFAULT_CURRENCY = "PEN"

    // los estados de WF-D en el orden del workflow: el orden de los desgloses por estado
    val STATES = listOf("draft", "pending_approval", "approved", "in_progress", "blocked", "fulfilled", "verified", "closed", "cancelled")

    // KPI-COM-01: todos, por estado (solo los estados con compromisos)
    fun registered(rows: List<CommitmentRow>): IndicatorResult {
        val counts = rows.groupingBy { it.state ?: "sin estado" }.eachCount()
        val ordered = STATES.filter { it in counts } + counts.keys.filter { it !in STATES }.sorted()
        return IndicatorResult(rows.size, breakdowns = listOf(Breakdown("workflow_state", ordered.map { mapOf("key" to it, "value" to counts.getValue(it)) })))
    }

    // KPI-COM-02: abiertos (todos salvo closed y cancelled)
    fun open(rows: List<CommitmentRow>): IndicatorResult = IndicatorResult(rows.count { it.state !in Commitments.CLOSED })

    // KPI-COM-03: pendientes de cumplimiento con deadline_status VENCIDO, por días de atraso con los umbrales de su proyecto:
    // «1-<primero>», «<primero+1>-<segundo>» y «más de <segundo>»
    fun overdue(
        rows: List<CommitmentRow>,
        today: LocalDate,
        escalation: (String?) -> Escalation
    ): IndicatorResult {
        val overdue = rows.filter { it.state in Commitments.PENDING && it.deadlineStatus == OVERDUE && it.dueDate != null }
        val buckets =
            overdue.map { row ->
                val days =
                    java.time.temporal.ChronoUnit.DAYS
                        .between(row.dueDate, today)
                val (first, second) = escalation(row.project).let { it.first to it.second }
                when {
                    days <= first -> "1-$first"
                    days <= second -> "${first + 1}-$second"
                    else -> "más de $second"
                }
            }
        val rowsOut =
            buckets
                .groupingBy { it }
                .eachCount()
                .entries
                .sortedBy { bucketOrder(it.key) }
                .map { mapOf("key" to it.key, "value" to it.value) }
        return IndicatorResult(overdue.size, breakdowns = listOf(Breakdown("daysOverdue", rowsOut)))
    }

    private fun bucketOrder(key: String): Long = if (key.startsWith("más de ")) Long.MAX_VALUE else key.substringBefore('-').toLongOrNull() ?: 0

    // KPI-COM-04: pendientes de cumplimiento con deadline_status POR VENCER
    fun dueSoon(rows: List<CommitmentRow>): IndicatorResult = IndicatorResult(rows.count { it.state in Commitments.PENDING && it.deadlineStatus == DUE_SOON })

    // KPI-COM-05: cumplidos con fulfilled_date en el periodo
    fun fulfilled(
        rows: List<CommitmentRow>,
        start: LocalDate,
        end: LocalDate
    ): IndicatorResult = IndicatorResult(fulfilledIn(rows, start, end).size)

    private fun fulfilledIn(
        rows: List<CommitmentRow>,
        start: LocalDate,
        end: LocalDate
    ): List<CommitmentRow> =
        rows.filter {
            it.state in Commitments.FULFILLED && it.fulfilledDate != null && !it.fulfilledDate.isBefore(start) &&
                !it.fulfilledDate.isAfter(end)
        }

    // KPI-COM-06: en blocked
    fun blocked(rows: List<CommitmentRow>): IndicatorResult = IndicatorResult(rows.count { it.state == "blocked" })

    // KPI-COM-07: abiertos y vencidos por responsable, proyecto, territorio (la localidad o, sin ella, la comunidad) y tema; los
    // 10 de más abiertos y «Otros». cada fila: key (el id o el correo), label, value (abiertos) y overdue (vencidos)
    fun breakdowns(
        rows: List<CommitmentRow>,
        projects: Map<String, String>,
        territories: Map<String, String>,
        topics: Map<String, String>
    ): IndicatorResult {
        val open = rows.filter { it.state !in Commitments.CLOSED }

        fun by(
            name: String,
            key: (CommitmentRow) -> String?,
            label: (String) -> String
        ): Breakdown {
            val groups = open.groupBy { key(it) ?: "" }
            val sorted =
                groups.entries
                    .map { (k, list) -> Triple(k, list.size, list.count { it.state in Commitments.PENDING && it.deadlineStatus == OVERDUE }) }
                    .sortedWith(compareByDescending<Triple<String, Int, Int>> { it.second }.thenBy { it.first })
            val top =
                sorted.take(TOP).map { (k, value, overdue) ->
                    mapOf(
                        "key" to k.ifEmpty { null },
                        "label" to if (k.isEmpty()) "Sin dato" else label(k),
                        "value" to value,
                        "overdue" to overdue
                    )
                }
            val rest = sorted.drop(TOP)
            val others =
                if (rest.isEmpty()) {
                    emptyList()
                } else {
                    listOf(
                        mapOf(
                            "key" to OTHERS,
                            "label" to OTHERS,
                            "value" to rest.sumOf { it.second },
                            "overdue" to rest.sumOf { it.third }
                        )
                    )
                }
            return Breakdown(name, top + others)
        }
        return IndicatorResult(
            open.size,
            breakdowns =
                listOf(
                    by("owner", { it.ownerEmail }) { it },
                    by("project", { it.project }) { projects[it] ?: "Proyecto" },
                    by("territory", { it.locality ?: it.community }) { territories[it] ?: "Territorio" },
                    by("topic", { it.topic }) { topics[it] ?: "Tema" }
                )
        )
    }

    // KPI-COM-08 (#10 de la Tabla 9.5): formales (escritos), salvo los cancelados, por tema, con agreed_date en el periodo
    fun formalByTheme(
        rows: List<CommitmentRow>,
        start: LocalDate,
        end: LocalDate
    ): IndicatorResult {
        val formal =
            rows.filter {
                it.formality == FORMAL && it.state != "cancelled" && it.agreedDate != null && !it.agreedDate.isBefore(start) && !it.agreedDate.isAfter(end)
            }
        return IndicatorResult(formal.size, breakdowns = listOf(Breakdown("commitment_theme", Numbers.counts(formal.map { it.theme ?: "SIN TEMA" }))))
    }

    // KPI-COM-09: cumplidos en el periodo dentro de su plazo vigente ÷ cumplidos en el periodo; también frente a la fecha
    // original (la previous_due_date de su primera reprogramación)
    fun onTime(
        rows: List<CommitmentRow>,
        reschedules: List<RescheduleRow>,
        start: LocalDate,
        end: LocalDate
    ): IndicatorResult {
        val done = fulfilledIn(rows, start, end)
        val original = reschedules.groupBy { it.commitment }.mapValues { (_, list) -> list.minWith(compareBy({ it.created }, { it.previous })).previous }
        val current = done.count { it.dueDate != null && !it.fulfilledDate!!.isAfter(it.dueDate) }
        val againstOriginal =
            done.count { row ->
                val date = original[row.id] ?: row.dueDate
                date != null && !row.fulfilledDate!!.isAfter(date)
            }
        return IndicatorResult(
            Numbers.percent(current, done.size),
            current,
            done.size,
            breakdowns =
                listOf(
                    Breakdown(
                        "against",
                        listOf(
                            mapOf("key" to "current", "value" to Numbers.percent(current, done.size), "numerator" to current, "denominator" to done.size),
                            mapOf(
                                "key" to "original",
                                "value" to Numbers.percent(againstOriginal, done.size),
                                "numerator" to againstOriginal,
                                "denominator" to done.size
                            )
                        )
                    )
                )
        )
    }

    // KPI-COM-10: por moneda, el monto de los comprometidos frente a su executed_amount (ASM-063). con campaña, solo se suma su
    // moneda y lo demás se cuenta como advertencia; sin campaña, value es el de PEN y cada moneda tiene su fila
    fun amounts(
        rows: List<CommitmentRow>,
        campaignCurrency: String?
    ): IndicatorResult {
        val committed = rows.filter { it.state in Commitments.COMMITTED }
        val byCurrency = committed.groupBy { it.currency ?: DEFAULT_CURRENCY }
        val shown = if (campaignCurrency == null) byCurrency else byCurrency.filterKeys { it == campaignCurrency }
        val outRows =
            shown.entries.sortedBy { it.key }.map { (currency, list) ->
                val amount = list.mapNotNull { it.amount }.fold(BigDecimal.ZERO, BigDecimal::add)
                val executed = list.mapNotNull { it.executed }.fold(BigDecimal.ZERO, BigDecimal::add)
                mapOf("key" to currency, "value" to amount, "committed" to amount, "executed" to executed)
            }
        val other = if (campaignCurrency == null) 0 else committed.count { (it.currency ?: DEFAULT_CURRENCY) != campaignCurrency }
        val main = outRows.firstOrNull { it["key"] == (campaignCurrency ?: DEFAULT_CURRENCY) }
        return IndicatorResult(
            main?.get("value") as BigDecimal?,
            breakdowns = listOf(Breakdown("currency", outRows)),
            warnings = if (other > 0) listOf("$other compromisos en otra moneda que la de la campaña no se suman") else emptyList()
        )
    }

    // KPI-COM-11: para cada fin de mes M de los 12 que terminan en `end`, los compromisos vencidos en M (dashboards.md §4.4):
    // hoy en approved o después (o cancelados después de M), con agreed_date hasta M, con su fecha vigente en M anterior a M y
    // sin fulfilled_date hasta M. la fecha vigente en M es la new_due_date de la última reprogramación registrada hasta M o, si
    // no la hay, la original (la previous_due_date de la primera, o due_date si nunca se reprogramó)
    fun overdueSeries(
        rows: List<CommitmentRow>,
        reschedules: List<RescheduleRow>,
        end: LocalDate
    ): IndicatorResult {
        val byCommitment = reschedules.groupBy { it.commitment }.mapValues { (_, list) -> list.sortedWith(compareBy({ it.created }, { it.newDate })) }
        val months = (11 downTo 0).map { YearMonth.from(end).minusMonths(it.toLong()) }
        val series =
            months.map { month ->
                val closing = month.atEndOfMonth()
                val count =
                    rows.count { row ->
                        val alive =
                            row.state in Commitments.COMMITTED || (row.state == "cancelled" && row.cancelledDate != null && row.cancelledDate.isAfter(closing))
                        alive && row.agreedDate != null && !row.agreedDate.isAfter(closing) &&
                            dueAt(row, byCommitment[row.id].orEmpty(), closing)?.isBefore(closing) == true &&
                            (row.fulfilledDate == null || row.fulfilledDate.isAfter(closing))
                    }
                mapOf("period" to month.toString(), "value" to count)
            }
        return IndicatorResult(series.last()["value"] as Int, series = series)
    }

    fun dueAt(
        row: CommitmentRow,
        reschedules: List<RescheduleRow>,
        closing: LocalDate
    ): LocalDate? {
        val last = reschedules.lastOrNull { !it.created.isAfter(closing) }
        if (last != null) return last.newDate
        return reschedules.firstOrNull()?.previous ?: row.dueDate
    }
}
