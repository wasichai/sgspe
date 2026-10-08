package sgspe.dashboard

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

// los datos de un grupo de interés que leen los indicadores, ya filtrados por proyecto, territorio y ámbito. stance es el
// último valor informado (ASM-087), el que mantiene stakeholder, no necesariamente el de la última evaluación
data class StakeholderRow(
    val id: String,
    val state: String?,
    val influenceGroup: String?,
    val scope: String?,
    val type: String?,
    val stance: String?,
    val interestIndex: BigDecimal?,
    val powerIndex: BigDecimal?,
    val person: String?,
    val organization: String?
) {
    val current: Boolean get() = state != "retired"
}

data class AssessmentRow(
    val stakeholder: String,
    val date: LocalDate,
    val stance: String?,
    val trust: Int?,
    val overridden: Boolean,
    val overrideReason: String?,
    val createdAt: Instant?,
    val influenceScore: BigDecimal? = null
)

// un contacto: una interacción realizada con un grupo de interés, por el vínculo M2M o por un participante que asistió
data class Contact(
    val stakeholder: String,
    val engagement: String,
    val date: LocalDate
)

// los KPI-STK de dashboards.md §4.2 como funciones puras, probadas sin base (RQ-DSH-031). los que leen la postura
// (KPI-STK-02, 05 y 06) reciben si quien llama puede leerla: si no, no están disponibles (FIELD_NOT_READABLE) y no calculan
// nada, ni siquiera con la postura de otros datos (dashboards.md §8)
object StakeholderKpis {
    const val NOT_ASSESSED = "SIN EVALUAR"
    val STANCES = listOf("A FAVOR", "NEUTRAL", "EN CONTRA PERO ABIERTO A ESCUCHAR", "EN CONTRA")
    val OPPOSITION = setOf("EN CONTRA", "EN CONTRA PERO ABIERTO A ESCUCHAR")

    private fun stanceUnavailable(field: String) = IndicatorResult.unavailable(UnavailableReason.FIELD_NOT_READABLE, listOf(field))

    // KPI-STK-01: vigentes por grupo de influencia (la forma que dibuja el portal), por grupo × ámbito y por tipo; sin
    // evaluación, «SIN EVALUAR». los nombres de desglose van en snake_case, como los fija la Fase 2.3
    fun byInfluenceGroup(stakeholders: List<StakeholderRow>): IndicatorResult {
        val current = stakeholders.filter { it.current }
        val groups = counts(current.map { it.influenceGroup ?: NOT_ASSESSED })
        val cross =
            current
                .groupBy { (it.influenceGroup ?: NOT_ASSESSED) to (it.scope ?: "SIN AMBITO") }
                .map { (key, rows) -> mapOf("key" to "${key.first}|${key.second}", "influenceGroup" to key.first, "scope" to key.second, "value" to rows.size) }
                .sortedBy { it["key"].toString() }
        return IndicatorResult(
            value = current.size,
            breakdowns =
                listOf(
                    Breakdown("influence_group", groups),
                    Breakdown("influence_group_by_scope", cross),
                    Breakdown("stakeholder_type", counts(current.map { it.type ?: "SIN TIPO" }))
                )
        )
    }

    // KPI-STK-02: vigentes por postura, en el orden del catálogo, más «SIN EVALUAR». sin permiso para leer stance no está
    // disponible
    fun byStance(
        stakeholders: List<StakeholderRow>,
        stanceReadable: Boolean
    ): IndicatorResult {
        if (!stanceReadable) return stanceUnavailable("stakeholder.stance")
        val current = stakeholders.filter { it.current }
        val order = STANCES + NOT_ASSESSED
        val rows = order.map { stance -> mapOf("key" to stance, "value" to current.count { (it.stance ?: NOT_ASSESSED) == stance }) }
        val byGroup =
            current
                .groupBy { (it.stance ?: NOT_ASSESSED) to (it.influenceGroup ?: NOT_ASSESSED) }
                .map { (key, list) ->
                    mapOf("key" to "${key.first}|${key.second}", "stance" to key.first, "influenceGroup" to key.second, "value" to list.size)
                }
        val byType =
            current
                .groupBy { (it.stance ?: NOT_ASSESSED) to (it.type ?: "SIN TIPO") }
                .map { (key, list) ->
                    mapOf("key" to "${key.first}|${key.second}", "stance" to key.first, "stakeholderType" to key.second, "value" to list.size)
                }
        return IndicatorResult(
            current.size,
            breakdowns = listOf(Breakdown("stance", rows), Breakdown("stance_by_influence_group", byGroup), Breakdown("stance_by_type", byType))
        )
    }

