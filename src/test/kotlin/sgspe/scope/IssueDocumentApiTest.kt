package sgspe.scope

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F3DomainApiTest
import tools.jackson.databind.JsonNode

// IssueDocumentFilter (security.md §11.4, hallazgo I1 de la revisión final del plan 3.1, ruling P60): emitir a mano un documento
// de una interacción exige UPDATE sobre engagement y una asignación vigente al proyecto de la interacción. ANALYST y READ_ONLY
// leen interacciones pero no las editan: su emisión es un 403 ACTION_REQUIRED y la minuta que AUT-05 congeló sigue vigente.
// COMMUNITY_RELATIONS_OFFICER, que edita interacciones, emite en su proyecto (y su emisión archiva la anterior, como en
// wasichai-documents) y no en otro: 403 NOT_ASSIGNED. AUT-05 emite como la plataforma, sin pasar por el filtro
class IssueDocumentApiTest : F3DomainApiTest() {
    private fun documents(id: String): List<JsonNode> =
        tree(send("GET", "/api/objects/engagement/records/$id/documents", null, HttpStatus.OK)).values().toList()

    private fun validMinutes(id: String): List<String> =
        documents(id).filter { it["number"].asString().startsWith("MR-") && it["status"].asString() == "VALID" }.map { it["id"].asString() }

    @Test
    fun `emitir a mano exige editar la interaccion y AUT-05 sigue emitiendo`() {
        val officer = member("COMMUNITY_RELATIONS_OFFICER")
        val id =
            engagement(
                testProject(),
                token = officer.token,
                attributes =
                    mapOf(
                        "scheduled_at" to "2026-10-08T15:00:00Z",
                        "held_at" to "2026-10-08T16:00:00Z",
                        "minutes" to "Se acordó mejorar el canal de riego."
                    )
            )
        // register_held exige al menos un participante que asistió (EngagementWriteGuard, ATTENDANCE_REQUIRED)
        participant(id, officer.token)
        for (step in listOf("schedule", "register_held", "draft_minutes", "submit_minutes")) move("engagement", id, step, officer.token)
        move("engagement", id, "approve_minutes", member("SOCIAL_MANAGER").token)
        assertEquals("SUCCEEDED", awaitRun("engagement", "aut-05-meeting-minutes", id)["status"].asString())
        val frozen = validMinutes(id)
        assertEquals(1, frozen.size, documents(id).toString())

        for (role in listOf("ANALYST", "READ_ONLY")) {
            val refused = exchange("POST", "/api/objects/engagement/records/$id/documents/meeting-minutes", null, member(role).token)
            assertEquals(HttpStatus.FORBIDDEN, refused.status, "$role: ${refused.body}")
            assertEquals("ACTION_REQUIRED", code(refused))
            assertEquals(listOf("record"), problemFields(refused.body))
        }
        assertEquals(frozen, validMinutes(id))

        val issued = exchange("POST", "/api/objects/engagement/records/$id/documents/meeting-minutes", null, officer.token)
        assertEquals(HttpStatus.CREATED, issued.status, issued.body)
        assertEquals(listOf(tree(issued.body)["id"].asString()), validMinutes(id))
    }

    @Test
    fun `con UPDATE sobre engagement se emite en el proyecto asignado y no en otro`() {
        val officer = member("COMMUNITY_RELATIONS_OFFICER")
        val other = engagement(project())
        val refused = exchange("POST", "/api/objects/engagement/records/$other/documents/meeting-minutes", null, officer.token)
        assertEquals(HttpStatus.FORBIDDEN, refused.status, refused.body)
        assertEquals("NOT_ASSIGNED", code(refused))
        assertEquals(listOf("project"), problemFields(refused.body))
        assertEquals(emptyList<String>(), validMinutes(other))

        val own = engagement(testProject(), token = officer.token)
        val issued = exchange("POST", "/api/objects/engagement/records/$own/documents/meeting-minutes", null, officer.token)
        assertEquals(HttpStatus.CREATED, issued.status, issued.body)
        assertEquals(listOf(tree(issued.body)["id"].asString()), validMinutes(own))
    }

    // P62: emitir es editar ese registro. FIELD_AGENT edita interacciones (UPDATE) pero sin EDIT_ANY solo las suyas, como en
    // EditAnyGuard: en la ajena de su proyecto, 403 NOT_OWN_RECORD sobre record y la interacción queda sin minuta; en la suya, 201
    @Test
    fun `sin EDIT_ANY se emite en la interaccion propia y no en la ajena`() {
        val agent = member("FIELD_AGENT")
        val foreign = engagement(testProject())
        val refused = exchange("POST", "/api/objects/engagement/records/$foreign/documents/meeting-minutes", null, agent.token)
        assertEquals(HttpStatus.FORBIDDEN, refused.status, refused.body)
        assertEquals("NOT_OWN_RECORD", code(refused))
        assertEquals(listOf("record"), problemFields(refused.body))
        assertEquals(emptyList<String>(), validMinutes(foreign))

        val own = engagement(testProject(), token = agent.token)
        val issued = exchange("POST", "/api/objects/engagement/records/$own/documents/meeting-minutes", null, agent.token)
        assertEquals(HttpStatus.CREATED, issued.status, issued.body)
        assertEquals(listOf(tree(issued.body)["id"].asString()), validMinutes(own))
    }

    // sin token, la cadena de seguridad responde 401 y nada se emite. el orden del filtro (después de esa cadena) lo fijan los 403
    // de las otras pruebas: si corriera antes no vería al usuario, dejaría pasar y wasichai emitiría con 201
    @Test
    fun `sin token la emision es un 401 de la seguridad`() {
        val id = engagement(testProject())
        val anonymous = exchange("POST", "/api/objects/engagement/records/$id/documents/meeting-minutes", null, null)
        assertEquals(HttpStatus.UNAUTHORIZED, anonymous.status, anonymous.body)
        assertEquals(emptyList<String>(), validMinutes(id))
    }
}
