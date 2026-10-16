package sgspe.dashboard

import sgspe.common.Attention
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

// cada fila agregada lleva `tile` (cuenta en las tiles) y `own` (cuenta en la fila de su proyecto en byProject): solo
// difieren con el ámbito AIDS o AIIS y varios proyectos
data class AttentionCount(
    val project: String,
    val type: String,
    val state: String?,
    val tile: Boolean,
    val own: Boolean,
    // un registro de la lista de las tiles (gone) o de la de su proyecto (goneOwn) lo reclasificó: no cuenta (§3.3)
    val gone: Boolean,
    val goneOwn: Boolean,
    // solo de las quejas: el mes de Lima de received_at y el de closed_date (si está en closed) dentro de los 12 de la serie
    val received: YearMonth?,
    val closed: YearMonth?,
    val n: Int
)

data class HeldCount(
    val project: String,
    val type: String?,
    val month: YearMonth,
    val inPeriod: Boolean,
    val tile: Boolean,
    val own: Boolean,
    val n: Int
)

// un conteo por proyecto y por las claves de la consulta, en su orden (texto o fecha)
data class GroupCount(
    val project: String,
    val keys: List<Any?>,
    val tile: Boolean,
    val own: Boolean,
    val n: Int
)

// los KPI del tablero executive desde los conteos de la base (AggregateSql, plan 8.2a-bis), puros: cada función da lo mismo
// que su gemela sobre filas, que queda como referencia de la prueba (AttentionKpis.open, EngagementKpis.held, CommitmentKpis.open y overdue, RiskKpis.high,
// ExecutiveKpis.activeWarnings y trend) con las filas agrupadas como las agrupa el SQL (AggregateKpisTest). las tiles cuentan
// las filas con `tile`; la fila de un proyecto en byProject, las suyas con `own`, solo con su valor (sin desgloses ni series)
object AggregateKpis {
    private fun sum(rows: List<GroupCount>): Int = rows.sumOf { it.n }

    private fun byProject(
        projects: List<String>,
        value: (String) -> Int
    ): Map<String, IndicatorResult> = projects.associateWith { IndicatorResult(value(it)) }

    private fun <T> totals(
        rows: List<T>,
        key: (T) -> String,
        n: (T) -> Int
    ): Map<String, Int> = rows.groupBy(key).mapValues { (_, list) -> list.sumOf(n) }

    // KPI-GRV-02: abiertas hoy, por tipo (en el orden de los tres objetos) y por «<tipo>.<estado>»
    fun open(rows: List<AttentionCount>): IndicatorResult {
        val open = rows.filter { it.tile && !it.gone && Attention.isOpen(it.type, it.state) }
        val types = totals(open, { it.type }, { it.n })
        return IndicatorResult(
            open.sumOf { it.n },
            breakdowns =
                listOf(
                    Breakdown("type", Attention.OBJECTS.filter { it in types }.map { mapOf("key" to it, "value" to types.getValue(it)) }),
                    Breakdown("workflow_state", Numbers.tally(totals(open, { "${it.type}.${it.state}" }, { it.n })))
                )
        )
    }

    fun openByProject(
        rows: List<AttentionCount>,
        projects: List<String>
    ): Map<String, IndicatorResult> =
        byProject(projects) { p -> rows.filter { it.project == p && it.own && !it.goneOwn && Attention.isOpen(it.type, it.state) }.sumOf { it.n } }

    // KPI-ENG-01: las realizadas en el periodo por tipo y la serie de los 12 meses que terminan en `end`
    fun held(
        rows: List<HeldCount>,
        end: LocalDate
    ): IndicatorResult {
        val tile = rows.filter { it.tile }
        val period = tile.filter { it.inPeriod }
        return IndicatorResult(
            period.sumOf { it.n },
            breakdowns = listOf(Breakdown("engagement_type", Numbers.tally(totals(period, { it.type ?: EngagementKpis.NO_TYPE }, { it.n })))),
            series =
                EngagementKpis.months(end).map { month ->
                    mapOf("period" to month.toString(), "value" to tile.filter { it.month == month }.sumOf { it.n })
                }
        )
    }

    fun heldByProject(
        rows: List<HeldCount>,
        projects: List<String>
    ): Map<String, IndicatorResult> = byProject(projects) { p -> rows.filter { it.project == p && it.own && it.inPeriod }.sumOf { it.n } }

    // KPI-COM-02: los abiertos (la consulta ya deja fuera closed y cancelled)
    fun openCommitments(rows: List<GroupCount>): IndicatorResult = IndicatorResult(sum(rows.filter { it.tile }))

