package sgspe.attention

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F4ApiTest
import sgspe.common.IdempotencyKey

// la reclasificación de una QCS (RF-GRV-007, ASM-072, api.md §4.6): crea el registro del tipo nuevo con los campos comunes y la
// fecha de recepción del primero de la cadena, lo vincula y cierra el original con su transición de comando y el motivo;
// honra Idempotency-Key; la API genérica no aplica close_reclassified_* (COMMAND_ONLY)
class ReclassificationApiTest : F4ApiTest() {
    private val manager by lazy { member("GRIEVANCE_MANAGER") }

    private fun reclassify(
        path: String,
        objectName: String,
        id: String,
        target: String,
        account: Account = manager,
        attributes: Map<String, Any?> = mapOf("code" to uniqueName("RC-")),
        headers: Map<String, String> = emptyMap()
    ): Response =
        exchange(
            "POST",
            "/api/sgspe/$path/$id/reclassify",
            mapOf("targetType" to target, "attributes" to attributes),
            account.token,
            ifMatch(objectName, id, account.token) + withReason("era una consulta") + headers
        )

    @Test
    fun `una queja reclasificada como consulta conserva la recepcion y cierra el original`() {
        val officer = member("COMMUNITY_RELATIONS_OFFICER")
        val id = grievance(testProject(), officer, mapOf("received_at" to "2026-10-05T15:00:00Z"))
        move("grievance", id, "acknowledge", manager.token, "acuse")
        val response = reclassify("grievances", "grievance", id, "inquiry")
        assertEquals(HttpStatus.CREATED, response.status, response.body)
        val created = tree(response.body)["created"]
        assertEquals(id, created["attributes"]["reclassified_from_grievance"].asString())
        assertEquals(officer.id, created["attributes"]["received_by_user_id"].asString())
        assertTrue(created["attributes"]["received_at"].asString().startsWith("2026-10-05T15:00"))
        // los plazos de la consulta corren desde la recepción original
        assertEquals("2026-10-20", record("inquiry", created["id"].asString())["attributes"]["response_due_date"].asString())
        assertEquals("closed", tree(response.body)["original"]["state"].asString())
        val history = tree(send("GET", "/api/objects/grievance/records/$id/history", null, HttpStatus.OK))
        assertTrue(history.values().any { it["reason"]?.asString() == "era una consulta" })
    }

    @Test
    fun `el mismo tipo es INVALID_TARGET y un estado sin reclasificacion es STATE_MISMATCH`() {
        val id = inquiry(testProject())
        val same = reclassify("inquiries", "inquiry", id, "inquiry")
        assertEquals(HttpStatus.BAD_REQUEST, same.status, same.body)
        assertEquals("INVALID_TARGET", code(same))
        assertEquals(listOf("targetType"), problemFields(same.body))
        move("inquiry", id, "answer", manager.token, "respuesta")
        val answered = reclassify("inquiries", "inquiry", id, "grievance")
        assertEquals(HttpStatus.CONFLICT, answered.status, answered.body)
        assertEquals("STATE_MISMATCH", code(answered))
    }

    @Test
    fun `solo el rol de la transicion reclasifica y la API generica no cierra por reclasificacion`() {
        val id = stakeholderRequest(testProject())
        val officer = member("COMMUNITY_RELATIONS_OFFICER")
        val refused = reclassify("stakeholder-requests", "stakeholder_request", id, "grievance", officer)
        assertEquals(HttpStatus.FORBIDDEN, refused.status, refused.body)
        assertEquals("ROLE_REQUIRED", code(refused))
        val generic = transition("stakeholder_request", id, "close_reclassified_received", manager.token, "cierre")
        assertEquals(HttpStatus.FORBIDDEN, generic.status, generic.body)
        assertEquals("COMMAND_ONLY", code(generic))
        assertEquals("received", state("stakeholder_request", id))
    }

    @Test
    fun `un reenvio con la misma clave devuelve la misma respuesta y no reclasifica dos veces`() {
        val id = grievance(testProject())
        val key = mapOf(IdempotencyKey.HEADER to uniqueName("rc-"))
        val attributes = mapOf("code" to uniqueName("RC-"))
        val first = reclassify("grievances", "grievance", id, "stakeholder_request", attributes = attributes, headers = key)
        assertEquals(HttpStatus.CREATED, first.status, first.body)
        val again =
            exchange(
                "POST",
                "/api/sgspe/grievances/$id/reclassify",
                mapOf("targetType" to "stakeholder_request", "attributes" to attributes),
                manager.token,
                key + withReason("era una consulta") + mapOf("If-Match" to first.headers.getFirst("ETag").orEmpty())
            )
        assertEquals("true", again.headers.getFirst("Idempotent-Replayed"))
        assertArrayEquals(first.body.toByteArray(), again.body.toByteArray())
    }

    // el receptor de la QCS nueva es el del original, copiado por el sistema (AttentionRules.receiverCopied): quien
    // reclasifica no tiene que ser quien la recibió, y `attributes` no lo cambia. los roles de close_reclassified_* tienen hoy
    // READ_CONFIDENTIAL; la exención la prueba sin base AttentionRulesTest
    @Test
    fun `al reclasificar una QCS confidencial el receptor se conserva y no se cambia`() {
        val officer = member("COMMUNITY_RELATIONS_OFFICER")
        val id =
            grievance(
                testProject(),
                officer,
                mapOf("confidential" to true, "owner_user_id" to manager.id, "owner_user_email" to manager.email)
            )
        val chosen =
            reclassify(
                "grievances",
                "grievance",
                id,
                "stakeholder_request",
                attributes = mapOf("code" to uniqueName("RC-"), "received_by_user_id" to manager.id)
            )
        assertEquals(HttpStatus.BAD_REQUEST, chosen.status, chosen.body)
        assertEquals(listOf("attributes.received_by_user_id"), problemFields(chosen.body))
        assertEquals("received", state("grievance", id))
        val response = reclassify("grievances", "grievance", id, "stakeholder_request")
        assertEquals(HttpStatus.CREATED, response.status, response.body)
        val created = tree(response.body)["created"]["attributes"]
        assertEquals(true, created["confidential"].asBoolean())
        assertEquals(officer.id, created["received_by_user_id"].asString())
        assertEquals(manager.id, created["owner_user_id"].asString())
    }

    // por la API genérica, un reclassified_from_* no exime: el alta confidencial con un receptor ajeno sigue en 400
    @Test
    fun `por la API generica un vinculo de reclasificacion no exime del receptor`() {
        val officer = member("COMMUNITY_RELATIONS_OFFICER")
        val other = member("COMMUNITY_RELATIONS_OFFICER")
        val original = grievance(testProject())
        val response =
            exchange(
                "POST",
                "/api/objects/inquiry/records",
                mapOf(
                    "attributes" to
                        attentionAttributes(testProject(), other, "CO-") +
                        mapOf("confidential" to true, "reclassified_from_grievance" to original)
                ),
                officer.token,
                withReason("alta de prueba")
            )
        assertEquals(HttpStatus.BAD_REQUEST, response.status, response.body)
        assertEquals("CONFIDENTIAL_RECEIVER_REQUIRED", code(response))
        assertEquals(listOf("received_by_user_id"), problemFields(response.body))
    }
}
