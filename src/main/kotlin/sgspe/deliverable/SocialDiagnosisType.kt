package sgspe.deliverable

import org.springframework.stereotype.Component
import sgspe.privacy.Block
import sgspe.privacy.Mentions
import sgspe.privacy.ReportMeta
import sgspe.privacy.ReportModel
import sgspe.privacy.ReportSection

// T1 · Diagnóstico social (especificación de F8 §3.8, deliverables.md §5, DOC-01): portada, índice con TOC y listas de
// tablas y de figuras, capítulos 1 a 9 y anexos 10.1 a 10.3, una sección por fila de deliverables.md §5 (decisión 12 del plan
// 8.2c: el capítulo 5 por tema con un título por ámbito). exige project, study, el AIDS aprobado, el periodo, cutoff_date y el
// working_crs del proyecto (§2.2.13). lee como la plataforma, solo lo que declara en `reads` (ReportSourcesTest). las
// personas van como menciones y los datos restringidos con su marca (EXTERNO los quita). el texto de cada sección se copia
// al final de sus bloques. los mapas (áreas y Anexo 1) se piden a ReportMaps; sin mapa de portada
@Component
class SocialDiagnosisType : DeliverableType {
    override val name: String = NAME
    override val version: Int = 1
    override val requiresApprovedArea: Boolean = true

    // una fila de la tabla de secciones: clave, título con su número literal, tipo, nivel, capítulo, si admite IA y su guía
    private data class Row(
        val key: String,
        val title: String,
        val kind: String,
        val level: Int,
        val chapter: Int?,
        val assisted: Boolean,
        val guide: String
    )

    override val sections: List<SectionSpec> = ROWS.mapIndexed { i, r -> SectionSpec(r.key, r.title, i + 1, r.kind, true, r.guide, r.assisted) }

