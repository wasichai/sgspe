package sgspe.ai

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F7ApiTest

// el asistente de wasichai por HTTP con la configuración por defecto (especificación de F7 §3.3 y §5): POST /api/agent/ask cerrado,
// también para el administrador; sin token, primero el 401 de la seguridad; GET /api/agent/status abierto, y sin clave dice que no
// está
class AgentHttpApiTest : F7ApiTest() {
    @Test
    fun `POST a api agent ask por HTTP es 403, tambien al administrador`() {
        val response = exchange("POST", "/api/agent/ask", mapOf("question" to "¿Cuántos proyectos hay?"))
        assertEquals(HttpStatus.FORBIDDEN, response.status, response.body)
        assertTrue("/api/sgspe/ai/assistant/questions" in response.body, response.body)
    }

    @Test
    fun `sin token es 401 antes que 403`() {
        assertEquals(HttpStatus.UNAUTHORIZED, exchange("POST", "/api/agent/ask", mapOf("question" to "¿Hola?"), null).status)
    }

    @Test
    fun `el estado del asistente de wasichai sigue abierto y sin clave dice que no esta`() {
        assertFalse(tree(send("GET", "/api/agent/status", null, HttpStatus.OK))["enabled"].asBoolean())
    }
}
