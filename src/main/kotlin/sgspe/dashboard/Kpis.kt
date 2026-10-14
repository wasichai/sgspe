package sgspe.dashboard

import sgspe.common.Attention
import sgspe.common.ProjectParameters
import sgspe.common.Values
import sgspe.engagement.AttendanceCounts
import sgspe.investment.Contributions
import sgspe.investment.Programs
import sgspe.risk.RiskLevels
import sgspe.territory.GateOutcome

// un KPI de dashboards.md §4. requires: "objeto.campo" que lee; si alguno no es legible para quien llama, el indicador
// sale no disponible. dashboard: el tablero que lo muestra en su fase; null si solo se consulta por su detalle (§11)
interface Indicator {
    val code: String
    val label: String
    val dashboard: String?
    val unit: String
    val requires: List<String>
    val singleProject: Boolean get() = false

    // los filtros comunes que el indicador acepta y no aplica: si vienen, la respuesta los nombra en ignoredFilters
    val ignores: Set<String> get() = emptySet()

    suspend fun compute(data: DashboardData): IndicatorResult
}

// un KPI con su cálculo: los de F2 de dashboards.md §4.2 y §4.3 (RQ-DSH-036). cada fase agrega los suyos a ALL
class Kpi(
    override val code: String,
    override val label: String,
    override val dashboard: String?,
    override val unit: String,
    override val requires: List<String>,
    override val singleProject: Boolean = false,
    // sin filtros ignorados por defecto: cada KPI declara los suyos (los de F2, abajo); un KPI de compromisos o de inversión de
    // F3 o F5 sí aplica explorationCampaign y no debe heredar que lo ignora
    override val ignores: Set<String> = emptySet(),
    private val block: suspend (DashboardData) -> IndicatorResult
) : Indicator {
    override suspend fun compute(data: DashboardData): IndicatorResult = block(data)
}

// los KPI de F2 con lo que leen. las funciones de StakeholderKpis y EngagementKpis reciben filas ya leídas como quien
// mira, ya acotadas a los proyectos, al territorio y a las interacciones realizadas (DashboardData); la postura solo se
// calcula si quien mira puede leerla (stanceReadable, dashboards.md §8)
object Kpis {
    const val STAKEHOLDERS = "stakeholders"
    const val ENGAGEMENT = "engagement"
    const val COMMITMENTS = "commitments"
    const val ATTENTION = "attention"
    const val RISKS = "risks"
    const val INVESTMENT = "investment"
    const val TERRITORY = "territory"

    // F8: los tableros consolidados (dashboards.md §5.1, §5.2 y §5.5)
    const val EXECUTIVE = "executive"
    const val PROJECT = "project"
    const val MONITORING = "monitoring"

    // R1 (decisión 3 del plan 8.2a): KPI-ENG-02 y el campo del catálogo con su meta por defecto en un tenant de F8
    const val MEETINGS = "KPI-ENG-02"
    const val MEETINGS_TARGET = "indicator.target_value"

    // los indicadores que leen QCS y que, a quien no ve las confidenciales, se lo dicen con confidentialHidden (dashboards.md §8,
    // ASM-095): los KPI-GRV y KPI-ENG-05, que cuenta como atendida una pregunta derivada a una QCS
    fun confidentialSensitive(code: String): Boolean =
        code.startsWith("KPI-GRV-") || code == "KPI-ENG-05" || code == TerritorySummary.CODE || code == ExecutiveKpis.TREND

    // R4 a prueba de fallos (decisión 25): lo que el cierre exige además legible en un KPI que lee las QCS, la marca
    // confidential de los tres objetos; quien no la lee contaría las confidenciales como no confidenciales, así que el KPI
    // sale no disponible y el cierre lo rechaza (UNAVAILABLE). KPI-ENG-05 no lee QCS: cuenta por referred_at de la pregunta,
    // igual para todo lector (dashboards.md §4.3)
    fun closingRequires(code: String): List<String> = if (confidentialSensitive(code) && code != "KPI-ENG-05") qcs(Attention.CONFIDENTIAL) else emptyList()

    // los KPI de F2 no aplican explorationCampaign (dashboards.md §3.1: solo compromisos e inversión social) ni owner, el
    // responsable de un compromiso que llega en F3
    val IGNORED_IN_F2: Set<String> = setOf("explorationCampaign", "owner")

    // los filtros que un KPI-COM no aplica, según su fila de dashboards.md §4.4: todos aplican el territorio y el ámbito
    // (traducidos a localidades y comunidades, §3.1); la campaña y el responsable, solo los que los nombran
    private fun commitmentIgnores(vararg accepts: String): Set<String> = setOf("explorationCampaign", "owner") - accepts.toSet()

    // los KPI-STK ignoran además community: stakeholder no tiene comunidad (ruling P27)
    private val STAKEHOLDER_IGNORES: Set<String> = IGNORED_IN_F2 + "community"

    // los filtros pedidos que el indicador ignora, en el orden de DashboardFilters.COMMON y después INVESTMENT; vacío si ninguno.
    // el periodo cuenta como pedido solo si vino periodStart o periodEnd (periodGiven): el mes en curso por defecto no se declara
    fun ignoredFilters(
        indicator: Indicator,
        filters: DashboardFilters
    ): List<String> {
        val given = if (filters.periodGiven) filters.given() + PERIOD else filters.given()
        return (DashboardFilters.COMMON + DashboardFilters.INVESTMENT).filter { it in indicator.ignores && it in given }
    }

    private val PERIOD: Set<String> = setOf("periodStart", "periodEnd")

