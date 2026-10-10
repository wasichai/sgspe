package sgspe.notification

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F4ApiTest

// el aviso de alerta nueva en la bandeja (workflows.md «Avisos»): una alerta registrada por una persona avisa a su
// responsable y a los roles de start_triage, no a quien la registró, y start_triage lo resuelve. el de las alertas de JOB-04
// lo prueba WarningRulesJobApiTest
class WarningNoticesApiTest : F4ApiTest() {
    private fun pendingFor(
        account: Account,
        id: String
    ): Boolean =
        tree(send("GET", "/api/auth/me/notifications?state=active", null, HttpStatus.OK, account.token))["content"]
            .values()
            .any { it["link"]?.get("recordId")?.asString() == id && it["kind"].asString() == "ACTION" }

    @Test
    fun `una alerta nueva avisa al responsable y a start_triage, y el triaje la resuelve`() {
        val creator = member("SOCIAL_RISK_MANAGER")
        val officer = member("COMMUNITY_RELATIONS_OFFICER")
        val owner = member("SOCIAL_MANAGER")
        val id = earlyWarning(testProject(), creator, mapOf("owner_user_id" to owner.id, "owner_user_email" to owner.email))
        assertTrue(pendingFor(officer, id))
        assertTrue(pendingFor(owner, id))
        assertFalse(pendingFor(creator, id))
        move("early_warning", id, "start_triage", officer.token)
        assertFalse(pendingFor(officer, id))
        assertFalse(pendingFor(owner, id))
    }
}
