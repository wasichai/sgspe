package sgspe.stakeholder

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import sgspe.F3DomainApiTest

// JOB-03 desde F3 (workflows.md): la tarea de inactividad de un grupo de interés sin contacto, asignada a su responsable, una
// sola vez aunque el trabajo corra otra vez
class FollowUpTasksApiTest : F3DomainApiTest() {
    @Autowired
    private lateinit var job: StakeholderFollowUpJob

    @Test
    fun `un grupo de interes sin contacto mas alla de inactivity_days recibe una tarea, una sola vez`() {
        val project = project(mapOf("inactivity_days" to 0))
        val owner = member("STAKEHOLDER_MANAGER", project)
        val id = stakeholder(project, profileFields(owner))
        runBlocking { job.runOnce() }
        val tasks = tree(send("GET", "/api/objects/task/records?stakeholder=$id", null, HttpStatus.OK))
        assertEquals(1, tasks["totalElements"].asInt())
        assertEquals(owner.id, tasks["content"][0]["attributes"]["assignee_user_id"].asString())
        runBlocking { job.runOnce() }
        assertEquals(1, tree(send("GET", "/api/objects/task/records?stakeholder=$id", null, HttpStatus.OK))["totalElements"].asInt())
    }
}
