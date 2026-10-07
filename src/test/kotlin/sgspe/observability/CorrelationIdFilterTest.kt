package sgspe.observability

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.web.server.WebFilterChain
import java.util.UUID

// el filtro sin servidor: acepta un identificador con formato, reemplaza cualquier otro por un UUID, lo devuelve en la
// respuesta y lo deja en el contexto de Reactor para el código de sgspe (ASM-034, ADR-0026)
class CorrelationIdFilterTest {
    private val filter = CorrelationIdFilter()

    // la cabecera de la respuesta y lo que vio la cadena en su contexto
    private fun run(sent: String?): Pair<String?, String?> {
        val request = MockServerHttpRequest.get("/api/auth/me")
        if (sent != null) request.header(CorrelationId.HEADER, sent)
        val exchange = MockServerWebExchange.from(request.build())
        var seen: String? = null
        filter.filter(exchange, WebFilterChain { _ -> CorrelationId.current().doOnNext { id -> seen = id }.then() }).block()
        return exchange.response.headers.getFirst(CorrelationId.HEADER) to seen
    }

    @Test
    fun `devuelve el que manda el cliente si cumple el formato y lo pone en el contexto`() {
        assertEquals("prueba-123.abc_X" to "prueba-123.abc_X", run("prueba-123.abc_X"))
        assertEquals("x".repeat(64), run("x".repeat(64)).first)
    }

    @Test
    fun `genera un UUID si no viene`() {
        val (header, seen) = run(null)
        UUID.fromString(header)
        assertEquals(header, seen)
    }

    @Test
    fun `reemplaza un valor sin formato y nunca lo devuelve`() {
        // Review Focus 2: largo, espacios, marcas y saltos de línea que inyectarían otra cabecera o una línea de log
        for (hostile in listOf("x".repeat(65), "con espacio", "<script>", "a\r\nSet-Cookie: x=1", "", "ñandú")) {
            val (header, seen) = run(hostile)
            assertNotEquals(hostile, header, hostile)
            UUID.fromString(header)
            assertEquals(header, seen, hostile)
        }
    }

    @Test
    fun `accept es una funcion del valor`() {
        assertEquals("abc", CorrelationId.accept("abc"))
        assertNotEquals(CorrelationId.accept(null), CorrelationId.accept(null))
    }
}
