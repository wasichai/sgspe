package sgspe.ai

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.codec.ServerCodecConfigurer
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import reactor.core.publisher.Mono
import wasichai.core.common.GlobalExceptionHandler

// POST /api/agent/ask por HTTP está cerrado (especificación de F7 §3.3): toda pregunta pasa por la activación, la
// seudonimización y el registro de POST /api/sgspe/ai/assistant/questions. GET /api/agent/status y lo demás siguen
class AgentHttpGateTest {
    private val gate = AgentHttpGate(GlobalExceptionHandler("https://sgspe.test/problems"), ServerCodecConfigurer.create())

    // el intercambio y si la cadena siguió
    private fun through(request: MockServerHttpRequest): Pair<MockServerWebExchange, Boolean> {
        val exchange = MockServerWebExchange.from(request)
        var passed = false
        gate
            .filter(exchange) {
                passed = true
                Mono.empty()
            }.block()
        return exchange to passed
    }

    @Test
    fun `POST a ask por HTTP es 403 con el camino que si se usa`() {
        val (exchange, passed) = through(MockServerHttpRequest.post("/api/agent/ask").body("""{"question":"¿Hola?"}"""))
        assertFalse(passed)
        assertEquals(HttpStatus.FORBIDDEN, exchange.response.statusCode)
        assertEquals(MediaType.APPLICATION_PROBLEM_JSON, exchange.response.headers.contentType)
        val body = exchange.response.bodyAsString.block()!!
        assertTrue("/api/sgspe/ai/assistant/questions" in body, body)
    }

    @Test
    fun `con parametros de ruta o por otro metodo tambien`() {
        assertFalse(through(MockServerHttpRequest.post("/api/agent/ask;x=1").build()).second)
        assertFalse(through(MockServerHttpRequest.put("/api/agent/ask").build()).second)
    }

    @Test
    fun `el estado y las demas rutas siguen`() {
        for (request in listOf(
            MockServerHttpRequest.get("/api/agent/status").build(),
            MockServerHttpRequest.post("/api/agent/asking").build(),
            MockServerHttpRequest.post("/api/sgspe/ai/assistant/questions").build()
        )) {
            assertTrue(through(request).second, request.path.value())
        }
    }
}
