package sgspe.ai

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.test.context.TestPropertySource
import sgspe.F7ApiTest

// AI-06 con la IA activada para la organización sembrada (slug demo) y sin clave del proveedor (el mismo contexto que
// AiEnabledApiTest): a quién se ofrece, el 403 a quien no lee los nombres de person o no crea interacciones, y el 503 provider a
// quien sí puede, antes de buscar nada (api.md §4.14, security.md §14 «IA»)
@TestPropertySource(properties = ["sgspe.ai.enabled-organizations=demo"])
class KnowledgeEnabledApiTest : F7ApiTest() {
    private val path = "/api/sgspe/ai/knowledge/questions"

    private fun useCases(token: String): List<String> =
        tree(send("GET", "/api/sgspe/ai/status", null, HttpStatus.OK, token))["useCases"].values().map {
            it.asString()
        }

    private fun ask(token: String) = exchange("POST", path, mapOf("question" to "¿Qué pidió la asamblea sobre el canal?", "project" to testProject()), token)

    @Test
    fun `se ofrece a quien crea interacciones, lee evidencias y lee los nombres`() {
        assertTrue("AI-06" in useCases(member("COMMUNITY_RELATIONS_OFFICER").token))
        val analyst = useCases(member("ANALYST").token)
        assertTrue("AI-05" in analyst)
        assertFalse("AI-06" in analyst)
        assertEquals(emptyList<String>(), useCases(member("READ_ONLY").token))
    }

    @Test
    fun `quien no lee los nombres de person recibe 403 antes de llamar al modelo`() {
        val response = ask(member("ANALYST").token)
        assertEquals(HttpStatus.FORBIDDEN, response.status, response.body)
        assertTrue("nombres de person" in tree(response.body)["detail"].asString(), response.body)
    }

    @Test
    fun `quien no crea interacciones recibe 403`() {
        val response = ask(member("READ_ONLY").token)
        assertEquals(HttpStatus.FORBIDDEN, response.status, response.body)
        assertTrue("ai_interaction" in tree(response.body)["detail"].asString(), response.body)
    }

    @Test
    fun `sin proveedor quien puede preguntar recibe 503 provider`() {
        val response = ask(member("COMMUNITY_RELATIONS_OFFICER").token)
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.status, response.body)
        assertEquals("AI_UNAVAILABLE", code(response))
        assertEquals(listOf("provider"), problemFields(response.body))
    }
}
