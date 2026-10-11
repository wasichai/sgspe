package sgspe.investment

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import sgspe.ContributionApiTest
import sgspe.common.DerivedFieldsJob
import sgspe.common.IdempotencyKey

// WF-P (workflows.md, especificación de F5 §3.3 a §3.5, §5): las reglas de InvestmentWriteGuard por la API genérica, por la
// consulta en seco y por el comando de entrega. cada tarea del plan 5.2a suma sus casos al final
class SocialContributionWorkflowApiTest : ContributionApiTest() {
    private fun idOf(response: Response): String = tree(response.body)["id"].asString()

    // decisión 1: después de aprobar los ítems no cambian; un cambio es otro aporte
    @Test
    fun `un item nuevo, cambiado o borrado despues de aprobar es ITEMS_FROZEN`() {
        val id = requestedContribution()
        val item = idOf(itemFor(id, 120))
        move("social_contribution", id, "approve", member("APPROVER").token, "aprobación")
        val added = itemFor(id, 50)
        assertEquals(HttpStatus.CONFLICT, added.status, added.body)
        assertEquals("ITEMS_FROZEN", code(added))
        assertEquals(listOf("social_contribution"), problemFields(added.body))
        assertEquals("ITEMS_FROZEN", code(edit("contribution_item", item, mapOf("line_amount" to 500))))
        val deleted = exchange("DELETE", "/api/objects/contribution_item/records/$item", null, token)
        assertEquals(HttpStatus.CONFLICT, deleted.status, deleted.body)
        assertEquals("ITEMS_FROZEN", code(deleted))
    }

    @Test
    fun `un item no cambia de aporte, y en requested se cambia y se borra`() {
        val id = requestedContribution()
        val other = requestedContribution()
        val item = idOf(itemFor(id, 80))
        assertEquals("ITEMS_FROZEN", code(edit("contribution_item", item, mapOf("social_contribution" to other))))
        assertEquals(HttpStatus.OK, edit("contribution_item", item, mapOf("line_amount" to 90)).status)
        assertTrue(exchange("DELETE", "/api/objects/contribution_item/records/$item", null, token).status.is2xxSuccessful)
    }

    // Review Focus 3 y elección 7: el PUT del portal reenvía lo leído; solo cambiar un término juzgado es CONTRIBUTION_FROZEN
    @Test
    fun `cambiar la moneda o la campana despues de aprobar es CONTRIBUTION_FROZEN y reenviar lo leido pasa`() {
        val project = testProject()
        val id = approvedWithItems(project)
        val currency = edit("social_contribution", id, mapOf("currency" to "USD"), headers = withReason())
        assertEquals(HttpStatus.CONFLICT, currency.status, currency.body)
        assertEquals("CONTRIBUTION_FROZEN", code(currency))
        assertEquals(listOf("currency"), problemFields(currency.body))
        val campaign = edit("social_contribution", id, mapOf("exploration_campaign" to cappedCampaign(project, 1000)), headers = withReason())
        assertEquals("CONTRIBUTION_FROZEN", code(campaign))
        assertEquals(listOf("exploration_campaign"), problemFields(campaign.body))
        assertEquals(HttpStatus.OK, edit("social_contribution", id, emptyMap(), headers = withReason()).status)
        val other = edit("social_contribution", id, mapOf("occasion" to "Día de la comunidad", "beneficiaries_count" to 120), headers = withReason())
        assertEquals(HttpStatus.OK, other.status, other.body)
    }

    @Test
    fun `beneficiaries_count negativo es OUT_OF_RANGE`() {
        val id = requestedContribution()
        val refused = edit("social_contribution", id, mapOf("beneficiaries_count" to -3), headers = withReason())
        assertEquals(HttpStatus.BAD_REQUEST, refused.status, refused.body)
        assertEquals("OUT_OF_RANGE", code(refused))
        assertEquals(listOf("beneficiaries_count"), problemFields(refused.body))
    }

    // ---- Tarea 4: P4, P5, P6, P7 y P9 ----

