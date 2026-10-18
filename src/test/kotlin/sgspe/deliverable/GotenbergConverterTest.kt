package sgspe.deliverable

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.net.InetSocketAddress
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

// especificación de F8 §3.3 («PDF») y deliverables.md §7.7 (plan 8.2c, Tarea 3; Review Focus 2): el conversor contra un
// Gotenberg falso (el servidor HTTP del JDK): lo que se envía, el PDF que vuelve y cada forma de no tenerlo
class GotenbergConverterTest {
    private class Received(
        val body: String,
        val trace: String?,
        val contentType: String?
    )

    private val received = CopyOnWriteArrayList<Received>()

    private fun server(respond: (HttpExchange) -> Unit): HttpServer =
        HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            executor = Executors.newCachedThreadPool()
            createContext(GotenbergConverter.ROUTE) { exchange ->
                exchange.use {
                    val body = it.requestBody.readAllBytes().toString(Charsets.ISO_8859_1)
                    received += Received(body, it.requestHeaders.getFirst(GotenbergConverter.TRACE), it.requestHeaders.getFirst("Content-Type"))
                    respond(it)
                }
            }
            start()
        }

    private fun reply(
        exchange: HttpExchange,
        status: Int,
        type: String,
        bytes: ByteArray
    ) {
        exchange.responseHeaders.add("Content-Type", type)
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.write(bytes)
    }

    private fun converter(
        url: String?,
        timeout: Duration = Duration.ofSeconds(5)
    ) = GotenbergConverter(DeliverableProperties(gotenberg = DeliverableProperties.Gotenberg(url, timeout)))

    private fun url(server: HttpServer) = "http://127.0.0.1:${server.address.port}/"

    private val docx = "documento de prueba".toByteArray()

    @Test
    fun `envia el DOCX con updateIndexes, PDF-A solo en la candidata y la traza, sin URL ni metadatos, y devuelve el PDF`() {
        val pdf = TestPdfs.liberation(pdfa = true)
        val server = server { reply(it, 200, "application/pdf", pdf) }
        try {
            val candidate = runBlocking { converter(url(server)).convert(docx, PdfRequest(true, "Informe", "ENT-001", "0", "job-05-prueba")) }
            assertArrayEquals(pdf, candidate)
            runBlocking { converter(url(server)).convert(docx, PdfRequest(false, "Informe", "ENT-001", "A", null)) }
        } finally {
            server.stop(0)
        }
        val (first, second) = received
        assertTrue(first.contentType.orEmpty().startsWith("multipart/form-data"), first.contentType)
        assertTrue("name=\"files\"; filename=\"${GotenbergConverter.FILE_NAME}\"" in first.body, first.body)
        assertTrue("name=\"updateIndexes\"" in first.body && "true" in first.body)
        assertTrue("name=\"pdfa\"" in first.body && GotenbergConverter.PDFA in first.body)
        assertEquals("job-05-prueba", first.trace)
        assertFalse("name=\"pdfa\"" in second.body, "un borrador no es PDF/A")
        for (forbidden in listOf("downloadFrom", "webhook", "metadata", "url")) {
            assertFalse("name=\"$forbidden" in first.body, forbidden)
        }
    }

    @Test
    fun `un plazo vencido, un 503, una respuesta que no es PDF o la URL vacia son PdfConverterUnavailable`() {
        val slow =
            server {
                Thread.sleep(2_000)
                reply(it, 200, "application/pdf", TestPdfs.liberation(false))
            }
        val down = server { reply(it, 503, "text/plain", "ocupado".toByteArray()) }
        val html = server { reply(it, 200, "text/html", "<html>error</html>".toByteArray()) }
        try {
            val request = PdfRequest(false, "Informe", "ENT-001", "A", null)
            assertThrows<PdfConverterUnavailable> { runBlocking { converter(url(slow), Duration.ofMillis(300)).convert(docx, request) } }
            assertThrows<PdfConverterUnavailable> { runBlocking { converter(url(down)).convert(docx, request) } }
            assertThrows<PdfConverterUnavailable> { runBlocking { converter(url(html)).convert(docx, request) } }
            assertThrows<PdfConverterUnavailable> { runBlocking { converter(null).convert(docx, request) } }
            assertThrows<PdfConverterUnavailable> { runBlocking { converter("  ").convert(docx, request) } }
        } finally {
            listOf(slow, down, html).forEach { it.stop(0) }
        }
    }

    // revisión final de 8.2c, Minor 2: solo application/pdf exacto; un comodín no es un PDF (deliverables.md §8)
    @Test
    fun `una respuesta con un tipo comodin es PdfConverterUnavailable`() {
        val servers = listOf("*/*", "application/*").map { type -> server { reply(it, 200, type, "<html>error</html>".toByteArray()) } }
        try {
            val request = PdfRequest(false, "Informe", "ENT-001", "A", null)
            for (server in servers) {
                assertThrows<PdfConverterUnavailable> { runBlocking { converter(url(server)).convert(docx, request) } }
            }
        } finally {
            servers.forEach { it.stop(0) }
        }
    }

    @Test
    fun `el plazo de Gotenberg es positivo y una URL que no es http detiene el arranque`() {
        assertThrows<IllegalArgumentException> { DeliverableProperties.Gotenberg("http://localhost:8096", Duration.ZERO) }
        assertThrows<IllegalArgumentException> { DeliverableProperties.Gotenberg("file:///etc/passwd") }
        assertFalse(DeliverableProperties.Gotenberg("").configured)
        assertTrue(DeliverableProperties.Gotenberg("http://localhost:8096").configured)
    }

    @Test
    fun `un PDF mayor que el tope es PdfTooLarge, no un conversor caido`() {
        val big = ByteArray((1024 * 1024) + 1) { 'a'.code.toByte() }
        val server = server { reply(it, 200, "application/pdf", big) }
        try {
            val small =
                GotenbergConverter(
                    DeliverableProperties(maxFileBytes = 1024L * 1024, gotenberg = DeliverableProperties.Gotenberg(url(server), Duration.ofSeconds(5)))
                )
            assertThrows<PdfTooLarge> { runBlocking { small.convert(docx, PdfRequest(false, "Informe", "ENT-001", "A", null)) } }
        } finally {
            server.stop(0)
        }
    }
}
