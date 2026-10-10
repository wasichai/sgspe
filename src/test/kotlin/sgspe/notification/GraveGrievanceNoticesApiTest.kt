package sgspe.notification

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F4ApiTest

// el aviso de una queja grave en la bandeja (decisión 8 de F4, ASM-102): al entrar en in_action una queja ALTA o CRITICA lo
// reciben los asignados con un rol de resolve_high_severity, también de una confidencial, y se resuelve al resolverla; con la
// política apagada no hay aviso
class GraveGrievanceNoticesApiTest : F4ApiTest() {
    private fun pendingFor(
        account: Account,
        id: String
    ): Boolean =
        tree(send("GET", "/api/auth/me/notifications?state=active", null, HttpStatus.OK, account.token))["content"]
            .values()
            .any { it["link"]?.get("recordId")?.asString() == id && it["kind"].asString() == "ACTION" }

    private fun inAction(
        project: String,
        manager: Account,
        attributes: Map<String, Any?>,
        creator: Account? = null
    ): String {
        val id = grievance(project, creator, attributes)
        for (step in listOf("acknowledge", "assess", "start_action")) move("grievance", id, step, manager.token, step)
        return id
    }

    @Test
    fun `una queja grave en atencion avisa al aprobador y resolverla lo resuelve`() {
        val manager = member("GRIEVANCE_MANAGER")
        val approver = member("APPROVER")
        val social = member("SOCIAL_MANAGER")
        val id = inAction(testProject(), manager, mapOf("severity" to "CRITICA"))
        assertTrue(pendingFor(approver, id))
        assertFalse(pendingFor(social, id))
        assertFalse(pendingFor(manager, id))
        move("grievance", id, "resolve_high_severity", approver.token, "resolución de la queja grave")
        assertFalse(pendingFor(approver, id))
    }

    @Test
    fun `una queja confidencial grave tambien avisa al aprobador, y con la politica apagada no hay aviso`() {
        val receiver = member("COMMUNITY_RELATIONS_OFFICER")
        val manager = member("GRIEVANCE_MANAGER")
        val approver = member("APPROVER")
        val confidential = inAction(testProject(), manager, mapOf("severity" to "ALTA", "confidential" to true), receiver)
        assertTrue(pendingFor(approver, confidential))
        val relaxed = project(mapOf("policy_high_severity_requires_approver" to false))
        val other = member("APPROVER", relaxed)
        val quiet = inAction(relaxed, member("GRIEVANCE_MANAGER", relaxed), mapOf("severity" to "ALTA"))
        assertFalse(pendingFor(other, quiet))
    }
}
