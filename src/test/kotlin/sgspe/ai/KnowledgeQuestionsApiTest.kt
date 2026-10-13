package sgspe.ai

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F7ApiTest

// POST /api/sgspe/ai/knowledge/questions con la configuración por defecto (api.md §4.14, ASM-128): el cuerpo se valida antes que
// la activación, y una organización sin IA responde 503 AI_UNAVAILABLE sobre organization
class KnowledgeQuestionsApiTest : F7ApiTest() {
    private val path = "/api/sgspe/ai/knowledge/questions"

    @Test
    fun `el cuerpo se valida antes que la activacion`() {
        val missing = exchange("POST", path, mapOf("question" to "¿Qué pidió la asamblea?"))
        assertEquals(HttpStatus.BAD_REQUEST, missing.status, missing.body)
        assertEquals(listOf("project"), problemFields(missing.body))
        val extra = exchange("POST", path, mapOf("question" to "¿Qué?", "project" to testProject(), "scope" to "tenant"))
        assertEquals(listOf("scope"), problemFields(extra.body))
        val long = exchange("POST", path, mapOf("question" to "a".repeat(4_001), "project" to testProject()))
        assertEquals(HttpStatus.valueOf(422), long.status, long.body)
        assertEquals("AI_INPUT_TOO_LARGE", code(long))
        assertEquals(listOf("question"), problemFields(long.body))
    }

    @Test
    fun `una organizacion sin IA responde 503 sobre organization`() {
        val social = member("SOCIAL_MANAGER")
        val response = exchange("POST", path, mapOf("question" to "¿Qué pidió la asamblea?", "project" to testProject()), social.token)
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.status, response.body)
        assertEquals("AI_UNAVAILABLE", code(response))
        assertEquals(listOf("organization"), problemFields(response.body))
    }

    @Test
    fun `sin token es 401`() {
        assertEquals(HttpStatus.UNAUTHORIZED, exchange("POST", path, mapOf("question" to "¿Hola?", "project" to testProject()), null).status)
    }
}
