package sgspe.workflow

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F3ApiTest

// workflows.md, «Pruebas de trabajos, listeners y automatizaciones»: cada automatización de F3 deja una ejecución
// SUCCEEDED con el registro o el documento esperado y su motivo `automation '<nombre>'`, y ninguna escribe un derivado.
// AUT-01 crea la tarea de ejecución de un compromiso aprobado, AUT-10 la de una interacción cancelada y AUT-05 emite la
// minuta y la lista de asistentes de un acta aprobada, sin congelar los datos de los asistentes (security.md §11.4).
// AUT-03, el acta de entrega, espera a UPS-23
class AutomationsApiTest : F3ApiTest() {
    // la tarea que creó una automatización para el registro `origin` del campo `field`
    private fun createdTask(
        field: String,
        origin: String
    ): Map<String, Any?> {
        val tasks = tree(send("GET", "/api/objects/task/records?$field=$origin", null, HttpStatus.OK))["content"].values()
        assertEquals(1, tasks.size, "tareas de $field $origin: $tasks")
        return attributesOf(tasks.single()) + ("id" to tasks.single()["id"].asString())
    }

    private fun reasonOf(
        objectName: String,
        id: String
    ): String? =
        tree(send("GET", "/api/objects/$objectName/records/$id/history", null, HttpStatus.OK))
            .values()
            .first()["reason"]
            ?.asString()

    @Test
    fun `AUT-01 crea la tarea de ejecucion del compromiso aprobado para su responsable`() {
        val manager = member("COMMITMENT_MANAGER")
        val owner = member("COMMUNITY_RELATIONS_OFFICER")
        val id = commitment(testProject(), owner = owner, verifier = member("SOCIAL_MANAGER"), creator = manager)
        move("commitment", id, "submit", manager.token, "matriz completa")
        move("commitment", id, "approve", member("APPROVER").token, "aprobado")

        val run = awaitRun("commitment", "aut-01-commitment-execution-task", id)
        assertEquals("SUCCEEDED", run["status"].asString(), run.toString())
        val code = attributesOf(tree(send("GET", "/api/objects/commitment/records/$id", null, HttpStatus.OK)))["code"]
        val task = createdTask("commitment", id)
        assertEquals("Ejecutar compromiso $code", task["title"])
        assertEquals(testProject(), task["project"])
        assertEquals(owner.id to owner.email, task["assignee_user_id"] to task["assignee_user_email"])
        assertEquals("2026-12-15", task["due_date"])
        assertEquals(null, task["deadline_status"])
        assertEquals("open", state("task", task["id"] as String))
        assertEquals("automation 'aut-01-commitment-execution-task'", reasonOf("task", task["id"] as String))
    }

    @Test
    fun `AUT-10 crea la tarea de reprogramar una interaccion cancelada`() {
        val officer = member("COMMUNITY_RELATIONS_OFFICER")
        val id = engagement(testProject(), token = officer.token, attributes = mapOf("title" to "Taller de riego", "cancel_reason" to "Lluvias intensas"))
        move("engagement", id, "cancel_planned", officer.token, "lluvias intensas")

        assertEquals("SUCCEEDED", awaitRun("engagement", "aut-10-engagement-reschedule-task", id)["status"].asString())
        val task = createdTask("engagement", id)
        assertEquals("Reprogramar o reemplazar la interacción Taller de riego", task["title"])
        assertEquals(testProject(), task["project"])
        assertEquals("automation 'aut-10-engagement-reschedule-task'", reasonOf("task", task["id"] as String))
    }

    @Test
    fun `AUT-05 emite la minuta y la lista de asistentes del acta aprobada sin los datos de los asistentes`() {
        val officer = member("COMMUNITY_RELATIONS_OFFICER")
        val social = member("SOCIAL_MANAGER")
        val id =
            engagement(
                testProject(),
                token = officer.token,
                attributes =
                    mapOf(
                        "title" to "Asamblea de la junta",
                        "scheduled_at" to "2026-10-08T15:00:00Z",
                        "held_at" to "2026-10-08T16:00:00Z",
                        "place" to "Local comunal",
                        "minutes" to "Se acordó mejorar el canal de riego."
                    )
            )
        participant(id, officer.token, mapOf("participant_name" to "Persona Ejemplo 9", "phone" to "900000009", "represented_people_count" to 2))
        for (step in listOf("schedule", "register_held", "draft_minutes", "submit_minutes")) move("engagement", id, step, officer.token)
        move("engagement", id, "approve_minutes", social.token)

        assertEquals("SUCCEEDED", awaitRun("engagement", "aut-05-meeting-minutes", id)["status"].asString())
        val documents = tree(send("GET", "/api/objects/engagement/records/$id/documents", null, HttpStatus.OK)).values()
        assertEquals(setOf("MR", "LA"), documents.map { it["number"].asString().substringBefore("-") }.toSet())
        for (document in documents) {
            assertEquals("VALID", document["status"].asString())
            val snapshot = document["snapshot"]
            assertEquals("Asamblea de la junta", snapshot["values"]["title"].asString())
            // security.md §11.4: nada de los asistentes llega a la copia congelada, ni una tabla relacionada
            assertTrue(snapshot["related"].isEmpty, snapshot["related"].toString())
            assertFalse(document.toString().contains("Persona Ejemplo 9") || document.toString().contains("900000009"), document.toString())
        }
    }

    @Test
    fun `las automatizaciones aplicadas son las de model y ninguna se queda sin correr`() {
        val names =
            listOf("commitment", "engagement").flatMap { objectName ->
                tree(send("GET", "/api/objects/$objectName/automations", null, HttpStatus.OK)).values().map { it["name"].asString() }
            }
        assertEquals(
            setOf("aut-01-commitment-execution-task", "aut-05-meeting-minutes", "aut-10-engagement-reschedule-task"),
            names.toSet()
        )
        val types = tree(send("GET", "/api/objects/engagement/document-types", null, HttpStatus.OK)).values()
        assertEquals(mapOf("meeting-minutes" to "MR", "attendance-list" to "LA"), types.associate { it["name"].asString() to it["prefix"].asString() })
        assertTrue(tree(send("GET", "/api/objects/delivery/document-types", null, HttpStatus.OK)).isEmpty, "el acta de entrega espera a UPS-23")
    }
}
