package sgspe.stakeholder

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import sgspe.DomainApiTest

// JOB-03 (workflows.md §Pruebas «Trabajos»): last_contact_date y el primer contacto, sin mover WF-B; el motivo nombra al
// trabajo (RQ-AUD-007); repetir la corrida no escribe nada (ni updatedAt ni historia). sgspe.jobs.enabled=false en las
// dos tareas de Gradle: la programación no corre durante las pruebas, que llaman a runOnce
class StakeholderFollowUpJobApiTest : DomainApiTest() {
    @Autowired
    private lateinit var job: StakeholderFollowUpJob

    private fun history(stakeholder: String) = tree(send("GET", "/api/objects/stakeholder/records/$stakeholder/history", null, HttpStatus.OK))

    @Test
    fun `una interaccion realizada da el ultimo contacto y el primer contacto, y la segunda corrida no escribe`() {
        val project = projectWithCrs()
        val linked = stakeholder(project, mapOf("relationship_status" to "SIN CONTACTO"))
        val attendee = stakeholder(project)
        val invited = stakeholder(project)
        val engagement = engagement(project, "REUNION", attributes = mapOf("held_at" to "2026-05-12T15:00:00Z"))
        link("engagement", engagement, "engagement_stakeholder", linked)
        participant(engagement, attributes = mapOf("stakeholder" to attendee))
        participant(engagement, attributes = mapOf("stakeholder" to invited, "attended" to false))
        move("engagement", engagement, "register_held_unplanned")
        val first = runBlocking { job.runOnce() }
        assertNotNull(first, "otro nodo o la programación tenía el candado")
        assertTrue(first!! >= 2)
        val linkedNow = record("stakeholder", linked)
        assertEquals("2026-05-12", linkedNow["attributes"]["last_contact_date"].asString())
        assertEquals("CONTACTO ESTABLECIDO", linkedNow["attributes"]["relationship_status"].asString())
        assertEquals("identified", linkedNow["state"].asString())
        assertEquals("2026-05-12", record("stakeholder", attendee)["attributes"]["last_contact_date"].asString())
        assertTrue(record("stakeholder", invited)["attributes"]["last_contact_date"].isNull)
        // la escritura del trabajo nombra al trabajo (RQ-AUD-007, P16)
        val reason = history(linked).values().first()["reason"].asString()
        assertEquals(StakeholderFollowUpJob.REASON, reason)
        assertTrue(reason.endsWith("(JOB-03 StakeholderFollowUpJob)"), reason)
        // la segunda corrida no escribe (P11): ni historia ni updatedAt
        val before = history(linked).size()
        val updatedAt = linkedNow["updatedAt"].asString()
        val attendeeUpdatedAt = record("stakeholder", attendee)["updatedAt"].asString()
        runBlocking { job.runOnce() }
        assertEquals(before, history(linked).size())
        assertEquals(updatedAt, record("stakeholder", linked)["updatedAt"].asString())
        assertEquals(attendeeUpdatedAt, record("stakeholder", attendee)["updatedAt"].asString())
    }

    @Test
    fun `una interaccion que aun no se realizo no da contacto, y el contacto mas reciente gana`() {
        val project = projectWithCrs()
        val contact = stakeholder(project, mapOf("relationship_status" to "SIN CONTACTO"))
        val planned = engagement(project, "REUNION")
        link("engagement", planned, "engagement_stakeholder", contact)
        val older = engagement(project, "REUNION", attributes = mapOf("held_at" to "2026-04-02T15:00:00Z"))
        link("engagement", older, "engagement_stakeholder", contact)
        val newer = engagement(project, "REUNION", attributes = mapOf("held_at" to "2026-05-12T15:00:00Z"))
        link("engagement", newer, "engagement_stakeholder", contact)
        participant(older, attributes = mapOf("stakeholder" to contact))
        participant(newer, attributes = mapOf("stakeholder" to contact))
        move("engagement", older, "register_held_unplanned")
        move("engagement", newer, "register_held_unplanned")
        assertNotNull(runBlocking { job.runOnce() })
        assertEquals("2026-05-12", record("stakeholder", contact)["attributes"]["last_contact_date"].asString())
        assertEquals("planned", state("engagement", planned))
    }
}