    // los filtros que, además de los comunes, aceptan los indicadores `codes`: program y year si alguno es un KPI-INV (decisión 2
    // del plan 5.2b); ninguno en los demás, así que en otro tablero o KPI son 422
    fun extraFilters(codes: Collection<String>): Set<String> = if (codes.any { it.startsWith(INVESTMENT_PREFIX) }) DashboardFilters.INVESTMENT else emptySet()

    private const val INVESTMENT_PREFIX = "KPI-INV-"

    // los tableros de F2 y sus zonas, en el orden de dashboards.md §5.3 y §5.4
    val DASHBOARDS: Map<String, List<String>> =
        mapOf(
            STAKEHOLDERS to listOf("KPI-STK-01", "KPI-STK-02", "KPI-STK-03", "KPI-STK-06", "KPI-STK-08"),
            ENGAGEMENT to listOf("KPI-ENG-01", "KPI-ENG-02", "KPI-ENG-03", "KPI-ENG-04", "KPI-ENG-05"),
            // dashboards.md §5.6: estado, desgloses, cumplimiento, montos, formales por tema e historia
            COMMITMENTS to
                listOf(
                    "KPI-COM-01",
                    "KPI-COM-02",
                    "KPI-COM-03",
                    "KPI-COM-04",
                    "KPI-COM-05",
                    "KPI-COM-06",
                    "KPI-COM-07",
                    "KPI-COM-09",
                    "KPI-COM-10",
                    "KPI-COM-08",
                    "KPI-COM-11"
                ),
            // dashboards.md §5.7: flujo, absueltas, desgloses, tendencia y tiempos
            ATTENTION to (1..10).map { "KPI-GRV-%02d".format(it) },
            // dashboards.md §5.8: mapa de calor, riesgos altos, distribución, evolución y mitigación
            RISKS to listOf("KPI-RSK-02", "KPI-RSK-01", "KPI-RSK-03", "KPI-RSK-04", "KPI-RSK-05"),
            // dashboards.md §5.10: campañas, programas, consolidado y beneficiarios
            INVESTMENT to listOf("KPI-INV-01", "KPI-INV-02", "KPI-INV-03", "KPI-INV-04"),
            // dashboards.md §5.9: las estadísticas del tablero territorial; KPI-TER-01 lo agrega Dashboards con locality o
            // community (es la ficha)
            TERRITORY to listOf("KPI-TER-02", "KPI-TER-03", "KPI-TER-04"),
            // F8, dashboards.md §5.1: las tiles, la inversión por moneda y la tendencia
            EXECUTIVE to ExecutiveKpis.TILES,
            // F8, dashboards.md §5.2: paisaje de grupos de interés, relacionamiento, compromisos, QCS, riesgos y programas
            PROJECT to
                listOf(
                    "KPI-STK-01",
                    "KPI-STK-02",
                    "KPI-STK-03",
                    "KPI-ENG-01",
                    "KPI-ENG-02",
                    "KPI-COM-02",
                    "KPI-COM-03",
                    "KPI-COM-04",
                    "KPI-COM-06",
                    "KPI-GRV-01",
                    "KPI-GRV-02",
                    "KPI-GRV-06",
                    "KPI-RSK-01",
                    "KPI-RSK-02",
                    "KPI-INV-02",
                    "KPI-INV-03",
                    "KPI-INV-04"
                ),
            // F8, dashboards.md §5.5: las 13 filas de la Tabla 9.5 en su orden (§4.9)
            MONITORING to MonitoringTable.CODES
        )

    // F6: KPI-TER-02 a 04 se filtran solo por proyecto (dashboards.md §4.8); los demás filtros comunes, el periodo incluido
    // (H5 del plan 6.2c), los declaran ignorados
    private val TERRITORY_IGNORES: Set<String> =
        setOf("periodStart", "periodEnd", "administrativeUnit", "locality", "community", "areaOfInfluence", "explorationCampaign", "scope", "owner")
    private val AREA = listOf("area_of_influence.project", "area_of_influence.area_type", "area_of_influence.version_number")
    private val LAND =
        listOf(
            "project_component.project",
            "project_component.component_type",
            "project_component.superseded",
            "project_component.location",
            "project_component.trace",
            "project_component.footprint",
            "community.territory",
            "land_parcel.tenure_type",
            "land_parcel.boundary"
        )

    // F5: los KPI-INV aplican de los filtros de territorio solo locality (decisión 5 del plan 5.2b); ninguno aplica el ámbito
    // ni el responsable de un compromiso. cada uno suma lo que su fila de dashboards.md §4.7 no aplica
    private val INV_IGNORES: Set<String> = setOf("administrativeUnit", "community", "areaOfInfluence", "scope", "owner")

    // lo que leen todos los KPI-INV de los programas
    private val PROGRAM =
        listOf("${Programs.PROGRAM}.project", "${Programs.PROGRAM}.code", "${Programs.PROGRAM}.name", "${Programs.PROGRAM}.currency")
    private val BUDGET =
        listOf(
            "${Programs.BUDGET}.social_program",
            "${Programs.BUDGET}.budget_year",
            "${Programs.BUDGET}.allocated_amount",
            "${Programs.BUDGET}.executed_amount",
            "${Programs.BUDGET}.currency"
        )

    // los KPI-RSK aplican el territorio y el ámbito por la localidad o la comunidad del riesgo; la campaña y el responsable no
    private val RISK_IGNORES: Set<String> = setOf("explorationCampaign", "owner")

    // lo que leen todos los KPI-RSK: el riesgo, su estado y su territorio
    private val RISK = listOf("social_risk.project", "social_risk.locality", "social_risk.community")

    // los KPI-GRV no aplican la campaña ni el responsable de un compromiso (dashboards.md §3.1); el territorio y el ámbito sí,
    // por la localidad o la comunidad de la QCS
    private val ATTENTION_IGNORES: Set<String> = setOf("explorationCampaign", "owner")