    override val reads: Map<String, Set<String>> =
        mapOf(
            Deliverables.PROJECT to
                setOf(
                    "code",
                    "name",
                    "working_crs",
                    "description",
                    "exploration_phase",
                    "interest_threshold",
                    "power_threshold",
                    "policy_local_jobs_direct_area_only",
                    "local_jobs_min",
                    "local_jobs_max",
                    "policy_no_paid_labor",
                    "policy_non_monetary_donations",
                    "policy_synergy_requires_agreement",
                    "risk_medium_min_score",
                    "risk_high_min_score",
                    "risk_critical_min_score"
                ),
            "organization" to setOf("name", "address", "organization_type", "locality"),
            "study" to setOf("title", "objectives", "scope_description", "methodology", "fieldwork_start_date", "fieldwork_end_date", "project"),
            "study_instrument" to setOf("instrument_type", "application_scope", "description", "study", "topic"),
            "topic" to setOf("name"),
            "area_of_influence" to setOf("area_type", "version_number", "criteria", "project"),
            "area_of_influence_member" to
                setOf(
                    "member_condition",
                    "applied_criteria",
                    "rationale",
                    "studied_in_diagnosis",
                    "area_of_influence",
                    "locality",
                    "community",
                    "administrative_unit"
                ),
            "locality" to setOf("name", "locality_type", "recognition_status", "history"),
            "community" to setOf("name"),
            "administrative_unit" to setOf("name", "level"),
            "locality_profile" to
                setOf(
                    "data_date",
                    "data_source",
                    "reliability",
                    "families_count",
                    "population_count",
                    "members_per_family",
                    "migration",
                    "economic_activities",
                    "crops",
                    "livestock",
                    "water_source",
                    "water_treatment",
                    "water_frequency",
                    "water_fee",
                    "water_system_age_years",
                    "water_problems",
                    "electricity",
                    "sanitation",
                    "phone_coverage",
                    "religion",
                    "electoral_process",
                    "locality",
                    "study"
                ),
            "local_conflict" to setOf("title", "conflict_type", "conflict_status", "disputed_resource"),
            "festivity" to setOf("name", "date_text", "co_organization_turns", "locality"),
            "school" to setOf("name", "education_level", "school_status", "locality"),
            "health_facility" to setOf("name", "facility_type", "facility_status", "locality"),
            "road_access" to setOf("road_type", "road_condition", "transport_means", "travel_time", "locality", "connects_to"),
            "state_program" to setOf("name"),
            "engagement" to setOf("engagement_type", "held_at", "scheduled_at", "locality", "study", "project"),
            "field_visit" to setOf("actual_start_date", "planned_start_date", "project"),
            "exploration_campaign" to setOf("name", "activity_type", "planned_start_date", "planned_end_date", "social_support_cap", "currency", "project"),
            "project_party" to setOf("party_role", "organization", "project"),
            "stakeholder" to
                setOf(
                    "stakeholder_type",
                    "stakeholder_scope",
                    "interview_status",
                    "profile_summary",
                    "influence_group",
                    "interest_index",
                    "power_index",
                    "person",
                    "organization",
                    "project",
                    "locality"
                ),
            "person" to setOf("given_names", "surnames", "residence_locality"),
            "position" to
                setOf("position_title", "election_mechanism", "term_start_date", "term_end_date", "position_status", "career_notes", "person", "organization"),
            "stakeholder_assessment" to
                setOf(
                    "assessment_date",
                    "stance",
                    "stance_reasons",
                    "influence_group",
                    "stance_changed",
                    "trust_score",
                    "communication_sufficient",
                    "information_adequate",
                    "stakeholder"
                ),
            "perception" to setOf("subject", "polarity", "perception_scope", "statement", "quote", "attributed_role", "study"),
            "key_theme" to setOf("name", "description", "risks", "opportunities", "project", "study"),
            "engagement_plan" to setOf("name", "strategic_objective", "action_axes", "project"),
            "planned_activity" to setOf("name", "planned_date", "completed", "engagement_plan"),
            "project_assignment" to setOf("role_name", "active", "project"),
            "prioritization_rule" to
                setOf(
                    "influence_group",
                    "stance",
                    "stakeholder_scope",
                    "stakeholder_type",
                    "information_priority",
                    "rationale",
                    "project"
                ),
            "key_message" to setOf("justification", "message", "support_actions", "target_audience", "project"),
            "strategic_alliance" to setOf("reasons", "possible_actions", "formalized", "organization", "project"),
            "potential_impact" to setOf("activity_type", "description", "impact_type", "impact_nature", "impact_sign", "project"),
            "social_risk" to
                setOf(
                    "code",
                    "title",
                    "risk_source",
                    "category",
                    "treatment_decision",
                    "treatment_rationale",
                    "current_controls",
                    "controls_adequate",
                    "monitoring_sources",
                    "residual_level",
                    "project"
                ),
            "risk_assessment" to setOf("assessment_date", "probability", "impact", "residual", "social_risk"),
            "mitigation_measure" to setOf("description", "completed", "social_risk"),
            "commitment" to setOf("deadline_status", "project"),
            "mining_concession" to setOf("code")
        )

    override suspend fun missingParameters(
        deliverable: Map<String, Any?>,
        project: Map<String, Any?>
    ): List<String> =
        listOfNotNull(
            "study".takeIf { deliverable["study"] == null },
            DiagnosisScene.AREA.takeIf { deliverable[DiagnosisScene.AREA] == null },
            Deliverables.PERIOD_START.takeIf { deliverable[Deliverables.PERIOD_START] == null },
            Deliverables.PERIOD_END.takeIf { deliverable[Deliverables.PERIOD_END] == null },
            Deliverables.CUTOFF.takeIf { deliverable[Deliverables.CUTOFF] == null },
            "project.working_crs".takeIf { project["working_crs"] == null }
        )

