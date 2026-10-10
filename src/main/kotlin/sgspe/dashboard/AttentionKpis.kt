package sgspe.dashboard

import sgspe.common.Attention
import sgspe.common.AttentionCounting
import sgspe.common.Raised
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

// una QCS como la leen los KPI-GRV, ya acotada a los proyectos, al territorio y a lo que quien mira lee (una confidencial que
// no ve no está). las fechas son de Lima. `reclassified`: sus vínculos reclassified_from_* informados (campo -> id)
data class AttentionRow(
    val type: String,
    val id: String,
    val state: String?,
    val received: LocalDate?,
    val responded: LocalDate?,
    val resolved: LocalDate?,
    val closed: LocalDate?,
    val resolutionDue: LocalDate?,
    val responseDue: LocalDate?,
    val deadlineStatus: String?,
    val channel: String?,
    val category: String?,
    val severity: String?,
    val locality: String?,
    val community: String?,
    val stakeholder: String?,
    val anonymous: Boolean,
    val reclassified: Map<String, String> = emptyMap()
) {
    fun raised(): Raised = Raised(type, id, state, reclassified)

    val open: Boolean get() = Attention.isOpen(type, state)
}

// los KPI-GRV (dashboards.md §4.5) como funciones puras. cada planteamiento cuenta una vez (AttentionCounting, §3.3): los
// originales reclasificados nunca cuentan, salvo que su registro nuevo no esté en la lista (quien mira no lo lee)
object AttentionKpis {
    const val OVERDUE = "VENCIDO"
    const val OTHERS = "Otros"
    const val ANONYMOUS = "ANONIMA"
    const val NONE = "SIN DATO"
    const val TOP = 10

    // los planteamientos de la lista (sin los originales reclasificados)
    fun counted(rows: List<AttentionRow>): List<AttentionRow> {
        val kept = AttentionCounting.counted(rows.map { it.raised() }).map { it.type to it.id }.toSet()
        return rows.filter { (it.type to it.id) in kept }
    }

    private fun within(
        date: LocalDate?,
        start: LocalDate,
        end: LocalDate
    ): Boolean = date != null && !date.isBefore(start) && !date.isAfter(end)

    // cuántas por clave, en el orden de los tres objetos o de mayor a menor
    private fun byType(rows: List<AttentionRow>): Breakdown {
        val counts = rows.groupingBy { it.type }.eachCount()
        return Breakdown("type", Attention.OBJECTS.filter { it in counts }.map { mapOf("key" to it, "value" to counts.getValue(it)) })
    }

    // KPI-GRV-01: recibidas en el periodo, por tipo y por canal
    fun received(
        rows: List<AttentionRow>,
        start: LocalDate,
        end: LocalDate
    ): IndicatorResult {
        val inPeriod = counted(rows).filter { within(it.received, start, end) }
        return IndicatorResult(inPeriod.size, breakdowns = listOf(byType(inPeriod), Breakdown("channel", Numbers.counts(inPeriod.map { it.channel ?: NONE }))))
    }

    // KPI-GRV-02: abiertas hoy (dashboards.md §3.2), por tipo y por estado (clave «<tipo>.<estado>»)
    fun open(rows: List<AttentionRow>): IndicatorResult {
        val open = counted(rows).filter { it.open }
        return IndicatorResult(open.size, breakdowns = listOf(byType(open), Breakdown("workflow_state", Numbers.counts(open.map { "${it.type}.${it.state}" }))))
    }

    // KPI-GRV-03: quejas con resolved_date en el periodo, por severidad (una reabierta no cuenta: la reapertura vacía la fecha)
    fun resolved(
        rows: List<AttentionRow>,
        start: LocalDate,
        end: LocalDate
    ): IndicatorResult {
        val done = rows.filter { it.type == Attention.GRIEVANCE && within(it.resolved, start, end) }
        return IndicatorResult(done.size, breakdowns = listOf(Breakdown("severity", Numbers.counts(done.map { it.severity ?: NONE }))))
    }

