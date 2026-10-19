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

// ADR-0030, deliverables.md §7.7: el compositor contra un sgspe-latex falso (el servidor HTTP del JDK): lo que se envía, los dos
// PDF que vuelven y cada forma de no tenerlos
class LatexConverterTest {
    private class Received(
        val files: Map<String, ByteArray>,
        val trace: String?,
        val contentType: String?
    )

    private val received = CopyOnWriteArrayList<Received>()

    private fun server(respond: (HttpExchange) -> Unit): HttpServer =
        HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            executor = Executors.newCachedThreadPool()
            createContext(LatexConverter.ROUTE) { exchange ->
                exchange.use {
                    val body = it.requestBody.readAllBytes()
                    received += Received(Tar.read(body), it.requestHeaders.getFirst(LatexConverter.TRACE), it.requestHeaders.getFirst("Content-Type"))
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
        overfull: Int? = null
    ) {
        exchange.responseHeaders.add("Content-Type", type)
        overfull?.let { exchange.responseHeaders.add(LatexConverter.OVERFULL, it.toString()) }
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.write(bytes)
    }

    private fun converter(
        url: String?,
        timeout: Duration = Duration.ofSeconds(5)
    ) = LatexConverter(DeliverableProperties(latex = DeliverableProperties.Latex(url, timeout)))

    private fun url(server: HttpServer) = "http://127.0.0.1:${server.address.port}/"

    private val bundle = LatexWriter.Bundle(sortedMapOf("main.tex" to "\\documentclass{x}".toByteArray(), "img/a.png" to byteArrayOf(1, 2, 3)))

    @Test
    fun `envia el paquete como tar con el id de la corrida y devuelve la candidata y el borrador`() {
        val candidate = TestPdfs.source(pdfa = true)
        val draft = TestPdfs.source(pdfa = false)
        val server = server { reply(it, 200, "application/x-tar", Tar.write(mapOf("candidata.pdf" to candidate, "borrador.pdf" to draft)), overfull = 2) }
        try {
            val pdfs = runBlocking { converter(url(server)).render(bundle, PdfRequest("job-05-x")) }
            assertArrayEquals(candidate, pdfs.candidate)
            assertArrayEquals(draft, pdfs.draft)
            assertEquals(2, pdfs.overfull)
            val request = received.single()
            assertEquals("job-05-x", request.trace)
            assertEquals("application/x-tar", request.contentType)
            assertEquals(bundle.files.keys, request.files.keys)
            assertArrayEquals(byteArrayOf(1, 2, 3), request.files["img/a.png"])
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `sin URL, con un error, con otro tipo o sin los dos PDF, el compositor no esta disponible`() {
        assertThrows<PdfConverterUnavailable> { runBlocking { converter(null).render(bundle, PdfRequest(null)) } }
        assertThrows<PdfConverterUnavailable> { runBlocking { converter(" ").render(bundle, PdfRequest(null)) } }
        val answers =
            listOf<(HttpExchange) -> Unit>(
                { reply(it, 422, "application/json", """{"code":"LATEX_ERROR","line":12}""".toByteArray()) },
                { reply(it, 200, "application/pdf", TestPdfs.source(pdfa = false)) },
                { reply(it, 200, "application/x-tar", Tar.write(mapOf("candidata.pdf" to TestPdfs.source(pdfa = true)))) },
                { reply(it, 200, "application/x-tar", "no es un tar".toByteArray()) }
            )
        for (answer in answers) {
            val server = server(answer)
            try {
                assertThrows<PdfConverterUnavailable> { runBlocking { converter(url(server)).render(bundle, PdfRequest(null)) } }
            } finally {
                server.stop(0)
            }
        }
    }

    @Test
    fun `un plazo vencido no espera al compositor`() {
        val server = server { Thread.sleep(2_000) }
        try {
            assertThrows<PdfConverterUnavailable> { runBlocking { converter(url(server), Duration.ofMillis(200)).render(bundle, PdfRequest(null)) } }
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `un PDF mas grande que el tope de los archivos es FILE_TOO_LARGE`() {
        val big = ByteArray(1024 * 1024 + 1)
        val server = server { reply(it, 200, "application/x-tar", Tar.write(mapOf("candidata.pdf" to big, "borrador.pdf" to big))) }
        try {
            val small =
                LatexConverter(DeliverableProperties(maxFileBytes = 1024L * 1024, latex = DeliverableProperties.Latex(url(server), Duration.ofSeconds(5))))
            assertThrows<PdfTooLarge> { runBlocking { small.render(bundle, PdfRequest(null)) } }
        } finally {
            server.stop(0)
        }
    }
}
