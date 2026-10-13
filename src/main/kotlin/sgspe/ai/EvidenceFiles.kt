package sgspe.ai

import kotlinx.coroutines.reactive.awaitSingle
import org.slf4j.LoggerFactory
import org.springframework.core.io.buffer.DataBufferLimitException
import org.springframework.core.io.buffer.DataBufferUtils
import org.springframework.stereotype.Component
import wasichai.core.common.NotFoundException
import wasichai.files.FileStore
import wasichai.files.StoredFileRepository
import java.io.FileNotFoundException
import java.nio.file.NoSuchFileException
import java.util.UUID

// los bytes de un archivo y su tipo detectado al subirlo
class StoredBytes(
    val contentType: String,
    val bytes: ByteArray
)

// los bytes del archivo de una evidencia para JOB-07, como la plataforma (decisión 5 del plan 7.2c): FileService.open de
// wasichai-files 1.2.0 exige una persona, así que el trabajo lee la fila de stored_files de su organización y el almacén, como
// StoredFileCleanup. el id sale del campo FILE de la evidencia que el trabajo leyó; nunca de una petición. a lo sumo `maxBytes` en
// memoria: uno más grande no se lee. ArchitectureTest vigila que solo este archivo use StoredFileRepository y FileStore
@Component
class EvidenceFiles(
    private val files: StoredFileRepository,
    private val store: FileStore
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // null si el archivo no existe en la organización, su objeto falta en el almacén (una advertencia con solo el id) o pasa
    // `maxBytes`; un error de E/S pasajero sí se propaga
    suspend fun read(
        organizationId: UUID,
        fileId: UUID,
        maxBytes: Long
    ): StoredBytes? {
        val file = files.find(organizationId, fileId) ?: return null
        if (file.sizeBytes > maxBytes) return null
        val joined =
            try {
                DataBufferUtils.join(store.open(file.objectKey), maxBytes.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()).awaitSingle()
            } catch (_: DataBufferLimitException) {
                return null
            } catch (e: Exception) {
                if (!missing(e)) throw e
                log.warn("KnowledgeIndexJob: falta en el almacén el archivo {} de la organización {}", fileId, organizationId)
                return null
            }
        try {
            val bytes = ByteArray(joined.readableByteCount())
            joined.read(bytes)
            return StoredBytes(file.contentType, bytes)
        } finally {
            DataBufferUtils.release(joined)
        }
    }

    // el objeto no está en el almacén: cada almacén lo dice a su manera (local, S3 envuelto en un CompletionException)
    private fun missing(e: Throwable): Boolean =
        generateSequence(e) { it.cause }.take(MAX_DEPTH).any {
            it is NoSuchFileException || it is FileNotFoundException || it is NotFoundException || it.javaClass.simpleName == "NoSuchKeyException"
        }

    private companion object {
        const val MAX_DEPTH = 6
    }
}