    // los campos de los tres objetos de QCS
    private fun qcs(vararg fields: String): List<String> = Attention.OBJECTS.flatMap { o -> fields.map { "$o.$it" } }

    // lo que leen todos los KPI-GRV: el proyecto, la recepción, el territorio y los vínculos de reclasificación (§3.3)
    private val QCS: List<String> =
        qcs("project", "received_at", "locality", "community") + Attention.RECLASSIFIED_FROM.flatMap { (o, links) -> links.keys.map { "$o.$it" } }

    // lo que leen todos los KPI-COM: el compromiso, su estado y su territorio
    private val COMMITMENT = listOf("commitment.project", "commitment.locality", "commitment.community")

    // el filtro explorationCampaign se aplica sobre este campo, así que los KPI que lo aceptan lo exigen legible
    private const val CAMPAIGN = "commitment.exploration_campaign"
    private val PLAZO = COMMITMENT + listOf("commitment.deadline_status", "commitment.due_date")

    // el contacto de dashboards.md §3.3: la interacción realizada, su vínculo M2M y el participante que asistió
    private val CONTACT =
        listOf(
            "engagement.held_at",
            "engagement_participant.attended",
            "engagement_participant.stakeholder",
            "engagement_participant.engagement",
            "stakeholder.stakeholder_scope"
        )
    private val HELD = listOf("engagement.held_at", "engagement.engagement_type")

    // F4: lo que leen KPI-ENG-07 y 08, y los filtros que no aplican: las menciones no tienen territorio ni ámbito (dashboards.md
    // §4.3: «Proyecto, periodo»)
    private val MEDIA =
        listOf("media_mention.project", "media_mention.published_date", "media_mention.outlet", "media_mention.outlet_name", "media_mention.tone")
    private val MEDIA_IGNORES: Set<String> = IGNORED_IN_F2 + setOf("administrativeUnit", "locality", "community", "areaOfInfluence", "scope")

    // F8: KPI-EXE-01 se filtra solo por proyecto; KPI-EXE-02, por proyecto y territorio (sin ámbito); los dos son conteos de
    // hoy, así que un periodo pedido se declara ignorado. KPI-EXE-03 aplica proyecto, territorio y ámbito (dashboards.md §4.1)
    private val EXECUTIVE_WARNING_IGNORES: Set<String> = setOf("periodStart", "periodEnd", "explorationCampaign", "scope", "owner")

