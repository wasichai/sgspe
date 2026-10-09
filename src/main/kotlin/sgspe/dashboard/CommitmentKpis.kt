package sgspe.dashboard

import sgspe.commitment.Commitments
import sgspe.common.Amounts
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

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

// una reprogramación: la fecha anterior, la nueva y el día (en Lima) en que se registró. `sequence` es el orden real de
// creación (el instante o el id) y desempata las del mismo día. previous y newDate son obligatorias: quien lee las filas
// descarta o rechaza una reprogramación sin ellas, estas funciones nunca la sustituyen por el plazo vigente
data class RescheduleRow(
    val commitment: String,
    val previous: LocalDate,
    val newDate: LocalDate,
    val created: LocalDate,
    val sequence: Long
)

// las reprogramaciones leídas y los ids de las que se excluyeron por datos incompletos
class RescheduleRead(
    val rows: List<RescheduleRow>,
    val skipped: List<String>
) {
    // el resultado con el aviso de que está incompleto si se excluyó alguna reprogramación (nunca en silencio)
    fun warn(result: IndicatorResult): IndicatorResult =
        if (skipped.isEmpty()) {
            result
        } else {
            result.copy(warnings = result.warnings + "${skipped.size} reprogramaciones con datos incompletos se excluyeron: el resultado está incompleto")
        }
}

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
    const val BUCKET_FIRST = "OVERDUE_FIRST"
    const val BUCKET_SECOND = "OVERDUE_SECOND"
    const val BUCKET_OVER = "OVERDUE_OVER"
    const val DEFAULT_FIRST = 7L
    const val DEFAULT_SECOND = 15L

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
        val overdue = rows.filter { it.state in Commitments.PENDING && it.deadlineStatus == OVERDUE }
        val thresholds = overdue.map { escalation(it.project) }.distinct()
        val buckets =
            overdue.map { row ->
                val (first, second) = escalation(row.project)
                val days = row.dueDate?.let { ChronoUnit.DAYS.between(it, today) } ?: Long.MAX_VALUE
                when {
                    days <= first -> BUCKET_FIRST
                    days <= second -> BUCKET_SECOND
                    else -> BUCKET_OVER
                }
            }
        val counts = buckets.groupingBy { it }.eachCount()
        // las claves son estables entre proyectos; la etiqueta lleva los umbrales solo si son los mismos en todas las filas
        val uniform = thresholds.singleOrNull() ?: Escalation(DEFAULT_FIRST, DEFAULT_SECOND).takeIf { thresholds.isEmpty() }
        val labels =
            mapOf(
                BUCKET_FIRST to (uniform?.let { "1-${it.first}" } ?: "Hasta el primer umbral"),
                BUCKET_SECOND to (uniform?.let { "${it.first + 1}-${it.second}" } ?: "Hasta el segundo umbral"),
                BUCKET_OVER to (uniform?.let { "más de ${it.second}" } ?: "Más del segundo umbral")
            )
        val rowsOut =
            listOf(BUCKET_FIRST, BUCKET_SECOND, BUCKET_OVER)
                .filter { it in counts }
                .map { mapOf("key" to it, "label" to labels.getValue(it), "value" to counts.getValue(it)) }
        return IndicatorResult(overdue.size, breakdowns = listOf(Breakdown("daysOverdue", rowsOut)))
    }

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
        val original = reschedules.groupBy { it.commitment }.mapValues { (_, list) -> list.minWith(compareBy({ it.created }, { it.sequence })).previous }
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
        // P45: sin monto o sin moneda no hay qué sumar y se omite la fila
        val committed = rows.filter { it.state in Commitments.COMMITTED && it.amount != null && it.currency != null }
        val main = campaignCurrency ?: DEFAULT_CURRENCY
        val byCurrency = committed.groupBy { it.currency!! }
        val shown = if (campaignCurrency == null) byCurrency else byCurrency.filterKeys { it == campaignCurrency }
        val outRows =
            shown.entries.sortedBy { it.key }.map { (currency, list) ->
                val amount = scaled(list.mapNotNull { it.amount }.fold(BigDecimal.ZERO, BigDecimal::add))
                val executed = scaled(list.mapNotNull { it.executed }.fold(BigDecimal.ZERO, BigDecimal::add))
                mapOf("key" to currency, "value" to amount, "committed" to amount, "executed" to executed)
            }
        val other = if (campaignCurrency == null) 0 else committed.count { it.currency != campaignCurrency }
        // P45: sin compromisos con monto en la moneda principal no hay base de comparación y value es null
        return IndicatorResult(
            outRows.firstOrNull { it["key"] == main }?.get("value") as BigDecimal?,
            breakdowns = listOf(Breakdown("currency", outRows)),
            warnings = if (other > 0) listOf("$other compromisos en otra moneda que la de la campaña no se suman") else emptyList()
        )
    }

    // P44: los montos salen con la escala de Amounts, para que el JSON no cambie con la fila de origen
    private fun scaled(value: BigDecimal): BigDecimal = value.setScale(Amounts.MAX_SCALE, java.math.RoundingMode.HALF_UP)

    // KPI-COM-11: para cada fin de mes M de los 12 que terminan en `end`, los compromisos vencidos en M (dashboards.md §4.4):
    // hoy en approved o después (o cancelados después de M), con agreed_date hasta M, con su fecha vigente en M anterior a M y
    // sin fulfilled_date hasta M. la fecha vigente en M es la new_due_date de la última reprogramación registrada hasta M o, si
    // no la hay, la original (la previous_due_date de la primera, o due_date si nunca se reprogramó). si `end` cae en un mes
    // posterior al de hoy, los meses después de hoy repiten el punto de hoy: el mes en curso se mide a hoy y no hay futuro
    fun overdueSeries(
        rows: List<CommitmentRow>,
        reschedules: List<RescheduleRow>,
        end: LocalDate,
        today: LocalDate
    ): IndicatorResult {
        val byCommitment = reschedules.groupBy { it.commitment }.mapValues { (_, list) -> list.sortedWith(compareBy({ it.created }, { it.sequence })) }
        val months = (11 downTo 0).map { YearMonth.from(end).minusMonths(it.toLong()) }
        val series =
            months.map { month ->
                // el mes que aún no cierra se mide a hoy: lo que vence entre hoy y fin de mes todavía no está vencido
                val closing = minOf(month.atEndOfMonth(), today)
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
