package sgspe.attention

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.ContributionApiTest
import sgspe.common.IdempotencyKey
import tools.jackson.databind.JsonNode

// WF-E3 (workflows.md, api.md §4.6, RN-GRV-001, ASM-068): aceptar es responder y crear el compromiso en draft, por su comando;
// la API genérica no acepta ni cierra por el compromiso (COMMAND_ONLY); el cierre del compromiso cierra la solicitud
// (RequestClosureListener, una acción del sistema con su origen y el motivo), también una confidencial que quien cierra no
// lee; close_unfulfilled exige el compromiso cancelado; declinar exige la respuesta; desde F5, aceptar crea también un aporte
// (creates = social_contribution), que cierra la solicitud al cerrarse
class StakeholderRequestWorkflowApiTest : ContributionApiTest() {
    private val manager by lazy { member("SOCIAL_MANAGER") }
    private val grievances by lazy { member("GRIEVANCE_MANAGER") }

    private fun underEvaluation(attributes: Map<String, Any?> = emptyMap()): String {
        val id = stakeholderRequest(testProject(), attributes = attributes)
        move("stakeholder_request", id, "start_evaluation", grievances.token, "evaluación")
        return id
    }

    private fun accept(
        id: String,
        body: Map<String, Any?> =
            mapOf(
                "creates" to "commitment",
                "response" to "Se atenderá con un compromiso de materiales.",
                "respondedAt" to "2026-10-08T15:00:00Z",
                "attributes" to mapOf("code" to uniqueName("COM-"), "title" to "Materiales para el canal de riego") + AMOUNT
            ),
        account: Account = manager
    ): Response =
        exchange(
            "POST",
            "/api/sgspe/stakeholder-requests/$id/accept",
            body,
            account.token,
            ifMatch("stakeholder_request", id, account.token) + withReason("se atiende la solicitud")
        )

    private fun attributes(id: String): JsonNode = record("stakeholder_request", id)["attributes"]

    // el compromiso creado sigue WF-D; para no recorrerlo entero, la solicitud apunta a uno ya aprobado del proyecto. `request`
    // cambia además la solicitud como la plataforma
    private fun linkApprovedCommitment(
        id: String,
        request: Map<String, Any?> = emptyMap()
    ): String {
        val commitment = approvedCommitment(testProject(), member("COMMITMENT_MANAGER"), mapOf("resource_type" to "NO MATERIAL"))
        platformPatch("stakeholder_request", id, mapOf("created_commitment" to commitment) + request)
        return commitment
    }

    // la entrega con conformidad, el cumplimiento y la verificación; `verifier` cierra el compromiso
    private fun closeCommitment(
        commitment: String,
        verifier: Account
    ) {
        val delivery =
            exchange(
                "POST",
                "/api/sgspe/commitments/$commitment/deliveries",
                mapOf(
                    "attributes" to mapOf("delivery_date" to "2026-10-01", "conformity_given" to true, "conformity_by" to "Junta directiva"),
                    "evidence" to listOf(evidence(testProject(), "ACTA"))
                ),
                token,
                withReason()
            )
        assertEquals(HttpStatus.CREATED, delivery.status, delivery.body)
        move("commitment", commitment, "register_fulfillment", reason = "entregado")
        move("commitment", commitment, "verify", verifier.token, "verificado")
        move("commitment", commitment, "close", verifier.token, "cerrado")
    }

    // la entrada del historial del cierre automático: su motivo nombra RN-GRV-001 y el compromiso
    private fun closing(id: String): JsonNode {
        val history = tree(send("GET", "/api/objects/stakeholder_request/records/$id/history", null, HttpStatus.OK))
        return history.values().firstOrNull { it["reason"]?.asString()?.startsWith("RN-GRV-001: compromiso") == true }
            ?: error("sin la entrada del cierre automático: $history")
    }