    @Test
    fun `aprueban APPROVER y SOCIAL_MANAGER, pero no quien registro el aporte`() {
        val project = testProject()
        for (role in listOf("APPROVER", "SOCIAL_MANAGER")) {
            val id = requestedContribution(project)
            move("social_contribution", id, "approve", member(role, project).token, "aprobación")
            assertEquals("approved", state("social_contribution", id), role)
        }
        val manager = member("SOCIAL_MANAGER", project)
        val own = requestedContribution(project, creator = manager)
        val refused = transition("social_contribution", own, "approve", manager.token, "aprobación")
        assertEquals(HttpStatus.FORBIDDEN, refused.status, refused.body)
        assertEquals("SEGREGATION_OF_DUTIES", code(refused))
        assertEquals(listOf("record"), problemFields(refused.body))
        assertEquals(HttpStatus.OK, transition("social_contribution", own, "approve", member("SOCIAL_MANAGER", project).token, "aprobación").status)
    }

    // decisión 13 del plan 5.2a: los dos roles de approve tienen APPROVE, así que P4 se ve en la consulta en seco
    @Test
    fun `sin la accion APPROVE la consulta en seco da ACTION_REQUIRED`() {
        val id = requestedContribution()
        val check = dryRun(id, "approve", member("SOCIAL_INVESTMENT_MANAGER"))
        assertTrue(check["blocking"].values().any { it["code"].asString() == "ACTION_REQUIRED" && it["field"].asString() == "record" }, check.toString())
    }

    @Test
    fun `sin items es ITEMS_REQUIRED y sin contribution_type es FIELD_REQUIRED`() {
        val project = testProject()
        val approver = member("APPROVER", project)
        val empty = requestedContribution(project, items = emptyList())
        val noItems = transition("social_contribution", empty, "approve", approver.token, "aprobación")
        assertEquals(HttpStatus.CONFLICT, noItems.status, noItems.body)
        assertEquals("ITEMS_REQUIRED", code(noItems))
        assertEquals(listOf("contribution_item"), problemFields(noItems.body))
        val untyped = requestedContribution(project, attributes = mapOf("contribution_type" to null))
        val noType = transition("social_contribution", untyped, "approve", approver.token, "aprobación")
        assertEquals(HttpStatus.BAD_REQUEST, noType.status, noType.body)
        assertEquals("FIELD_REQUIRED", code(noType))
        assertEquals(listOf("contribution_type"), problemFields(noType.body))
        assertEquals("requested", state("social_contribution", empty))
    }

    @Test
    fun `approve_direct con la aprobacion interna activa es INTERNAL_APPROVAL_REQUIRED`() {
        val strict = testProject()
        val id = requestedContribution(strict)
        val refused = transition("social_contribution", id, "approve_direct", registrar(strict).token, "aprobación directa")
        assertEquals(HttpStatus.CONFLICT, refused.status, refused.body)
        assertEquals("INTERNAL_APPROVAL_REQUIRED", code(refused))
        assertEquals(listOf("project"), problemFields(refused.body))
        val relaxed = project(mapOf("policy_internal_approval" to false))
        val direct = requestedContribution(relaxed)
        move("social_contribution", direct, "approve_direct", registrar(relaxed).token, "aprobación directa")
        assertEquals("approved", state("social_contribution", direct))
    }

    // elección 9: la sinergia no es un lineamiento, así que la justificación no la cubre
    @Test
    fun `una sinergia exige un acuerdo vigente, tambien con justificacion`() {
        val project = testProject()
        val approver = member("APPROVER", project)

        fun approve(extra: Map<String, Any?>): Response {
            val attributes =
                mapOf("has_institutional_synergy" to true, "guideline_deviation_justification" to "Convenio en trámite con la municipalidad.") + extra
            return transition("social_contribution", requestedContribution(project, attributes = attributes), "approve", approver.token, "aprobación")
        }
        for (extra in listOf(emptyMap(), mapOf("agreement" to agreement(project)))) {
            val refused = approve(extra)
            assertEquals(HttpStatus.CONFLICT, refused.status, refused.body)
            assertEquals("SYNERGY_AGREEMENT_REQUIRED", code(refused))
            assertEquals(listOf("agreement"), problemFields(refused.body))
        }
        val expired = liveAgreement(project).also { platformPatch("agreement", it, mapOf("validity_status" to "VENCIDO")) }
        assertEquals("SYNERGY_AGREEMENT_REQUIRED", code(approve(mapOf("agreement" to expired))))
        assertEquals(HttpStatus.OK, approve(mapOf("agreement" to liveAgreement(project))).status)
    }

    // ---- Tarea 5: P2 y P3 ----

