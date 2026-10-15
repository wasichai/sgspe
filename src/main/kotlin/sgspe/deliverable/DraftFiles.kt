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
// draft_warnings en una sola escritura. es un puerto (nació mientras UPS-31 seguía abierta, wasichai/wasichai#111) para que
// las pruebas puedan cambiarlo. JOB-05 lo llama fuera de asPlatform: cada implementación pone su propio contexto
fun interface DraftFiles {
    suspend fun attach(
        organizationId: UUID,
        deliverableId: UUID,
        files: List<DraftFile>,
        fields: Map<String, Any?>
    )
}

// la implementación de producción: dentro de asPlatform, FileService.stage de cada archivo y una sola escritura de derivados
// (Records.patch, con la marca SgspeWrite) con los ids, LISTO y los avisos. desde wasichai 1.3.0 (UPS-31, WAS-ADR-0068) la
// plataforma sube sin `created_by` y solo una escritura del mismo bloque asPlatform adjunta lo que subió: el stage y el patch
// van en el mismo bloque
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
