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
import wasichai.notifications.Audience
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

    // los avisos de plazo vigentes de la organización cuya clave empieza con `prefix`, con los usuarios exactos de cada uno
    private fun audiences(prefix: String): Map<String, Set<UUID>> {
        val organization = organizationId()
        val drafts = runBlocking { records.asPlatform(organization) { notices.currentNotifications(organization, Instant.now()) } }
        return drafts
            .filter { it.key?.startsWith(prefix) == true }
            .associate { draft -> draft.key!! to draft.audience.map { (it as Audience.User).id }.toSet() }
    }

    private fun users(vararg accounts: Account): Set<UUID> = accounts.map { UUID.fromString(it.id) }.toSet()

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
        // nivel 0 al responsable; nivel 1 a los roles de verify (el responsable también lo tiene); nivel 2 a los de approve_senior
        assertEquals(
            mapOf(
                "commitment_overdue:$id:2026-01-05:0" to users(owner),
                "commitment_overdue:$id:2026-01-05:1" to users(owner, verifier),
                "commitment_overdue:$id:2026-01-05:2" to users(senior)
            ),
            audiences("commitment_overdue:$id:")
        )
    }

    @Test
    fun `una tarea vencida queda VENCIDO y avisa al asignado y, al escalar, a los roles de su registro vinculado`() {
        val project = testProject()
        val assignee = member("COMMITMENT_MANAGER", project)
        val social = member("SOCIAL_MANAGER", project)
        val senior = member("PROJECT_MANAGER", project)
        val id =
            task(project, attributes = mapOf("due_date" to "2026-01-05", "assignee_user_id" to assignee.id, "assignee_user_email" to assignee.email))
        runBlocking { job.runOnce() }
        val after = record("task", id)
        assertEquals("VENCIDO", after["attributes"]["deadline_status"].asString())
        assertEquals("open", after["state"].asString())
        // la tarea cuelga de una interacción: su nivel 1 son los roles de approve_minutes
        assertEquals(
            mapOf(
                "task_overdue:$id:2026-01-05:0" to users(assignee),
                "task_overdue:$id:2026-01-05:1" to users(social),
                "task_overdue:$id:2026-01-05:2" to users(senior)
            ),
            audiences("task_overdue:$id:")
        )
    }

    // fuera de los estados de su tabla el campo queda vacío: VENCIDO siempre significa «abierto y atrasado»
    @Test
    fun `JOB-01 vacia deadline_status cuando el compromiso pasa a fulfilled y deja de avisar`() {
        val project = testProject()
        val id = approvedCommitment(project, attributes = mapOf("resource_type" to "NO MATERIAL", "due_date" to "2026-01-05"))
        runBlocking { job.runOnce() }
        assertEquals("VENCIDO", record("commitment", id)["attributes"]["deadline_status"].asString())
        val delivery =
            exchange(
                "POST",
                "/api/sgspe/commitments/$id/deliveries",
                mapOf(
                    "attributes" to mapOf("delivery_date" to "2026-10-01", "conformity_given" to true, "conformity_by" to "Junta directiva"),
                    "evidence" to listOf(evidence(project, "CARTA"))
                ),
                token,
                withReason()
            )
        assertEquals(HttpStatus.CREATED, delivery.status, delivery.body)
        move("commitment", id, "register_fulfillment", reason = "entregado")
        runBlocking { job.runOnce() }
        val fulfilled = record("commitment", id)
        assertEquals("fulfilled", fulfilled["state"].asString())
        assertTrue(blank(fulfilled["attributes"]["deadline_status"]))
        assertEquals(emptyMap<String, Set<UUID>>(), audiences("commitment_overdue:$id:"))
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
