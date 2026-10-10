package sgspe.deadline

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import sgspe.F4ApiTest
import tools.jackson.databind.JsonNode
import java.time.LocalDate

// JOB-01 con las QCS (workflows.md «Plazos y escalamiento», ASM-100): la queja vencida queda VENCIDO sin cambiar de estado y
// escala con una tarea por nivel, una sola vez y con solo su código en el título (las de una confidencial, de los dos niveles,
// genéricas y sin vínculo con ella, D-1); la consulta respondida deja de medirse; la solicitud no tiene plazo
class DeadlineJobQcsApiTest : F4ApiTest() {
    @Autowired
    private lateinit var job: DeadlineJob

    private fun escalations(
        objectName: String,
        id: String
    ): List<JsonNode> =
        tree(send("GET", "/api/objects/task/records?$objectName=$id", null, HttpStatus.OK))["content"]
            .values()
            .filter { it["attributes"]["generator_key"]?.asString()?.startsWith("escalation:") == true }

    private fun blank(node: JsonNode?): Boolean = node == null || node.isNull || node.isMissingNode

    @Test
    fun `una queja sin acuse desde enero queda VENCIDO y escala una sola vez`() {
        val id = grievance(testProject(), attributes = mapOf("received_at" to "2026-01-05T15:00:00Z", "code" to uniqueName("QJ-")))
        val ack = record("grievance", id)["attributes"]["ack_due_date"].asString()
        runBlocking { job.runOnce() }
        val after = record("grievance", id)
        assertEquals("VENCIDO", after["attributes"]["deadline_status"].asString())
        assertEquals("received", after["state"].asString())
        val first = escalations("grievance", id)
        assertEquals(listOf(1, 2).map { "escalation:grievance:$id:$ack:$it" }, first.map { it["attributes"]["generator_key"].asString() }.sorted())
        val code = after["attributes"]["code"].asString()
        assertTrue(first.all { it["attributes"]["title"].asString().endsWith("queja $code vencida") }, first.toString())
        runBlocking { job.runOnce() }
        assertEquals(2, escalations("grievance", id).size)
    }

    @Test
    fun `los niveles 1 y 2 de una queja confidencial crean tareas genericas sin vinculo, una sola vez`() {
        val id = grievance(testProject(), attributes = mapOf("received_at" to "2026-01-05T15:00:00Z", "confidential" to true))
        val attributes = record("grievance", id)["attributes"]
        val ack = LocalDate.parse(attributes["ack_due_date"].asString())
        val code = attributes["code"].asString()
        runBlocking { job.runOnce() }
        // ningún nivel lleva el id en la clave ni el vínculo con la queja (D-1)
        assertEquals(emptyList<JsonNode>(), escalations("grievance", id))
        val name = record("project", testProject())["attributes"]["name"].asString()
        val found =
            (1..2).map { level ->
                val key = DeadlineJob.confidentialEscalationKey("grievance", id, ack, level)
                val tasks = tree(send("GET", "/api/objects/task/records?generator_key=$key", null, HttpStatus.OK))["content"].values().toList()
                assertEquals(1, tasks.size, tasks.toString())
                val task = tasks.single()["attributes"]
                assertEquals("QCS confidencial vencida en $name", task["title"].asString())
                assertTrue(task["description"].asString().contains("nivel $level"), task.toString())
                assertEquals(testProject(), task["project"].asString())
                assertTrue(blank(task["grievance"]), task.toString())
                assertFalse(task.toString().contains(id), task.toString())
                assertFalse(task.toString().contains(code), task.toString())
                tasks.single()
            }
        runBlocking { job.runOnce() }
        for (level in 1..2) {
            val key = DeadlineJob.confidentialEscalationKey("grievance", id, ack, level)
            assertEquals(1, tree(send("GET", "/api/objects/task/records?generator_key=$key", null, HttpStatus.OK))["content"].values().count())
        }
        // una persona que no lee la queja trabaja la tarea genérica: se la asigna (ExactlyOneGuard la admite sin origen)
        val manager = member("PROJECT_MANAGER")
        val taskId = found.last()["id"].asString()
        val assigned = edit("task", taskId, mapOf("assignee_user_id" to manager.id, "assignee_user_email" to manager.email), manager.token)
        assertEquals(HttpStatus.OK, assigned.status, assigned.body)
        // y nadie puede crear una tarea sin origen con una clave de esa forma: la clave la fija solo la plataforma
        val forged =
            exchange(
                "POST",
                "/api/objects/task/records",
                mapOf(
                    "attributes" to
                        mapOf(
                            "project" to testProject(),
                            "title" to "Otra",
                            "generator_key" to DeadlineJob.confidentialEscalationKey("grievance", id, ack.plusDays(1), 1)
                        )
                ),
                manager.token
            )
        assertEquals(HttpStatus.FORBIDDEN, forged.status, forged.body)
        assertEquals("GENERATOR_KEY_PLATFORM_ONLY", code(forged))
    }

    @Test
    fun `una consulta respondida deja de medirse y una solicitud no tiene plazo`() {
        val inquiry = inquiry(testProject(), attributes = mapOf("received_at" to "2026-01-05T15:00:00Z"))
        runBlocking { job.runOnce() }
        assertEquals("VENCIDO", record("inquiry", inquiry)["attributes"]["deadline_status"].asString())
        move("inquiry", inquiry, "answer", member("GRIEVANCE_MANAGER").token, "respuesta")
        runBlocking { job.runOnce() }
        assertTrue(blank(record("inquiry", inquiry)["attributes"]["deadline_status"]))
        val request = stakeholderRequest(testProject(), attributes = mapOf("received_at" to "2026-01-05T15:00:00Z"))
        runBlocking { job.runOnce() }
        assertTrue(blank(record("stakeholder_request", request)["attributes"]["deadline_status"]))
    }
}
