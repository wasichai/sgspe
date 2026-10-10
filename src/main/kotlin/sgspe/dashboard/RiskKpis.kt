package sgspe.dashboard

import sgspe.risk.RiskLevels
import sgspe.risk.RiskThresholds
import sgspe.risk.Risks
import java.time.DayOfWeek
import java.time.LocalDate

// un riesgo social como lo leen los KPI-RSK, ya acotado a los proyectos, al territorio y a los activos (todos salvo closed,
// dashboards.md §3.2). los valores vigentes y residuales son los de su última revisión
data class RiskRow(
    val id: String,
    val project: String?,
    val code: String?,
    val category: String?,
    val probability: Int?,
    val impact: Int?,
    val level: String?,
    val residualProbability: Int?,
    val residualImpact: Int?,
    val residualLevel: String?,
    val locality: String?,
    val community: String?,
    val areaOfInfluence: String?
)

// una revisión de un riesgo activo (risk_assessment, appendOnly): la historia de KPI-RSK-04
data class RiskPoint(
    val risk: String,
    val date: LocalDate,
    val residual: Boolean,
    val level: String?,
    val trend: String?,
    val order: Long
)

// una medida de mitigación de un riesgo activo (KPI-RSK-05)
data class MeasureRow(
    val id: String,
    val risk: String,
    val description: String?,
    val ownerEmail: String?,
    val planned: LocalDate?,
    val completed: Boolean
)

// los KPI-RSK (dashboards.md §4.6) como funciones puras. las filas llegan leídas como quien mira
object RiskKpis {
    const val NO_LEVEL = "SIN NIVEL"
    const val NO_DATA = "SIN DATO"
    const val UNASSESSED = "SIN EVALUAR"
    const val NO_OWNER = "SIN RESPONSABLE"
    const val WEEKS = 12L

    // KPI-RSK-01: riesgos activos de nivel ALTO o CRITICO, por categoría
    fun high(rows: List<RiskRow>): IndicatorResult {
        val high = rows.filter { it.level in Risks.ESCALATED }
        return IndicatorResult(high.size, breakdowns = listOf(Breakdown("category", Numbers.counts(high.map { it.category ?: NO_DATA }))))
    }

    // KPI-RSK-02: la matriz 5 × 5 de los riesgos activos por probabilidad e impacto de su última revisión y su vista residual; con
    // un solo proyecto (`thresholds`), cada celda lleva el nivel que da su puntaje. los riesgos sin revisión van aparte
    fun heatmap(
        rows: List<RiskRow>,
        thresholds: RiskThresholds?
    ): IndicatorResult {
        fun matrix(
            by: String,
            cell: (RiskRow) -> Pair<Int?, Int?>
        ): Breakdown {
            val counts =
                rows
                    .map(cell)
                    .filter { it.first != null && it.second != null }
                    .groupingBy { it }
                    .eachCount()
            val cells =
                (1..5).flatMap { p ->
                    (1..5).map { i ->
                        mapOf("key" to "$p-$i", "probability" to p, "impact" to i, "value" to (counts[p to i] ?: 0)) +
                            (thresholds?.let { mapOf("level" to it.level(p * i)) } ?: emptyMap())
                    }
                }
            return Breakdown(by, cells)
        }
        val assessed = rows.count { it.probability != null && it.impact != null }
        return IndicatorResult(
            assessed,
            breakdowns =
                listOf(
                    matrix("matrix") { it.probability to it.impact },
                    matrix("residual") { it.residualProbability to it.residualImpact },
                    Breakdown("assessment", listOf(mapOf("key" to UNASSESSED, "value" to rows.size - assessed)))
                )
        )
    }