    // KPI-STK-03: una fila por grupo de interés vigente evaluado (key su id, value su influence_score de la última
    // evaluación, interest y power sus índices), con los umbrales del proyecto. numerator son los evaluados y denominator
    // los vigentes: «sin evaluar» es denominator − numerator. la postura de cada punto solo va si quien llama puede leerla
    fun matrix(
        stakeholders: List<StakeholderRow>,
        latest: Map<String, AssessmentRow>,
        labels: Map<String, String>,
        interestThreshold: BigDecimal,
        powerThreshold: BigDecimal,
        stanceReadable: Boolean
    ): IndicatorResult {
        val current = stakeholders.filter { it.current }
        val points =
            current
                .filter { it.interestIndex != null && it.powerIndex != null }
                .map { s ->
                    val last = latest[s.id]
                    val point =
                        mutableMapOf<String, Any?>(
                            "key" to s.id,
                            "label" to (labels[s.id] ?: "Grupo de interés"),
                            "value" to last?.influenceScore,
                            "interest" to s.interestIndex,
                            "power" to s.powerIndex,
                            "influenceGroup" to s.influenceGroup,
                            "overridden" to (last?.overridden ?: false),
                            "overrideReason" to last?.overrideReason
                        )
                    if (stanceReadable) point["stance"] = s.stance
                    point
                }
        val thresholds = listOf(mapOf("key" to "interest", "value" to interestThreshold), mapOf("key" to "power", "value" to powerThreshold))
        return IndicatorResult(
            points.size,
            points.size,
            current.size,
            breakdowns = listOf(Breakdown("stakeholder", points), Breakdown("thresholds", thresholds))
        )
    }

    // KPI-STK-04: grupos de interés distintos contactados en el periodo, por sexo, ámbito e institución
    fun contacted(
        contacts: List<Contact>,
        stakeholders: Map<String, StakeholderRow>,
        sexOf: Map<String, String?>,
        institutionsOf: Map<String, List<String>>
    ): IndicatorResult {
        val ids = contacts.map { it.stakeholder }.filter { it in stakeholders }.toSet()
        val sex = ids.map { id -> if (stakeholders.getValue(id).organization != null) "ORGANIZACION" else (sexOf[id] ?: "SIN DATO") }
        val scope = ids.map { stakeholders.getValue(it).scope ?: "SIN AMBITO" }
        val institutions = ids.flatMap { institutionsOf[it].orEmpty().ifEmpty { listOf("SIN INSTITUCION") } }
        return IndicatorResult(
            ids.size,
            breakdowns = listOf(Breakdown("sex", counts(sex)), Breakdown("scope", counts(scope)), Breakdown("institution", counts(institutions)))
        )
    }

    // la postura vigente antes de `date` (ASM-087): la de la última evaluación anterior que la informó
    fun stanceBefore(
        assessments: List<AssessmentRow>,
        date: LocalDate
    ): String? = assessments.filter { it.stance != null && it.date.isBefore(date) }.maxWithOrNull(ORDER)?.stance

    private fun inPeriod(
        assessments: List<AssessmentRow>,
        start: LocalDate,
        end: LocalDate
    ) = assessments.filter { !it.date.isBefore(start) && !it.date.isAfter(end) }

    // KPI-STK-05: en oposición al inicio del periodo (la vigente antes de periodStart o, sin evaluación, la primera del
    // periodo que informa postura) con al menos un contacto en el periodo ÷ en oposición al inicio del periodo
    fun oppositionContacted(
        stakeholders: List<StakeholderRow>,
        assessments: Map<String, List<AssessmentRow>>,
        contacts: List<Contact>,
        start: LocalDate,
        end: LocalDate,
        stanceReadable: Boolean
    ): IndicatorResult {
        if (!stanceReadable) return stanceUnavailable("stakeholder_assessment.stance")
        val contacted = contacts.map { it.stakeholder }.toSet()
        val opposition =
            stakeholders.filter { it.current }.map { it.id }.filter { id ->
                val own = assessments[id].orEmpty()
                val initial = stanceBefore(own, start) ?: inPeriod(own, start, end).filter { it.stance != null }.minWithOrNull(ORDER)?.stance
                initial in OPPOSITION
            }
        val numerator = opposition.count { it in contacted }
        return IndicatorResult(Numbers.percent(numerator, opposition.size), numerator, opposition.size)
    }

