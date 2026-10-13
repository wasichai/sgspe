package sgspe.ai

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory

// el texto que JOB-07 indexa: lo largo se corta, lo roto o sin texto es null y solo lo roto avisa
class EvidenceTextTest {
    private val text = EvidenceText(TextExtraction(), KnowledgeProperties(chunkChars = 200, overlapChars = 20, maxChars = 500))
    private val log = LoggerFactory.getLogger(EvidenceText::class.java) as ch.qos.logback.classic.Logger
    private val lines = ListAppender<ILoggingEvent>()

    @BeforeEach
    fun capture() {
        log.level = Level.WARN
        lines.start()
        log.addAppender(lines)
    }

    @AfterEach
    fun release() {
        log.detachAppender(lines)
    }

    @Test
    fun `un texto largo se corta en maxChars`() {
        val bytes = TestDocuments.pdf(*Array(40) { "x".repeat(90) })
        val read = runBlocking { text.text(bytes, "application/pdf") }
        assertEquals(500, (read as CorpusText.Text).value.length)
    }

    @Test
    fun `un PDF roto es sin texto y avisa solo con la clase del error`() {
        assertEquals(CorpusText.NoText, runBlocking { text.text("%PDF-1.4 roto".toByteArray(), "application/pdf") })
        assertTrue(lines.list.size <= 1)
        assertTrue(lines.list.none { "roto" in it.formattedMessage })
    }

    @Test
    fun `un PDF escaneado es sin texto y no avisa`() {
        assertEquals(CorpusText.NoText, runBlocking { text.text(TestDocuments.scannedPdf(), "application/pdf") })
        assertTrue(lines.list.isEmpty())
    }

    @Test
    fun `un error inesperado al extraer no es sin texto sino no disponible, y avisa solo con la clase`() {
        val failing =
            object : TextExtraction() {
                override fun extract(
                    bytes: ByteArray,
                    contentType: String,
                    fileName: String?,
                    maxChars: Int,
                    truncate: Boolean
                ): String = throw IllegalStateException("contenido secreto")
            }
        val read = runBlocking { EvidenceText(failing, KnowledgeProperties()).text(ByteArray(1), "application/pdf") }
        assertEquals(CorpusText.Unavailable, read)
        assertTrue(
            lines.list
                .single()
                .formattedMessage
                .contains("IllegalStateException")
        )
        assertTrue(lines.list.none { "secreto" in it.formattedMessage })
    }
}
