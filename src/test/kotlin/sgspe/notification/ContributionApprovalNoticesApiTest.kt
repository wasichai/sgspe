package sgspe.notification

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.ContributionApiTest

// el aviso «aprobación pendiente» de un aporte (§3.8 de la especificación de F5): llega al alta a los asignados con un rol de
// approve (y de approve_direct si el proyecto no tiene aprobación interna), nunca a quien lo registró, y se resuelve al salir
// de requested
class ContributionApprovalNoticesApiTest : ContributionApiTest() {
    private fun pendingFor(
        token: String,
        id: String
    ): Boolean =
        tree(send("GET", "/api/auth/me/notifications?state=active", null, HttpStatus.OK, token))["content"].values().any {
            it["link"]?.get("recordId")?.asString() == id && it["kind"].asString() == "ACTION"
        }

    @Test
    fun `registrar un aporte avisa a quienes lo aprueban y aprobarlo lo resuelve`() {
        val project = testProject()
        val approver = member("APPROVER", project)
        val manager = member("SOCIAL_MANAGER", project)
        val direct = member("SOCIAL_INVESTMENT_MANAGER", project)
        val id = requestedContribution(project)
        assertTrue(pendingFor(approver.token, id))
        assertTrue(pendingFor(manager.token, id))
        assertFalse(pendingFor(direct.token, id), "con aprobación interna, approve_direct no aplica")
        assertFalse(pendingFor(registrar(project).token, id), "quien lo registra no recibe su aviso")
        move("social_contribution", id, "approve", approver.token, "aprobación")
        assertFalse(pendingFor(manager.token, id))
    }

    @Test
    fun `sin aprobacion interna avisa tambien a los roles de approve_direct`() {
        val project = project(mapOf("policy_internal_approval" to false))
        val direct = member("SOCIAL_INVESTMENT_MANAGER", project)
        val id = requestedContribution(project)
        assertTrue(pendingFor(direct.token, id))
        move("social_contribution", id, "reject", member("APPROVER", project).token, "no procede")
        assertFalse(pendingFor(direct.token, id))
    }
}
