package sgspe.common

import kotlinx.coroutines.reactive.awaitSingle
import org.slf4j.LoggerFactory
import org.springframework.core.io.buffer.DataBufferLimitException
import org.springframework.core.io.buffer.DataBufferUtils
import org.springframework.stereotype.Component
import wasichai.core.common.NotFoundException
import wasichai.files.FileDescriptor
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

// lo que dio leer el archivo de una evidencia: sus bytes; más grande que el tope (definitivo: el archivo es inmutable, ASM-013); o
// que falta (su fila en stored_files o su objeto en el almacén): quizá pasajero, JOB-07 lo vuelve a intentar
sealed interface StoredRead {
    data class Found(
        val file: StoredBytes
    ) : StoredRead

    data object TooLarge : StoredRead

    data object Missing : StoredRead
}

// los bytes y el descriptor de un archivo de wasichai-files para los trabajos de fondo, como la plataforma: escrito cuando
// FileService exigía una persona (UPS-31, resuelta en wasichai 1.3.0, que ya deja abrir dentro de asPlatform; pasar JOB-05 y
// JOB-07 a FileService.open es otra tarea), así que se lee la fila de stored_files de la organización y el almacén, como
// StoredFileCleanup. lo usan JOB-07 (el texto de una evidencia, decisión 5 del plan 7.2c), JOB-05 (las fotos de un entregable,
// con su SHA-256 comprobado contra el descriptor) y DeliverableWriteGuard (el SHA-256 del archivo de una versión). el id sale
// de un campo FILE que el código leyó; nunca de una petición. a lo sumo `maxBytes` en memoria: uno más grande no se lee.
// ArchitectureTest vigila que solo este archivo use StoredFileRepository y FileStore
@Component
class StoredFiles(
    private val files: StoredFileRepository,
    private val store: FileStore
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // Missing si el archivo no existe en la organización o su objeto falta en el almacén (una advertencia con solo el id);
    // TooLarge si pasa `maxBytes`; un error de E/S pasajero sí se propaga
    suspend fun read(
        organizationId: UUID,
        fileId: UUID,
        maxBytes: Long
    ): StoredRead {
        val file = files.find(organizationId, fileId) ?: return StoredRead.Missing
        if (file.sizeBytes > maxBytes) return StoredRead.TooLarge
        val joined =
            try {
                DataBufferUtils.join(store.open(file.objectKey), maxBytes.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()).awaitSingle()
            } catch (_: DataBufferLimitException) {
                return StoredRead.TooLarge
            } catch (e: Exception) {
                if (!missing(e)) throw e
                log.warn("StoredFiles: falta en el almacén el archivo {} de la organización {}", fileId, organizationId)
                return StoredRead.Missing
            }
        try {
            val bytes = ByteArray(joined.readableByteCount())
            joined.read(bytes)
            return StoredRead.Found(StoredBytes(file.contentType, bytes))
        } finally {
            DataBufferUtils.release(joined)
        }
    }

    // el descriptor de un archivo subido de la organización ({ id, name, contentType, size, sha256 }), adjunto o no; null si
    // no existe
    suspend fun find(
        organizationId: UUID,
        fileId: UUID
    ): FileDescriptor? = files.find(organizationId, fileId)?.descriptor()

    // el objeto no está en el almacén: cada almacén lo dice a su manera (local, S3 envuelto en un CompletionException)
    private fun missing(e: Throwable): Boolean =
        generateSequence(e) { it.cause }.take(MAX_DEPTH).any {
            it is NoSuchFileException || it is FileNotFoundException || it is NotFoundException || it.javaClass.simpleName == "NoSuchKeyException"
        }

    private companion object {
        const val MAX_DEPTH = 6
    }
}
