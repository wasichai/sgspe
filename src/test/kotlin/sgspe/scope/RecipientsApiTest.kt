package sgspe.scope

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import sgspe.DomainApiTest
import sgspe.common.Records
import java.util.UUID

// security.md §14 «Destinatarios por rol» (ASM-070): role_name es un rol declarado (AssignmentRoleGuard), y los
// destinatarios de una transición son las asignaciones vigentes con un role_name de esa transición, salvo quien la aplicó
class RecipientsApiTest : DomainApiTest() {
    @Autowired
    private lateinit var assignments: Assignments

    @Autowired
    private lateinit var records: Records

    @Test
    fun `un role_name que no es un rol declarado es un 400`() {
        val project = projectWithCrs()
        val user = userWithRole("ANALYST")
        for (role in listOf("JEFE DE CAMPO", "SUPER_ADMIN")) {
            val response =
                exchange(
                    "POST",
                    "/api/objects/project_assignment/records",
                    mapOf(
                        "attributes" to
                            mapOf("project" to project, "member_user_id" to user.id, "member_user_email" to user.email, "role_name" to role, "active" to true)
                    )
                )
            assertEquals(HttpStatus.BAD_REQUEST, response.status, role)
            assertEquals(listOf("role_name"), problemFields(response.body))
        }
    }

    @Test
    fun `los destinatarios salen de la definicion del workflow y de las asignaciones vigentes`() {
        val project = projectWithCrs()
        val approver = member("PROJECT_ADMIN", project)
        val actor = member("PROJECT_ADMIN", project)
        val manager = member("PROJECT_MANAGER", project)
        val inactive = userWithRole("PROJECT_ADMIN")
        assignWith(inactive, project, "PROJECT_ADMIN", mapOf("active" to false))
        val organization = UUID.fromString(me()["organizationId"].asString())
        val approve =
            runBlocking {
                records.asPlatform(organization) { assignments.recipients(UUID.fromString(project), "project", "approve", UUID.fromString(actor.id)) }
            }
        assertEquals(listOf(approver.email), approve.map { it.email })
        val submit =
            runBlocking {
                records.asPlatform(organization) { assignments.recipients(UUID.fromString(project), "project", "submit_for_review", null) }
            }
        assertEquals(setOf(approver.email, actor.email, manager.email), submit.map { it.email }.toSet())
    }
}