    // KPI-RSK-03: riesgos activos por localidad, comunidad, área de influencia y proyecto, cada fila apilada por nivel
    fun distribution(
        rows: List<RiskRow>,
        names: Map<String, String>
    ): IndicatorResult {
        fun stacked(
            by: String,
            key: (RiskRow) -> String?
        ): Breakdown {
            val groups = rows.filter { key(it) != null }.groupBy { key(it)!! }
            val stack =
                groups
                    .map { (id, list) ->
                        mapOf("key" to id, "label" to (names[id] ?: id), "value" to list.size) +
                            (Risks.LEVELS + NO_LEVEL).associateWith { level -> list.count { (it.level ?: NO_LEVEL) == level } }
                    }.sortedWith(compareByDescending<Map<String, Any?>> { it["value"] as Int }.thenBy { it["label"].toString() })
            return Breakdown(by, stack)
        }
        return IndicatorResult(
            rows.size,
            breakdowns =
                listOf(
                    stacked("locality") { it.locality },
                    stacked("community") { it.community },
                    stacked("areaOfInfluence") { it.areaOfInfluence },
                    stacked("project") { it.project }
                )
        )
    }

    // las WEEKS semanas (de lunes a domingo) que terminan en la de `end`, por su lunes
    fun weeks(end: LocalDate): List<LocalDate> {
        val last = end.with(DayOfWeek.MONDAY)
        return (WEEKS - 1 downTo 0).map { last.minusWeeks(it) }
    }

    // KPI-RSK-04: por semana, la distribución por nivel de los riesgos hoy activos según su última revisión no residual hasta el
    // fin de esa semana, y las revisiones de la semana por tendencia. el valor son las revisiones de las doce semanas
    fun evolution(
        active: List<RiskRow>,
        points: List<RiskPoint>,
        end: LocalDate
    ): IndicatorResult {
        val ids = active.map { it.id }.toSet()
        val mine = points.filter { it.risk in ids }
        val weeks = weeks(end)
        val series =
            weeks.map { monday ->
                val sunday = monday.plusDays(6)
                val levels =
                    mine
                        .filter { !it.residual && !it.date.isAfter(sunday) }
                        .groupBy { it.risk }
                        .mapValues { (_, list) -> list.maxWith(compareBy({ it.date }, { it.order })).level }
                val inWeek = mine.filter { !it.date.isBefore(monday) && !it.date.isAfter(sunday) }
                mapOf<String, Any?>("period" to monday.toString()) +
                    Risks.LEVELS.associateWith { level -> levels.values.count { it == level } } +
                    listOf(RiskLevels.UP, RiskLevels.SAME, RiskLevels.DOWN).associateWith { trend -> inWeek.count { it.trend == trend } }
            }
        val total = mine.count { !it.date.isBefore(weeks.first()) && !it.date.isAfter(weeks.last().plusDays(6)) }
        return IndicatorResult(total, series = series)
    }

    // KPI-RSK-05: medidas completadas ÷ medidas de los riesgos activos; aparte, las atrasadas (sin completar y con planned_date
    // anterior a hoy), y el avance por riesgo y por responsable
    fun mitigation(
        measures: List<MeasureRow>,
        codes: Map<String, String?>,
        today: LocalDate
    ): IndicatorResult {
        val done = measures.count { it.completed }
        val late =
            measures.filter { !it.completed && it.planned != null && it.planned.isBefore(today) }.sortedBy { it.planned }.map {
                mapOf(
                    "key" to it.id,
                    "label" to (codes[it.risk] ?: it.risk),
                    "description" to it.description,
                    "owner" to it.ownerEmail,
                    "plannedDate" to it.planned.toString()
                )
            }

        fun progress(
            by: String,
            key: (MeasureRow) -> String,
            label: (String) -> String
        ) = Breakdown(
            by,
            measures.groupBy(key).map { (id, list) ->
                mapOf("key" to id, "label" to label(id), "value" to list.count { it.completed }, "total" to list.size)
            }
        )
        return IndicatorResult(
            Numbers.percent(done, measures.size),
            done,
            measures.size,
            listOf(
                Breakdown("overdue", late),
                progress("risk", { it.risk }) { codes[it] ?: it },
                progress("owner", { it.ownerEmail ?: NO_OWNER }) { it }
            )
        )
    }
}
