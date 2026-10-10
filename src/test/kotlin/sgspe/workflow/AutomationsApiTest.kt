package sgspe.workflow

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F4ApiTest

// workflows.md, «Pruebas de trabajos, listeners y automatizaciones»: cada automatización de F3 y F4 deja una ejecución
// SUCCEEDED con el registro o el documento esperado y su motivo `automation '<nombre>'`, y ninguna escribe un derivado.
// AUT-01 crea la tarea de ejecución de un compromiso aprobado, AUT-10 la de una interacción cancelada y AUT-05 emite la
// minuta y la lista de asistentes de un acta aprobada, sin congelar los datos de los asistentes (security.md §11.4).
// desde F4, AUT-02 crea la tarea de acuse de una queja, AUT-09 la de triaje de una alerta y AUT-11 la de un riesgo que
// pasa a CRITICO. AUT-03, el acta de entrega, y AUT-06 a AUT-08, el cargo de queja y las cartas de respuesta, esperan a
// UPS-23
class AutomationsApiTest : F4ApiTest() {
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

    // los estados de las ejecuciones terminadas de `name` para el registro, de la más nueva a la más vieja, cuando son al
    // menos `count`: una que no cumple sus condiciones queda SKIPPED, y también cuenta
    private fun awaitRuns(
        objectName: String,
        name: String,
        recordId: String,
        count: Int,
        seconds: Long = 30
    ): List<String> {
        val deadline = System.nanoTime() + seconds * 1_000_000_000
        while (System.nanoTime() < deadline) {
            val statuses =
                tree(send("GET", "/api/objects/$objectName/automations/$name/runs?limit=50", null, HttpStatus.OK))
                    .values()
                    .filter { it["recordId"]?.asString() == recordId }
                    .map { it["status"].asString() }
            if (statuses.size >= count && statuses.none { it == "PENDING" || it == "RUNNING" }) return statuses
            Thread.sleep(250)
        }
        throw AssertionError("$name no llegó a $count ejecuciones terminadas en $seconds s para $objectName $recordId")
    }

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
        val minutes = documents.single { it["number"].asString().startsWith("MR-") }
        assertTrue(minutes.toString().contains("attendees_total_count"), minutes.toString())
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
    fun `AUT-02 crea la tarea de acuse de la queja recibida, sin responsable ni fecha`() {
        val officer = member("COMMUNITY_RELATIONS_OFFICER")
        val id = grievance(testProject(), officer)

        assertEquals("SUCCEEDED", awaitRun("grievance", "aut-02-grievance-ack-task", id)["status"].asString())
        val code = attributesOf(record("grievance", id))["code"]
        val task = createdTask("grievance", id)
        assertEquals("Acusar recibo de la queja $code", task["title"])
        assertEquals(testProject(), task["project"])
        // el plazo vive en ack_due_date de la queja (workflows.md, AUT-02): la tarea no lleva responsable ni fecha
        assertEquals(null to null, task["assignee_user_id"] to task["due_date"])
        assertEquals("open", state("task", task["id"] as String))
        assertEquals("automation 'aut-02-grievance-ack-task'", reasonOf("task", task["id"] as String))
    }

    @Test
    fun `AUT-09 crea la tarea de triaje de la alerta que registra una persona`() {
        val manager = member("SOCIAL_RISK_MANAGER")
        val id = earlyWarning(testProject(), manager, mapOf("title" to "Movilización anunciada"))

        assertEquals("SUCCEEDED", awaitRun("early_warning", "aut-09-warning-triage-task", id)["status"].asString())
        val task = createdTask("early_warning", id)
        assertEquals("Triaje de la alerta Movilización anunciada", task["title"])
        assertEquals(testProject(), task["project"])
        assertEquals("automation 'aut-09-warning-triage-task'", reasonOf("task", task["id"] as String))
    }

    @Test
    fun `AUT-11 crea una sola tarea cuando el riesgo pasa a CRITICO, para su responsable`() {
        val owner = member("SOCIAL_RISK_MANAGER")
        val id = socialRisk(testProject(), owner = owner)
        // risk_level es derivado: en 4.2b lo escribe el endpoint de revisiones; aquí, la plataforma con la marca. pasar a
        // ALTO no la dispara (EQUALS CRITICO), pasar a CRITICO sí, y otra escritura que lo deja en CRITICO no (CHANGED)
        platformPatch("social_risk", id, mapOf("risk_level" to "ALTO", "score" to 12))
        assertEquals(listOf("SKIPPED"), awaitRuns("social_risk", "aut-11-critical-risk-task", id, 1))
        platformPatch("social_risk", id, mapOf("risk_level" to "CRITICO", "score" to 20))
        assertEquals(listOf("SUCCEEDED", "SKIPPED"), awaitRuns("social_risk", "aut-11-critical-risk-task", id, 2))
        platformPatch("social_risk", id, mapOf("score" to 25))
        assertEquals(listOf("SKIPPED", "SUCCEEDED", "SKIPPED"), awaitRuns("social_risk", "aut-11-critical-risk-task", id, 3))

        val code = attributesOf(record("social_risk", id))["code"]
        val task = createdTask("social_risk", id)
        assertEquals("Revisar el riesgo crítico $code", task["title"])
        assertEquals(owner.id to owner.email, task["assignee_user_id"] to task["assignee_user_email"])
        assertEquals("automation 'aut-11-critical-risk-task'", reasonOf("task", task["id"] as String))
    }

    @Test
    fun `las automatizaciones aplicadas son las de model y ninguna se queda sin correr`() {
        val names =
            listOf("commitment", "engagement", "grievance", "early_warning", "social_risk").flatMap { objectName ->
                tree(send("GET", "/api/objects/$objectName/automations", null, HttpStatus.OK)).values().map { it["name"].asString() }
            }
        assertEquals(
            setOf(
                "aut-01-commitment-execution-task",
                "aut-05-meeting-minutes",
                "aut-10-engagement-reschedule-task",
                "aut-02-grievance-ack-task",
                "aut-09-warning-triage-task",
                "aut-11-critical-risk-task"
            ),
            names.toSet()
        )
        val types = tree(send("GET", "/api/objects/engagement/document-types", null, HttpStatus.OK)).values()
        assertEquals(mapOf("meeting-minutes" to "MR", "attendance-list" to "LA"), types.associate { it["name"].asString() to it["prefix"].asString() })
        assertTrue(tree(send("GET", "/api/objects/delivery/document-types", null, HttpStatus.OK)).isEmpty, "el acta de entrega espera a UPS-23")
        // el cargo de queja y las cartas de respuesta esperan a UPS-23 (decisión 3 de F4): la QCS restringe su persona
        for (objectName in listOf("grievance", "inquiry", "stakeholder_request")) {
            assertTrue(tree(send("GET", "/api/objects/$objectName/document-types", null, HttpStatus.OK)).isEmpty, objectName)
        }
    }
}