    private fun topic(parent: String? = null): String =
        createRecord(
            "topic",
            mapOf("code" to uniqueName("TEM-"), "name" to "Tema de prueba", "active" to true) + listOfNotNull(parent?.let { "parent_topic" to it })
        )

    // un proyecto con `topic` priorizado (project_priority_topic, RF-INV-002)
    private fun prioritizing(topic: String): String {
        val project = project()
        val linked = linkWithReason("project", project, "project_priority_topic", topic)
        assertEquals(HttpStatus.NO_CONTENT, linked.status, linked.body)
        return project
    }

    @Test
    fun `mano de obra y dinero en una donacion bloquean sin justificacion y pasan con ella`() {
        val project = testProject()
        val approver = member("APPROVER", project)
        val labor = requestedContribution(project, items = listOf(200 to "MANO DE OBRA"))
        val refused = transition("social_contribution", labor, "approve", approver.token, "aprobación")
        assertEquals(HttpStatus.CONFLICT, refused.status, refused.body)
        assertEquals("PAID_LABOR", code(refused))
        assertEquals(listOf("contribution_item"), problemFields(refused.body))
        val money = requestedContribution(project, items = listOf(500 to "DINERO"), attributes = mapOf("contribution_type" to "DONACION"))
        assertEquals("MONETARY_DONATION", code(transition("social_contribution", money, "approve", approver.token, "aprobación")))
        val justified =
            requestedContribution(
                project,
                items = listOf(200 to "MANO DE OBRA"),
                attributes =
                    mapOf("guideline_deviation_justification" to "Faena comunal.")
            )
        val warnings = dryRun(justified, "approve", approver)["warnings"].values().map { it["code"].asString() }
        assertTrue("PAID_LABOR" in warnings, warnings.toString())
        move("social_contribution", justified, "approve", approver.token, "aprobación")
    }

    @Test
    fun `un tema fuera de las prioridades es PRIORITY_NOT_MET y uno que desciende de una prioridad pasa`() {
        val education = topic()
        val project = prioritizing(education)
        val approver = member("APPROVER", project)
        val outside = requestedContribution(project, attributes = mapOf("topic" to topic()))
        val refused = transition("social_contribution", outside, "approve", approver.token, "aprobación")
        assertEquals(HttpStatus.CONFLICT, refused.status, refused.body)
        assertEquals("PRIORITY_NOT_MET", code(refused))
        assertEquals(listOf("topic"), problemFields(refused.body))
        val inside = requestedContribution(project, attributes = mapOf("topic" to topic(parent = topic(parent = education))))
        move("social_contribution", inside, "approve", approver.token, "aprobación")
    }

    @Test
    fun `un proyecto sin temas priorizados solo advierte PRIORITIES_NOT_CONFIGURED`() {
        val id = requestedContribution()
        val approver = member("APPROVER")
        val check = dryRun(id, "approve", approver)
        assertTrue(check["warnings"].values().any { it["code"].asString() == "PRIORITIES_NOT_CONFIGURED" }, check.toString())
        assertTrue(check["allowed"].asBoolean(), check.toString())
        move("social_contribution", id, "approve", approver.token, "aprobación")
    }

    // ---- Tarea 7: P1 ----

    @Test
    fun `con un tope que bloquea approve por APPROVER se rechaza, por SOCIAL_MANAGER pasa y approve_direct se rechaza`() {
        val project = project(mapOf("policy_internal_approval" to false))
        val over = mapOf("exploration_campaign" to cappedCampaign(project, 500))
        val id = requestedContribution(project, items = listOf(400 to "BIEN", 200 to "SERVICIO"), attributes = over)
        val refused = transition("social_contribution", id, "approve", member("APPROVER", project).token, "aprobación")
        assertEquals(HttpStatus.CONFLICT, refused.status, refused.body)
        assertEquals("SUPPORT_CAP_EXCEEDED", code(refused))
        assertEquals(listOf("exploration_campaign"), problemFields(refused.body))
        assertEquals(HttpStatus.OK, transition("social_contribution", id, "approve", member("SOCIAL_MANAGER", project).token, "sobre el tope").status)
        val direct = requestedContribution(project, items = listOf(100 to "BIEN"), attributes = over)
        val refusedDirect = transition("social_contribution", direct, "approve_direct", registrar(project).token, "aprobación directa")
        assertEquals(HttpStatus.CONFLICT, refusedDirect.status, refusedDirect.body)
        assertEquals("SUPPORT_CAP_EXCEEDED", code(refusedDirect))
    }

