package sgspe

import org.junit.jupiter.api.Assertions.assertEquals
import org.springframework.http.HttpStatus

// la base de las pruebas de integración del plan 5.2b, sobre F5ApiTest (plan 5.1), que ya trae los programas y sus hijos: aquí
// solo lo que esa base no da. acuerdos en active con sus partes para G3, y aportes de un solo ítem con monto y moneda conocidos
// llevados por WF-P hasta aprobados o entregados con sus tres evidencias (P8) por el endpoint de 5.2a, como el portal; los
// tableros los suman. los nombres no chocan con los de F5ApiTest (decisión 12 del plan 5.2b)
abstract class F5ProgramApiTest : F5ApiTest() {
    // un proyecto sin aprobación interna: plan_direct (G4) y approve_direct (P7) pasan
    protected fun projectWithoutInternalApproval(attributes: Map<String, Any?> = emptyMap()): String =
        project(mapOf("policy_internal_approval" to false) + attributes)

    // un acuerdo en active (AgreementWriteGuard: el CONVENIO firmado y vigente de F3ApiTest.agreement) con `parties` como sus
    // partes, cargadas antes de activarlo
    protected fun activeAgreementWith(
        project: String,
        parties: List<String> = emptyList()
    ): String {
        val id = agreement(project)
        for (party in parties) createRecord("agreement_party", mapOf("agreement" to id, "organization" to party, "signed" to true))
        move("agreement", id, "activate_from_draft", reason = "vigencia de prueba")
        return id
    }

    // un aporte en requested con un solo ítem BIEN de `lineAmount` en `currency` y 30 beneficiarios (F5ApiTest.socialContribution
    // sin ítems y F5ApiTest.contributionItem), registrado por `creator`
    protected fun registerContribution(
        project: String,
        creator: Account,
        currency: String = "PEN",
        lineAmount: Number = 2000,
        attributes: Map<String, Any?> = emptyMap()
    ): String {
        val id = socialContribution(project, creator, mapOf("currency" to currency, "beneficiaries_count" to 30) + attributes, items = 0)
        contributionItem(id, creator, mapOf("item_kind" to "BIEN", "line_amount" to lineAmount))
        return id
    }

    // un aporte aprobado sin revisión interna (approve_direct, P7: el proyecto de `investor` no tiene aprobación interna)
    protected fun registerApprovedContribution(
        project: String,
        investor: Account,
        currency: String = "PEN",
        lineAmount: Number = 2000,
        attributes: Map<String, Any?> = emptyMap()
    ): String {
        val id = registerContribution(project, investor, currency, lineAmount, attributes)
        move("social_contribution", id, "approve_direct", investor.token, "aprobación de prueba")
        return id
    }

    // un aporte entregado: adquirido, con su entrega capturada en `date` por el endpoint de 5.2a con conformidad, acta de
    // recepción, foto y comprobante del proyecto (P8) y register_delivery aplicado. el comando exige el If-Match del aporte
    // (IfMatch.required en ContributionDeliveries)
    protected fun registerDeliveredContribution(
        project: String,
        investor: Account,
        date: String = "2026-10-10",
        currency: String = "PEN",
        lineAmount: Number = 2000,
        attributes: Map<String, Any?> = emptyMap()
    ): String {
        val id = registerApprovedContribution(project, investor, currency, lineAmount, attributes)
        move("social_contribution", id, "register_procurement", investor.token, "compra de prueba")
        val evidence = listOf("ACTA DE RECEPCION", "FOTO", "COMPROBANTE").map { evidence(project, it, account = investor) }
        val captured =
            exchange(
                "POST",
                "/api/sgspe/social-contributions/$id/deliveries",
                mapOf(
                    "attributes" to mapOf("delivery_date" to date, "conformity_given" to true, "conformity_by" to "Persona Ejemplo"),
                    "evidence" to evidence
                ),
                investor.token,
                ifMatch("social_contribution", id, investor.token) + withReason("entrega de prueba")
            )
        assertEquals(HttpStatus.CREATED, captured.status, captured.body)
        move("social_contribution", id, "register_delivery", investor.token, "entrega registrada")
        return id
    }
}
