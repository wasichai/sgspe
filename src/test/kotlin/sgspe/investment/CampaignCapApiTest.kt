package sgspe.investment

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.ContributionApiTest

// SupportPolicyGuard (D7 de WF-D) con lo comprometido de F5: un compromiso que se aprueba cuenta también los aportes
// comprometidos de su campaña, con el mismo candado (elección 6)
class CampaignCapApiTest : ContributionApiTest() {
    @Test
    fun `un compromiso que se aprueba cuenta los aportes comprometidos de la campana`() {
        val project = testProject()
        val campaign = cappedCampaign(project, 1000)
        approvedWithItems(project, items = listOf(700 to "BIEN"), attributes = mapOf("exploration_campaign" to campaign))
        val approver = member("APPROVER", project)
        val over = commitment(project, attributes = mapOf("exploration_campaign" to campaign, "amount" to 400))
        move("commitment", over, "submit", reason = "envío")
        val refused = transition("commitment", over, "approve", approver.token, "aprobación")
        assertEquals(HttpStatus.CONFLICT, refused.status, refused.body)
        assertEquals("SUPPORT_CAP_EXCEEDED", code(refused))
        val fits = commitment(project, attributes = mapOf("exploration_campaign" to campaign, "amount" to 300))
        move("commitment", fits, "submit", reason = "envío")
        assertEquals(HttpStatus.OK, transition("commitment", fits, "approve", approver.token, "aprobación").status)
    }
}