    override suspend fun assemble(
        input: AssemblyInput,
        source: ReportSource
    ): Assembled {
        val scene = DiagnosisScene.load(input, source)
        val texts = input.sections.associate { (it.text(Deliverables.SECTION_KEY) ?: "") to it.text(Deliverables.CONTENT) }
        val built =
            ROWS.map { row ->
                val narrative = texts[row.key]?.let { listOf(Block.Paragraph(it)) }.orEmpty()
                ReportSection(row.key, row.title, row.kind, blocks(row.key, scene) + narrative, row.level, row.chapter)
            }
        input.sections
            .flatMap { Mentions.keys(it.text(Deliverables.CONTENT).orEmpty()) }
            .distinct()
            .forEach { scene.people.mention(it) }
        scene.people.projectNames()
        val d = input.deliverable
        val preparedFor = d.ref("prepared_for_organization")?.let { source.one("organization", it) }
        val preparedBy = d.ref("prepared_by_organization")?.let { source.one("organization", it) }
        val meta =
            ReportMeta(
                type = NAME,
                typeVersion = version,
                code = d.text(Deliverables.CODE)!!,
                title = d.text(Deliverables.TITLE)!!,
                draftRevision = input.draftRevision,
                issueRevision = input.issueRevision,
                project = scene.project.text("name")!!,
                periodStart = scene.start.toString(),
                periodEnd = scene.end.toString(),
                cutoff = scene.cutoff.toString(),
                scope = "Áreas de influencia social del proyecto",
                preparedFor = preparedFor?.text("name"),
                preparedBy = preparedBy?.text("name"),
                authorAddress = preparedBy?.text("address"),
                dateLabel = ReportFormat.monthYear(scene.cutoff),
                templateId = input.templateId,
                templateSha256 = input.templateSha256
            )
        val maps = listOfNotNull(DiagnosisStudy.areasMap(scene))
        val sectionTexts = built.associate { it.key to (texts[it.key]?.let { text -> listOf(text) }.orEmpty() + scene.recordTexts[it.key].orEmpty()) }
        val model =
            ReportModel(
                meta = meta,
                sections = built,
                people = scene.people.people,
                aiAssistedSections =
                    input.sections
                        .filter { it.text(Deliverables.CONTENT_ORIGIN) == Deliverables.AI_ASSISTED }
                        .mapNotNull { it.text(Deliverables.SECTION_KEY) }
                        .sorted()
            )
        return Assembled(model, maps, scene.people.names, sectionTexts)
    }

    // los bloques calculados de cada sección; las Tareas 10 a 13 del plan 8.2c suman los capítulos 5 a 10
    private suspend fun blocks(
        key: String,
        scene: DiagnosisScene
    ): List<Block> =
        when (key) {
            "portada" -> DiagnosisStudy.cover(scene)
            "indice" -> DiagnosisStudy.index()
            "introduccion" -> DiagnosisStudy.introduction(scene)
            "objetivos" -> DiagnosisStudy.objectives(scene)
            "metodologia" -> DiagnosisStudy.methodology(scene)
            "alcance" -> DiagnosisStudy.scope(scene)
            "diseno" -> DiagnosisStudy.design(scene)
            "trabajo_campo" -> DiagnosisStudy.fieldwork(scene)
            "areas" -> DiagnosisStudy.areasIntro(scene)
            "aids" -> DiagnosisStudy.aids(scene)
            "aiis" -> DiagnosisStudy.aiis(scene)
            "historia" -> DiagnosisContext.history(scene)
            "poblacion" -> DiagnosisContext.population(scene)
            "economia" -> DiagnosisContext.economy(scene)
            "servicios" -> DiagnosisContext.services(scene)
            "educacion_salud" -> DiagnosisContext.educationHealth(scene)
            "vias" -> DiagnosisContext.roads(scene)
            "organizaciones" -> DiagnosisContext.organizations(scene)
            "proceso_electoral" -> DiagnosisContext.electoral(scene)
            "costumbres" -> DiagnosisContext.customs(scene)
            else -> emptyList()
        }

