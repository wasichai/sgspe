package sgspe.privacy

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F3DomainApiTest

// un documento emitido se lee con GET /api/documents/{id} y READ sobre el objeto, sin permisos de campo (UPS-23): la minuta y
// la lista de asistentes que AUT-05 emite al aprobar el acta llegan a un ANALYST, que no lee los datos de los asistentes, sin
// ninguno de ellos (RQ-REP-029, RQ-SEC-010, ASM-090). wasichai-documents no devuelve el nombre técnico del tipo: cada documento
// se reconoce por el prefijo de su número (MR-2026-001), y snapshot.platform.documentName es su etiqueta. AutomationsApiTest
// (plan 3.1) prueba la emisión y la copia que ve el admin
class IssuedDocumentsApiTest : F3DomainApiTest() {
    @Test
    fun `quien lee la interaccion recibe la minuta y la lista sin los datos de los asistentes`() {
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
        participant(id, officer.token, mapOf("participant_name" to "Persona Ejemplo 7", "phone" to "900000007"))
        for (step in listOf("schedule", "register_held", "draft_minutes", "submit_minutes")) move("engagement", id, step, officer.token)
        move("engagement", id, "approve_minutes", member("SOCIAL_MANAGER").token)
        assertEquals("SUCCEEDED", awaitRun("engagement", "aut-05-meeting-minutes", id)["status"].asString())

        val documents = tree(send("GET", "/api/objects/engagement/records/$id/documents", null, HttpStatus.OK)).values()
        assertEquals(setOf("MR", "LA"), documents.map { it["number"].asString().substringBefore("-") }.toSet())
        val analyst = member("ANALYST")
        for (document in documents) {
            val body = send("GET", "/api/documents/${document["id"].asString()}", null, HttpStatus.OK, analyst.token)
            assertFalse(body.contains("Persona Ejemplo 7") || body.contains("900000007"), body)
        }
    }
}
