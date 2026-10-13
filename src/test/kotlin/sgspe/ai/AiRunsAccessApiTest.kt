package sgspe.ai

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F7ApiTest

// la IA viene apagada (ASM-015, ASM-128): sin la organización en sgspe.ai.enabled-organizations, la corrida es 503 organization
// antes que cualquier otro rechazo de permisos, y no deja ai_interaction
class AiRunsAccessApiTest : F7ApiTest() {
    @Test
    fun `sin la IA activada la corrida es 503 organization`() {
        val engagement = engagement(testProject(), attributes = mapOf("minutes" to "Se acordó reparar el canal de riego."))
        for (role in listOf("COMMUNITY_RELATIONS_OFFICER", "ANALYST")) {
            val response = aiRun(member(role), "AI-03", "engagement", engagement)
            assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.status, response.body)
            assertEquals("AI_UNAVAILABLE", code(response))
            assertEquals(listOf("organization"), problemFields(response.body))
        }
    }

    @Test
    fun `el cuerpo se valida antes que la activacion`() {
        val response = aiRun(member("COMMUNITY_RELATIONS_OFFICER"), "AI-05", "engagement", testProject())
        assertEquals(HttpStatus.BAD_REQUEST, response.status, response.body)
        assertEquals(listOf("useCase"), problemFields(response.body))
    }
}
