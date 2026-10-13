package sgspe.ai

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.slf4j.LoggerFactory
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.core.io.buffer.DefaultDataBufferFactory
import reactor.core.publisher.Flux
import wasichai.files.FileStore
import wasichai.files.StoredFile
import wasichai.files.StoredFileRepository
import java.nio.file.NoSuchFileException
import java.time.Instant
import java.util.UUID

// los bytes de un archivo para JOB-07 con dobles del almacén y de stored_files: lo grande, lo que falta y lo que se lee
class EvidenceFilesTest {
    private val organization = UUID.randomUUID()
    private val fileId = UUID.randomUUID()
    private val log = LoggerFactory.getLogger(EvidenceFiles::class.java) as ch.qos.logback.classic.Logger
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
        val read = runBlocking { EvidenceFiles(files(stored(4)), store { content(bytes) }).read(organization, fileId, 100) }
        assertEquals("application/pdf", read!!.contentType)
        assertArrayEquals(bytes, read.bytes)
    }

    @Test
    fun `un archivo que no esta registrado o cuyo tamano pasa el tope no se lee`() {
        var opened = false
        val open = {
            opened = true
            content(ByteArray(1))
        }
        val reader = EvidenceFiles(files(stored(101)), store(open))
        assertNull(runBlocking { reader.read(organization, fileId, 100) })
        assertTrue(!opened)
        assertNull(runBlocking { EvidenceFiles(files(null), store { content(ByteArray(1)) }).read(organization, fileId, 100) })
    }

    @Test
    fun `si los bytes pasan el tope al leerlos tampoco se lee`() {
        val reader = EvidenceFiles(files(stored(5)), store { content(ByteArray(500)) })
        assertNull(runBlocking { reader.read(organization, fileId, 100) })
    }

    @Test
    fun `un objeto que falta en el almacen es sin texto, con el id en la advertencia`() {
        val reader = EvidenceFiles(files(stored(5)), store { Flux.error(NoSuchFileException("k")) })
        assertNull(runBlocking { reader.read(organization, fileId, 100) })
        assertEquals(1, lines.list.size)
        assertTrue(fileId.toString() in lines.list.single().formattedMessage)
        assertNotNull(lines.list.single())
    }

    @Test
    fun `un error de E S pasajero se propaga`() {
        val reader = EvidenceFiles(files(stored(5)), store { Flux.error(java.io.IOException("red")) })
        assertThrows(java.io.IOException::class.java) { runBlocking { reader.read(organization, fileId, 100) } }
    }
}