    // KPI-GRV-04: en closed con closed_date en el periodo, por tipo, sin los originales cerrados por reclasificación
    fun closed(
        rows: List<AttentionRow>,
        start: LocalDate,
        end: LocalDate
    ): IndicatorResult {
        val done = counted(rows).filter { it.state == CLOSED && within(it.closed, start, end) }
        return IndicatorResult(done.size, breakdowns = listOf(byType(done)))
    }

    // KPI-GRV-05: quejas rechazadas con closed_date en el periodo; las desistidas, aparte en el desglose
    fun rejected(
        rows: List<AttentionRow>,
        start: LocalDate,
        end: LocalDate
    ): IndicatorResult {
        val finals = rows.filter { it.type == Attention.GRIEVANCE && it.state in REJECTED_OR_WITHDRAWN && within(it.closed, start, end) }
        val counts = finals.groupingBy { it.state!! }.eachCount()
        return IndicatorResult(
            counts[REJECTED] ?: 0,
            breakdowns = listOf(Breakdown("workflow_state", REJECTED_OR_WITHDRAWN.map { mapOf("key" to it, "value" to (counts[it] ?: 0)) }))
        )
    }

    // KPI-GRV-06: abiertas con deadline_status VENCIDO, por tipo
    fun overdue(rows: List<AttentionRow>): IndicatorResult {
        val late = counted(rows).filter { it.open && it.deadlineStatus == OVERDUE }
        return IndicatorResult(late.size, breakdowns = listOf(byType(late)))
    }

    // KPI-GRV-07 (#6 de la Tabla 9.5): recibidas en el periodo con responded_at, en cualquier estado ÷ recibidas en el periodo
    fun absolved(
        rows: List<AttentionRow>,
        start: LocalDate,
        end: LocalDate
    ): IndicatorResult {
        val inPeriod = counted(rows).filter { within(it.received, start, end) }
        val answered = inPeriod.count { it.responded != null }
        val byType =
            Attention.OBJECTS.mapNotNull { type ->
                val list = inPeriod.filter { it.type == type }
                if (list.isEmpty()) {
                    null
                } else {
                    val n = list.count { it.responded != null }
                    mapOf("key" to type, "value" to Numbers.percent(n, list.size), "numerator" to n, "denominator" to list.size)
                }
            }
        return IndicatorResult(Numbers.percent(answered, inPeriod.size), answered, inPeriod.size, listOf(Breakdown("type", byType)))
    }

