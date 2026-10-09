package sgspe.notification

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F3DomainApiTest

// el aviso «aprobación pendiente» en la bandeja de wasichai-notifications (decisión D2, workflows.md «Destinatarios por rol»,
// ASM-070): llega a los asignados vigentes con un rol de la aprobación que sigue, nunca a quien envió, a un asignado inactivo ni
// a otro rol, y se resuelve cuando el compromiso sale de pending_approval
class ApprovalNoticesApiTest : F3DomainApiTest() {
    private fun inbox(token: String) = tree(send("GET", "/api/auth/me/notifications?state=active", null, HttpStatus.OK, token))["content"].values()

    private fun pendingFor(
        token: String,
        id: String
    ): Boolean =
        inbox(token).any {
            it["link"]?.get("recordId")?.asString() == id && it["kind"].asString() == "ACTION"
        }

    @Test
    fun `enviar un compromiso avisa a los aprobadores asignados y aprobarlo lo resuelve`() {
        val project = testProject()
        val approver = member("APPROVER", project)
        val senior = member("PROJECT_MANAGER", project)
        val other = member("GIS_ANALYST", project)
        val inactive = userWithRole("APPROVER").also { assignWith(it, project, "APPROVER", mapOf("active" to false)) }
        val manager = member("COMMITMENT_MANAGER", project)
        val id = commitment(project, creator = manager)
        move("commitment", id, "submit", manager.token, "envío")
        assertTrue(pendingFor(approver.token, id))
        assertTrue(pendingFor(senior.token, id))
        assertFalse(pendingFor(other.token, id))
        assertFalse(pendingFor(inactive.token, id))
        assertFalse(pendingFor(manager.token, id))
        move("commitment", id, "approve", approver.token, "aprobado")
        assertFalse(pendingFor(approver.token, id))
    }

    @Test
    fun `un compromiso que exige nivel superior avisa solo a los roles de approve_senior`() {
        val project = testProject()
        val approver = member("APPROVER", project)
        val senior = member("PROJECT_MANAGER", project)
        val id = commitment(project, attributes = mapOf("origin_engagement" to engagement(project, "MESA DE DIALOGO")))
        move("commitment", id, "submit", reason = "envío")
        assertTrue(pendingFor(senior.token, id))
        assertFalse(pendingFor(approver.token, id))
    }
}
