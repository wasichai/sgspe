package sgspe.deadline

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import sgspe.F4ApiTest
import tools.jackson.databind.JsonNode

// JOB-01 con los riesgos (workflows.md «Plazos y escalamiento» y «Pruebas de trabajos», ASM-099): la revisión vencida queda
// REVISION VENCIDA sin cambiar el estado, crea una sola tarea risk_review:<id>:<fecha> para el responsable, la cadencia nueva
// mueve la próxima revisión y un riesgo cerrado deja de medirse
class DeadlineJobRiskApiTest : F4ApiTest() {
    @Autowired
    private lateinit var job: DeadlineJob

    private fun reviews(risk: String): List<JsonNode> =
        tree(send("GET", "/api/objects/task/records?social_risk=$risk", null, HttpStatus.OK))["content"]
            .values()
            .filter { it["attributes"]["generator_key"]?.asString()?.startsWith("risk_review:") == true }

    private fun blank(node: JsonNode?): Boolean = node == null || node.isNull || node.isMissingNode

    @Test
    fun `una revision vencida crea una sola tarea para el responsable y la cadencia nueva mueve la fecha`() {
        val owner = member("SOCIAL_RISK_MANAGER")
        val reviewer = member("SOCIAL_RISK_MANAGER")
        val risk = socialRisk(testProject(), owner = owner)
        val assessed =
            exchange(
                "POST",
                "/api/sgspe/social-risks/$risk/assessments",
                mapOf("attributes" to mapOf("assessment_date" to "2026-01-05", "probability" to 2, "impact" to 2)),
                reviewer.token,
                withReason("primera revisión")
            )
        assertEquals(HttpStatus.CREATED, assessed.status, assessed.body)
        runBlocking { job.runOnce() }
        val after = record("social_risk", risk)
        assertEquals("REVISION VENCIDA", after["attributes"]["review_status"].asString())
        assertEquals("assessed", after["state"].asString())
        val code = after["attributes"]["code"].asString()
        val first = reviews(risk).single()
        assertEquals("risk_review:$risk:2026-01-12", first["attributes"]["generator_key"].asString())
        assertEquals("Revisar el riesgo $code", first["attributes"]["title"].asString())
        assertEquals(owner.id, first["attributes"]["assignee_user_id"].asString())
        assertEquals("2026-01-12", first["attributes"]["due_date"].asString())
        runBlocking { job.runOnce() }
        assertEquals(1, reviews(risk).size)
        assertEquals(HttpStatus.OK, edit("social_risk", risk, mapOf("review_every_days" to 30), owner.token, withReason("revisión mensual")).status)
        runBlocking { job.runOnce() }
        assertEquals("2026-02-04", record("social_risk", risk)["attributes"]["next_review_date"].asString())
        assertEquals(
            setOf("risk_review:$risk:2026-01-12", "risk_review:$risk:2026-02-04"),
            reviews(risk).map { it["attributes"]["generator_key"].asString() }.toSet()
        )
        val history = tree(send("GET", "/api/objects/social_risk/records/$risk/history", null, HttpStatus.OK))
        assertTrue(history.values().any { it["reason"]?.asString() == DeadlineJob.REASON })
    }

    @Test
    fun `un riesgo cerrado deja de medirse`() {
        val reviewer = member("SOCIAL_RISK_MANAGER")
        val risk = socialRisk(testProject())
        exchange(
            "POST",
            "/api/sgspe/social-risks/$risk/assessments",
            mapOf("attributes" to mapOf("assessment_date" to "2026-01-05", "probability" to 2, "impact" to 2)),
            reviewer.token,
            withReason("primera revisión")
        )
        move("social_risk", risk, "close_assessed", reviewer.token, "el riesgo dejó de existir")
        runBlocking { job.runOnce() }
        assertTrue(blank(record("social_risk", risk)["attributes"]["review_status"]))
    }
}
