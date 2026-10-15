package sgspe.dashboard

import sgspe.common.Attention
import java.time.LocalDate
import java.time.YearMonth

// un proyecto como lo lee KPI-EXE-01: su estado de WF-A, su etapa y su tipo
data class ProjectRow(
    val id: String,
    val state: String?,
    val stage: String?,
    val type: String?
)

// una alerta temprana como la lee KPI-EXE-02: su estado de WF-J y su severidad
data class WarningRow(
    val id: String,
    val state: String?,
    val severity: String?
)

// los KPI ejecutivos (dashboards.md §4.1) como funciones puras, y las zonas del tablero executive (§5.1)
object ExecutiveKpis {
    const val ACTIVE_PROJECTS = "KPI-EXE-01"
    const val ACTIVE_WARNINGS = "KPI-EXE-02"
    const val TREND = "KPI-EXE-03"
    const val ACTIVE = "active"
    const val NO_DATA = "SIN DATO"

    // dashboards.md §3.2: alerta activa
    val ACTIVE_WARNING_STATES: Set<String> = setOf("detected", "in_triage", "confirmed", "responding")

    // las zonas de §5.1 en su orden: las tiles, la inversión y la tendencia
    val TILES: List<String> =
        listOf(ACTIVE_PROJECTS, "KPI-COM-02", "KPI-COM-03", "KPI-GRV-02", "KPI-RSK-01", "KPI-ENG-01", ACTIVE_WARNINGS, "KPI-INV-03", TREND)

    // la tabla por proyecto: las mismas cifras con valor (la tendencia no tiene valor escalar)
    val BY_PROJECT: List<String> = TILES - TREND

    // KPI-EXE-01: los proyectos en active, por etapa y por tipo
    fun activeProjects(rows: List<ProjectRow>): IndicatorResult {
        val active = rows.filter { it.state == ACTIVE }
        return IndicatorResult(
            active.size,
            breakdowns =
                listOf(
                    Breakdown("stage", Numbers.counts(active.map { it.stage ?: NO_DATA })),
                    Breakdown("project_type", Numbers.counts(active.map { it.type ?: NO_DATA }))
                )
        )
    }

    // KPI-EXE-02: las alertas activas, por severidad y por estado
    fun activeWarnings(rows: List<WarningRow>): IndicatorResult {
        val active = rows.filter { it.state in ACTIVE_WARNING_STATES }
        return IndicatorResult(
            active.size,
            breakdowns =
                listOf(
                    Breakdown("severity", Numbers.counts(active.map { it.severity ?: NO_DATA })),
                    Breakdown("workflow_state", Numbers.counts(active.map { it.state ?: NO_DATA }))
                )
        )
    }

    // KPI-EXE-03: una fila por mes de los 12 que terminan en `end`, con las interacciones realizadas (la serie de KPI-ENG-01),
    // las quejas recibidas y cerradas (como KPI-GRV-09 pero solo grievance, con la regla de reclasificación de las tres QCS) y
    // los compromisos vencidos al cierre (la serie de KPI-COM-11). sin valor escalar: no se cierra (NO_SCALAR_VALUE)
    fun trend(
        engagements: IndicatorResult,
        attention: List<AttentionRow>,
        overdue: IndicatorResult,
        end: LocalDate
    ): IndicatorResult {
        val grievances = AttentionKpis.counted(attention).filter { it.type == Attention.GRIEVANCE }
        val held = engagements.series.orEmpty().associate { it["period"] to it["value"] }
        val late = overdue.series.orEmpty().associate { it["period"] to it["value"] }
        val series =
            EngagementKpis.months(end).map { month ->
                val period = month.toString()
                mapOf(
                    "period" to period,
                    "engagements" to (held[period] ?: 0),
                    "grievancesReceived" to grievances.count { it.received != null && YearMonth.from(it.received) == month },
                    "grievancesClosed" to grievances.count { it.state == CLOSED && it.closed != null && YearMonth.from(it.closed) == month },
                    "overdueCommitments" to (late[period] ?: 0)
                )
            }
        return IndicatorResult(null, series = series)
    }

    private const val CLOSED = "closed"
}
