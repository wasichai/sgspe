package sgspe.dashboard

import sgspe.engagement.AttendanceCounts
import sgspe.engagement.EngagementWriteGuard
import java.time.LocalDate
import java.time.YearMonth

data class EngagementRow(
    val id: String,
    val project: String?,
    val type: String?,
    val title: String?,
    val heldDate: LocalDate,
    val counts: Map<String, Long?>
)

data class QuestionRow(
    val engagement: String,
    val topic: String?,
    val answered: Boolean
)

// la meta de reuniones de un mes (dashboards.md KPI-ENG-02): la del plan vigente en el mes o, si no hay, la del proyecto
data class MonthlyTarget(
    val month: YearMonth,
    val target: Long
)

// los KPI-ENG de F2 (dashboards.md §4.3) como funciones puras. las filas llegan ya filtradas a las interacciones realizadas
// (EngagementWriteGuard.HELD_STATES) y a los permisos de quien llama
object EngagementKpis {
    // los tipos formales (RN-ENG-004), que dashboards.md §3.3 cuenta como reunión (#5): la misma lista de la guarda
    val MEETINGS: Set<String> = EngagementWriteGuard.FORMAL_TYPES

    // reunión informativa (#7, #8, #9): REUNION INFORMATIVA o TALLER
    val INFORMATIVE = setOf("REUNION INFORMATIVA", "TALLER")

    // los siete conteos de asistentes de AttendanceCounts por su nombre corto (total, female, male, direct_area,
    // indirect_area, regional, national): la lista es la de allí, no una copia
    val COUNT_FIELDS: Map<String, String> = AttendanceCounts.FIELDS.associateBy { it.removePrefix("attendees_").removeSuffix("_count") }

    private fun field(short: String): String = COUNT_FIELDS.getValue(short)

    // KPI-ENG-01: realizadas en el periodo, por tipo, y su serie mensual de 12 meses
    fun held(
        inPeriod: List<EngagementRow>,
        window: List<EngagementRow>,
        end: LocalDate
    ): IndicatorResult =
        IndicatorResult(
            inPeriod.size,
            breakdowns = listOf(Breakdown("engagement_type", Numbers.counts(inPeriod.map { it.type ?: "SIN TIPO" }))),
            series = months(end).map { month -> mapOf("period" to month.toString(), "value" to window.count { YearMonth.from(it.heldDate) == month }) }
        )

    // KPI-ENG-02: reuniones realizadas en el periodo frente a la meta; serie de 12 meses con la meta de cada mes
    fun meetings(
        inPeriod: List<EngagementRow>,
        window: List<EngagementRow>,
        targets: List<MonthlyTarget>,
        start: LocalDate,
        end: LocalDate
    ): IndicatorResult {
        val count = inPeriod.count { it.type in MEETINGS }
        val target =
            targets
                .filter { !it.month.atEndOfMonth().isBefore(start) && !it.month.atDay(1).isAfter(end) }
                .sumOf { it.target }
        return IndicatorResult(
            count,
            numerator = count,
            denominator = target,
            breakdowns = listOf(Breakdown("engagement_type", Numbers.counts(inPeriod.filter { it.type in MEETINGS }.map { it.type!! }))),
            series =
                months(end).map { month ->
                    mapOf(
                        "period" to month.toString(),
                        "value" to window.count { it.type in MEETINGS && YearMonth.from(it.heldDate) == month },
                        "target" to (targets.firstOrNull { it.month == month }?.target ?: 0L)
                    )
                }
        )
    }

    // KPI-ENG-03: por reunión informativa, sus asistentes, los porcentajes por sexo (desde los conteos, sin pasar por otro
    // porcentaje redondeado), los asistentes de cada ámbito como conteos y las organizaciones que representan; el valor es
    // el promedio del periodo
    fun attendance(
        meetings: List<EngagementRow>,
        organizations: Map<String, List<String>>
    ): IndicatorResult {
        val informative = meetings.filter { it.type in INFORMATIVE }

        fun count(
            m: EngagementRow,
            short: String
        ): Long = m.counts[field(short)] ?: 0L
        val rows =
            informative.sortedBy { it.heldDate }.map { m ->
                val total = count(m, "total")
                mapOf(
                    "key" to m.id,
                    "label" to m.title,
                    "heldDate" to m.heldDate.toString(),
                    "value" to total,
                    "femalePercent" to Numbers.percent(count(m, "female"), total),
                    "malePercent" to Numbers.percent(count(m, "male"), total),
                    "directArea" to count(m, "direct_area"),
                    "indirectArea" to count(m, "indirect_area"),
                    "regional" to count(m, "regional"),
                    "national" to count(m, "national"),
                    "organizations" to organizations[m.id].orEmpty().distinct().sorted()
                )
            }
        val totals = informative.map { count(it, "total") }
        return IndicatorResult(Numbers.average(totals), totals.sum(), informative.size, listOf(Breakdown("engagement", rows)))
    }

