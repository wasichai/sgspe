package sgspe.deliverable

import java.time.LocalDate

// los datos de un T1 de prueba sin base (plan 8.2c, Tareas 9 a 13): «Proyecto Ejemplo A» con su estudio, su AIDS y su AIIS
// aprobados, dos localidades con sus perfiles, instituciones, vías, fiestas, un conflicto y un programa del Estado, una
// persona con su cargo, dos grupos de interés con sus evaluaciones, percepciones, temas clave, el plan y las tablas del
// capítulo 9, un riesgo con su revisión y sus medidas, compromisos y una concesión. todo ficticio (RQ-SEC-018)
object T1Source {
    fun id(n: Int): String = "00000000-0000-0000-0000-%012d".format(n)

    val PROJECT = id(1)
    val STUDY = id(2)
    val AIDS = id(3)
    val ORGANIZATION = id(4)
    val MUNICIPALITY = id(5)
    val CONSULTANT = id(6)
    val AIIS = id(7)
    val BOARD = id(9)
    val UPPER = id(20)
    val LOWER = id(21)
    val COMMUNITY = id(22)
    val PERSON = id(80)
    val LEADER = id(82)
    val DISTRICT = id(83)
    val RISK = id(98)
    val CONCESSION = id(103)
    val DELIVERABLE = id(200)

