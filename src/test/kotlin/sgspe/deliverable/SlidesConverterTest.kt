package sgspe.deliverable

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.net.InetSocketAddress
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

// ADR-0031, deliverables.md §16.7: el compositor de láminas contra un sgspe-slides falso (el servidor HTTP del JDK): lo que se
// envía, el PDF que vuelve con sus versiones y cada forma de no tenerlo
class SlidesConverterTest {
    private class Received(
        val body: ByteArray,
        val trace: String?,
        val contentType: String?
    )

    private val received = CopyOnWriteArrayList<Received>()

    private fun server(respond: (HttpExchange) -> Unit): HttpServer =
        HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            executor = Executors.newCachedThreadPool()
            createContext(SlidesConverter.ROUTE) { exchange ->
                exchange.use {
                    received +=
                        Received(it.requestBody.readAllBytes(), it.requestHeaders.getFirst(SlidesConverter.TRACE), it.requestHeaders.getFirst("Content-Type"))
                    respond(it)
                }
            }
            start()
        }

    private fun reply(
        exchange: HttpExchange,
        status: Int,
        type: String,
        bytes: ByteArray,
        headers: Map<String, String> = emptyMap()
    ) {
        exchange.responseHeaders.add("Content-Type", type)
        headers.forEach { (k, v) -> exchange.responseHeaders.add(k, v) }
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.write(bytes)
    }

    private fun converter(
        url: String?,
        timeout: Duration = Duration.ofSeconds(5)
    ) = SlidesConverter(DeliverableProperties(slides = DeliverableProperties.Slides(url, timeout)))

    private fun url(server: HttpServer) = "http://127.0.0.1:${server.address.port}/"

    private val spec = """{"schema":1}""".toByteArray()

    @Test
    fun `envia la especificacion en JSON con el id de la corrida y devuelve el PDF con sus versiones`() {
        val pdf = TestPdfs.source(pdfa = false)
        val server =
            server {
                reply(
                    it,
                    200,
                    "application/pdf",
                    pdf,
                    mapOf(
                        SlidesConverter.COUNT to "12",
                        SlidesConverter.RENDERER to "remotion@4.0.533",
                        SlidesConverter.DESIGN to "1"
                    )
                )
            }
        try {
            val deck = runBlocking { converter(url(server)).render(spec, "job-09-x") }
            assertArrayEquals(pdf, deck.pdf)
            assertEquals(12, deck.slides)
            assertEquals("remotion@4.0.533", deck.renderer)
            assertEquals("1", deck.design)
            val request = received.single()
            assertEquals("job-09-x", request.trace)
            assertEquals("application/json", request.contentType)
            assertArrayEquals(spec, request.body)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `un 422 con su codigo es SlideFailed con la lamina, sin texto del documento`() {
        val server = server { reply(it, 422, "application/json", """{"code":"SLIDE_OVERFLOW","slide":"tesis"}""".toByteArray()) }
        try {
            val failure = assertThrows<SlideFailed> { runBlocking { converter(url(server)).render(spec, "job-09-x") } }
            assertEquals("SLIDE_OVERFLOW", failure.code)
            assertEquals("tesis", failure.slide)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `sin URL, caido, vencido o con otra respuesta es SlidesUnavailable`() {
        assertThrows<SlidesUnavailable> { runBlocking { converter(null).render(spec, "x") } }
        assertThrows<SlidesUnavailable> { runBlocking { converter("http://127.0.0.1:1/").render(spec, "x") } }
        val slow =
            server {
                Thread.sleep(1500)
                reply(it, 200, "application/pdf", TestPdfs.source(pdfa = false))
            }
        val wrong = server { reply(it, 500, "application/json", """{"code":"INTERNAL"}""".toByteArray()) }
        try {
            assertThrows<SlidesUnavailable> { runBlocking { converter(url(slow), Duration.ofMillis(300)).render(spec, "x") } }
            assertThrows<SlidesUnavailable> { runBlocking { converter(url(wrong)).render(spec, "x") } }
        } finally {
            slow.stop(0)
            wrong.stop(0)
        }
    }
}
