package sgspe.observability

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import java.util.UUID

// el filtro sin servidor: acepta un identificador con formato, reemplaza cualquier otro por un UUID, lo devuelve en la
// respuesta y lo deja en el contexto de Reactor para el código de sgspe (ASM-034, ADR-0026)
class CorrelationIdFilterTest {
    private val filter = CorrelationIdFilter()
    private val accessLog = LoggerFactory.getLogger("sgspe.access") as ch.qos.logback.classic.Logger
    private val lines = ListAppender<ILoggingEvent>()

    @BeforeEach
    fun captureAccessLog() {
        accessLog.level = Level.INFO
        lines.start()
        accessLog.addAppender(lines)
    }

    @AfterEach
    fun releaseAccessLog() {
        accessLog.detachAppender(lines)
        lines.stop()
    }

    private fun accessLines(): List<String> = lines.list.map { it.formattedMessage }

    private fun get(sent: String? = null): MockServerWebExchange {
        val request = MockServerHttpRequest.get("/api/auth/me?pista=secreto")
        if (sent != null) request.header(CorrelationId.HEADER, sent)
        return MockServerWebExchange.from(request.build())
    }

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
        for (hostile in listOf("x".repeat(65), "con espacio", "<script>", "a\r\nSet-Cookie: x=1", "", "ñandú", "abc\n", "abc\r\n", "abc ", "a, b")) {
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

    // la línea de acceso: método, ruta sin consulta, estado, milisegundos e identificador, y nada más
    @Test
    fun `la linea de acceso tiene el formato completo y no lleva la consulta`() {
        val exchange = get("acceso-9")
        filter.filter(exchange, WebFilterChain { e -> e.response.setStatusCode(HttpStatus.UNAUTHORIZED).let { Mono.empty() } }).block()
        val line = accessLines().single()
        assertTrue(Regex("""GET /api/auth/me 401 \d+ms correlationId=acceso-9""").matches(line), line)
    }

    // I1: un error que la cadena deja pasar llega a ExceptionHandlingWebHandler, que pone el 500 después del filtro: la
    // línea ya tiene que decir 500, no null, y el error sigue su camino sin cambios
    @Test
    fun `un error de la cadena se registra como 500 y se propaga igual`() {
        val boom = IllegalStateException("boom")
        val thrown = assertThrows(IllegalStateException::class.java) { filter.filter(get(), WebFilterChain { _ -> Mono.error(boom) }).block() }
        assertSame(boom, thrown)
        assertTrue(Regex("""GET /api/auth/me 500 \d+ms correlationId=\S+""").matches(accessLines().single()), accessLines().toString())
    }

    @Test
    fun `un ResponseStatusException se registra con su estado`() {
        val error = ResponseStatusException(HttpStatus.BAD_GATEWAY)
        assertThrows(ResponseStatusException::class.java) { filter.filter(get(), WebFilterChain { _ -> Mono.error(error) }).block() }
        assertTrue(accessLines().single().contains("GET /api/auth/me 502 "), accessLines().toString())
    }

    // el cliente que corta la conexión cancela la cadena. el marcador es 499, el «client closed request» de nginx: no es un
    // estado que sgspe devuelva, y distingue en el log un corte de un 200
    @Test
    fun `una peticion cancelada se registra como 499`() {
        filter.filter(get(), WebFilterChain { _ -> Mono.never() }).subscribe().dispose()
        assertTrue(Regex("""GET /api/auth/me 499 \d+ms correlationId=\S+""").matches(accessLines().single()), accessLines().toString())
    }
}
