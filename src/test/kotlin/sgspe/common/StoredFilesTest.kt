package sgspe.common

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.slf4j.LoggerFactory
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.core.io.buffer.DefaultDataBufferFactory
import reactor.core.publisher.Flux
import sgspe.ai.CorpusText
import sgspe.ai.DocumentText
import sgspe.ai.IndexableEvidence
import sgspe.ai.KnowledgeEvidences
import sgspe.ai.KnowledgeProperties
import wasichai.files.FileStore
import wasichai.files.StoredFile
import wasichai.files.StoredFileRepository
import java.nio.file.NoSuchFileException
import java.time.Instant
import java.util.UUID

// los bytes de un archivo para los trabajos de fondo (JOB-07, JOB-05) con dobles del almacén y de stored_files: lo grande, lo que falta y lo que se lee
class StoredFilesTest {
    private val organization = UUID.randomUUID()
    private val fileId = UUID.randomUUID()
    private val log = LoggerFactory.getLogger(StoredFiles::class.java) as ch.qos.logback.classic.Logger
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

    private fun stored(size: Long) =
        StoredFile(fileId, organization, UUID.randomUUID(), "file", "k/$fileId", "a.pdf", "application/pdf", size, "h", null, Instant.EPOCH)

    private fun files(found: StoredFile?): StoredFileRepository =
        mock(StoredFileRepository::class.java) { call -> if (call.method.name == "find") found else null }

    private fun store(open: () -> Flux<DataBuffer>): FileStore =
        object : FileStore {
            override suspend fun put(
                key: String,
                bytes: ByteArray,
                contentType: String
            ) = Unit

            override fun open(key: String) = open()

            override suspend fun delete(key: String) = Unit
        }

    private fun content(bytes: ByteArray): Flux<DataBuffer> = Flux.just(DefaultDataBufferFactory.sharedInstance.wrap(bytes))

    @Test
    fun `lee los bytes y el tipo`() {
        val bytes = "hola".toByteArray()
        val read = runBlocking { StoredFiles(files(stored(4)), store { content(bytes) }).read(organization, fileId, 100) }
        read as StoredRead.Found
        assertEquals("application/pdf", read.file.contentType)
        assertArrayEquals(bytes, read.file.bytes)
    }

    @Test
    fun `un archivo cuyo tamano pasa el tope no se lee y es definitivo`() {
        var opened = false
        val open = {
            opened = true
            content(ByteArray(1))
        }
        val reader = StoredFiles(files(stored(101)), store(open))
        assertEquals(StoredRead.TooLarge, runBlocking { reader.read(organization, fileId, 100) })
        assertTrue(!opened)
    }

    @Test
    fun `un archivo que no esta registrado falta, y se vuelve a intentar`() {
        assertEquals(StoredRead.Missing, runBlocking { StoredFiles(files(null), store { content(ByteArray(1)) }).read(organization, fileId, 100) })
    }

    @Test
    fun `si los bytes pasan el tope al leerlos tampoco se lee`() {
        val reader = StoredFiles(files(stored(5)), store { content(ByteArray(500)) })
        assertEquals(StoredRead.TooLarge, runBlocking { reader.read(organization, fileId, 100) })
    }

    @Test
    fun `un objeto que falta en el almacen falta, con el id en la advertencia`() {
        val reader = StoredFiles(files(stored(5)), store { Flux.error(NoSuchFileException("k")) })
        assertEquals(StoredRead.Missing, runBlocking { reader.read(organization, fileId, 100) })
        assertEquals(1, lines.list.size)
        assertTrue(fileId.toString() in lines.list.single().formattedMessage)
        assertNotNull(lines.list.single())
    }

    @Test
    fun `un error de E S pasajero se propaga`() {
        val reader = StoredFiles(files(stored(5)), store { Flux.error(java.io.IOException("red")) })
        assertThrows(java.io.IOException::class.java) { runBlocking { reader.read(organization, fileId, 100) } }
    }

    // el texto de una evidencia para JOB-07 (KnowledgeEvidences): sin archivo o con uno más grande que el tope, sin texto
    // definitivo; un archivo que falta, no disponible por ahora (la vuelta siguiente lo vuelve a intentar)
    @Test
    fun `el corpus distingue sin texto de no disponible`() {
        val evidence = IndexableEvidence(UUID.randomUUID(), null, "INTERNA", null, fileId)
        val documents = DocumentText { bytes, _ -> CorpusText.Text(String(bytes)) }

        fun corpus(files: StoredFiles) = KnowledgeEvidences(mock(Records::class.java), files, documents, KnowledgeProperties(maxBytes = 100))
        val found = corpus(StoredFiles(files(stored(4)), store { content("hola".toByteArray()) }))
        assertEquals(CorpusText.Text("hola"), runBlocking { found.text(organization, evidence) })
        assertEquals(CorpusText.NoText, runBlocking { found.text(organization, evidence.copy(file = null)) })
        val large = corpus(StoredFiles(files(stored(101)), store { content(ByteArray(1)) }))
        assertEquals(CorpusText.NoText, runBlocking { large.text(organization, evidence) })
        val missing = corpus(StoredFiles(files(null), store { content(ByteArray(1)) }))
        assertEquals(CorpusText.Unavailable, runBlocking { missing.text(organization, evidence) })
    }
}
