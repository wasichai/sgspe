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
// escala con una tarea por nivel, una sola vez y con solo su código en el título (la del nivel 2 de una confidencial, genérica
// y sin vínculo con ella); la consulta respondida deja de medirse; la solicitud no tiene plazo
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
    fun `el nivel 2 de una queja confidencial crea una tarea generica sin vinculo, una sola vez`() {
        val id = grievance(testProject(), attributes = mapOf("received_at" to "2026-01-05T15:00:00Z", "confidential" to true))
        val attributes = record("grievance", id)["attributes"]
        val ack = LocalDate.parse(attributes["ack_due_date"].asString())
        val code = attributes["code"].asString()
        runBlocking { job.runOnce() }
        // el nivel 1 sigue con el id en la clave y el vínculo con la queja
        assertEquals(listOf("escalation:grievance:$id:$ack:1"), escalations("grievance", id).map { it["attributes"]["generator_key"].asString() })
        val key = DeadlineJob.confidentialEscalationKey("grievance", id, ack)
        val found = tree(send("GET", "/api/objects/task/records?generator_key=$key", null, HttpStatus.OK))["content"].values().toList()
        assertEquals(1, found.size, found.toString())
        val task = found.single()["attributes"]
        val name = record("project", testProject())["attributes"]["name"].asString()
        assertEquals("QCS confidencial vencida en $name", task["title"].asString())
        assertEquals(testProject(), task["project"].asString())
        assertTrue(blank(task["grievance"]), task.toString())
        assertFalse(task.toString().contains(id), task.toString())
        assertFalse(task.toString().contains(code), task.toString())
        runBlocking { job.runOnce() }
        assertEquals(1, tree(send("GET", "/api/objects/task/records?generator_key=$key", null, HttpStatus.OK))["content"].values().count())
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