    @Test
    fun `aceptar responde la solicitud y crea el compromiso en draft`() {
        val id = underEvaluation(mapOf("response" to null, "responded_at" to null))
        val response = accept(id)
        assertEquals(HttpStatus.CREATED, response.status, response.body)
        val body = tree(response.body)
        assertEquals("accepted", body["request"]["state"].asString())
        assertEquals("draft", body["commitment"]["state"].asString())
        assertEquals(body["commitment"]["id"].asString(), body["request"]["attributes"]["created_commitment"].asString())
        assertEquals(testProject(), body["commitment"]["attributes"]["project"].asString())
        assertEquals("Se atenderá con un compromiso de materiales.", attributes(id)["response"].asString())
    }

    @Test
    fun `un destino que no es compromiso ni aporte, una solicitud fuera de evaluacion o sin respuesta no se aceptan`() {
        val id = underEvaluation(mapOf("response" to null, "responded_at" to null))
        val other = accept(id, mapOf("creates" to "agreement", "response" to "x", "respondedAt" to "2026-10-08T15:00:00Z"))
        assertEquals(HttpStatus.BAD_REQUEST, other.status, other.body)
        assertEquals("INVALID_TARGET", code(other))
        assertEquals(listOf("creates"), problemFields(other.body))
        val silent = accept(id, mapOf("creates" to "commitment", "attributes" to mapOf("code" to uniqueName("COM-"), "title" to "Sin respuesta") + AMOUNT))
        assertEquals(HttpStatus.BAD_REQUEST, silent.status, silent.body)
        assertEquals("FIELD_REQUIRED", code(silent))
        assertEquals("under_evaluation", state("stakeholder_request", id))
        assertTrue(attributes(id)["created_commitment"].let { it == null || it.isNull })
        val received = stakeholderRequest(testProject())
        assertEquals("STATE_MISMATCH", code(accept(received)))
        val generic = transition("stakeholder_request", id, "accept", manager.token, "aceptada")
        assertEquals(HttpStatus.FORBIDDEN, generic.status, generic.body)
        assertEquals("COMMAND_ONLY", code(generic))
    }

    @Test
    fun `cerrar el compromiso creado cierra la solicitud, como accion del sistema y con el motivo`() {
        val id = underEvaluation()
        assertEquals(HttpStatus.CREATED, accept(id).status)
        closeCommitment(linkApprovedCommitment(id), member("COMMITMENT_MANAGER"))
        assertEquals("closed", state("stakeholder_request", id))
        assertEquals(RequestClosureListener.SOURCE, closing(id)["source"].asString())
        // por la API genérica, una solicitud aceptada no se cierra por el compromiso
        val other = underEvaluation()
        assertEquals(HttpStatus.CREATED, accept(other).status)
        val generic = transition("stakeholder_request", other, "close_accepted", manager.token, "cierre")
        assertEquals(HttpStatus.FORBIDDEN, generic.status, generic.body)
        assertEquals("COMMAND_ONLY", code(generic))
    }

    // decisión 4 del plan 4.2a: el cierre automático no depende de que quien cierra el compromiso lea la solicitud
    @Test
    fun `una solicitud confidencial se cierra con su compromiso aunque quien lo cierra no la lea`() {
        val id = stakeholderRequest(testProject(), member("COMMUNITY_RELATIONS_OFFICER"))
        move("stakeholder_request", id, "start_evaluation", grievances.token, "evaluación")
        assertEquals(HttpStatus.CREATED, accept(id).status)
        val commitment = linkApprovedCommitment(id, mapOf("confidential" to true))
        val verifier = member("COMMITMENT_MANAGER")
        val path = "/api/objects/stakeholder_request/records/$id"
        // quien cierra no tiene READ_CONFIDENTIAL ni es su responsable ni quien la recibió: no la ve
        assertEquals(HttpStatus.NOT_FOUND, exchange("GET", path, null, verifier.token).status)
        closeCommitment(commitment, verifier)
        assertEquals("closed", state("stakeholder_request", id))
        assertEquals(RequestClosureListener.SOURCE, closing(id)["source"].asString())
        // la liberación duró solo el cierre: después sigue sin verla
        assertEquals(HttpStatus.NOT_FOUND, exchange("GET", path, null, verifier.token).status)
    }

