package sgspe.deadline

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import sgspe.F3DomainApiTest
import sgspe.common.Records
import tools.jackson.databind.JsonNode
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

// JOB-01 (workflows.md, «Pruebas de trabajos»): deadline_status y validity_status sin mover ningún estado, la tarea de
// escalamiento una sola vez, el motivo y la etiqueta que nombran al trabajo, los avisos de plazo de DeadlineNotices y la
// situación recalculada al reprogramar
class DeadlineJobApiTest : F3DomainApiTest() {
    @Autowired
    private lateinit var job: DeadlineJob

    @Autowired
    private lateinit var notices: DeadlineNotices

    @Autowired
    private lateinit var records: Records

    // las tareas de escalamiento de un compromiso: AUT-01 también crea su tarea de ejecución, fuera de la petición
    private fun escalations(commitment: String): List<JsonNode> =
        tree(send("GET", "/api/objects/task/records?commitment=$commitment", null, HttpStatus.OK))["content"]
            .values()
            .filter { it["attributes"]["generator_key"]?.asString()?.startsWith("escalation:") == true }

    private fun blank(node: JsonNode?): Boolean = node == null || node.isNull || node.isMissingNode

    @Test
    fun `un compromiso vencido queda VENCIDO, sigue en ejecucion, y su escalamiento crea una tarea una sola vez`() {
        val project = testProject()
        val id = approvedCommitment(project, attributes = mapOf("due_date" to "2026-01-05"))
        runBlocking { job.runOnce() }
        val after = record("commitment", id)
        assertEquals("VENCIDO", after["attributes"]["deadline_status"].asString())
        assertEquals("in_progress", after["state"].asString())
        val history = tree(send("GET", "/api/objects/commitment/records/$id/history", null, HttpStatus.OK))
        assertTrue(history.values().any { it["reason"]?.asString() == DeadlineJob.REASON })
        val first = escalations(id)
        assertEquals(
            listOf(1, 2).map { DeadlineJob.escalationKey(id, LocalDate.parse("2026-01-05"), it) },
            first.map { it["attributes"]["generator_key"].asString() }.sorted()
        )
        runBlocking { job.runOnce() }
        assertEquals(first.size, escalations(id).size)
        assertEquals(after["updatedAt"].asString(), record("commitment", id)["updatedAt"].asString())
    }

    @Test
    fun `un compromiso en borrador no tiene plazo y un acuerdo vencido sigue vigente en su estado`() {
        val project = testProject()
        val draft = commitment(project, attributes = mapOf("due_date" to "2026-01-05"))
        val agreement = agreement(project, attributes = mapOf("signed_date" to "2025-01-01", "effective_date" to "2025-01-01", "expiry_date" to "2026-01-01"))
        move("agreement", agreement, "activate_from_draft", reason = "firmado")
        runBlocking { job.runOnce() }
        assertTrue(blank(record("commitment", draft)["attributes"]["deadline_status"]))
        val expired = record("agreement", agreement)
        assertEquals("VENCIDO", expired["attributes"]["validity_status"].asString())
        assertEquals("active", expired["state"].asString())
    }

    @Test
    fun `los avisos de plazo van al responsable y, al escalar, a los roles de verify y de approve_senior del proyecto`() {
        val project = testProject()
        val owner = member("COMMITMENT_MANAGER", project)
        val verifier = member("SOCIAL_MANAGER", project)
        val senior = member("PROJECT_MANAGER", project)
        val id = approvedCommitment(project, owner, mapOf("due_date" to "2026-01-05"))
        runBlocking { job.runOnce() }
        val organization = UUID.fromString(me()["organizationId"].asString())
        val drafts = runBlocking { records.asPlatform(organization) { notices.currentNotifications(organization, Instant.now()) } }
        val mine = drafts.filter { it.key?.startsWith("commitment_overdue:$id:") == true }
        assertEquals(3, mine.size)
        val audience = mine.flatMap { it.audience }.toString()
        for (user in listOf(owner, verifier, senior)) assertTrue(audience.contains(user.id), audience)
    }

    // decisión de la Tarea 17: la reprogramación recalcula deadline_status en su transacción, sin esperar a JOB-01
    @Test
    fun `un compromiso vencido que se reprograma a una fecha futura deja de estar VENCIDO al momento`() {
        val id = approvedCommitment(testProject(), attributes = mapOf("due_date" to "2026-01-05"))
        runBlocking { job.runOnce() }
        assertEquals("VENCIDO", record("commitment", id)["attributes"]["deadline_status"].asString())
        val body = mapOf("attributes" to mapOf("new_due_date" to "2030-01-15", "reschedule_reason" to "Lluvias en la zona", "agreed_with_stakeholder" to true))
        val response = exchange("POST", "/api/sgspe/commitments/$id/reschedules", body, token, ifMatch("commitment", id) + withReason())
        assertEquals(HttpStatus.CREATED, response.status, response.body)
        val stored = record("commitment", id)
        val now = stored["attributes"]
        assertEquals("2030-01-15", now["due_date"].asString())
        assertNotEquals("VENCIDO", now["deadline_status"].asString())
        assertEquals("AL DIA", now["deadline_status"].asString())
        // la respuesta y su ETag son los del compromiso tal como quedó, con la situación recalculada
        val answered = tree(response.body)["commitment"]
        assertEquals("AL DIA", answered["attributes"]["deadline_status"].asString())
        assertEquals("\"${stored["updatedAt"].asString()}\"", response.headers.getFirst(HttpHeaders.ETAG))
    }
}
