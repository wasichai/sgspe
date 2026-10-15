package sgspe.deliverable

import org.springframework.core.io.buffer.DefaultDataBufferFactory
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import sgspe.common.Records
import wasichai.files.FileService
import wasichai.files.FileUpload
import java.util.UUID

// un archivo del borrador listo para subir: su campo FILE, su nombre, el tipo declarado (el modelo como text/plain y los DOCX
// con su tipo, por el sniffer de wasichai-files, decisión 3 del plan 8.1) y sus bytes
class DraftFile(
    val field: String,
    val fileName: String,
    val contentType: String,
    val bytes: ByteArray
)

// el último paso de JOB-05 (especificación de F8 §3.3, paso 4; ASM-133): sube los siete archivos y los adjunta con LISTO y
// draft_warnings en una sola escritura. es un puerto porque wasichai-files 1.2.0 no deja a la plataforma subir ni adjuntar
// (UPS-31, wasichai/wasichai#111): la Tarea 15 del plan 8.2b sube a la versión que lo permite. JOB-05 lo llama fuera de
// asPlatform: cada implementación pone su propio contexto
fun interface DraftFiles {
    suspend fun attach(
        organizationId: UUID,
        deliverableId: UUID,
        files: List<DraftFile>,
        fields: Map<String, Any?>
    )
}

// la implementación de producción: dentro de asPlatform, FileService.stage de cada archivo y una sola escritura de derivados
// (Records.patch, con la marca SgspeWrite) con los ids, LISTO y los avisos. con wasichai 1.2.0, stage exige una persona y
// falla (el borrador queda FALLIDO con GENERATION_FAILED); con la versión que resuelve UPS-31, la plataforma sube y adjunta
@Component
class PlatformDraftFiles(
    private val records: Records,
    private val files: FileService
) : DraftFiles {
    override suspend fun attach(
        organizationId: UUID,
        deliverableId: UUID,
        files: List<DraftFile>,
        fields: Map<String, Any?>
    ) {
        records.asPlatform(organizationId, Deliverables.JOB_SOURCE) {
            val ids =
                files.associate { file ->
                    val upload = FileUpload(file.fileName, file.contentType, Flux.just(DefaultDataBufferFactory.sharedInstance.wrap(file.bytes)))
                    file.field to
                        this.files
                            .stage(Deliverables.DELIVERABLE, file.field, upload)
                            .id
                            .toString()
                }
            records.patch(Deliverables.DELIVERABLE, deliverableId, fields + ids, Deliverables.JOB_REASON)
        }
    }
}