    @Test
    fun `el tope cuenta un compromiso comprometido y otro aporte aprobado de la misma campana`() {
        val project = testProject()
        val campaign = mapOf("exploration_campaign" to cappedCampaign(project, 1000))
        approvedCommitment(project, attributes = campaign + ("amount" to 400), start = false)
        approvedWithItems(project, items = listOf(300 to "BIEN"), attributes = campaign)
        val approver = member("APPROVER", project)
        val over = requestedContribution(project, items = listOf(400 to "BIEN"), attributes = campaign)
        assertEquals("SUPPORT_CAP_EXCEEDED", code(transition("social_contribution", over, "approve", approver.token, "aprobación")))
        val fits = requestedContribution(project, items = listOf(300 to "BIEN"), attributes = campaign)
        move("social_contribution", fits, "approve", approver.token, "aprobación")
    }

    // Review Focus 1: el monto que se juzga es el de los ítems, aunque el total guardado diga otra cosa
    @Test
    fun `el tope suma los items leidos en la transaccion, no el total guardado`() {
        val project = testProject()
        val id = requestedContribution(project, items = listOf(600 to "BIEN"), attributes = mapOf("exploration_campaign" to cappedCampaign(project, 500)))
        platformPatch("social_contribution", id, mapOf("total_amount" to 0))
        assertEquals("SUPPORT_CAP_EXCEEDED", code(transition("social_contribution", id, "approve", member("APPROVER", project).token, "aprobación")))
    }

    @Test
    fun `con la politica apagada el tope superado es una advertencia y lo aprobado queda escrito`() {
        val project = project(mapOf("policy_cap_blocks_approval" to false))
        val id = requestedContribution(project, items = listOf(600 to "BIEN"), attributes = mapOf("exploration_campaign" to cappedCampaign(project, 500)))
        val approver = member("APPROVER", project)
        val check = dryRun(id, "approve", approver)
        assertTrue(check["warnings"].values().any { it["code"].asString() == "SUPPORT_CAP_EXCEEDED" }, check.toString())
        move("social_contribution", id, "approve", approver.token, "aprobación")
        assertEquals(0, java.math.BigDecimal("600").compareTo(record("social_contribution", id)["attributes"]["total_amount"].decimalValue()))
    }

    // ---- Tarea 9: la entrega del aporte ----

    private fun deliveriesOf(contribution: String): Int =
        tree(send("GET", "/api/objects/delivery/records?social_contribution=$contribution", null, HttpStatus.OK))["totalElements"].asInt()

    @Test
    fun `capturar la entrega fuera de procured es STATE_MISMATCH y en procured no cambia el estado`() {
        val project = testProject()
        val approved = approvedWithItems(project)
        val early = captureDelivery(approved, evidence = deliveryEvidence(project))
        assertEquals(HttpStatus.CONFLICT, early.status, early.body)
        assertEquals("STATE_MISMATCH", code(early))
        assertEquals(listOf("record"), problemFields(early.body))
        assertEquals(0, deliveriesOf(approved))
        val id = procuredWithItems(project)
        val evidence = deliveryEvidence(project)
        val captured = captureDelivery(id, evidence = evidence)
        assertEquals(HttpStatus.CREATED, captured.status, captured.body)
        val body = tree(captured.body)
        assertEquals("procured", body["contribution"]["state"].asString())
        assertEquals(id, body["delivery"]["attributes"]["social_contribution"].asString())
        val delivery = body["delivery"]["id"].asString()
        val linked =
            tree(send("GET", "/api/objects/delivery/records/$delivery/related/evidence_delivery", null, HttpStatus.OK))["content"]
                .values()
                .map { it["id"].asString() }
                .toSet()
        assertEquals(evidence.toSet(), linked)
    }

    @Test
    fun `una segunda entrega es DELIVERY_EXISTS y delivered_amount no se admite`() {
        val id = procuredWithItems()
        val amount = captureDelivery(id, mapOf("delivered_amount" to 100))
        assertEquals(HttpStatus.BAD_REQUEST, amount.status, amount.body)
        assertEquals("FIELD_NOT_ALLOWED", code(amount))
        assertEquals(listOf("attributes.delivered_amount"), problemFields(amount.body))
        assertEquals(HttpStatus.CREATED, captureDelivery(id).status)
        val second = captureDelivery(id)
        assertEquals(HttpStatus.CONFLICT, second.status, second.body)
        assertEquals("DELIVERY_EXISTS", code(second))
        assertEquals(listOf("social_contribution"), problemFields(second.body))
        assertEquals(1, deliveriesOf(id))
    }

