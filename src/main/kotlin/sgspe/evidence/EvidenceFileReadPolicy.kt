package sgspe.evidence

import org.springframework.stereotype.Component
import sgspe.common.Permissions
import sgspe.common.RequestMemo
import wasichai.files.FileDescriptorReadPolicy
import wasichai.files.FileRead

// el descriptor del archivo de una evidencia según quien lo lee (UPS-24, wasichai 1.0.0, WAS-ADR-065): si la evidencia es
// CONFIDENCIAL o DATOS PERSONALES, o su clasificación falta (falla cerrado, como la descarga), quien no tiene
// DOWNLOAD_RESTRICTED lo lee sin nombre ni huella. wasichai pregunta con el registro tal como está guardado, así que decide
// aunque quien llama no lea la clasificación, y lo hace en cada ruta por la que sale el descriptor: el registro, la lista,
// los vínculos, las respuestas de escritura, la historia y /api/audit. ADMIN tiene todas las acciones y lo lee entero. la
// respuesta de una subida (sin registro todavía) no cambia: quien sube ya tiene el archivo
@Component
class EvidenceFileReadPolicy(
    private val permissions: Permissions
) : FileDescriptorReadPolicy {
    override suspend fun descriptor(read: FileRead): Map<String, Any?> {
        val record = read.record ?: return read.descriptor
        if (read.definition.obj.name != EvidenceRules.EVIDENCE || read.field.name != EvidenceRules.FILE) return read.descriptor
        if (!EvidenceRules.downloadRestricted(record)) return read.descriptor
        val user = read.caller
        // una lista de evidencias pregunta por cada una: la acción se pregunta una vez por petición (RequestMemo)
        val mayDownload =
            user.isAdmin ||
                RequestMemo.rememberForRequest(MEMO to user.userId) {
                    permissions.has(user, EvidenceRules.DOWNLOAD_RESTRICTED, EvidenceRules.EVIDENCE)
                }
        return if (mayDownload) read.descriptor else EvidenceRules.reserved(read.descriptor)
    }

    private companion object {
        const val MEMO = "evidence-download-restricted"
    }
}
