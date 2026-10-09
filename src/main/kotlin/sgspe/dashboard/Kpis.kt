package sgspe.dashboard

import sgspe.common.ProjectParameters
import sgspe.common.Values
import sgspe.engagement.AttendanceCounts

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

    // los KPI de F2 no aplican explorationCampaign (dashboards.md §3.1: solo compromisos e inversión social) ni owner, el
    // responsable de un compromiso que llega en F3
    val IGNORED_IN_F2: Set<String> = setOf("explorationCampaign", "owner")

    // los filtros que un KPI-COM no aplica, según su fila de dashboards.md §4.4: todos aplican el territorio y el ámbito
    // (traducidos a localidades y comunidades, §3.1); la campaña y el responsable, solo los que los nombran
    private fun commitmentIgnores(vararg accepts: String): Set<String> = setOf("explorationCampaign", "owner") - accepts.toSet()

    // los KPI-STK ignoran además community: stakeholder no tiene comunidad (ruling P27)
    private val STAKEHOLDER_IGNORES: Set<String> = IGNORED_IN_F2 + "community"

    // los filtros pedidos que el indicador ignora, en el orden de DashboardFilters.COMMON; vacío si ninguno
    fun ignoredFilters(
        indicator: Indicator,
        filters: DashboardFilters
    ): List<String> = DashboardFilters.COMMON.filter { it in indicator.ignores && it in filters.given() }

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
                )
        )

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
                null,
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
                null,
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
            Kpi("KPI-STK-07", "Grupos de interés con más de un contacto", null, "%", CONTACT, ignores = STAKEHOLDER_IGNORES) {
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
            ) { EngagementKpis.meetings(it.inPeriod(), it.window(), it.monthlyTargets(), it.filters.periodStart, it.filters.periodEnd) },
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