    // Review Focus 2: el portal reintenta con la misma clave tras perder la red
    @Test
    fun `un reenvio de la captura con la misma clave devuelve la misma respuesta y deja una sola entrega`() {
        val id = procuredWithItems()
        val headers = ifMatch("social_contribution", id) + withReason("entrega") + (IdempotencyKey.HEADER to uniqueName("en-"))
        val first = captureDelivery(id, headers = headers)
        assertEquals(HttpStatus.CREATED, first.status, first.body)
        val again = captureDelivery(id, headers = headers)
        assertEquals(HttpStatus.CREATED, again.status, again.body)
        assertEquals("true", again.headers.getFirst("Idempotent-Replayed"))
        assertArrayEquals(first.body.toByteArray(), again.body.toByteArray())
        assertEquals(1, deliveriesOf(id))
    }

    @Test
    fun `la captura exige If-Match del aporte`() {
        val id = procuredWithItems()
        assertEquals(HttpStatus.PRECONDITION_REQUIRED, captureDelivery(id, headers = withReason("entrega")).status)
        assertEquals(0, deliveriesOf(id))
    }

    @Test
    fun `la entrega se corrige en procured y queda congelada al anular el aporte`() {
        val project = testProject()
        val id = procuredWithItems(project)
        val foto = evidence(project, "FOTO")
        val delivery = tree(captureDelivery(id, evidence = listOf(foto)).body)["delivery"]["id"].asString()
        val fixed =
            patch(
                "/api/sgspe/deliveries/$delivery",
                mapOf("attributes" to mapOf("place" to "Local comunal")),
                token,
                ifMatch("delivery", delivery) + withReason()
            )
        assertEquals(HttpStatus.OK, fixed.status, fixed.body)
        assertEquals(id, tree(fixed.body)["contribution"]["id"].asString())
        assertEquals("Local comunal", tree(fixed.body)["delivery"]["attributes"]["place"].asString())
        move("social_contribution", id, "cancel_procured", member("APPROVER", project).token, "se anula")
        val frozen =
            patch(
                "/api/sgspe/deliveries/$delivery",
                mapOf("attributes" to mapOf("place" to "Otro local")),
                token,
                ifMatch("delivery", delivery) + withReason()
            )
        assertEquals(HttpStatus.CONFLICT, frozen.status, frozen.body)
        assertEquals("DELIVERY_FROZEN", code(frozen))
        assertEquals(listOf("social_contribution"), problemFields(frozen.body))
        assertEquals("DELIVERY_FROZEN", code(exchange("DELETE", "/api/sgspe/evidence/$foto/links/delivery/$delivery", null, token)))
    }

    // ---- Tarea 10: P8 ----

    @Autowired
    private lateinit var job: DerivedFieldsJob

    private fun linkTo(
        delivery: String,
        evidence: String
    ): Response = exchange("POST", "/api/sgspe/evidence/$evidence/links", mapOf("object" to "delivery", "recordId" to delivery), token)

    @Test
    fun `register_delivery exige la entrega con conformidad`() {
        val project = testProject()
        val id = procuredWithItems(project)
        val none = transition("social_contribution", id, "register_delivery", token, "entregado")
        assertEquals(HttpStatus.CONFLICT, none.status, none.body)
        assertEquals("DELIVERY_REQUIRED", code(none))
        assertEquals(listOf("delivery"), problemFields(none.body))
        val delivery = tree(captureDelivery(id, mapOf("conformity_given" to false), deliveryEvidence(project)).body)["delivery"]["id"].asString()
        assertEquals("DELIVERY_REQUIRED", code(transition("social_contribution", id, "register_delivery", token, "entregado")))
        val given =
            patch(
                "/api/sgspe/deliveries/$delivery",
                mapOf("attributes" to mapOf("conformity_given" to true)),
                token,
                ifMatch("delivery", delivery) + withReason()
            )
        assertEquals(HttpStatus.OK, given.status, given.body)
        move("social_contribution", id, "register_delivery", reason = "entregado")
        assertEquals("delivered", state("social_contribution", id))
    }

