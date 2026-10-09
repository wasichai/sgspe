package sgspe.privacy

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F3DomainApiTest
import tools.jackson.databind.JsonNode

// IssueDocumentFilter (security.md §11.4, hallazgo I1 de la revisión final del plan 3.1): emitir a mano un documento de una
// interacción exige UPDATE sobre engagement. ANALYST y READ_ONLY leen interacciones pero no las editan: su emisión es un 403
// ACTION_REQUIRED y la minuta que AUT-05 congeló sigue vigente. COMMUNITY_RELATIONS_OFFICER, que edita interacciones, emite
// (y su emisión archiva la anterior, como en wasichai-documents). AUT-05 emite como la plataforma, sin pasar por el filtro
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
}
