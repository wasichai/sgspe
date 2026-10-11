package sgspe.common

import kotlinx.coroutines.reactor.mono
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.web.server.CoWebFilter
import org.springframework.web.server.WebFilterChain
import java.util.UUID
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

// RequestMemoFilter: toda petición de /api abre su propio memo, que llega al controlador por el contexto de la corrutina (como
// lo pasa Spring a un controlador suspend); fuera de /api no hay memo. un comando de SGSPE (una escritura bajo /api/sgspe/)
// abre además el memo del comando, donde Assignments.isAssigned guarda la asignación de quien escribe
class RequestMemoFilterTest {
    // cuántas veces se calcula una pregunta hecha tres veces por el controlador de una petición `method` a `path`: la de quien
    // llama (rememberForRequest, los permisos) o, con `command`, la de un comando (remember, la asignación de quien escribe)
    private fun computed(
        path: String,
        method: HttpMethod = HttpMethod.GET,
        command: Boolean = false
    ): Int {
        var calls = 0
        val compute: suspend () -> Boolean = {
            calls++
            true
        }
        val exchange = MockServerWebExchange.from(MockServerHttpRequest.method(method, path))
        val chain =
            WebFilterChain { forwarded ->
                val context = forwarded.getAttribute<CoroutineContext>(CoWebFilter.COROUTINE_CONTEXT_ATTRIBUTE) ?: EmptyCoroutineContext
                mono(context) {
                    repeat(3) {
                        if (command) RequestMemo.remember("asignacion", compute) else RequestMemo.rememberForRequest("permisos", compute)
                    }
                }.then()
            }
        RequestMemoFilter().filter(exchange, chain).block()
        return calls
    }

    @Test
    fun `una peticion de api pregunta una vez`() {
        assertEquals(1, computed("/api/objects/stakeholder_assessment/records?size=50"))
        assertEquals(1, computed("/api/sgspe/evidence", HttpMethod.POST))
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

    // el alta de una entrega con tres evidencias preguntaba unas 7 veces si quien escribe está asignado al proyecto
    @Test
    fun `un comando de SGSPE abre el memo del comando, y una lectura o la API generica no`() {
        val id = UUID.randomUUID()
        assertEquals(1, computed("/api/sgspe/social-contributions/$id/deliveries", HttpMethod.POST, command = true))
        assertEquals(1, computed("/api/sgspe/deliveries/$id", HttpMethod.PATCH, command = true))
        assertEquals(1, computed("/api/sgspe/evidence/$id/links/delivery/$id", HttpMethod.DELETE, command = true))
        assertEquals(3, computed("/api/sgspe/dashboards/indicators", command = true))
        assertEquals(3, computed("/api/objects/project_assignment/records", HttpMethod.POST, command = true))
        assertEquals(3, computed("/actuator/health", HttpMethod.POST, command = true))
    }

    // una asignación nunca pasa de un comando a otro: cambia al instante por /api/objects/project_assignment
    @Test
    fun `dos comandos no comparten la asignacion`() {
        val path = "/api/sgspe/evidence"
        assertEquals(2, computed(path, HttpMethod.POST, command = true) + computed(path, HttpMethod.POST, command = true))
    }
}
