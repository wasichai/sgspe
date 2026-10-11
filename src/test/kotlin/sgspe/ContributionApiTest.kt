package sgspe

import org.junit.jupiter.api.Assertions.assertEquals
import org.springframework.http.HttpStatus
import tools.jackson.databind.JsonNode

// la base de las pruebas de integración del plan 5.2a sobre F5ApiTest del plan 5.1: aportes que ya cumplen las guardas de
// WF-P en todo su ciclo (P9: un ítem BIEN y contribution_type APOYO EN ESPECIE; P5: los registra un
// SOCIAL_INVESTMENT_MANAGER, que no aprueba con approve; P6: sin sinergia; P2 y P3: ni mano de obra ni dinero, y un proyecto
// sin temas priorizados, que solo advierte; P1: sin campaña), la captura de la entrega por su comando con las tres
// evidencias de P8, campañas con tope y acuerdos vigentes. los nombres son propios (decisión 15 del plan 5.2a): no chocan
// con los de F5ApiTest
abstract class ContributionApiTest : F5ApiTest() {
    private val registrars = mutableMapOf<String, Account>()

    // quien registra los aportes de `project`: un SOCIAL_INVESTMENT_MANAGER asignado, el mismo para todo el proyecto
    protected fun registrar(project: String = testProject()): Account = registrars.getOrPut(project) { member("SOCIAL_INVESTMENT_MANAGER", project) }

    protected fun contributionAttributes(project: String): Map<String, Any?> =
        mapOf(
            "project" to project,
            "code" to uniqueName("APO-"),
            "contribution_type" to "APOYO EN ESPECIE",
            "occasion" to "Aniversario de la escuela",
            "declared_purpose" to "Útiles para el aniversario de la escuela.",
            "conditions" to "Se entrega con acta de recepción firmada.",
            "currency" to "PEN",
            "payer_organization" to organization(),
            "has_institutional_synergy" to false
        )

    // un ítem por la API genérica, como `account` (el admin si es null): la respuesta, para las pruebas que esperan un error
    protected fun itemFor(
        contribution: String,
        amount: Any? = 300,
        kind: String = "BIEN",
        account: Account? = null
    ): Response =
        exchange(
            "POST",
            "/api/objects/contribution_item/records",
            mapOf(
                "attributes" to
                    mapOf("social_contribution" to contribution, "description" to "Útiles escolares", "line_amount" to amount, "item_kind" to kind)
            ),
            account?.token ?: token
        )

    // un aporte en requested con sus ítems (monto y tipo), registrado por `creator` (el registrador del proyecto si es null)
    protected fun requestedContribution(
        project: String = testProject(),
        items: List<Pair<Any, String>> = listOf(300 to "BIEN"),
        creator: Account? = null,
        attributes: Map<String, Any?> = emptyMap()
    ): String {
        val account = creator ?: registrar(project)
        val id = createRecord("social_contribution", contributionAttributes(project) + attributes, withReason("alta de prueba"), account.token)
        for ((amount, kind) in items) {
            val created = itemFor(id, amount, kind, account)
            assertEquals(HttpStatus.CREATED, created.status, created.body)
        }
        return id
    }

    // aprobado por un APPROVER asignado, que no lo registró (P5)
    protected fun approvedWithItems(
        project: String = testProject(),
        items: List<Pair<Any, String>> = listOf(300 to "BIEN"),
        attributes: Map<String, Any?> = emptyMap()
    ): String {
        val id = requestedContribution(project, items, attributes = attributes)
        move("social_contribution", id, "approve", member("APPROVER", project).token, "aprobación")
        return id
    }

    // adquirido por su registrador (register_procurement es de SOCIAL_INVESTMENT_MANAGER)
    protected fun procuredWithItems(
        project: String = testProject(),
        attributes: Map<String, Any?> = emptyMap()
    ): String {
        val id = approvedWithItems(project, attributes = attributes)
        move("social_contribution", id, "register_procurement", registrar(project).token, "compra")
        return id
    }

    // las evidencias de P8, del proyecto del aporte, registradas por el admin
    protected fun deliveryEvidence(
        project: String = testProject(),
        types: List<String> = listOf("ACTA DE RECEPCION", "FOTO", "COMPROBANTE")
    ): List<String> = types.map { evidence(project, it) }

    // la captura de la entrega por su comando (POST /api/sgspe/social-contributions/{id}/deliveries), con conformidad y,
    // salvo `headers`, el If-Match del aporte y el motivo
    protected fun captureDelivery(
        contribution: String,
        attributes: Map<String, Any?> = emptyMap(),
        evidence: List<String> = emptyList(),
        headers: Map<String, String>? = null,
        token: String = this.token
    ): Response =
        exchange(
            "POST",
            "/api/sgspe/social-contributions/$contribution/deliveries",
            mapOf(
                "attributes" to
                    mapOf("delivery_date" to "2026-10-01", "conformity_given" to true, "conformity_by" to "Junta directiva de la escuela") + attributes,
                "evidence" to evidence
            ),
            token,
            headers ?: (ifMatch("social_contribution", contribution, token) + withReason("entrega"))
        )

    // entregado: la captura con sus tres evidencias y register_delivery por el admin
    protected fun deliveredContribution(project: String = testProject()): String {
        val id = procuredWithItems(project)
        val captured = captureDelivery(id, evidence = deliveryEvidence(project))
        assertEquals(HttpStatus.CREATED, captured.status, captured.body)
        move("social_contribution", id, "register_delivery", reason = "entregado")
        return id
    }

    protected fun cappedCampaign(
        project: String,
        cap: Int,
        currency: String? = "PEN"
    ): String =
        createRecord(
            "exploration_campaign",
            mapOf(
                "project" to project,
                "name" to "Campaña de prueba",
                "social_support_cap" to cap,
                "currency" to currency
            )
        )

    // un acuerdo en active: el CONVENIO firmado de F3ApiTest, activado por el admin
    protected fun liveAgreement(project: String = testProject()): String {
        val id = agreement(project)
        move("agreement", id, "activate_from_draft", reason = "firmado")
        return id
    }

    // la consulta en seco de una transición de WF-P (POST /api/sgspe/transition-checks)
    protected fun dryRun(
        id: String,
        transition: String,
        account: Account? = null
    ): JsonNode =
        tree(
            send(
                "POST",
                "/api/sgspe/transition-checks",
                mapOf("object" to "social_contribution", "recordId" to id, "transition" to transition),
                HttpStatus.OK,
                account?.token ?: token
            )
        )
}
