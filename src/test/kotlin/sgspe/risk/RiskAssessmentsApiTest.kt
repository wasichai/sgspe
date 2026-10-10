package sgspe.risk

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F4ApiTest
import tools.jackson.databind.JsonNode

// el endpoint de revisiones de riesgo (api.md §4.7, ASM-099): la primera revisión lleva el riesgo a assessed con assess, que por
// la API genérica es COMMAND_ONLY; las siguientes actualizan los valores vigentes o los residuales, la tendencia y la próxima
// revisión; los errores con su código; Idempotency-Key; y la escritura de risk_level dispara AUT-11
class RiskAssessmentsApiTest : F4ApiTest() {
    private val reviewer by lazy { member("SOCIAL_RISK_MANAGER") }

    private fun assess(
        risk: String,
        attributes: Map<String, Any?>,
        account: Account = reviewer,
        headers: Map<String, String> = emptyMap()
    ): Response =
        exchange(
            "POST",
            "/api/sgspe/social-risks/$risk/assessments",
            mapOf("attributes" to attributes),
            account.token,
            withReason("revisión semanal") + headers
        )

    private fun review(
        probability: Int,
        impact: Int,
        date: String,
        residual: Boolean = false
    ) = mapOf("assessment_date" to date, "probability" to probability, "impact" to impact, "residual" to residual)

    private fun assessments(risk: String): List<JsonNode> =
        tree(send("GET", "/api/objects/risk_assessment/records?social_risk=$risk", null, HttpStatus.OK))["content"].values().toList()

    @Test
    fun `la primera revision evalua el riesgo y la siguiente actualiza sus valores`() {
        val owner = member("SOCIAL_RISK_MANAGER")
        val risk = socialRisk(testProject(), owner = owner, attributes = mapOf("review_every_days" to 7))
        val first = assess(risk, review(3, 4, "2026-01-05"))
        assertEquals(HttpStatus.CREATED, first.status, first.body)
        val body = tree(first.body)
        assertEquals("ALTO", body["assessment"]["attributes"]["risk_level"].asString())
        assertEquals(12, body["assessment"]["attributes"]["score"].asInt())
        assertEquals(reviewer.email, body["assessment"]["attributes"]["assessor_user_email"].asString())
        val after = record("social_risk", risk)
        assertEquals("assessed", after["state"].asString())
        assertEquals("ALTO", after["attributes"]["risk_level"].asString())
        assertEquals("2026-01-12", after["attributes"]["next_review_date"].asString())
        assertEquals("REVISION VENCIDA", after["attributes"]["review_status"].asString())
        assertTrue(
            first.headers.location
                .toString()
                .endsWith("/api/objects/risk_assessment/records/${body["assessment"]["id"].asString()}")
        )
        val second = tree(assess(risk, review(4, 4, "2026-01-12")).body)
        assertEquals("AUMENTA", second["assessment"]["attributes"]["trend"].asString())
        assertEquals("CRITICO", second["risk"]["attributes"]["risk_level"].asString())
        val residual = tree(assess(risk, review(2, 2, "2026-01-13", residual = true)).body)
        assertEquals("BAJO", residual["risk"]["attributes"]["residual_level"].asString())
        assertEquals("CRITICO", residual["risk"]["attributes"]["risk_level"].asString())
        assertEquals("2026-01-20", residual["risk"]["attributes"]["next_review_date"].asString())
    }

    @Test
    fun `una revision fuera de orden, una primera residual o un riesgo cerrado responden 409`() {
        val risk = socialRisk(testProject())
        val residual = assess(risk, review(2, 2, "2026-01-05", residual = true))
        assertEquals(HttpStatus.CONFLICT, residual.status, residual.body)
        assertEquals("FIRST_ASSESSMENT_NOT_RESIDUAL", code(residual))
        assertEquals(HttpStatus.CREATED, assess(risk, review(3, 3, "2026-01-10")).status)
        val late = assess(risk, review(3, 3, "2026-01-09"))
        assertEquals("ASSESSMENT_OUT_OF_ORDER", code(late))
        assertEquals(listOf("attributes.assessment_date"), problemFields(late.body))
        move("social_risk", risk, "close_assessed", reviewer.token, "el riesgo dejó de existir")
        val closed = assess(risk, review(3, 3, "2026-01-11"))
        assertEquals(HttpStatus.CONFLICT, closed.status, closed.body)
        assertEquals("STATE_MISMATCH", code(closed))
        assertEquals(1, assessments(risk).size)
    }

