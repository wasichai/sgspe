package sgspe.notification

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F4ApiTest

// el aviso de riesgo escalado por nivel en la bandeja (workflows.md «Avisos», RF-NTF-001): una revisión que sube el riesgo a
// ALTO lo publica al responsable y a los roles de decide_mitigation, no a quien revisó; la revisión siguiente lo resuelve, y
// cerrar el riesgo resuelve el de la última
class RiskNoticesApiTest : F4ApiTest() {
    private fun pendingFor(
        account: Account,
        risk: String
    ): Boolean =
        tree(send("GET", "/api/auth/me/notifications?state=active", null, HttpStatus.OK, account.token))["content"]
            .values()
            .any { it["link"]?.get("recordId")?.asString() == risk && it["kind"].asString() == "ACTION" }

    private fun assess(
        risk: String,
        reviewer: Account,
        probability: Int,
        impact: Int,
        date: String
    ) {
        val response =
            exchange(
                "POST",
                "/api/sgspe/social-risks/$risk/assessments",
                mapOf("attributes" to mapOf("assessment_date" to date, "probability" to probability, "impact" to impact)),
                reviewer.token,
                withReason("revisión semanal")
            )
        assertEquals(HttpStatus.CREATED, response.status, response.body)
    }

    @Test
    fun `subir a ALTO avisa al responsable y a decide_mitigation, y la revision siguiente lo resuelve`() {
        val owner = member("SOCIAL_RISK_MANAGER")
        val reviewer = member("SOCIAL_RISK_MANAGER")
        val social = member("SOCIAL_MANAGER")
        val risk = socialRisk(testProject(), owner = owner)
        assess(risk, reviewer, 3, 3, "2026-01-05")
        assertFalse(pendingFor(owner, risk), "MEDIO no escala")
        assess(risk, reviewer, 3, 4, "2026-01-12")
        assertTrue(pendingFor(owner, risk))
        assertTrue(pendingFor(social, risk))
        assertFalse(pendingFor(reviewer, risk))
        assess(risk, reviewer, 2, 2, "2026-01-19")
        assertFalse(pendingFor(owner, risk))
        assertFalse(pendingFor(social, risk))
    }

    @Test
    fun `cerrar el riesgo resuelve el aviso de su ultima revision`() {
        val owner = member("SOCIAL_RISK_MANAGER")
        val reviewer = member("SOCIAL_RISK_MANAGER")
        val social = member("SOCIAL_MANAGER")
        val risk = socialRisk(testProject(), owner = owner)
        assess(risk, reviewer, 4, 4, "2026-01-05")
        assertTrue(pendingFor(owner, risk))
        move("social_risk", risk, "close_assessed", social.token, "el riesgo dejó de existir")
        assertFalse(pendingFor(owner, risk))
    }
}