    // KPI-ENG-04: por tema, las reuniones informativas del periodo que lo trataron y sus preguntas con ese tema
    fun topics(
        meetings: List<EngagementRow>,
        topicsOf: Map<String, Set<String>>,
        questions: List<QuestionRow>,
        names: Map<String, String>
    ): IndicatorResult {
        val informative = meetings.filter { it.type in INFORMATIVE }.map { it.id }.toSet()
        val treated = informative.flatMap { id -> topicsOf[id].orEmpty().map { it to id } }.groupBy({ it.first }, { it.second })
        val asked = questions.filter { it.engagement in informative && it.topic != null }.groupingBy { it.topic!! }.eachCount()
        val ids = treated.keys + asked.keys
        val rows =
            ids
                .map { id ->
                    mapOf(
                        "key" to id,
                        "label" to (names[id] ?: "Tema"),
                        "value" to treated[id].orEmpty().toSet().size,
                        "meetings" to treated[id].orEmpty().toSet().size,
                        "questions" to (asked[id] ?: 0)
                    )
                }.sortedWith(
                    compareByDescending<Map<String, Any?>> { it["value"] as Int }.thenByDescending { it["questions"] as Int }.thenBy { it["label"].toString() }
                )
        return IndicatorResult(ids.size, breakdowns = listOf(Breakdown("topic", rows)))
    }

    // KPI-ENG-05: preguntas absueltas ÷ preguntas de las reuniones informativas del periodo, con su desglose por tema
    fun answered(
        meetings: List<EngagementRow>,
        questions: List<QuestionRow>,
        names: Map<String, String>
    ): IndicatorResult {
        val informative = meetings.filter { it.type in INFORMATIVE }.map { it.id }.toSet()
        val asked = questions.filter { it.engagement in informative }
        val numerator = asked.count { it.answered }
        val byTopic =
            asked.groupBy { it.topic }.map { (topic, list) ->
                mapOf(
                    "key" to (topic ?: "SIN TEMA"),
                    "label" to (topic?.let { names[it] } ?: "Sin tema"),
                    "value" to Numbers.percent(list.count { it.answered }, list.size),
                    "questions" to list.size
                )
            }
        return IndicatorResult(Numbers.percent(numerator, asked.size), numerator, asked.size, listOf(Breakdown("topic", byTopic)))
    }

    // los 12 meses que terminan en el de `end`
    fun months(end: LocalDate): List<YearMonth> = (11 downTo 0).map { YearMonth.from(end).minusMonths(it.toLong()) }

    // un plan de relacionamiento con meta (engagement_plan): su proyecto, su vigencia y su meta de reuniones al mes
    data class PlanTarget(
        val id: String,
        val project: String,
        val start: LocalDate?,
        val end: LocalDate?,
        val target: Long
    )

    // los meses que necesitan meta en KPI-ENG-02: la unión de los 12 de la serie y todos los del periodo, que puede pasar
    // de 12 meses (el denominador suma la meta de cada mes del periodo). en orden
    fun targetMonths(
        start: LocalDate,
        end: LocalDate
    ): List<YearMonth> {
        val first = minOf(YearMonth.from(start), months(end).first())
        val last = YearMonth.from(end)
        return generateSequence(first) { it.plusMonths(1) }.takeWhile { !it.isAfter(last) }.toList()
    }

    // la meta de cada mes, una entrada por mes ya sumada entre los proyectos (RN-ENG-003): por proyecto, la del plan
    // vigente en el mes (con dos o más, el de start_date más reciente, un plan sin inicio cuenta como el más antiguo, y a
    // igual inicio el de id mayor) o, sin plan, la del proyecto (projectTargets, con su valor por defecto ya aplicado). un
    // mes cuenta su meta entera aunque el periodo lo cubra solo en parte
    fun monthlyTargets(
        months: List<YearMonth>,
        projectTargets: Map<String, Long>,
        plans: List<PlanTarget>
    ): List<MonthlyTarget> {
        val newest = compareBy<PlanTarget>({ it.start ?: LocalDate.MIN }, { it.id })
        return months.map { month ->
            val target =
                projectTargets.entries.sumOf { (project, fallback) ->
                    plans
                        .filter { p ->
                            p.project == project &&
                                (p.start == null || !p.start.isAfter(month.atEndOfMonth())) &&
                                (p.end == null || !p.end.isBefore(month.atDay(1)))
                        }.maxWithOrNull(newest)
                        ?.target ?: fallback
                }
            MonthlyTarget(month, target)
        }
    }
}
