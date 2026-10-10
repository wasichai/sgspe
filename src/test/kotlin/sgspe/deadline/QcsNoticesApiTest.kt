package sgspe.deadline

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import sgspe.F4ApiTest
import sgspe.common.Records
import wasichai.notifications.Audience
import wasichai.notifications.NotificationDraft
import java.time.Instant
import java.util.UUID

// los avisos de plazo de las QCS como fuente de wasichai-notifications (ASM-102): qcs_deadline por nivel con sus destinatarios
// y, en una QCS confidencial, solo a quien la vería, con el aviso genérico del nivel 2 para los roles de approve_senior que no
// la ven: sin código, sin enlace y con una clave sin el id
class QcsNoticesApiTest : F4ApiTest() {
    @Autowired
    private lateinit var job: DeadlineJob

    @Autowired
    private lateinit var notices: DeadlineNotices

    @Autowired
    private lateinit var records: Records

    private fun drafts(): List<NotificationDraft> {
        val organization = organizationId()
        return runBlocking { records.asPlatform(organization) { notices.currentNotifications(organization, Instant.now()) } }
    }

    private fun users(draft: NotificationDraft): Set<UUID> = draft.audience.map { (it as Audience.User).id }.toSet()

    private fun ids(vararg accounts: Account): Set<UUID> = accounts.map { UUID.fromString(it.id) }.toSet()

    @Test
    fun `una queja vencida avisa al responsable, a verify y a approve_senior`() {
        val owner = member("GRIEVANCE_MANAGER")
        val social = member("SOCIAL_MANAGER")
        val senior = member("PROJECT_MANAGER")
        val id =
            grievance(
                testProject(),
                attributes =
                    mapOf(
                        "received_at" to "2026-01-05T15:00:00Z",
                        "owner_user_id" to owner.id,
                        "owner_user_email" to owner.email
                    )
            )
        runBlocking { job.runOnce() }
        val mine = drafts().filter { it.key?.startsWith("qcs_deadline:grievance:$id:") == true }.associateBy { it.key!!.substringAfterLast(":") }
        assertEquals(setOf("0", "1", "2"), mine.keys)
        assertEquals(ids(owner), users(mine.getValue("0")))
        assertTrue(users(mine.getValue("1")).containsAll(ids(owner, social)))
        assertEquals(ids(senior), users(mine.getValue("2")))
    }

    @Test
    fun `una queja confidencial solo avisa a quien la veria y el nivel 2 generico no lleva datos`() {
        val receiver = member("COMMUNITY_RELATIONS_OFFICER")
        val other = member("COMMUNITY_RELATIONS_OFFICER")
        val manager = member("GRIEVANCE_MANAGER")
        val social = member("SOCIAL_MANAGER")
        val senior = member("PROJECT_MANAGER")
        val id = grievance(testProject(), receiver, mapOf("received_at" to "2026-01-05T15:00:00Z", "confidential" to true))
        val code = record("grievance", id)["attributes"]["code"].asString()
        runBlocking { job.runOnce() }
        val all = drafts()
        val mine = all.filter { it.key?.startsWith("qcs_deadline:grievance:$id:") == true }.associateBy { it.key!!.substringAfterLast(":") }
        // sin responsable, el nivel 0 va a los roles de acknowledge que la verían: quien la recibió y el gestor de quejas, no
        // otro relacionista
        assertEquals(ids(receiver, manager), users(mine.getValue("0")))
        assertFalse(UUID.fromString(other.id) in users(mine.getValue("0")))
        assertEquals(ids(manager), users(mine.getValue("1")))
        assertFalse(users(mine.getValue("1")).containsAll(ids(social)))
        assertNull(mine["2"])
        val generic = all.filter { it.key?.startsWith("qcs_confidential_overdue:${testProject()}:") == true }
        assertEquals(1, generic.size, all.map { it.key }.toString())
        assertEquals(ids(senior), users(generic.single()))
        assertNull(generic.single().link)
        assertFalse(generic.single().key!!.contains(id))
        assertFalse((generic.single().title + generic.single().body).contains(code))
    }

    @Test
    fun `una tarea vencida vinculada a una queja confidencial solo avisa a quien la veria`() {
        val receiver = member("COMMUNITY_RELATIONS_OFFICER")
        val manager = member("GRIEVANCE_MANAGER")
        val social = member("SOCIAL_MANAGER")
        val grievance = grievance(testProject(), receiver, mapOf("confidential" to true))
        val task =
            createRecord(
                "task",
                mapOf(
                    "project" to testProject(),
                    "title" to "Visitar al reclamante",
                    "grievance" to grievance,
                    "due_date" to "2026-01-05"
                )
            )
        runBlocking { job.runOnce() }
        val mine = drafts().filter { it.key?.startsWith("task_overdue:$task:") == true }.associateBy { it.key!!.substringAfterLast(":") }
        // sin asignado, el nivel 1 va a los roles de verify de WF-E1 que la verían: el gestor de quejas, no la gerencia social
        assertEquals(ids(manager), users(mine.getValue("1")))
        assertFalse(UUID.fromString(social.id) in users(mine.getValue("1")))
    }
}
