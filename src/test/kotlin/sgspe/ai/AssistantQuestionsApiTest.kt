package sgspe.ai

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F7ApiTest

// POST /api/sgspe/ai/assistant/questions con la configuración por defecto (api.md §4.14, ASM-128): el cuerpo se valida antes que la
// activación, y una organización sin IA responde 503 AI_UNAVAILABLE sobre organization
class AssistantQuestionsApiTest : F7ApiTest() {
    private val path = "/api/sgspe/ai/assistant/questions"

    @Test
    fun `el cuerpo se valida antes que la activacion`() {
        assertEquals(HttpStatus.BAD_REQUEST, exchange("POST", path, emptyMap<String, Any>()).status)
        val extra = exchange("POST", path, mapOf("question" to "¿Hola?", "project" to "x"))
        assertEquals(HttpStatus.BAD_REQUEST, extra.status, extra.body)
        assertEquals(listOf("project"), problemFields(extra.body))
        assertEquals(HttpStatus.BAD_REQUEST, exchange("POST", path, mapOf("question" to 5)).status)
        val long = exchange("POST", path, mapOf("question" to "a".repeat(4_001)))
        assertEquals(HttpStatus.valueOf(422), long.status, long.body)
        assertEquals("AI_INPUT_TOO_LARGE", code(long))
        assertEquals(listOf("question"), problemFields(long.body))
    }

    @Test
    fun `una organizacion sin IA responde 503 sobre organization`() {
        val cro = member("COMMUNITY_RELATIONS_OFFICER")
        val response = exchange("POST", path, mapOf("question" to "¿Cuántos compromisos vencieron?"), cro.token)
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.status, response.body)
        assertEquals("AI_UNAVAILABLE", code(response))
        assertEquals(listOf("organization"), problemFields(response.body))
    }

    @Test
    fun `sin token es 401`() {
        assertEquals(HttpStatus.UNAUTHORIZED, exchange("POST", path, mapOf("question" to "¿Hola?"), null).status)
    }
}