    val ALL: List<Indicator> =
        listOf(
            Kpi(
                "KPI-STK-01",
                "Grupos de interés por grupo de influencia y ámbito",
                STAKEHOLDERS,
                "grupos de interés",
                listOf("stakeholder.influence_group", "stakeholder.stakeholder_scope", "stakeholder.stakeholder_type"),
                ignores = STAKEHOLDER_IGNORES
            ) { StakeholderKpis.byInfluenceGroup(it.stakeholders()) },
            Kpi(
                "KPI-STK-02",
                "Distribución de posturas",
                STAKEHOLDERS,
                "grupos de interés",
                listOf("stakeholder.stance", "stakeholder.influence_group", "stakeholder.stakeholder_type"),
                ignores = STAKEHOLDER_IGNORES
            ) { StakeholderKpis.byStance(it.stakeholders(), stanceReadable = it.readable("stakeholder.stance")) },
            Kpi(
                "KPI-STK-03",
                "Matriz interés × poder",
                STAKEHOLDERS,
                "grupos de interés",
                listOf(
                    "stakeholder.interest_index",
                    "stakeholder.power_index",
                    "stakeholder.influence_group",
                    "stakeholder_assessment.influence_score",
                    "stakeholder_assessment.group_overridden",
                    "stakeholder_assessment.override_reason",
                    "project.interest_threshold",
                    "project.power_threshold"
                ),
                singleProject = true,
                ignores = STAKEHOLDER_IGNORES
            ) { data ->
                val project =
                    data
                        .projectRecords()
                        .singleOrNull()
                        ?.attributes
                        .orEmpty()
                val stakeholders = data.stakeholders()
                StakeholderKpis.matrix(
                    stakeholders,
                    data.assessments().mapValues { (_, list) -> list.maxWith(StakeholderKpis.ORDER) },
                    Labels.of(data, stakeholders.filter { it.current }),
                    ProjectParameters.decimal(project, "interest_threshold"),
                    ProjectParameters.decimal(project, "power_threshold"),
                    stanceReadable = data.readable("stakeholder.stance")
                )
            },
            Kpi(
                "KPI-STK-04",
                "Grupos de interés contactados",
                MONITORING,
                "grupos de interés",
                CONTACT + listOf("person.sex", "position.organization", "position.position_status"),
                ignores = STAKEHOLDER_IGNORES
            ) { data ->
                val current = data.currentStakeholders()
                StakeholderKpis.contacted(data.contacts(), current.associateBy { it.id }, Labels.sexOf(data, current), Labels.institutionsOf(data, current))
            },
            Kpi(
                "KPI-STK-05",
                "Oposición que aceptó conversar",
                MONITORING,
                "%",
                CONTACT + listOf("stakeholder_assessment.stance", "stakeholder_assessment.assessment_date"),
                ignores = STAKEHOLDER_IGNORES
            ) {
                StakeholderKpis.oppositionContacted(
                    it.stakeholders(),
                    it.assessments(),
                    it.contacts(),
                    it.filters.periodStart,
                    it.filters.periodEnd,
                    stanceReadable = it.readable("stakeholder_assessment.stance")
                )
            },
            Kpi(
                "KPI-STK-06",
                "Oposición que cambió de postura",
                STAKEHOLDERS,
                "%",
                listOf("stakeholder_assessment.stance", "stakeholder_assessment.assessment_date"),
                ignores = STAKEHOLDER_IGNORES
            ) {
                StakeholderKpis.oppositionChanged(
                    it.stakeholders(),
                    it.assessments(),
                    it.filters.periodStart,
                    it.filters.periodEnd,
                    stanceReadable = it.readable("stakeholder_assessment.stance")
                )
            },
            // el mismo conjunto de contactos que KPI-STK-04 (DashboardData.contacts): su denominador son los contactados
            Kpi("KPI-STK-07", "Grupos de interés con más de un contacto", MONITORING, "%", CONTACT, ignores = STAKEHOLDER_IGNORES) {
                StakeholderKpis.repeatedContact(it.contacts())
            },
            Kpi(
                "KPI-STK-08",
                "Confianza promedio y tendencia",
                STAKEHOLDERS,
                "confianza de 1 a 5",
                listOf("stakeholder_assessment.trust_score", "stakeholder_assessment.assessment_date"),
                ignores = STAKEHOLDER_IGNORES
            ) { StakeholderKpis.trust(it.stakeholders(), it.assessments(), it.filters.periodEnd) },
            Kpi("KPI-ENG-01", "Interacciones realizadas", ENGAGEMENT, "interacciones", HELD, ignores = IGNORED_IN_F2) {
                EngagementKpis.held(it.inPeriod(), it.window(), it.filters.periodEnd)
            },
            Kpi(
                "KPI-ENG-02",
                "Reuniones sostenidas al mes",
                ENGAGEMENT,
                "reuniones",
                HELD + listOf("engagement_plan.meetings_per_month_target", "project.meetings_per_month_target"),
                ignores = IGNORED_IN_F2
            ) { data ->
                // R1: la meta por defecto vive en un catálogo que quien mira no lee: no disponible, nunca con otra meta
                if (data.meetingsDefaultUnreadable()) {
                    val missing = listOf(MEETINGS_TARGET)
                    IndicatorResult.unavailable(KpiEvaluation.reason(missing, data), missing)
                } else {
                    EngagementKpis.meetings(data.inPeriod(), data.window(), data.monthlyTargets(), data.filters.periodStart, data.filters.periodEnd)
                }
            },
            Kpi(
                "KPI-ENG-03",
                "Asistentes por reunión informativa",
                ENGAGEMENT,
                "asistentes",
                HELD + AttendanceCounts.FIELDS.map { "engagement.$it" } +
                    listOf("engagement_participant.organization", "engagement_participant.represented_group", "engagement_participant.attended"),
                ignores = IGNORED_IN_F2
            ) { data ->
                val meetings = data.inPeriod().filter { it.type in EngagementKpis.INFORMATIVE }
                EngagementKpis.attendance(meetings, Labels.organizationsOf(data, meetings.map { it.id }))
            },
            Kpi(
                "KPI-ENG-04",
                "Temas consultados",
                ENGAGEMENT,
                "temas",
                HELD + listOf("topic.name", "engagement_question.topic", "engagement_question.engagement"),
                ignores = IGNORED_IN_F2
            ) { data ->
                val meetings = data.inPeriod().filter { it.type in EngagementKpis.INFORMATIVE }
                val ids = meetings.map { it.id }
                val topics = data.topicsOf(ids)
                val questions = data.questions(ids)
                EngagementKpis.topics(meetings, topics, questions, data.names("topic", topics.values.flatten() + questions.mapNotNull { it.topic }))
            },
            Kpi(
                "KPI-ENG-05",
                "Inquietudes resueltas",
                ENGAGEMENT,
                "%",
                HELD + listOf("engagement_question.answered_on_site", "engagement_question.answered_at", "engagement_question.engagement"),
                ignores = IGNORED_IN_F2
            ) { data ->
                val meetings = data.inPeriod().filter { it.type in EngagementKpis.INFORMATIVE }
                val questions = data.questions(meetings.map { it.id })
                EngagementKpis.answered(meetings, questions, data.names("topic", questions.mapNotNull { it.topic }))
            },
            // ---- F4: relacionamiento (dashboards.md §4.3, #11 a #13 de la Tabla 9.5): en el tablero de monitoreo desde F8 (§11) ----
            Kpi(
                "KPI-ENG-06",
                "Monitoreos participativos de agua",
                MONITORING,
                "monitoreos",
                HELD + listOf("engagement.title", "stakeholder.project"),
                ignores = IGNORED_IN_F2
            ) { data ->
                val monitoring = data.inPeriod().filter { it.type == EngagementKpis.MONITORING }
                EngagementKpis.monitoring(monitoring, data.stakeholdersOf(monitoring.map { it.id }))
            },
            Kpi(
                "KPI-ENG-07",
                "Comunicados sobre compromisos cumplidos",
                MONITORING,
                "menciones",
                MEDIA + listOf("media_mention.reports_fulfilled_commitment", "organization.name"),
                ignores = MEDIA_IGNORES
            ) { data ->
                val mentions = data.mentions()
                EngagementKpis.fulfilledCommunications(mentions, data.names("organization", mentions.mapNotNull { it.outlet }))
            },
            Kpi(
                "KPI-ENG-08",
                "Medios con información positiva y negativa",
                MONITORING,
                "%",
                MEDIA + listOf("media_mention.topic", "organization.name", "topic.name"),
                ignores = MEDIA_IGNORES
            ) { data ->
                val mentions = data.mentions()
                EngagementKpis.tones(
                    mentions,
                    data.names("organization", mentions.mapNotNull { it.outlet }),
                    data.names("topic", mentions.mapNotNull { it.topic })
                )
            },
            // ---- F3: compromisos (dashboards.md §4.4); cada uno aplica los filtros de su fila y declara los demás ----
            Kpi(
                "KPI-COM-01",
                "Compromisos registrados",
                COMMITMENTS,
                "compromisos",
                COMMITMENT + CAMPAIGN,
                ignores = commitmentIgnores("explorationCampaign")
            ) {
                CommitmentKpis.registered(it.commitments(campaign = true, owner = false))
            },
            Kpi("KPI-COM-02", "Compromisos abiertos", COMMITMENTS, "compromisos", COMMITMENT + CAMPAIGN, ignores = commitmentIgnores("explorationCampaign")) {
                CommitmentKpis.open(it.commitments(campaign = true, owner = false))
            },
            Kpi(
                "KPI-COM-03",
                "Compromisos vencidos",
                COMMITMENTS,
                "compromisos",
                PLAZO + listOf(CAMPAIGN, "commitment.owner_user_id", "project.escalation_first_days", "project.escalation_second_days"),
                ignores = commitmentIgnores("explorationCampaign", "owner")
            ) { data ->
                val escalation = data.escalation()
                CommitmentKpis.overdue(data.commitments(campaign = true, owner = true), data.today) { escalation[it] ?: Escalation(7, 15) }
            },
            Kpi(
                "KPI-COM-04",
                "Compromisos por vencer",
                COMMITMENTS,
                "compromisos",
                PLAZO + listOf(CAMPAIGN, "commitment.owner_user_id"),
                ignores = commitmentIgnores("explorationCampaign", "owner")
            ) { CommitmentKpis.dueSoon(it.commitments(campaign = true, owner = true)) },
            Kpi(
                "KPI-COM-05",
                "Compromisos cumplidos en el periodo",
                COMMITMENTS,
                "compromisos",
                COMMITMENT + listOf(CAMPAIGN, "commitment.fulfilled_date"),
                ignores = commitmentIgnores("explorationCampaign")
            ) { CommitmentKpis.fulfilled(it.commitments(campaign = true, owner = false), it.filters.periodStart, it.filters.periodEnd) },
            Kpi(
                "KPI-COM-06",
                "Compromisos bloqueados",
                COMMITMENTS,
                "compromisos",
                COMMITMENT + "commitment.owner_user_id",
                ignores = commitmentIgnores("owner")
            ) {
                CommitmentKpis.blocked(it.commitments(campaign = false, owner = true))
            },
            Kpi(
                "KPI-COM-07",
                "Desgloses de compromisos",
                COMMITMENTS,
                "compromisos",
                PLAZO + listOf("commitment.owner_user_email", "commitment.topic", "project.code", "locality.name", "community.name", "topic.name"),
                ignores = commitmentIgnores()
            ) { data ->
                val rows = data.commitments(campaign = false, owner = false)
                CommitmentKpis.breakdowns(
                    rows,
                    data.names("project", rows.mapNotNull { it.project }, "code"),
                    data.names("locality", rows.mapNotNull { it.locality }) + data.names("community", rows.mapNotNull { it.community }),
                    data.names("topic", rows.mapNotNull { it.topic })
                )
            },
            Kpi(
                "KPI-COM-08",
                "Compromisos formales por tema",
                COMMITMENTS,
                "compromisos",
                COMMITMENT + listOf("commitment.formality", "commitment.commitment_theme", "commitment.agreed_date"),
                ignores = commitmentIgnores()
            ) { CommitmentKpis.formalByTheme(it.commitments(campaign = false, owner = false), it.filters.periodStart, it.filters.periodEnd) },
            Kpi(
                "KPI-COM-09",
                "Compromisos cumplidos a tiempo",
                COMMITMENTS,
                "%",
                COMMITMENT +
                    listOf(
                        "commitment.fulfilled_date",
                        "commitment.due_date",
                        "commitment.owner_user_id",
                        "commitment_reschedule.previous_due_date",
                        "commitment_reschedule.new_due_date",
                        "commitment_reschedule.commitment"
                    ),
                ignores = commitmentIgnores("owner")
            ) { data ->
                val rows = data.commitments(campaign = false, owner = true)
                val reschedules = data.reschedules(rows.map { it.id })
                reschedules.warn(CommitmentKpis.onTime(rows, reschedules.rows, data.filters.periodStart, data.filters.periodEnd))
            },
            Kpi(
                "KPI-COM-10",
                "Monto comprometido frente a ejecutado",
                COMMITMENTS,
                "monto",
                COMMITMENT + listOf(CAMPAIGN, "commitment.amount", "commitment.currency", "commitment.executed_amount", "exploration_campaign.currency"),
                ignores = commitmentIgnores("explorationCampaign")
            ) { data ->
                // con una campaña pedida que no existe o no se lee no hay moneda base: no disponible, no PEN supuesto
                val currency = data.campaignCurrency()
                if (data.filters.explorationCampaign != null && currency == null) {
                    IndicatorResult.unavailable(UnavailableReason.OBJECT_NOT_READABLE, listOf("exploration_campaign"))
                } else {
                    CommitmentKpis.amounts(data.commitments(campaign = true, owner = false), currency)
                }
            },
            Kpi(
                "KPI-COM-11",
                "Compromisos vencidos al cierre de cada mes",
                COMMITMENTS,
                "compromisos",
                COMMITMENT +
                    listOf(
                        "commitment.agreed_date",
                        "commitment.due_date",
                        "commitment.fulfilled_date",
                        "commitment.cancelled_date",
                        "commitment_reschedule.previous_due_date",
                        "commitment_reschedule.new_due_date",
                        "commitment_reschedule.commitment"
                    ),
                ignores = commitmentIgnores()
            ) { data ->
                val rows = data.commitments(campaign = false, owner = false)
                val reschedules = data.reschedules(rows.map { it.id })
                reschedules.warn(CommitmentKpis.overdueSeries(rows, reschedules.rows, data.filters.periodEnd, data.today))
            },
            // ---- F4: quejas, consultas y solicitudes (dashboards.md §4.5); leen como quien mira (ASM-095) ----
            Kpi("KPI-GRV-01", "QCS recibidas", ATTENTION, "QCS", QCS + qcs("channel"), ignores = ATTENTION_IGNORES) {
                AttentionKpis.received(it.attention(), it.filters.periodStart, it.filters.periodEnd)
            },
            Kpi("KPI-GRV-02", "QCS abiertas", ATTENTION, "QCS", QCS, ignores = ATTENTION_IGNORES) { AttentionKpis.open(it.attention()) },
            Kpi(
                "KPI-GRV-03",
                "Quejas resueltas",
                ATTENTION,
                "quejas",
                QCS + listOf("grievance.resolved_date", "grievance.severity"),
                ignores = ATTENTION_IGNORES
            ) {
                AttentionKpis.resolved(it.attention(), it.filters.periodStart, it.filters.periodEnd)
            },
            Kpi("KPI-GRV-04", "QCS cerradas", ATTENTION, "QCS", QCS + qcs("closed_date"), ignores = ATTENTION_IGNORES) {
                AttentionKpis.closed(it.attention(), it.filters.periodStart, it.filters.periodEnd)
            },
            Kpi("KPI-GRV-05", "Quejas rechazadas", ATTENTION, "quejas", QCS + "grievance.closed_date", ignores = ATTENTION_IGNORES) {
                AttentionKpis.rejected(it.attention(), it.filters.periodStart, it.filters.periodEnd)
            },
            Kpi("KPI-GRV-06", "QCS fuera de plazo", ATTENTION, "QCS", QCS + qcs("deadline_status"), ignores = ATTENTION_IGNORES) {
                AttentionKpis.overdue(it.attention())
            },
            Kpi("KPI-GRV-07", "QCS absueltas", ATTENTION, "%", QCS + qcs("responded_at"), ignores = ATTENTION_IGNORES) {
                AttentionKpis.absolved(it.attention(), it.filters.periodStart, it.filters.periodEnd)
            },
            Kpi(
                "KPI-GRV-08",
                "Desgloses de QCS",
                ATTENTION,
                "QCS",
                QCS + qcs("category", "stakeholder", "anonymous", "channel") + "grievance.severity",
                ignores = ATTENTION_IGNORES
            ) { data ->
                val rows = data.attention()
                val stakeholders = rows.mapNotNull { it.stakeholder }.toSet()
                AttentionKpis.breakdowns(
                    rows,
                    data.filters.periodStart,
                    data.filters.periodEnd,
                    data.names("locality", rows.mapNotNull { it.locality }) + data.names("community", rows.mapNotNull { it.community }),
                    Labels.of(data, data.stakeholders().filter { it.id in stakeholders })
                )
            },
            Kpi("KPI-GRV-09", "QCS recibidas frente a cerradas por mes", ATTENTION, "QCS", QCS + qcs("closed_date"), ignores = ATTENTION_IGNORES) {
                AttentionKpis.receivedVsClosed(it.attention(), it.filters.periodEnd)
            },
            Kpi(
                "KPI-GRV-10",
                "Tiempo de atención",
                ATTENTION,
                "días",
                QCS + qcs("responded_at") + listOf("grievance.resolved_date", "grievance.resolution_due_date", "inquiry.response_due_date"),
                ignores = ATTENTION_IGNORES
            ) { AttentionKpis.attentionTime(it.attention(), it.filters.periodStart, it.filters.periodEnd) },
            // ---- F4: riesgos (dashboards.md §4.6); leen como quien mira, los riesgos activos ----
            Kpi("KPI-RSK-01", "Riesgos altos", RISKS, "riesgos", RISK + listOf("social_risk.risk_level", "social_risk.category"), ignores = RISK_IGNORES) {
                RiskKpis.high(it.risks())
            },
            Kpi(
                "KPI-RSK-02",
                "Mapa de calor probabilidad × impacto",
                RISKS,
                "riesgos",
                RISK +
                    listOf(
                        "social_risk.probability",
                        "social_risk.impact",
                        "social_risk.residual_probability",
                        "social_risk.residual_impact",
                        "project.risk_medium_min_score",
                        "project.risk_high_min_score",
                        "project.risk_critical_min_score"
                    ),
                ignores = RISK_IGNORES
            ) { data ->
                // el nivel de cada celda solo con un proyecto: con varios, los umbrales pueden diferir (dashboards.md §7)
                val single = if (data.filters.projects.size == 1) data.projectRecords().singleOrNull() else null
                RiskKpis.heatmap(data.risks(), single?.let { RiskLevels.thresholds(it.attributes) })
            },
            Kpi(
                "KPI-RSK-03",
                "Riesgos por territorio y por proyecto",
                RISKS,
                "riesgos",
                RISK +
                    listOf(
                        "social_risk.area_of_influence",
                        "social_risk.risk_level",
                        "project.code",
                        "locality.name",
                        "community.name",
                        "area_of_influence.area_type"
                    ),
                ignores = RISK_IGNORES
            ) { data ->
                val rows = data.risks()
                val names =
                    data.names("locality", rows.mapNotNull { it.locality }) + data.names("community", rows.mapNotNull { it.community }) +
                        data.names("area_of_influence", rows.mapNotNull { it.areaOfInfluence }, "area_type") +
                        data.names("project", rows.mapNotNull { it.project }, "code")
                RiskKpis.distribution(rows, names)
            },
            Kpi(
                "KPI-RSK-04",
                "Evolución de los riesgos",
                RISKS,
                "revisiones",
                RISK +
                    listOf(
                        "risk_assessment.social_risk",
                        "risk_assessment.assessment_date",
                        "risk_assessment.risk_level",
                        "risk_assessment.trend",
                        "risk_assessment.residual"
                    ),
                ignores = RISK_IGNORES
            ) { data ->
                val rows = data.risks()
                RiskKpis.evolution(rows, data.riskPoints(rows.map { it.id }), data.filters.periodEnd)
            },
            Kpi(
                "KPI-RSK-05",
                "Avance de la mitigación",
                RISKS,
                "%",
                RISK +
                    listOf(
                        "social_risk.code",
                        "mitigation_measure.social_risk",
                        "mitigation_measure.completed",
                        "mitigation_measure.planned_date",
                        "mitigation_measure.owner_user_email",
                        "mitigation_measure.description"
                    ),
                ignores = RISK_IGNORES
            ) { data ->
                val rows = data.risks()
                RiskKpis.mitigation(data.measures(rows.map { it.id }), rows.associate { it.id to it.code }, data.today)
            },
            // ---- F5: inversión social (dashboards.md §4.7, especificación de F5 §3.9); leen como quien mira ----
            Kpi(
                "KPI-INV-01",
                "Ejecución de la campaña frente a su tope",
                INVESTMENT,
                "%",
                listOf(
                    "exploration_campaign.project",
                    "exploration_campaign.name",
                    "exploration_campaign.social_support_cap",
                    "exploration_campaign.currency",
                    "exploration_campaign.executed_social_amount",
                    "exploration_campaign.other_currency_items_count",
                    "commitment.exploration_campaign",
                    "commitment.amount",
                    "commitment.currency",
                    "social_contribution.exploration_campaign",
                    "social_contribution.total_amount",
                    "social_contribution.currency"
                ),
                ignores = INV_IGNORES + setOf("locality", "program", "year")
            ) { data ->
                val campaigns = data.investmentCampaigns(campaign = true)
                InvestmentKpis.campaigns(campaigns, data.committedOf(campaigns.map { it.id }))
            },
            Kpi(
                "KPI-INV-02",
                "Ejecución de programas frente a presupuesto",
                INVESTMENT,
                "%",
                PROGRAM + BUDGET,
                ignores = INV_IGNORES + setOf("locality", "explorationCampaign")
            ) { data ->
                val programs = data.programs(program = true)
                InvestmentKpis.programs(programs, data.budgetsOf(programs.map { it.id }), data.filters.year)
            },
            Kpi(
                "KPI-INV-03",
                "Inversión social ejecutada",
                INVESTMENT,
                "monto",
                PROGRAM + BUDGET +
                    listOf(
                        "commitment.project",
                        "commitment.locality",
                        "commitment.currency",
                        "delivery.commitment",
                        "delivery.social_contribution",
                        "delivery.delivery_date",
                        "delivery.delivered_amount",
                        "social_contribution.project",
                        "social_contribution.locality",
                        "social_contribution.total_amount",
                        "social_contribution.currency",
                        "exploration_campaign.project",
                        "exploration_campaign.social_support_cap",
                        "exploration_campaign.currency",
                        "project.code"
                    ),
                ignores = INV_IGNORES + setOf("explorationCampaign", "program", "year")
            ) { data ->
                val commitments = data.investmentCommitments()
                val contributions = data.investmentContributions()
                val withPrograms = data.filters.locality == null
                val programs = if (withPrograms) data.programs(program = false) else emptyList()
                InvestmentKpis.executed(
                    commitments,
                    data.deliveriesOf("commitment", commitments.map { it.id }),
                    contributions,
                    data.deliveriesOf(Contributions.CONTRIBUTION, contributions.map { it.id }),
                    programs,
                    data.budgetsOf(programs.map { it.id }),
                    data.investmentCampaigns(campaign = false),
                    data.filters.periodStart,
                    data.filters.periodEnd,
                    withPrograms,
                    data.names("project", data.filters.projects.map { it.toString() }, "code")
                )
            },
            Kpi(
                "KPI-INV-04",
                "Beneficiarios",
                INVESTMENT,
                "beneficiarios",
                PROGRAM +
                    listOf(
                        "beneficiary_group.social_program",
                        "beneficiary_group.target_count",
                        "beneficiary_group.reached_count",
                        "beneficiary_group.locality",
                        "social_contribution.project",
                        "social_contribution.locality",
                        "social_contribution.beneficiaries_count",
                        "commitment.project",
                        "commitment.locality",
                        "commitment.beneficiaries_count"
                    ),
                ignores = INV_IGNORES + setOf("explorationCampaign", "year")
            ) { data ->
                val programs = data.programs(program = true)
                InvestmentKpis.beneficiaries(programs, data.groupsOf(programs.map { it.id }), data.investmentContributions(), data.investmentCommitments())
            },
            // F6: el territorio (dashboards.md §4.8). KPI-TER-01 no lee nada por sí mismo: cada fila comprueba lo suyo
            Kpi(TerritorySummary.CODE, "Resumen de una localidad o comunidad", TERRITORY, "cifras", emptyList(), ignores = TerritorySummary.IGNORES) {
                TerritorySummary.summary(it, BY_CODE)
            },
            Kpi(
                "KPI-TER-02",
                "Superficie del área de influencia",
                TERRITORY,
                "ha",
                AREA + "area_of_influence.calculated_area_ha",
                ignores = TERRITORY_IGNORES
            ) {
                TerritoryKpis.surface(it.approvedAreas())
            },
            Kpi(
                "KPI-TER-03",
                "Miembros por tipo de área",
                TERRITORY,
                "miembros",
                AREA +
                    listOf(
                        "area_of_influence_member.area_of_influence",
                        "area_of_influence_member.locality",
                        "area_of_influence_member.community",
                        "area_of_influence_member.administrative_unit",
                        "area_of_influence_member.member_condition"
                    ),
                ignores = TERRITORY_IGNORES
            ) { data ->
                val areas = data.approvedAreas()
                TerritoryKpis.members(areas, data.areaMembers(areas.map { it.id }))
            },
            Kpi("KPI-TER-04", "Componentes sobre tierras comunales o privadas", TERRITORY, "componentes", LAND, ignores = TERRITORY_IGNORES) { data ->
                when (val outcome = data.landCrossings()) {
                    is GateOutcome.Rows -> TerritoryKpis.componentsOnLand(outcome.rows)
                    is GateOutcome.Unavailable -> IndicatorResult.unavailable(UnavailableReason.FIELD_NOT_READABLE, outcome.missingFields)
                }
            },
            // ---- F8: ejecutivos (dashboards.md §4.1) ----
            Kpi(
                ExecutiveKpis.ACTIVE_PROJECTS,
                "Proyectos activos",
                EXECUTIVE,
                "proyectos",
                listOf("project.stage", "project.project_type"),
                ignores = TERRITORY_IGNORES
            ) {
                ExecutiveKpis.activeProjects(it.projectRows())
            },
            Kpi(
                ExecutiveKpis.ACTIVE_WARNINGS,
                "Alertas activas",
                EXECUTIVE,
                "alertas",
                listOf("early_warning.project", "early_warning.severity", "early_warning.locality", "early_warning.community"),
                ignores = EXECUTIVE_WARNING_IGNORES
            ) { ExecutiveKpis.activeWarnings(it.earlyWarnings()) },
            Kpi(
                ExecutiveKpis.TREND,
                "Tendencia mensual",
                EXECUTIVE,
                "cifras",
                (
                    HELD + QCS + qcs("closed_date") + COMMITMENT +
                        listOf(
                            "commitment.agreed_date",
                            "commitment.due_date",
                            "commitment.fulfilled_date",
                            "commitment.cancelled_date",
                            "commitment_reschedule.previous_due_date",
                            "commitment_reschedule.new_due_date",
                            "commitment_reschedule.commitment"
                        )
                ).distinct(),
                ignores = setOf("explorationCampaign", "owner")
            ) { data ->
                val commitments = data.commitments(campaign = false, owner = false)
                val reschedules = data.reschedules(commitments.map { it.id })
                reschedules.warn(
                    ExecutiveKpis.trend(
                        EngagementKpis.held(data.inPeriod(), data.window(), data.filters.periodEnd),
                        data.attention(),
                        CommitmentKpis.overdueSeries(commitments, reschedules.rows, data.filters.periodEnd, data.today),
                        data.filters.periodEnd
                    )
                )
            }
        )

