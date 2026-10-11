package sgspe

import org.springframework.http.HttpStatus

// la base de las pruebas de integración de F5 (plan 5.1; los planes 5.2a y 5.2b la usan para las suyas): lo de F4ApiTest más
// aportes con sus ítems, campañas con tope, acuerdos vigentes, temas priorizados, la entrega de un aporte con sus tres
// evidencias y programas con presupuesto, indicadores, mediciones, iniciativas, grupos de beneficiarios e hitos, que ya cumplen
// las guardas de 5.2: un aporte APOYO EN ESPECIE con ítems BIEN (ningún lineamiento incumplido), con su tipo (P9), sin
// sinergia (P6) ni campaña salvo que se pidan, registrado por quien se indica (P5); la entrega con conformidad y quien la dio
// (P8); el programa sin socio (G3 no aplica)
abstract class F5ApiTest : F4ApiTest() {
    // el pagador de los aportes de la prueba: una organización que leen todos los roles que registran aportes
    private val payer: String by lazy { organization("Asociación Ejemplo") }

    // ---- aportes (WF-P) ----

    // un aporte en requested, creado por `creator` (el admin si es null) con motivo (requiresReason), y `items` ítems BIEN de
    // 500 que carga el mismo `creator` (decisión 1: los ítems se cargan antes de aprobar)
    protected fun socialContribution(
        project: String,
        creator: Account? = null,
        attributes: Map<String, Any?> = emptyMap(),
        items: Int = 1
    ): String {
        val id =
            createRecord(
                "social_contribution",
                mapOf(
                    "project" to project,
                    "code" to uniqueName("AP-"),
                    "contribution_type" to "APOYO EN ESPECIE",
                    "occasion" to "Aniversario de la escuela",
                    "declared_purpose" to "Útiles para el aniversario de la escuela de la comunidad.",
                    "conditions" to "Entrega en el local comunal con acta de recepción firmada.",
                    "currency" to "PEN",
                    "beneficiaries_count" to 120,
                    "has_institutional_synergy" to false,
                    "payer_organization" to payer
                ) + attributes,
                withReason("alta de prueba"),
                creator?.token ?: token
            )
        repeat(items) { contributionItem(id, creator) }
        return id
    }

    // un ítem BIEN de 500 (50 juegos de 10), en la moneda del aporte
    protected fun contributionItem(
        contribution: String,
        creator: Account? = null,
        attributes: Map<String, Any?> = emptyMap()
    ): String =
        createRecord(
            "contribution_item",
            mapOf(
                "social_contribution" to contribution,
                "description" to "Juego de útiles escolares",
                "quantity" to 50,
                "unit" to "juegos",
                "unit_cost" to 10,
                "line_amount" to 500,
                "item_kind" to "BIEN"
            ) + attributes,
            token = creator?.token ?: token
        )

    // un aporte aprobado por un APPROVER asignado, que no es quien lo registró (P4 y P5) y, con `procure`, adquirido por quien lo
    // registró (register_procurement es de SOCIAL_INVESTMENT_MANAGER)
    protected fun approvedContribution(
        project: String,
        creator: Account = member("SOCIAL_INVESTMENT_MANAGER", project),
        attributes: Map<String, Any?> = emptyMap(),
        procure: Boolean = false
    ): String {
        val id = socialContribution(project, creator, attributes)
        move("social_contribution", id, "approve", member("APPROVER", project).token, "aprobación de prueba")
        if (procure) move("social_contribution", id, "register_procurement", creator.token, "compra de prueba")
        return id
    }

    // la entrega de un aporte como la plataforma (delivery es apiOnly y su endpoint, POST
    // /api/sgspe/social-contributions/{id}/deliveries, es del plan 5.2a), con conformidad y quien la dio (P8), y sus evidencias
    // del proyecto del aporte vinculadas por el admin: por defecto ACTA DE RECEPCION (DATOS PERSONALES: lleva nombres y firmas,
    // §3.10 de la especificación), FOTO y COMPROBANTE
    protected fun contributionDelivery(
        contribution: String,
        project: String,
        evidenceTypes: List<String> = listOf("ACTA DE RECEPCION", "FOTO", "COMPROBANTE")
    ): String {
        val delivery =
            platformCreate(
                "delivery",
                mapOf(
                    "social_contribution" to contribution,
                    "delivery_date" to "2026-10-20",
                    "place" to "Local comunal",
                    "delivered_items" to "50 juegos de útiles escolares",
                    "recipients" to "Junta directiva de la Asociación Ejemplo",
                    "conformity_given" to true,
                    "conformity_by" to "Junta directiva de la Asociación Ejemplo"
                ),
                "entrega de prueba como la plataforma"
            )
        for (type in evidenceTypes) {
            val classification = if (type == "ACTA DE RECEPCION") "DATOS PERSONALES" else "INTERNA"
            evidence(project, type, classification, links = listOf(mapOf("object" to "delivery", "recordId" to delivery)))
        }
        return delivery
    }