    // la liberación abre solo las solicitudes del compromiso que se cierra: otra confidencial aceptada, con otro compromiso,
    // sigue en accepted y oculta para quien cerró el primero
    @Test
    fun `cerrar un compromiso no toca ni muestra la solicitud confidencial de otro`() {
        val officer = member("COMMUNITY_RELATIONS_OFFICER")
        val id = stakeholderRequest(testProject(), officer)
        val other = stakeholderRequest(testProject(), officer)
        for (request in listOf(id, other)) {
            move("stakeholder_request", request, "start_evaluation", grievances.token, "evaluación")
            assertEquals(HttpStatus.CREATED, accept(request).status)
        }
        val commitment = linkApprovedCommitment(id, mapOf("confidential" to true))
        linkApprovedCommitment(other, mapOf("confidential" to true))
        val verifier = member("COMMITMENT_MANAGER")
        val path = "/api/objects/stakeholder_request/records/$other"
        assertEquals(HttpStatus.NOT_FOUND, exchange("GET", path, null, verifier.token).status)
        closeCommitment(commitment, verifier)
        assertEquals("closed", state("stakeholder_request", id))
        assertEquals("accepted", state("stakeholder_request", other))
        assertEquals(HttpStatus.NOT_FOUND, exchange("GET", path, null, verifier.token).status)
    }

    // un respondedAt mal formado es un 400 sobre la propiedad del cuerpo, no sobre responded_at del modelo
    @Test
    fun `un respondedAt mal formado responde sobre respondedAt`() {
        val id = underEvaluation(mapOf("response" to null, "responded_at" to null))
        val response =
            accept(
                id,
                mapOf(
                    "creates" to "commitment",
                    "response" to "Se atenderá con un compromiso de materiales.",
                    "respondedAt" to "ayer por la tarde",
                    "attributes" to mapOf("code" to uniqueName("COM-"), "title" to "Materiales para el canal de riego") + AMOUNT
                )
            )
        assertEquals(HttpStatus.BAD_REQUEST, response.status, response.body)
        assertEquals(listOf("respondedAt"), problemFields(response.body))
        assertEquals("under_evaluation", state("stakeholder_request", id))
    }

    // un reenvío con la misma Idempotency-Key, con las mismas cabeceras (también el If-Match ya vencido), devuelve la
    // respuesta guardada: la solicitud no se acepta dos veces ni nace otro compromiso
    @Test
    fun `un reenvio con la misma clave devuelve la misma respuesta y no acepta dos veces`() {
        val id = underEvaluation()
        val headers = ifMatch("stakeholder_request", id, manager.token) + withReason("se atiende la solicitud") + (IdempotencyKey.HEADER to uniqueName("ac-"))
        val body =
            mapOf(
                "creates" to "commitment",
                "response" to "Se atenderá con un compromiso de materiales.",
                "respondedAt" to "2026-10-08T15:00:00Z",
                "attributes" to mapOf("code" to uniqueName("COM-"), "title" to "Materiales para el canal de riego") + AMOUNT
            )
        val path = "/api/sgspe/stakeholder-requests/$id/accept"
        val first = exchange("POST", path, body, manager.token, headers)
        assertEquals(HttpStatus.CREATED, first.status, first.body)
        val again = exchange("POST", path, body, manager.token, headers)
        assertEquals(HttpStatus.CREATED, again.status, again.body)
        assertEquals("true", again.headers.getFirst("Idempotent-Replayed"))
        assertArrayEquals(first.body.toByteArray(), again.body.toByteArray())
        assertEquals(tree(first.body)["commitment"]["id"].asString(), attributes(id)["created_commitment"].asString())
    }

