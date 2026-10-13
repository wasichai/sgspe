package sgspe.ai

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.test.context.TestPropertySource
import sgspe.F7ApiTest
import tools.jackson.databind.JsonNode

// la IA activada para la organización sembrada (slug demo) en un despliegue sin clave del proveedor (ASM-128, ASM-130): lo que
// ve cada persona en GET /api/sgspe/ai/status. la Tarea 11 suma las preguntas al asistente en este mismo contexto
@TestPropertySource(properties = ["sgspe.ai.enabled-organizations=demo"])
class AiEnabledApiTest : F7ApiTest() {
    private fun status(token: String): JsonNode = tree(send("GET", "/api/sgspe/ai/status", null, HttpStatus.OK, token))

    @Test
    fun `quien crea interacciones ve el asistente aunque no haya proveedor`() {
        val cro = member("COMMUNITY_RELATIONS_OFFICER")
        val body = status(cro.token)
        assertTrue(body["enabled"].asBoolean())
        assertFalse(body["providerAvailable"].asBoolean())
        // cada plan de F7 suma sus casos (7.2b, 7.2c): aquí, los que ya se ofrecen a este rol
        assertTrue(body["useCases"].values().map { it.asString() }.containsAll(listOf("AI-05", "AI-06")), body.toString())
    }

    @Test
    fun `quien no crea interacciones no ve casos`() {
        val reader = member("READ_ONLY")
        assertEquals(emptyList<String>(), status(reader.token)["useCases"].values().map { it.asString() })
    }

    @Test
    fun `otra organizacion no tiene la IA activada`() {
        val other = provisionTenant()
        val body = status(other.token)
        assertFalse(body["enabled"].asBoolean())
        assertEquals(emptyList<String>(), body["useCases"].values().map { it.asString() })
    }

    @Test
    fun `sin proveedor la pregunta responde 503 provider, y 403 a quien no crea interacciones`() {
        val path = "/api/sgspe/ai/assistant/questions"
        val cro = member("COMMUNITY_RELATIONS_OFFICER")
        val unavailable = exchange("POST", path, mapOf("question" to "¿Cuántas reuniones hubo?"), cro.token)
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, unavailable.status, unavailable.body)
        assertEquals("AI_UNAVAILABLE", code(unavailable))
        assertEquals(listOf("provider"), problemFields(unavailable.body))
        val reader = member("READ_ONLY")
        val forbidden = exchange("POST", path, mapOf("question" to "¿Cuántas reuniones hubo?"), reader.token)
        assertEquals(HttpStatus.FORBIDDEN, forbidden.status, forbidden.body)
        assertTrue("ai_interaction" in tree(forbidden.body)["detail"].asString(), forbidden.body)
        val other = provisionTenant()
        val elsewhere = exchange("POST", path, mapOf("question" to "¿Cuántas reuniones hubo?"), other.token)
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, elsewhere.status, elsewhere.body)
        assertEquals(listOf("organization"), problemFields(elsewhere.body))
    }
}