    // una campaña con su tope y su moneda (RN-INV-003); `cap` o `currency` nulos la dejan sin ese dato
    protected fun campaign(
        project: String,
        cap: Number? = 10000,
        currency: String? = "PEN"
    ): String =
        createRecord(
            "exploration_campaign",
            mapOf("project" to project, "name" to "Campaña de perforación", "social_support_cap" to cap, "currency" to currency)
                .filterValues { it != null }
        )

    // un convenio vigente en active (P6 y G3 piden un acuerdo en active y no VENCIDO): firmado y vigente, activado por el admin
    protected fun activeAgreement(
        project: String,
        attributes: Map<String, Any?> = emptyMap()
    ): String {
        val id = agreement(project, attributes = attributes)
        move("agreement", id, "activate_from_draft", reason = "convenio firmado")
        return id
    }

    // un tema del catálogo vinculado a los temas priorizados del proyecto (project_priority_topic, RN-INV-005)
    protected fun priorityTopic(
        project: String,
        name: String = "Educación"
    ): String {
        val topic = createRecord("topic", mapOf("code" to uniqueName("TEMA-"), "name" to name, "active" to true))
        val linked = link("project", project, "project_priority_topic", topic)
        if (linked.status != HttpStatus.NO_CONTENT) throw AssertionError("project_priority_topic: ${linked.status} ${linked.body}")
        return topic
    }

    // ---- programas (WF-G) ----

    // un programa en draft, sin socio
    protected fun socialProgram(
        project: String,
        creator: Account? = null,
        attributes: Map<String, Any?> = emptyMap()
    ): String =
        createRecord(
            "social_program",
            mapOf(
                "project" to project,
                "code" to uniqueName("PS-"),
                "name" to "Programa Ejemplo",
                "description" to "Programa de nutrición infantil de prueba.",
                "start_date" to "2026-01-01",
                "end_date" to "2027-12-31",
                "currency" to "PEN"
            ) + attributes,
            token = creator?.token ?: token
        )

    protected fun programBudget(
        program: String,
        year: Int = 2026,
        allocated: Number = 50000,
        currency: String = "PEN",
        executed: Number? = null,
        creator: Account? = null
    ): String =
        createRecord(
            "program_budget",
            mapOf(
                "social_program" to program,
                "budget_year" to year,
                "allocated_amount" to allocated,
                "executed_amount" to executed,
                "currency" to currency
            ).filterValues { it != null },
            token = creator?.token ?: token
        )

    protected fun programIndicator(
        program: String,
        creator: Account? = null,
        attributes: Map<String, Any?> = emptyMap()
    ): String =
        createRecord(
            "program_indicator",
            mapOf(
                "social_program" to program,
                "name" to "Niños de 3 a 5 años con anemia",
                "unit" to "%",
                "baseline_value" to 40,
                "target_value" to 20,
                "frequency" to "Trimestral"
            ) + attributes,
            token = creator?.token ?: token
        )

    // una medición inmutable (appendOnly) de un periodo
    protected fun programMeasurement(
        indicator: String,
        start: String = "2026-01-01",
        end: String = "2026-03-31",
        value: Number = 35,
        creator: Account? = null
    ): String =
        createRecord(
            "program_measurement",
            mapOf("program_indicator" to indicator, "period_start_date" to start, "period_end_date" to end, "measured_value" to value),
            token = creator?.token ?: token
        )

    protected fun socialInitiative(
        program: String,
        creator: Account? = null,
        attributes: Map<String, Any?> = emptyMap()
    ): String =
        createRecord(
            "social_initiative",
            mapOf("social_program" to program, "name" to "Iniciativa Ejemplo", "start_date" to "2026-03-01", "end_date" to "2026-12-15") + attributes,
            token = creator?.token ?: token
        )

    // un grupo de beneficiarios del programa y, si se da, de una de sus iniciativas (PROGRAM_MISMATCH, plan 5.2b)
    protected fun beneficiaryGroup(
        program: String,
        initiative: String? = null,
        creator: Account? = null,
        attributes: Map<String, Any?> = emptyMap()
    ): String =
        createRecord(
            "beneficiary_group",
            mapOf(
                "social_program" to program,
                "social_initiative" to initiative,
                "name" to "Niños de 3 a 5 años",
                "target_count" to 200,
                "reached_count" to 150
            ).filterValues { it != null } + attributes,
            token = creator?.token ?: token
        )

    protected fun programMilestone(
        program: String,
        creator: Account? = null,
        attributes: Map<String, Any?> = emptyMap()
    ): String =
        createRecord(
            "program_milestone",
            mapOf("social_program" to program, "name" to "Primera campaña de tamizaje", "planned_date" to "2026-06-30") + attributes,
            token = creator?.token ?: token
        )
}