    // los datos en `s` (una subclase de prueba puede responder también la Tabla 9.5, Tarea 12)
    fun source(s: FakeReportSource = FakeReportSource()): FakeReportSource {
        s.add(
            "project",
            PROJECT,
            "active",
            "code" to "PEA-1",
            "name" to "Proyecto Ejemplo A",
            "working_crs" to "EPSG 32717",
            "description" to "Exploración de cobre en la sierra norte.",
            "exploration_phase" to "PERFORACION",
            "interest_threshold" to "3.00",
            "power_threshold" to "3.00",
            "policy_local_jobs_direct_area_only" to true,
            "local_jobs_min" to 10,
            "local_jobs_max" to 40,
            "policy_no_paid_labor" to true,
            "policy_non_monetary_donations" to false,
            "policy_synergy_requires_agreement" to true,
            "risk_medium_min_score" to 5,
            "risk_high_min_score" to 10,
            "risk_critical_min_score" to 15
        )
        s.add("organization", ORGANIZATION, null, "name" to "Asociación Ejemplo", "address" to "Av. Ejemplo 123, Cajamarca")
        s.add("organization", MUNICIPALITY, null, "name" to "Municipalidad Distrital Ejemplo", "organization_type" to "MUNICIPALIDAD", "locality" to UPPER)
        s.add("organization", CONSULTANT, null, "name" to "Consultora Ejemplo")
        s.add("organization", BOARD, null, "name" to "Junta Directiva Comunal Ejemplo", "organization_type" to "ORGANIZACION COMUNAL", "locality" to UPPER)
        s.add(
            "study",
            STUDY,
            null,
            "project" to PROJECT,
            "title" to "Diagnóstico social del Proyecto Ejemplo A",
            "objectives" to "Conocer la situación social del área de influencia.",
            "scope_description" to "Caseríos del AIDS y del AIIS y actores distritales.",
            "methodology" to "Enfoque cualitativo con fuentes primarias y secundarias.",
            "fieldwork_start_date" to "2026-08-01",
            "fieldwork_end_date" to "2026-08-20"
        )
        s.add("topic", id(31), null, "name" to "Agua")
        s.add(
            "study_instrument",
            id(30),
            null,
            "study" to STUDY,
            "topic" to id(31),
            "instrument_type" to "ENTREVISTA SEMIESTRUCTURADA",
            "application_scope" to "AIDS"
        )
        s.add(
            "area_of_influence",
            AIDS,
            "approved",
            "project" to PROJECT,
            "area_type" to "AIDS",
            "version_number" to 1,
            "criteria" to "Impactos reales o percibidos de la exploración."
        )
        s.add(
            "area_of_influence",
            AIIS,
            "approved",
            "project" to PROJECT,
            "area_type" to "AIIS",
            "version_number" to 1,
            "criteria" to "Caseríos de la microcuenca."
        )
        s.add(
            "locality",
            UPPER,
            null,
            "name" to "Caserío Ejemplo Alto",
            "locality_type" to "CASERIO",
            "recognition_status" to "RECONOCIDO",
            "history" to "Fundado en 1950."
        )
        s.add("locality", LOWER, null, "name" to "Caserío Ejemplo Bajo", "locality_type" to "CASERIO", "recognition_status" to "EN SEPARACION")
        s.add("community", COMMUNITY, null, "name" to "Comunidad Ejemplo")
        s.add(
            "area_of_influence_member",
            id(40),
            null,
            "area_of_influence" to AIDS,
            "locality" to UPPER,
            "member_condition" to "INCLUIDO",
            "rationale" to "Cercanía a las plataformas.",
            "studied_in_diagnosis" to true
        )
        s.add(
            "area_of_influence_member",
            id(41),
            null,
            "area_of_influence" to AIIS,
            "locality" to LOWER,
            "member_condition" to "INCLUIDO",
            "studied_in_diagnosis" to false
        )
        s.add(
            "area_of_influence_member",
            id(42),
            null,
            "area_of_influence" to AIDS,
            "community" to COMMUNITY,
            "member_condition" to "PROVISIONAL",
            "applied_criteria" to "Territorio comunal"
        )
        s.add(
            "locality_profile",
            id(50),
            null,
            "locality" to UPPER,
            "study" to STUDY,
            "data_date" to "2026-08-10",
            "data_source" to "Trabajo de campo",
            "reliability" to "ALTA",
            "families_count" to 120,
            "population_count" to 480,
            "members_per_family" to "4.00",
            "migration" to "Los jóvenes migran a la costa.",
            "crops" to "Papa y oca",
            "livestock" to "Ovinos",
            "economic_activities" to "Comercio",
            "water_source" to "Manantial",
            "water_treatment" to "Cloración",
            "water_frequency" to "Diaria",
            "water_fee" to "S/ 2 al mes",
            "water_system_age_years" to 12,
            "water_problems" to "Turbidez en lluvias",
            "electricity" to "Red pública",
            "sanitation" to "Letrinas",
            "phone_coverage" to "Señal móvil parcial",
            "religion" to "Católica y evangélica.",
            "electoral_process" to "Dos movimientos locales compiten."
        )
        s.add("locality_profile", id(51), null, "locality" to LOWER, "study" to STUDY, "families_count" to 40)
        s.add("school", id(60), null, "locality" to UPPER, "name" to "I. E. N° 101", "education_level" to "Primaria", "school_status" to "Activa")
        s.add(
            "health_facility",
            id(61),
            null,
            "locality" to LOWER,
            "name" to "Puesto de salud Ejemplo",
            "facility_type" to "Puesto",
            "facility_status" to "Activo"
        )
        s.add(
            "road_access",
            id(62),
            null,
            "locality" to UPPER,
            "connects_to" to LOWER,
            "road_type" to "Trocha",
            "road_condition" to "Regular",
            "transport_means" to "Combi",
            "travel_time" to "40 min"
        )
        s.add(
            "festivity",
            id(63),
            null,
            "locality" to UPPER,
            "name" to "Fiesta patronal",
            "date_text" to "15 de agosto",
            "co_organization_turns" to "Por sectores"
        )
        s.add("state_program", id(64), null, "name" to "Programa social del Estado")
        s.link("state_program", id(64), "state_program_locality", "locality", UPPER)
        s.add(
            "local_conflict",
            id(65),
            null,
            "title" to "Linderos con el caserío vecino",
            "conflict_type" to "TERRITORIAL",
            "conflict_status" to "ACTIVO",
            "disputed_resource" to "Pastos"
        )
        s.link("local_conflict", id(65), "local_conflict_locality", "locality", LOWER)
        s.add(
            "engagement",
            id(70),
            "held",
            "project" to PROJECT,
            "study" to STUDY,
            "locality" to UPPER,
            "engagement_type" to "ENTREVISTA",
            "held_at" to "2026-08-05T10:00:00Z"
        )
        s.add(
            "engagement",
            id(71),
            "held",
            "project" to PROJECT,
            "study" to STUDY,
            "locality" to LOWER,
            "engagement_type" to "FICHA DE OBSERVACION",
            "held_at" to "2026-08-06T10:00:00Z"
        )
        s.add("engagement", id(72), "held", "project" to PROJECT, "engagement_type" to "REUNION", "held_at" to "2026-09-10T15:00:00Z")
        s.add("field_visit", id(73), null, "project" to PROJECT, "actual_start_date" to "2026-08-03")
        s.add(
            "exploration_campaign",
            id(74),
            null,
            "project" to PROJECT,
            "name" to "Campaña 2026",
            "activity_type" to "PERFORACION",
            "planned_start_date" to "2026-10-01",
            "planned_end_date" to "2027-03-31",
            "social_support_cap" to "50000.00",
            "currency" to "PEN"
        )
        s.add("project_party", id(75), null, "project" to PROJECT, "party_role" to "CLIENTE", "organization" to ORGANIZATION)
        s.add("project_party", id(76), null, "project" to PROJECT, "party_role" to "CONSULTORA", "organization" to CONSULTANT)
        s.add("project_party", id(77), null, "project" to PROJECT, "party_role" to "CONTRATISTA", "organization" to MUNICIPALITY)
        s.add("person", PERSON, null, "given_names" to "Persona", "surnames" to "Ejemplo 1", "residence_locality" to UPPER)
        s.add(
            "position",
            id(81),
            null,
            "person" to PERSON,
            "organization" to BOARD,
            "position_title" to "Presidente",
            "position_status" to "VIGENTE",
            "career_notes" to "Dirigente desde 2018.",
            "election_mechanism" to "Asamblea",
            "term_start_date" to "2025-01-01",
            "term_end_date" to "2026-12-31"
        )
        s.add(
            "stakeholder",
            LEADER,
            null,
            "project" to PROJECT,
            "person" to PERSON,
            "stakeholder_scope" to "AIDS",
            "stakeholder_type" to "AUTORIDAD LOCAL",
            "interview_status" to "ENTREVISTADO",
            "profile_summary" to "Coordina las faenas comunales.",
            "influence_group" to "ESTRATEGICO",
            "interest_index" to "4.20",
            "power_index" to "3.80"
        )
        s.add(
            "stakeholder",
            DISTRICT,
            null,
            "project" to PROJECT,
            "organization" to MUNICIPALITY,
            "stakeholder_scope" to "AIIS",
            "stakeholder_type" to "ORGANIZACION SOCIAL DISTRITAL",
            "interview_status" to "SE NEGO",
            "influence_group" to "POLITICO",
            "interest_index" to "2.00",
            "power_index" to "4.00"
        )
        s.add(
            "stakeholder_assessment",
            id(84),
            null,
            "stakeholder" to LEADER,
            "assessment_date" to "2026-09-15",
            "stance" to "A FAVOR",
            "stance_reasons" to "Espera empleo local.",
            "influence_group" to "ESTRATEGICO",
            "stance_changed" to false,
            "trust_score" to 4,
            "communication_sufficient" to true,
            "information_adequate" to true
        )
        s.add(
            "stakeholder_assessment",
            id(85),
            null,
            "stakeholder" to LEADER,
            "assessment_date" to "2026-08-10",
            "stance" to "NEUTRAL",
            "trust_score" to 3,
            "stance_changed" to true
        )
        s.add(
            "stakeholder_assessment",
            id(86),
            null,
            "stakeholder" to DISTRICT,
            "assessment_date" to "2026-09-01",
            "stance" to "EN CONTRA",
            "stance_reasons" to "Teme por el agua.",
            "influence_group" to "POLITICO",
            "trust_score" to 2
        )
        s.add(
            "perception",
            id(87),
            null,
            "study" to STUDY,
            "subject" to "PROYECTO",
            "polarity" to "NEGATIVA",
            "perception_scope" to "AIDS",
            "statement" to "El proyecto afectará el agua.",
            "quote" to "Queremos agua limpia",
            "attributed_role" to "Dirigente comunal"
        )
        s.add(
            "perception",
            id(88),
            null,
            "study" to STUDY,
            "subject" to "MINERIA",
            "polarity" to "POSITIVA",
            "perception_scope" to "AIIS",
            "statement" to "La minería trae trabajo."
        )
        s.add(
            "key_theme",
            id(89),
            null,
            "project" to PROJECT,
            "study" to STUDY,
            "name" to "Agua",
            "description" to "Cantidad y calidad del agua.",
            "risks" to "Conflicto por el agua.",
            "opportunities" to "Monitoreo participativo."
        )
        s.add(
            "engagement_plan",
            id(90),
            null,
            "project" to PROJECT,
            "name" to "Plan 2026",
            "strategic_objective" to "Mantener la licencia social.",
            "action_axes" to "1. Información.\n2. Diálogo."
        )
        s.add(
            "planned_activity",
            id(91),
            null,
            "engagement_plan" to id(90),
            "name" to "Taller informativo",
            "planned_date" to "2026-10-10",
            "completed" to false
        )
        s.add("project_assignment", id(92), null, "project" to PROJECT, "role_name" to "Relacionista comunitario", "active" to true)
        s.add("project_assignment", id(93), null, "project" to PROJECT, "role_name" to "Relacionista comunitario", "active" to true)
        s.add(
            "prioritization_rule",
            id(94),
            null,
            "project" to PROJECT,
            "information_priority" to "MUY ALTA",
            "influence_group" to "ESTRATEGICO",
            "stance" to "A FAVOR",
            "stakeholder_scope" to "AIDS",
            "stakeholder_type" to "AUTORIDAD LOCAL",
            "rationale" to "Deciden en la asamblea."
        )
        s.add(
            "key_message",
            id(95),
            null,
            "project" to PROJECT,
            "justification" to "Dudas sobre el agua.",
            "message" to "El proyecto monitorea el agua con la comunidad.",
            "support_actions" to "Monitoreo participativo",
            "target_audience" to "Comuneros"
        )
        s.add(
            "strategic_alliance",
            id(96),
            null,
            "project" to PROJECT,
            "organization" to MUNICIPALITY,
            "reasons" to "Gestiona el agua potable.",
            "possible_actions" to "Convenio de agua.",
            "formalized" to false
        )
        s.add(
            "potential_impact",
            id(97),
            null,
            "project" to PROJECT,
            "activity_type" to "PERFORACION",
            "impact_type" to "AMBIENTAL",
            "impact_nature" to "DIRECTO",
            "impact_sign" to "NEGATIVO",
            "description" to "Polvo en la vía."
        )
        s.add(
            "social_risk",
            RISK,
            "active",
            "project" to PROJECT,
            "code" to "RS-1",
            "title" to "Conflicto por el agua",
            "risk_source" to "Percepción de contaminación.",
            "category" to "AGUA",
            "treatment_decision" to "MITIGAR",
            "current_controls" to "Monitoreo mensual",
            "controls_adequate" to true,
            "monitoring_sources" to "Actas de monitoreo",
            "residual_level" to "MEDIO"
        )
        s.add("risk_assessment", id(99), null, "social_risk" to RISK, "assessment_date" to "2026-09-20", "probability" to 4, "impact" to 4, "residual" to false)
        s.add("mitigation_measure", id(100), null, "social_risk" to RISK, "description" to "Monitoreo participativo del agua.", "completed" to false)
        s.add("commitment", id(101), "in_progress", "project" to PROJECT, "deadline_status" to "AL DIA")
        s.add("commitment", id(102), "fulfilled", "project" to PROJECT, "deadline_status" to "VENCIDO")
        s.add("mining_concession", CONCESSION, null, "code" to "CM-01")
        s.link("project", PROJECT, "project_concession", "mining_concession", CONCESSION)
        return s
    }

    // el entregable y sus secciones (las del tipo, con el texto dado por clave)
    fun input(content: Map<String, String> = emptyMap()): AssemblyInput =
        AssemblyInput(
            deliverable =
                SourceRow(
                    DELIVERABLE,
                    "draft",
                    mapOf(
                        "project" to PROJECT,
                        "study" to STUDY,
                        "area_of_influence" to AIDS,
                        "document_code" to "ENT-T1",
                        "title" to "Diagnóstico social del Proyecto Ejemplo A",
                        "deliverable_type" to SocialDiagnosisType.NAME,
                        "period_start_date" to "2026-09-01",
                        "period_end_date" to "2026-09-30",
                        "cutoff_date" to "2026-09-30",
                        "prepared_by_organization" to ORGANIZATION,
                        "prepared_for_organization" to CONSULTANT
                    )
                ),
            sections =
                SocialDiagnosisType().sections.mapIndexed { i, spec ->
                    SourceRow(id(300 + i), null, mapOf("section_key" to spec.key, "content" to content[spec.key], "content_origin" to "HUMANO"))
                },
            templateId = null,
            templateSha256 = "0".repeat(64),
            draftRevision = "A",
            issueRevision = "0",
            today = LocalDate.of(2026, 10, 15)
        )
}