    val BY_CODE: Map<String, Indicator> = ALL.associateBy { it.code }
}

// las etiquetas y los cortes que necesitan leer actores. el nombre de una persona (stakeholder → person) solo sale si
// quien mira puede leerlo; si no, su cargo vigente y su organización o, sin cargo, «Persona» (dashboards.md §5.3,
// RQ-SEC-010). DashboardData no pide lo que quien mira no puede leer
object Labels {
    const val PERSON = "Persona"
    const val ORGANIZATION = "Organización"
    const val STAKEHOLDER = "Grupo de interés"

    suspend fun of(
        data: DashboardData,
        stakeholders: List<StakeholderRow>
    ): Map<String, String> {
        val people = stakeholders.mapNotNull { it.person }
        val names = data.namesOfPeople(people)
        // los cargos solo de las personas cuyo nombre no se puede mostrar
        val positions = data.positionLabels(people.filter { it !in names })
        val organizations = data.names("organization", stakeholders.mapNotNull { it.organization })
        return stakeholders.associate { s ->
            s.id to
                when {
                    s.organization != null -> organizations[s.organization]?.ifBlank { null } ?: ORGANIZATION
                    s.person != null -> names[s.person] ?: positions[s.person]?.firstOrNull() ?: PERSON
                    else -> STAKEHOLDER
                }
        }
    }

