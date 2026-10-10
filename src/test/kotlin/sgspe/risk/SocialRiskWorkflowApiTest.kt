package sgspe.risk

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F4ApiTest

// WF-F con RiskWriteGuard (workflows.md, especificación de F4 §3.6): los casos propios del riesgo que la matriz de workflows no
// cubre. la primera revisión aplica assess; mitigar exige la decisión MITIGAR y una medida con responsable; monitorear, una
// revisión residual (una no residual no basta); cerrar, el motivo. también en la consulta en seco; y la cadencia propia de 1 a
// 365 días, en el alta y en el cambio
class SocialRiskWorkflowApiTest : F4ApiTest() {
    private val manager by lazy { member("SOCIAL_RISK_MANAGER") }

    private fun assessed(): String {
        val risk = socialRisk(testProject())
        val response =
            exchange(
                "POST",
                "/api/sgspe/social-risks/$risk/assessments",
                mapOf("attributes" to mapOf("assessment_date" to "2026-01-05", "probability" to 3, "impact" to 3)),
                manager.token,
                withReason("primera revisión")
            )
        assertEquals(HttpStatus.CREATED, response.status, response.body)
        return risk
    }

    private fun residual(risk: String) {
        val response =
            exchange(
                "POST",
                "/api/sgspe/social-risks/$risk/assessments",
                mapOf("attributes" to mapOf("assessment_date" to "2026-01-06", "probability" to 2, "impact" to 2, "residual" to true)),
                manager.token,
                withReason("riesgo residual")
            )
        assertEquals(HttpStatus.CREATED, response.status, response.body)
    }

    private fun decide(
        risk: String,
        decision: String
    ) = assertEquals(HttpStatus.OK, edit("social_risk", risk, mapOf("treatment_decision" to decision), manager.token, withReason("decisión")).status)

    @Test
    fun `la primera revision lleva el riesgo a assessed`() {
        val risk = assessed()
        assertEquals("assessed", state("social_risk", risk))
        val history = tree(send("GET", "/api/objects/social_risk/records/$risk/history", null, HttpStatus.OK))
        assertEquals(true, history.values().any { it["reason"]?.asString() == "primera revisión" })
    }

    @Test
    fun `mitigar exige la decision MITIGAR y una medida con responsable`() {
        val risk = assessed()
        decide(risk, "MONITOREAR")
        val wrong = transition("social_risk", risk, "decide_mitigation", manager.token, "mitigar")
        assertEquals(HttpStatus.BAD_REQUEST, wrong.status, wrong.body)
        assertEquals("FIELD_REQUIRED", code(wrong))
        assertEquals(listOf("treatment_decision"), problemFields(wrong.body))
        decide(risk, "MITIGAR")
        createRecord("mitigation_measure", mapOf("social_risk" to risk, "description" to "Medida sin responsable."))
        val ownerless = transition("social_risk", risk, "decide_mitigation", manager.token, "mitigar")
        assertEquals(HttpStatus.CONFLICT, ownerless.status, ownerless.body)
        assertEquals("MEASURE_OWNER_REQUIRED", code(ownerless))
        assertEquals(listOf("mitigation_measure"), problemFields(ownerless.body))
        mitigationMeasure(risk, manager)
        move("social_risk", risk, "decide_mitigation", manager.token, "mitigar")
        assertEquals("mitigating", state("social_risk", risk))
    }

    @Test
    fun `monitorear exige una revision residual`() {
        val risk = assessed()
        decide(risk, "ACEPTAR")
        val missing = transition("social_risk", risk, "decide_monitoring", manager.token, "monitorear")
        assertEquals(HttpStatus.CONFLICT, missing.status, missing.body)
        assertEquals("RESIDUAL_ASSESSMENT_REQUIRED", code(missing))
        residual(risk)
        move("social_risk", risk, "decide_monitoring", manager.token, "monitorear")
        assertEquals("monitoring", state("social_risk", risk))
    }