    // created_commitment solo lo escribe la aceptación (AttentionCommandFieldsGuard): el cierre del compromiso y
    // close_unfulfilled confían en él. por la API genérica, reapuntarlo a otro compromiso o vaciarlo es 403 COMMAND_ONLY sobre
    // el campo; reenviar la solicitud tal como se leyó pasa
    @Test
    fun `created_commitment lo escribe solo la aceptacion`() {
        val id = underEvaluation()
        val accepted = accept(id)
        assertEquals(HttpStatus.CREATED, accepted.status, accepted.body)
        val created = tree(accepted.body)["commitment"]["id"].asString()
        val other = approvedCommitment(testProject(), member("COMMITMENT_MANAGER"), mapOf("resource_type" to "NO MATERIAL"))
        for (value in listOf(other, null)) {
            val refused = edit("stakeholder_request", id, mapOf("created_commitment" to value), token, withReason())
            assertEquals(HttpStatus.FORBIDDEN, refused.status, refused.body)
            assertEquals("COMMAND_ONLY", code(refused))
            assertEquals(listOf("created_commitment"), problemFields(refused.body))
        }
        assertEquals(HttpStatus.OK, edit("stakeholder_request", id, emptyMap(), token, withReason()).status)
        assertEquals(created, attributes(id)["created_commitment"].asString())
    }

    @Test
    fun `close_unfulfilled exige el compromiso cancelado`() {
        val id = underEvaluation()
        val commitment = tree(accept(id).body)["commitment"]["id"].asString()
        val refused = transition("stakeholder_request", id, "close_unfulfilled", grievances.token, "sin ejecución")
        assertEquals(HttpStatus.CONFLICT, refused.status, refused.body)
        assertEquals("STATE_MISMATCH", code(refused))
        assertEquals(listOf("created_commitment"), problemFields(refused.body))
        move("commitment", commitment, "cancel_draft", reason = "no se ejecutará")
        move("stakeholder_request", id, "close_unfulfilled", grievances.token, "sin ejecución")
    }

    @Test
    fun `declinar exige la respuesta`() {
        val id = underEvaluation(mapOf("response" to null))
        val response = transition("stakeholder_request", id, "decline", grievances.token, "no procede")
        assertEquals(HttpStatus.BAD_REQUEST, response.status, response.body)
        assertEquals(listOf("response"), problemFields(response.body))
    }

    // ---- F5: la aceptación como aporte (§3.7 de la especificación de F5) ----

    private fun acceptAsContribution(
        id: String,
        account: Account
    ): Response =
        accept(
            id,
            mapOf(
                "creates" to "social_contribution",
                "response" to "Se atenderá con un aporte de útiles escolares.",
                "respondedAt" to "2026-10-08T15:00:00Z",
                "attributes" to contributionAttributes(testProject()) - "project"
            ),
            account
        )

    @Test
    fun `aceptar como aporte crea el aporte en requested con created_contribution`() {
        val id = underEvaluation()
        val response = acceptAsContribution(id, member("SOCIAL_INVESTMENT_MANAGER"))
        assertEquals(HttpStatus.CREATED, response.status, response.body)
        val body = tree(response.body)
        val contribution = body["contribution"]["id"].asString()
        assertEquals("accepted", body["request"]["state"].asString())
        assertEquals("requested", body["contribution"]["state"].asString())
        assertEquals(contribution, body["request"]["attributes"]["created_contribution"].asString())
        assertEquals(testProject(), body["contribution"]["attributes"]["project"].asString())
        assertTrue(response.headers.getFirst("Location")!!.endsWith("/api/objects/social_contribution/records/$contribution"))
    }

    // §2.2: COMMITMENT_MANAGER tiene el rol de accept pero no CREATE sobre social_contribution
    @Test
    fun `COMMITMENT_MANAGER no acepta una solicitud como aporte`() {
        val id = underEvaluation()
        val refused = acceptAsContribution(id, member("COMMITMENT_MANAGER"))
        assertEquals(HttpStatus.FORBIDDEN, refused.status, refused.body)
        assertEquals("under_evaluation", state("stakeholder_request", id))
    }

    @Test
    fun `created_contribution lo escribe solo la aceptacion`() {
        val id = underEvaluation()
        val refused = edit("stakeholder_request", id, mapOf("created_contribution" to requestedContribution()), token, withReason())
        assertEquals(HttpStatus.FORBIDDEN, refused.status, refused.body)
        assertEquals("COMMAND_ONLY", code(refused))
        assertEquals(listOf("created_contribution"), problemFields(refused.body))
    }

