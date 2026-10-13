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

    // desde 7.2b, cada caso de las corridas se ofrece a quien cumple sus requisitos (decisión 10 del plan 7.2b)
    @Test
    fun `cada persona ve los casos que sus permisos le dejan usar, aunque no haya proveedor`() {
        val cro = member("COMMUNITY_RELATIONS_OFFICER")
        val body = status(cro.token)
        assertTrue(body["enabled"].asBoolean())
        assertFalse(body["providerAvailable"].asBoolean())
        // «contiene»: 7.2c suma AI-06 y AI-07 (una corrida más) en esta misma prueba
        val runs = listOf("AI-01", "AI-02", "AI-03", "AI-04", "AI-07", "AI-08")
        assertTrue(body["useCases"].values().map { it.asString() }.containsAll(runs + "AI-05" + "AI-06"), body.toString())
        // STAKEHOLDER_MANAGER no crea candidatos: sin AI-03; ANALYST no crea sugerencias ni lee los nombres: ningún caso de corrida
        val stakeholders = status(member("STAKEHOLDER_MANAGER").token)["useCases"].values().map { it.asString() }
        assertTrue(stakeholders.containsAll(runs - "AI-03") && "AI-03" !in stakeholders, stakeholders.toString())
        val analyst = status(member("ANALYST").token)["useCases"].values().map { it.asString() }
        assertTrue("AI-05" in analyst && analyst.none { it in runs }, analyst.toString())
    }

    // el orden de AiRuns (decisión 4 del plan 7.2b): sin proveedor, 503 provider a quien puede pedir la corrida y 403 a quien no
    @Test
    fun `sin proveedor la corrida responde 503 provider, y 403 a quien no puede pedirla`() {
        val engagement = engagement(testProject(), attributes = mapOf("minutes" to "Se acordó reparar el canal de riego."))
        val cro = member("COMMUNITY_RELATIONS_OFFICER")
        val unavailable = aiRun(cro, "AI-03", "engagement", engagement)
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, unavailable.status, unavailable.body)
        assertEquals("AI_UNAVAILABLE", code(unavailable))
        assertEquals(listOf("provider"), problemFields(unavailable.body))
        assertEquals(HttpStatus.FORBIDDEN, aiRun(member("ANALYST"), "AI-03", "engagement", engagement).status)
        // sin llamada no hay ai_interaction
        val logged =
            tree(
                send(
                    "GET",
                    // send codifica la consulta: el correo va sin codificar
                    "/api/objects/ai_interaction/records?requested_by_user_email=${cro.email}",
                    null,
                    HttpStatus.OK
                )
            )
        assertEquals(0, logged["content"].size())
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