    // KPI-GRV-08: las recibidas en el periodo (value) y las abiertas (open) por severidad, categoría, territorio (la localidad o,
    // sin ella, la comunidad), grupo de interés (las anónimas aparte) y canal; los 10 primeros y «Otros»
    fun breakdowns(
        rows: List<AttentionRow>,
        start: LocalDate,
        end: LocalDate,
        territories: Map<String, String>,
        stakeholders: Map<String, String>
    ): IndicatorResult {
        val counted = counted(rows)
        val inPeriod = counted.filter { within(it.received, start, end) }
        val open = counted.filter { it.open }

        fun by(
            name: String,
            key: (AttentionRow) -> String?,
            label: (String) -> String
        ): Breakdown {
            val keys = (inPeriod + open).map { key(it) ?: NONE }.distinct()
            val sorted =
                keys
                    .map { k -> Triple(k, inPeriod.count { (key(it) ?: NONE) == k }, open.count { (key(it) ?: NONE) == k }) }
                    .sortedWith(compareByDescending<Triple<String, Int, Int>> { it.second }.thenByDescending { it.third }.thenBy { it.first })
            val top =
                sorted.take(TOP).map { (k, value, opened) ->
                    mapOf(
                        "key" to k,
                        "label" to
                            if (k ==
                                NONE
                            ) {
                                "Sin dato"
                            } else {
                                label(k)
                            },
                        "value" to value,
                        "open" to opened
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
                            "open" to rest.sumOf { it.third }
                        )
                    )
                }
            return Breakdown(name, top + others)
        }
        return IndicatorResult(
            inPeriod.size,
            breakdowns =
                listOf(
                    by("severity", { if (it.type == Attention.GRIEVANCE) it.severity else null }) { it },
                    by("category", { it.category }) { it },
                    by("territory", { it.locality ?: it.community }) { territories[it] ?: "Territorio" },
                    by("stakeholder", { if (it.anonymous) ANONYMOUS else it.stakeholder }) {
                        if (it ==
                            ANONYMOUS
                        ) {
                            "Anónimas"
                        } else {
                            stakeholders[it] ?: Labels.STAKEHOLDER
                        }
                    },
                    by("channel", { it.channel }) { it }
                )
        )
    }

    // KPI-GRV-09: los 12 meses que terminan en `end`, recibidas (como KPI-GRV-01) y cerradas (como KPI-GRV-04); value es lo
    // recibido en el último mes
    fun receivedVsClosed(
        rows: List<AttentionRow>,
        end: LocalDate
    ): IndicatorResult {
        val counted = counted(rows)
        val series =
            (11 downTo 0).map { YearMonth.from(end).minusMonths(it.toLong()) }.map { month ->
                val received = counted.count { it.received != null && YearMonth.from(it.received) == month }
                val closed = counted.count { it.state == CLOSED && it.closed != null && YearMonth.from(it.closed) == month }
                mapOf("period" to month.toString(), "value" to received, "received" to received, "closed" to closed)
            }
        return IndicatorResult(series.last()["value"] as Int, series = series)
    }

    // KPI-GRV-10: sobre las atendidas en el periodo (queja con resolved_date; consulta y solicitud con responded_at), la mediana
    // de días desde la recepción (value) y el porcentaje dentro de plazo frente a resolution_due_date (quejas) y
    // response_due_date (consultas): numerator y denominator. las solicitudes no tienen plazo y solo dan su mediana
    fun attentionTime(
        rows: List<AttentionRow>,
        start: LocalDate,
        end: LocalDate
    ): IndicatorResult {
        val done = counted(rows).filter { within(attended(it), start, end) && it.received != null }
        val measured = done.filter { due(it) != null }
        val onTime = measured.count { !attended(it)!!.isAfter(due(it)) }
        val byType =
            Attention.OBJECTS.mapNotNull { type ->
                val list = done.filter { it.type == type }
                if (list.isEmpty()) return@mapNotNull null
                val withDue = list.filter { due(it) != null }
                val n = withDue.count { !attended(it)!!.isAfter(due(it)) }
                linkedMapOf<String, Any?>("key" to type, "value" to median(list.map(::days)), "attended" to list.size).apply {
                    if (type != Attention.REQUEST) {
                        put("onTime", Numbers.percent(n, withDue.size))
                        put("numerator", n)
                        put("denominator", withDue.size)
                    }
                }
            }
        return IndicatorResult(median(done.map(::days)), onTime, measured.size, listOf(Breakdown("type", byType)))
    }

    // el día en que se atendió: la resolución de una queja, la respuesta de una consulta o una solicitud
    private fun attended(row: AttentionRow): LocalDate? = if (row.type == Attention.GRIEVANCE) row.resolved else row.responded

    private fun due(row: AttentionRow): LocalDate? =
        when (row.type) {
            Attention.GRIEVANCE -> row.resolutionDue
            Attention.INQUIRY -> row.responseDue
            else -> null
        }

    private fun days(row: AttentionRow): Long = ChronoUnit.DAYS.between(row.received, attended(row))

    // la mediana con un decimal; null sin valores
    fun median(values: List<Long>): BigDecimal? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val middle = sorted.size / 2
        val value = if (sorted.size % 2 == 1) BigDecimal(sorted[middle]) else BigDecimal(sorted[middle - 1] + sorted[middle]).divide(BigDecimal(2))
        return value.setScale(1, RoundingMode.HALF_UP)
    }

    const val CLOSED = "closed"
    const val REJECTED = "rejected"
    val REJECTED_OR_WITHDRAWN = listOf(REJECTED, "withdrawn")
}