    // RiskWriteGuard cuenta solo las revisiones residuales: una revisión más que no es residual no basta para pasar a monitoreo
    @Test
    fun `pasar a monitoreo exige una revision residual y una no residual no basta`() {
        val risk = assessed()
        decide(risk, "MITIGAR")
        mitigationMeasure(risk, manager)
        move("social_risk", risk, "decide_mitigation", manager.token, "mitigar")
        val review =
            exchange(
                "POST",
                "/api/sgspe/social-risks/$risk/assessments",
                mapOf("attributes" to mapOf("assessment_date" to "2026-01-06", "probability" to 3, "impact" to 2, "residual" to false)),
                manager.token,
                withReason("revisión no residual")
            )
        assertEquals(HttpStatus.CREATED, review.status, review.body)
        val missing = transition("social_risk", risk, "start_monitoring", manager.token, "monitorear")
        assertEquals(HttpStatus.CONFLICT, missing.status, missing.body)
        assertEquals("RESIDUAL_ASSESSMENT_REQUIRED", code(missing))
        assertEquals(listOf("risk_assessment"), problemFields(missing.body))
        assertEquals("mitigating", state("social_risk", risk))
        residual(risk)
        move("social_risk", risk, "start_monitoring", manager.token, "monitorear")
        assertEquals("monitoring", state("social_risk", risk))
    }

    @Test
    fun `cerrar exige el motivo de cierre`() {
        val risk = socialRisk(testProject(), attributes = mapOf("close_reason" to null))
        val response = transition("social_risk", risk, "close_identified", manager.token, "cerrar")
        assertEquals(HttpStatus.BAD_REQUEST, response.status, response.body)
        assertEquals(listOf("close_reason"), problemFields(response.body))
    }

    // decisión 16: la cadencia propia va de 1 a 365 días (RiskWriteGuard); vacía vale risk_review_days del proyecto
    @Test
    fun `una cadencia propia fuera de 1 a 365 dias es OUT_OF_RANGE y vacia vale la del proyecto`() {
        val risk = socialRisk(testProject())
        for (days in listOf(0, 366)) {
            val wrong = edit("social_risk", risk, mapOf("review_every_days" to days), manager.token, withReason("cadencia"))
            assertEquals(HttpStatus.BAD_REQUEST, wrong.status, wrong.body)
            assertEquals("OUT_OF_RANGE", code(wrong))
            assertEquals(listOf("review_every_days"), problemFields(wrong.body))
        }
        assertEquals(HttpStatus.OK, edit("social_risk", risk, mapOf("review_every_days" to 365), manager.token, withReason("cadencia")).status)
        assertEquals(HttpStatus.OK, edit("social_risk", risk, mapOf("review_every_days" to null), manager.token, withReason("cadencia")).status)
    }

    // en el alta también: un riesgo con la cadencia propia fuera de 1 a 365 días no se crea
    @Test
    fun `un alta con una cadencia propia fuera de 1 a 365 dias es OUT_OF_RANGE`() {
        for (days in listOf(0, 366)) {
            val attributes =
                mapOf(
                    "project" to testProject(),
                    "code" to uniqueName("RS-"),
                    "title" to "Expectativas de empleo local",
                    "category" to "EXPECTATIVAS Y BENEFICIOS",
                    "owner_user_id" to manager.id,
                    "owner_user_email" to manager.email,
                    "review_every_days" to days
                )
            val wrong = exchange("POST", "/api/objects/social_risk/records", mapOf("attributes" to attributes), manager.token, withReason("alta"))
            assertEquals(HttpStatus.BAD_REQUEST, wrong.status, wrong.body)
            assertEquals("OUT_OF_RANGE", code(wrong))
            assertEquals(listOf("review_every_days"), problemFields(wrong.body))
        }
    }

    // POST /api/sgspe/transition-checks corre RiskWriteGuard en seco, como las demás guardas
    @Test
    fun `la consulta en seco dice que falta la medida`() {
        val risk = assessed()
        decide(risk, "MITIGAR")
        val check =
            tree(
                send(
                    "POST",
                    "/api/sgspe/transition-checks",
                    mapOf("object" to "social_risk", "recordId" to risk, "transition" to "decide_mitigation"),
                    HttpStatus.OK,
                    manager.token
                )
            )
        assertFalse(check["allowed"].asBoolean())
        assertEquals(listOf("MEASURE_OWNER_REQUIRED"), check["blocking"].values().map { it["code"].asString() })
    }
}