    @Test
    fun `register_delivery sin acta de recepcion, sin foto o sin comprobante es EVIDENCE_REQUIRED`() {
        val project = testProject()
        val all = listOf("ACTA DE RECEPCION", "FOTO", "COMPROBANTE")
        for (absent in all) {
            val id = procuredWithItems(project)
            val delivery = tree(captureDelivery(id, evidence = deliveryEvidence(project, all - absent)).body)["delivery"]["id"].asString()
            val refused = transition("social_contribution", id, "register_delivery", token, "entregado")
            assertEquals(HttpStatus.CONFLICT, refused.status, refused.body)
            assertEquals("EVIDENCE_REQUIRED", code(refused), absent)
            assertEquals(listOf("evidence_delivery"), problemFields(refused.body))
            assertEquals(HttpStatus.NO_CONTENT, linkTo(delivery, evidence(project, absent)).status, absent)
            move("social_contribution", id, "register_delivery", reason = "entregado")
        }
    }

    @Test
    fun `la entrega y sus vinculos de evidencia no cambian desde delivered`() {
        val project = testProject()
        val id = procuredWithItems(project)
        val attached = deliveryEvidence(project)
        val delivery = tree(captureDelivery(id, evidence = attached).body)["delivery"]["id"].asString()
        move("social_contribution", id, "register_delivery", reason = "entregado")
        val frozen =
            patch(
                "/api/sgspe/deliveries/$delivery",
                mapOf("attributes" to mapOf("place" to "Otro local")),
                token,
                ifMatch("delivery", delivery) + withReason()
            )
        assertEquals("DELIVERY_FROZEN", code(frozen))
        assertEquals("DELIVERY_FROZEN", code(exchange("DELETE", "/api/sgspe/evidence/${attached.first()}/links/delivery/$delivery", null, token)))
        assertEquals("DELIVERY_FROZEN", code(linkTo(delivery, evidence(project, "FOTO"))))
    }

    // ASM-063 y elección 12: el aporte entregado cuenta como ejecutado de su campaña, con su total_amount
    @Test
    fun `JOB-02 suma el aporte entregado al ejecutado de la campana`() {
        val project = testProject()
        val campaign = cappedCampaign(project, 10_000)
        val id = procuredWithItems(project, attributes = mapOf("exploration_campaign" to campaign))
        assertEquals(HttpStatus.CREATED, captureDelivery(id, evidence = deliveryEvidence(project)).status)
        move("social_contribution", id, "register_delivery", reason = "entregado")
        assertNotNull(runBlocking { job.runOnce(full = true) }, "otro nodo o la programación tenía el candado")
        val executed = record("exploration_campaign", campaign)["attributes"]["executed_social_amount"].decimalValue()
        assertEquals(0, java.math.BigDecimal("300").compareTo(executed))
    }

    // ---- tarea extra del plan 5.2b: el comprobante congelado desde delivered (decisión del usuario del 2026-10-11) ----

    private fun receiptFor(
        contribution: String,
        amount: Any = 300
    ): Response =
        exchange(
            "POST",
            "/api/objects/receipt/records",
            mapOf("attributes" to mapOf("social_contribution" to contribution, "receipt_type" to "BOLETA DE VENTA", "amount" to amount, "currency" to "PEN")),
            token
        )

    @Test
    fun `el comprobante se registra antes de la entrega y desde delivered no se crea, cambia, mueve ni borra`() {
        val project = testProject()
        val id = procuredWithItems(project)
        val receipt = receiptFor(id)
        assertEquals(HttpStatus.CREATED, receipt.status, receipt.body)
        val receiptId = idOf(receipt)
        val other = procuredWithItems(project)
        val loose = receiptFor(other)
        assertEquals(HttpStatus.CREATED, loose.status, loose.body)
        assertEquals(HttpStatus.CREATED, captureDelivery(id, evidence = deliveryEvidence(project)).status)
        move("social_contribution", id, "register_delivery", reason = "entregado")
        val refused =
            listOf(
                receiptFor(id, 50),
                edit("receipt", receiptId, mapOf("amount" to 320)),
                edit("receipt", idOf(loose), mapOf("social_contribution" to id)),
                exchange("DELETE", "/api/objects/receipt/records/$receiptId", null, token)
            )
        for (response in refused) {
            assertEquals(HttpStatus.CONFLICT, response.status, response.body)
            assertEquals("DELIVERY_FROZEN", code(response))
            assertEquals(listOf("social_contribution"), problemFields(response.body))
        }
    }
}
