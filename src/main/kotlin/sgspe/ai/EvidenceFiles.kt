package sgspe.ai

import kotlinx.coroutines.reactive.awaitSingle
import org.springframework.core.io.buffer.DataBufferLimitException
import org.springframework.core.io.buffer.DataBufferUtils
import org.springframework.stereotype.Component
import wasichai.files.FileStore
import wasichai.files.StoredFileRepository
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
    // null si el archivo no existe en la organización o pasa `maxBytes`
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
            }
        try {
            val bytes = ByteArray(joined.readableByteCount())
            joined.read(bytes)
            return StoredBytes(file.contentType, bytes)
        } finally {
            DataBufferUtils.release(joined)
        }
    }
}