    // KPI-COM-03: los pendientes vencidos (la consulta ya los filtra), por días de atraso desde su due_date (la clave 0) con
    // los umbrales de su proyecto, con las claves y etiquetas de CommitmentKpis.overdue
    fun overdueCommitments(
        rows: List<GroupCount>,
        today: LocalDate,
        escalation: (String?) -> Escalation
    ): IndicatorResult {
        val tile = rows.filter { it.tile }
        val thresholds = tile.map { escalation(it.project) }.distinct()
        val counts = mutableMapOf<String, Int>()
        for (row in tile) {
            val (first, second) = escalation(row.project)
            val days = (row.keys[0] as LocalDate?)?.let { ChronoUnit.DAYS.between(it, today) } ?: Long.MAX_VALUE
            val bucket =
                when {
                    days <= first -> CommitmentKpis.BUCKET_FIRST
                    days <= second -> CommitmentKpis.BUCKET_SECOND
                    else -> CommitmentKpis.BUCKET_OVER
                }
            counts[bucket] = (counts[bucket] ?: 0) + row.n
        }
        val uniform = thresholds.singleOrNull() ?: Escalation(CommitmentKpis.DEFAULT_FIRST, CommitmentKpis.DEFAULT_SECOND).takeIf { thresholds.isEmpty() }
        val labels =
            mapOf(
                CommitmentKpis.BUCKET_FIRST to (uniform?.let { "1-${it.first}" } ?: "Hasta el primer umbral"),
                CommitmentKpis.BUCKET_SECOND to (uniform?.let { "${it.first + 1}-${it.second}" } ?: "Hasta el segundo umbral"),
                CommitmentKpis.BUCKET_OVER to (uniform?.let { "más de ${it.second}" } ?: "Más del segundo umbral")
            )
        val out =
            listOf(CommitmentKpis.BUCKET_FIRST, CommitmentKpis.BUCKET_SECOND, CommitmentKpis.BUCKET_OVER)
                .filter { it in counts }
                .map { mapOf("key" to it, "label" to labels.getValue(it), "value" to counts.getValue(it)) }
        return IndicatorResult(sum(tile), breakdowns = listOf(Breakdown("daysOverdue", out)))
    }

    // KPI-RSK-01: los activos con nivel ALTO o CRITICO (la consulta los filtra), por categoría (la clave 0)
    fun highRisks(rows: List<GroupCount>): IndicatorResult {
        val tile = rows.filter { it.tile }
        return IndicatorResult(
            sum(tile),
            breakdowns = listOf(Breakdown("category", Numbers.tally(totals(tile, { it.keys[0] as String? ?: RiskKpis.NO_DATA }, { it.n }))))
        )
    }

    // KPI-EXE-02: las activas (la consulta las filtra), por severidad (la clave 0) y por estado (la clave 1)
    fun activeWarnings(rows: List<GroupCount>): IndicatorResult {
        val tile = rows.filter { it.tile }
        return IndicatorResult(
            sum(tile),
            breakdowns =
                listOf(
                    Breakdown("severity", Numbers.tally(totals(tile, { it.keys[0] as String? ?: ExecutiveKpis.NO_DATA }, { it.n }))),
                    Breakdown("workflow_state", Numbers.tally(totals(tile, { it.keys[1] as String? ?: ExecutiveKpis.NO_DATA }, { it.n })))
                )
        )
    }

    // la cifra de cada proyecto de un conteo agrupado (KPI-COM-02, KPI-COM-03, KPI-RSK-01, KPI-EXE-02)
    fun groupedByProject(
        rows: List<GroupCount>,
        projects: List<String>
    ): Map<String, IndicatorResult> = byProject(projects) { p -> sum(rows.filter { it.project == p && it.own }) }

    // KPI-EXE-03: la serie de ExecutiveKpis.trend con las quejas desde los conteos (la regla de conteo de las tres QCS ya
    // aplicada en la base: gone)
    fun trend(
        engagements: IndicatorResult,
        attention: List<AttentionCount>,
        overdue: IndicatorResult,
        end: LocalDate
    ): IndicatorResult {
        val grievances = attention.filter { it.tile && !it.gone && it.type == Attention.GRIEVANCE }
        val held = engagements.series.orEmpty().associate { it["period"] to it["value"] }
        val late = overdue.series.orEmpty().associate { it["period"] to it["value"] }
        val series =
            EngagementKpis.months(end).map { month ->
                val period = month.toString()
                mapOf(
                    "period" to period,
                    "engagements" to (held[period] ?: 0),
                    "grievancesReceived" to grievances.filter { it.received == month }.sumOf { it.n },
                    "grievancesClosed" to grievances.filter { it.closed == month }.sumOf { it.n },
                    "overdueCommitments" to (late[period] ?: 0)
                )
            }
        return IndicatorResult(null, series = series)
    }
}