    @Test
    fun `close_unfulfilled con el aporte rechazado pasa y con el aporte aprobado es STATE_MISMATCH`() {
        val investment = member("SOCIAL_INVESTMENT_MANAGER")
        val rejected = underEvaluation()
        val first = tree(acceptAsContribution(rejected, investment).body)["contribution"]["id"].asString()
        move("social_contribution", first, "reject", member("APPROVER").token, "no procede")
        move("stakeholder_request", rejected, "close_unfulfilled", grievances.token, "sin ejecución")
        val approved = underEvaluation()
        val second = tree(acceptAsContribution(approved, investment).body)["contribution"]["id"].asString()
        assertEquals(HttpStatus.CREATED, itemFor(second).status)
        move("social_contribution", second, "approve", member("APPROVER").token, "aprobación")
        val refused = transition("stakeholder_request", approved, "close_unfulfilled", grievances.token, "sin ejecución")
        assertEquals(HttpStatus.CONFLICT, refused.status, refused.body)
        assertEquals("STATE_MISMATCH", code(refused))
        assertEquals(listOf("created_contribution"), problemFields(refused.body))
    }

    // ---- F5: el cierre del aporte cierra la solicitud (RN-GRV-001, §3.7 de la especificación de F5) ----

    // lleva el aporte creado al aceptar hasta closed: un ítem, la aprobación de un APPROVER, la adquisición, la entrega con
    // sus tres evidencias (P8), register_delivery y close por `closer`
    private fun closeContribution(
        contribution: String,
        closer: Account
    ) {
        assertEquals(HttpStatus.CREATED, itemFor(contribution).status)
        move("social_contribution", contribution, "approve", member("APPROVER").token, "aprobación")
        move("social_contribution", contribution, "register_procurement", reason = "compra")
        val captured = captureDelivery(contribution, evidence = deliveryEvidence())
        assertEquals(HttpStatus.CREATED, captured.status, captured.body)
        move("social_contribution", contribution, "register_delivery", reason = "entregado")
        move("social_contribution", contribution, "close", closer.token, "cerrado")
    }

    private fun closingByContribution(id: String): JsonNode {
        val history = tree(send("GET", "/api/objects/stakeholder_request/records/$id/history", null, HttpStatus.OK))
        return history.values().firstOrNull { it["reason"]?.asString()?.startsWith("RN-GRV-001: aporte") == true }
            ?: error("sin la entrada del cierre automático por el aporte: $history")
    }

    @Test
    fun `cerrar el aporte creado cierra la solicitud, como accion del sistema y con el motivo`() {
        val id = underEvaluation()
        val contribution = tree(acceptAsContribution(id, member("SOCIAL_INVESTMENT_MANAGER")).body)["contribution"]["id"].asString()
        closeContribution(contribution, member("SOCIAL_MANAGER"))
        assertEquals("closed", state("social_contribution", contribution))
        assertEquals("closed", state("stakeholder_request", id))
        assertEquals(RequestClosureListener.SOURCE, closingByContribution(id)["source"].asString())
    }

    // decisión 4 del plan 4.2a con el aporte: el cierre automático no depende de que quien cierra el aporte lea la solicitud
    @Test
    fun `una solicitud confidencial se cierra con su aporte aunque quien lo cierra no la lea`() {
        val id = stakeholderRequest(testProject(), member("COMMUNITY_RELATIONS_OFFICER"))
        move("stakeholder_request", id, "start_evaluation", grievances.token, "evaluación")
        val contribution = tree(acceptAsContribution(id, member("SOCIAL_INVESTMENT_MANAGER")).body)["contribution"]["id"].asString()
        platformPatch("stakeholder_request", id, mapOf("confidential" to true))
        val closer = member("SOCIAL_INVESTMENT_MANAGER")
        val path = "/api/objects/stakeholder_request/records/$id"
        assertEquals(HttpStatus.NOT_FOUND, exchange("GET", path, null, closer.token).status)
        closeContribution(contribution, closer)
        assertEquals("closed", state("stakeholder_request", id))
        assertEquals(HttpStatus.NOT_FOUND, exchange("GET", path, null, closer.token).status)
    }

    companion object {
        // el monto y la moneda que model.json exige a todo compromiso (como F3ApiTest)
        private val AMOUNT = mapOf("amount" to 15000, "currency" to "PEN")
    }
}