    @Test
    fun `una escala fuera de 1 a 5 o una fecha futura son OUT_OF_RANGE y no escriben nada`() {
        val risk = socialRisk(testProject())
        val big = assess(risk, review(6, 3, "2026-01-05"))
        assertEquals(HttpStatus.BAD_REQUEST, big.status, big.body)
        assertEquals("OUT_OF_RANGE", code(big))
        assertEquals(listOf("attributes.probability"), problemFields(big.body))
        assertEquals("OUT_OF_RANGE", code(assess(risk, review(3, 3, "2099-01-05"))))
        assertEquals(0, assessments(risk).size)
        assertEquals("identified", state("social_risk", risk))
    }

    @Test
    fun `sin responsable la primera revision no se guarda, porque assess lo exige`() {
        val risk = socialRisk(testProject(), attributes = mapOf("owner_user_id" to null, "owner_user_email" to null))
        val response = assess(risk, review(3, 3, "2026-01-05"))
        assertEquals(HttpStatus.BAD_REQUEST, response.status, response.body)
        assertEquals("FIELD_REQUIRED", code(response))
        assertEquals(listOf("owner_user_id"), problemFields(response.body))
        // la transacción se revierte entera: ni la revisión ni los valores vigentes
        assertEquals(0, assessments(risk).size)
        assertTrue(record("social_risk", risk)["attributes"]["risk_level"].let { it == null || it.isNull })
    }

    @Test
    fun `assess por la API generica es COMMAND_ONLY y quien no evalua recibe 403`() {
        val risk = socialRisk(testProject())
        val generic = transition("social_risk", risk, "assess", reviewer.token, "evaluar")
        assertEquals(HttpStatus.FORBIDDEN, generic.status, generic.body)
        assertEquals("COMMAND_ONLY", code(generic))
        val social = member("SOCIAL_MANAGER")
        assertEquals(HttpStatus.FORBIDDEN, assess(risk, review(3, 3, "2026-01-05"), social).status)
        assertEquals("identified", state("social_risk", risk))
    }

    @Test
    fun `un reenvio con la misma clave devuelve la misma respuesta y no revisa dos veces`() {
        val risk = socialRisk(testProject())
        val key = mapOf("Idempotency-Key" to uniqueName("revision-"))
        val first = assess(risk, review(3, 3, "2026-01-05"), headers = key)
        val again = assess(risk, review(3, 3, "2026-01-05"), headers = key)
        assertEquals(HttpStatus.CREATED, again.status, again.body)
        assertEquals(first.body, again.body)
        assertEquals("true", again.headers.getFirst("Idempotent-Replayed"))
        assertEquals(1, assessments(risk).size)
    }

    // workflows.md «Pruebas de trabajos, listeners y automatizaciones»: la escritura de risk_level del endpoint dispara AUT-11
    @Test
    fun `la revision que lleva el riesgo a CRITICO dispara AUT-11 para su responsable`() {
        val owner = member("SOCIAL_RISK_MANAGER")
        val risk = socialRisk(testProject(), owner = owner)
        assertEquals(HttpStatus.CREATED, assess(risk, review(5, 4, "2026-01-05")).status)
        assertEquals("SUCCEEDED", awaitRun("social_risk", "aut-11-critical-risk-task", risk)["status"].asString())
        val code = record("social_risk", risk)["attributes"]["code"].asString()
        val tasks = tree(send("GET", "/api/objects/task/records?social_risk=$risk", null, HttpStatus.OK))["content"].values()
        val critical = tasks.single { it["attributes"]["title"].asString() == "Revisar el riesgo crítico $code" }
        assertEquals(owner.id, critical["attributes"]["assignee_user_id"].asString())
    }
}