    companion object {
        const val NAME = "DIAGNOSTICO SOCIAL"
        private const val CALCULATED = "CALCULADA"
        private const val MIXED = "MIXTA"
        private const val NARRATIVE = "NARRATIVA"
        private const val ANNEX = "ANEXO"

        private val ROWS: List<Row> =
            listOf(
                Row("portada", "Portada", CALCULATED, 1, null, false, "Título con el proyecto, preparado para, suscrito por, mes y año."),
                Row("indice", "Índice", CALCULATED, 1, null, false, "Índice y listas de tablas y de figuras, que se actualizan al convertir."),
                Row(
                    "introduccion",
                    "1. Introducción",
                    MIXED,
                    1,
                    1,
                    true,
                    "Ubicación, fase, campaña prevista, apoyos y oposición, empresa a cargo y estructura del estudio."
                ),
                Row("objetivos", "2. Objetivos", MIXED, 1, 2, false, "Objetivo general y objetivos específicos del estudio."),
                Row("metodologia", "3. Metodología", MIXED, 1, 3, false, "Enfoque cualitativo, fuentes primarias y secundarias."),
                Row(
                    "alcance",
                    "3.1 Alcance del estudio",
                    MIXED,
                    2,
                    3,
                    false,
                    "Localidades del AIDS y del AIIS y actores distritales, provinciales y regionales."
                ),
                Row("diseno", "3.2 Diseño del estudio", MIXED, 2, 3, false, "Temas, instrumentos, fuentes secundarias y plan de campo."),
                Row("trabajo_campo", "3.3 Trabajo de campo", MIXED, 2, 3, false, "Fechas, localidades visitadas, entrevistas y fichas por ámbito."),
                Row("areas", "4. Definición de áreas de influencia", MIXED, 1, 4, true, "Criterio general: impactos reales o percibidos de las actividades."),
                Row("aids", "4.1 Área de influencia directa social", MIXED, 2, 4, false, "Criterios y miembros con su condición y su justificación."),
                Row(
                    "aiis",
                    "4.2 Área de influencia indirecta social",
                    MIXED,
                    2,
                    4,
                    false,
                    "Criterios, caseríos y ámbitos distrital y provincial; mapa de áreas."
                ),
                Row("contexto", "5. Contexto socioeconómico", NARRATIVE, 1, 5, true, "Fuentes y alcance del capítulo por ámbito."),
                Row(
                    "historia",
                    "5.1 Historia y organización del territorio",
                    MIXED,
                    2,
                    5,
                    false,
                    "Fundación, sectores, reconocimiento, conflictos y tenencia de la tierra."
                ),
                Row("poblacion", "5.2 Población y migración", MIXED, 2, 5, false, "Familias y población por localidad; migración y sus causas."),
                Row("economia", "5.3 Actividades económicas", MIXED, 2, 5, false, "Cultivos, ganadería y otras actividades."),
                Row("servicios", "5.4 Servicios básicos", CALCULATED, 2, 5, false, "Agua, electricidad y saneamiento por localidad."),
                Row("educacion_salud", "5.5 Condiciones educativas y de salud", MIXED, 2, 5, false, "Instituciones educativas y establecimientos de salud."),
                Row("vias", "5.6 Vías y medios de comunicación", CALCULATED, 2, 5, false, "Vías, estado, transporte y telefonía."),
                Row(
                    "organizaciones",
                    "5.7 Organizaciones e instituciones locales",
                    CALCULATED,
                    2,
                    5,
                    false,
                    "Organizaciones por localidad y programas del Estado."
                ),
                Row(
                    "proceso_electoral",
                    "5.8 Proceso electoral",
                    MIXED,
                    2,
                    5,
                    false,
                    "Preferencias electorales y percepción de la política local (solo INTERNO)."
                ),
                Row("costumbres", "5.9 Costumbres y fechas festivas", MIXED, 2, 5, false, "Religión (solo INTERNO) y festividades con sus turnos."),
                Row(
                    "grupos_interes",
                    "6. Identificación de grupos de interés",
                    MIXED,
                    1,
                    6,
                    false,
                    "Qué es un grupo de interés y las cuatro posturas; grupos por ámbito y tipo."
                ),
                Row("perfiles", "6.1 Perfiles por cargo", MIXED, 2, 6, false, "Trayectoria, elección y periodo, funciones, postura y estado de entrevista."),
                Row("caracterizacion", "7. Caracterización de los grupos de interés", MIXED, 1, 7, false, "Qué contiene el capítulo."),
                Row("percepciones", "7.1 Percepciones", MIXED, 2, 7, true, "Percepciones sobre la minería, el proyecto y la empresa, con citas por cargo."),
                Row("clasificacion", "7.2 Clasificación según interés y poder", MIXED, 2, 7, false, "Método, umbrales y grupos de influencia por ámbito."),
                Row("grupos_estrategicos", "7.2.1 Grupos estratégicos", MIXED, 3, 7, true, "Quiénes son y qué implican."),
                Row("grupos_politicos", "7.2.2 Grupos políticos", MIXED, 3, 7, true, "Quiénes son y qué implican."),
                Row("grupos_monitoreo", "7.2.3 Grupos para monitoreo", MIXED, 3, 7, true, "Quiénes son y qué implican."),
                Row("temas_clave", "8. Definición de temas clave", CALCULATED, 1, 8, false, "Tema, descripción, riesgos y oportunidades."),
                Row("estrategias", "9. Estrategias sociales", MIXED, 1, 9, true, "Meta del periodo y ejes de acción."),
                Row("comunicacion", "9.1 Información y comunicación", MIXED, 2, 9, true, "Situación de la comunicación, contactos regulares y protocolos."),
                Row(
                    "priorizacion",
                    "9.1.1 Priorización de la información",
                    CALCULATED,
                    3,
                    9,
                    false,
                    "Prioridad por grupo de influencia, postura, ámbito y tipo."
                ),
                Row("mensajes", "9.1.2 Mensajes clave", CALCULATED, 3, 9, false, "Justificación, mensaje, acciones de soporte y público."),
                Row("alianzas", "9.2 Alianzas estratégicas", MIXED, 2, 9, false, "Institución, razones y acciones posibles."),
                Row("impactos", "9.3 Gestión de las percepciones de impactos", CALCULATED, 2, 9, false, "Impactos potenciales por tipo de actividad."),
                Row("monitoreo", "9.3.1 Monitoreo", CALCULATED, 3, 9, false, "Tabla 9.5 con meta, valor del periodo y cifra oficial."),
                Row(
                    "evaluacion_grupos",
                    "9.3.1.1 Evaluación de los grupos de interés",
                    CALCULATED,
                    3,
                    9,
                    false,
                    "Postura, confianza, comunicación e información por mes."
                ),
                Row(
                    "seguimiento_compromisos",
                    "9.3.1.2 Seguimiento de compromisos sociales",
                    CALCULATED,
                    3,
                    9,
                    false,
                    "Resumen de la matriz y lineamientos de apoyo de la campaña."
                ),
                Row("riesgos", "9.3.2 Riesgos sociales", CALCULATED, 3, 9, false, "Riesgos, su fuente y la matriz de probabilidad e impacto."),
                Row("gestion_riesgos", "9.3.2.2 Gestión del riesgo", MIXED, 3, 9, false, "Tratamiento, controles, medidas, monitoreo y riesgo residual."),
                Row("necesidades", "9.4 Necesidades internas", MIXED, 2, 9, true, "Plan de trabajo con metas y responsables; equipo y contratistas."),
                Row("anexos", "10. Anexos", ANNEX, 1, 10, false, "Recursos del diagnóstico."),
                Row(
                    "anexo_concesiones",
                    "10.1 Anexo 1: concesiones del proyecto",
                    ANNEX,
                    2,
                    10,
                    false,
                    "Mapa con las diez capas de la leyenda, en una sección horizontal."
                ),
                Row("anexo_instrumentos", "10.2 Anexo 2: temas e instrumentos", ANNEX, 2, 10, false, "Tema, tipo de instrumento y ámbito de aplicación."),
                Row(
                    "anexo_metodologia",
                    "10.3 Anexo 3: metodología de interés y poder",
                    ANNEX,
                    2,
                    10,
                    false,
                    "Sistema de variables, índices de 1 a 5 y gráfico de influencia."
                )
            )
    }
}
