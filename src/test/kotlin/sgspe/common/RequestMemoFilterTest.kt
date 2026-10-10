package sgspe.common

import kotlinx.coroutines.reactor.mono
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.web.server.CoWebFilter
import org.springframework.web.server.WebFilterChain
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

// RequestMemoFilter: toda petición de /api abre su propio memo, que llega al controlador por el contexto de la corrutina (como
// lo pasa Spring a un controlador suspend); fuera de /api no hay memo
class RequestMemoFilterTest {
    // cuántas veces se calcula una pregunta de quien llama hecha tres veces por el controlador de una petición a `path`
    private fun computed(path: String): Int {
        var calls = 0
        val exchange = MockServerWebExchange.from(MockServerHttpRequest.get(path))
        val chain =
            WebFilterChain { forwarded ->
                val context = forwarded.getAttribute<CoroutineContext>(CoWebFilter.COROUTINE_CONTEXT_ATTRIBUTE) ?: EmptyCoroutineContext
                mono(context) {
                    repeat(3) {
                        RequestMemo.rememberForRequest<Boolean>("permisos") {
                            calls++
                            true
                        }
                    }
                }.then()
            }
        RequestMemoFilter().filter(exchange, chain).block()
        return calls
    }

    @Test
    fun `una peticion de api pregunta una vez`() {
        assertEquals(1, computed("/api/objects/stakeholder_assessment/records?size=50"))
    }

    @Test
    fun `dos peticiones no comparten el memo`() {
        assertEquals(2, computed("/api/sgspe/dashboards/indicators") + computed("/api/sgspe/dashboards/indicators"))
    }

    @Test
    fun `fuera de api no hay memo`() {
        assertEquals(3, computed("/actuator/health"))
        assertEquals(3, computed("/apix/otra"))
    }
}