    // KPI-STK-06: en oposición en la línea base (la postura vigente al inicio del periodo, ASM-087) con al menos una
    // evaluación en el periodo, los que cambiaron: su última evaluación del periodo con postura informada tiene otra postura.
    // desglose por dirección (más o menos favorable, en el orden del catálogo) y cobertura (evaluados ÷ en oposición)
    fun oppositionChanged(
        stakeholders: List<StakeholderRow>,
        assessments: Map<String, List<AssessmentRow>>,
        start: LocalDate,
        end: LocalDate,
        stanceReadable: Boolean
    ): IndicatorResult {
        if (!stanceReadable) return stanceUnavailable("stakeholder_assessment.stance")
        var base = 0
        var evaluated = 0
        var changed = 0
        var better = 0
        var worse = 0
        for (s in stakeholders.filter { it.current }) {
            val baseline = stanceBefore(assessments[s.id].orEmpty(), start) ?: continue
            if (baseline !in OPPOSITION) continue
            base++
            val period = inPeriod(assessments[s.id].orEmpty(), start, end)
            if (period.isEmpty()) continue
            evaluated++
            val stance = period.filter { it.stance != null }.maxWithOrNull(ORDER)?.stance ?: continue
            if (stance == baseline) continue
            changed++
            if (STANCES.indexOf(stance) < STANCES.indexOf(baseline)) better++ else worse++
        }
        return IndicatorResult(
            Numbers.percent(changed, evaluated),
            changed,
            evaluated,
            breakdowns =
                listOf(
                    Breakdown("direction", listOf(mapOf("key" to "MORE_FAVORABLE", "value" to better), mapOf("key" to "LESS_FAVORABLE", "value" to worse))),
                    Breakdown("coverage", listOf(mapOf("key" to "evaluated", "value" to evaluated), mapOf("key" to "opposition", "value" to base)))
                )
        )
    }

    // KPI-STK-07: contactados en al menos dos interacciones distintas ÷ contactados
    fun repeatedContact(contacts: List<Contact>): IndicatorResult {
        val engagementsBy = contacts.groupBy { it.stakeholder }.mapValues { (_, list) -> list.map { it.engagement }.toSet().size }
        val numerator = engagementsBy.count { it.value >= 2 }
        return IndicatorResult(Numbers.percent(numerator, engagementsBy.size), numerator, engagementsBy.size)
    }

    // KPI-STK-08: para cada fin de mes de los 12 que terminan en `end`, el promedio del último trust_score informado hasta
    // esa fecha (ASM-087) de cada grupo de interés vigente, con su distribución de 1 a 5 y el número de evaluados
    fun trust(
        stakeholders: List<StakeholderRow>,
        assessments: Map<String, List<AssessmentRow>>,
        end: LocalDate
    ): IndicatorResult {
        val current = stakeholders.filter { it.current }.map { it.id }
        val last = YearMonth.from(end)
        val series =
            (11 downTo 0).map { back ->
                val month = last.minusMonths(back.toLong())
                val until = if (back == 0) end else month.atEndOfMonth()
                val scores =
                    current.mapNotNull { id ->
                        assessments[id]
                            .orEmpty()
                            .filter { it.trust != null && !it.date.isAfter(until) }
                            .maxWithOrNull(ORDER)
                            ?.trust
                    }
                mapOf(
                    "period" to month.toString(),
                    "value" to Numbers.average(scores),
                    "evaluated" to scores.size,
                    "distribution" to (1..5).associate { it.toString() to scores.count { score -> score == it } }
                )
            }
        return IndicatorResult(series.last()["value"] as BigDecimal?, series = series)
    }

    // la última evaluación es la de fecha más reciente y, a igual fecha, la creada después
    val ORDER: Comparator<AssessmentRow> = compareBy<AssessmentRow>({ it.date }, { it.createdAt })

    fun counts(keys: List<String>): List<Map<String, Any?>> =
        keys
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { mapOf("key" to it.key, "value" to it.value) }
}