    suspend fun sexOf(
        data: DashboardData,
        stakeholders: List<StakeholderRow>
    ): Map<String, String?> {
        val sex = data.names("person", stakeholders.mapNotNull { it.person }, "sex")
        return stakeholders.filter { it.person != null }.associate { it.id to sex[it.person]?.ifEmpty { null } }
    }

    suspend fun institutionsOf(
        data: DashboardData,
        stakeholders: List<StakeholderRow>
    ): Map<String, List<String>> {
        val organizations = data.names("organization", stakeholders.mapNotNull { it.organization })
        val byPerson = data.positionOrganizations(stakeholders.mapNotNull { it.person })
        return stakeholders.associate { s ->
            s.id to (s.organization?.let { listOfNotNull(organizations[it]?.ifBlank { null }) } ?: byPerson[s.person].orEmpty())
        }
    }

    // las organizaciones que representan los participantes que asistieron a cada interacción: la organización o, sin
    // ella, el grupo representado
    suspend fun organizationsOf(
        data: DashboardData,
        engagements: List<String>
    ): Map<String, List<String>> {
        val participants = data.participants(engagements).filter { Values.bool(it.attributes["attended"]) == true }
        val names = data.names("organization", participants.mapNotNull { Values.text(it.attributes["organization"]) })
        return participants
            .groupBy { Values.text(it.attributes["engagement"]).orEmpty() }
            .mapValues { (_, list) ->
                list.mapNotNull { p ->
                    Values.text(p.attributes["organization"])?.let { names[it]?.ifBlank { null } } ?: Values.text(p.attributes["represented_group"])
                }
            }
    }
}
