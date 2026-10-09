package sgspe.evidence

import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import sgspe.common.Callers
import sgspe.common.Criteria
import sgspe.common.Finding
import sgspe.common.GuardOrder
import sgspe.common.Permissions
import sgspe.common.Records
import sgspe.common.RuleGuard
import sgspe.common.WriteView
import sgspe.common.isLink
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordWrite

// la evidencia inmutable (RF-DOC-001, ASM-013) y su clasificación (RF-DOC-003, ASM-071): una evidencia no se borra, su archivo,
// la versión a la que reemplaza y su importación no cambian, y bajar la clasificación de una restringida exige
// DOWNLOAD_RESTRICTED, para que reclasificar no abra la descarga. evidence es apiOnly: la API genérica ya no la escribe; la
// guarda vale igual para los comandos de sgspe.evidence, /admin y cualquier otra puerta (WAS-ADR-0040). el motivo al cambiar
// la clasificación lo exige FieldChangeReasonGuard. un archivo pertenece a una sola evidencia: adjuntarlo a otra abriría la
// descarga de una restringida con otra clasificación (ASM-071) y un reintento sin Idempotency-Key dejaría un duplicado que
// no se borra
@Component
@Order(GuardOrder.DOMAIN)
class EvidenceWriteGuard(
    private val view: WriteView,
    private val callers: Callers,
    private val permissions: Permissions,
    private val records: Records
) : RuleGuard() {
    override suspend fun findings(
        change: RecordWrite,
        dryRun: Boolean
    ): List<Finding> {
        if (change.objectName != EvidenceRules.EVIDENCE) return emptyList()
        if (change.kind == RecordChangeKind.DELETED) {
            return listOf(
                Finding("EVIDENCE_NOT_DELETABLE", "Una evidencia no se borra: se reemplaza con una versión nueva", status = HttpStatus.CONFLICT)
            )
        }
        if (change.kind == RecordChangeKind.CREATED) return fileInUse(change)
        if (change.kind != RecordChangeKind.UPDATED || change.isLink()) return emptyList()
        val before = change.before.orEmpty()
        val after = view.after(change)
        val findings =
            EvidenceRules.changedImmutable(before, after).mapTo(mutableListOf()) { field ->
                Finding(
                    "EVIDENCE_IMMUTABLE",
                    "El campo $field de una evidencia no cambia: suba una evidencia nueva que reemplace a esta",
                    field,
                    status = HttpStatus.CONFLICT
                )
            }
        if (change.userId != null && EvidenceRules.downgrade(before[EvidenceRules.CLASSIFICATION], after[EvidenceRules.CLASSIFICATION])) {
            val user = callers.userOrNull()
            if (user == null || !permissions.has(user, EvidenceRules.DOWNLOAD_RESTRICTED, EvidenceRules.EVIDENCE)) {
                findings +=
                    Finding(
                        "ACTION_REQUIRED",
                        "Bajar la clasificación de una evidencia restringida exige la acción DOWNLOAD_RESTRICTED sobre evidence",
                        EvidenceRules.CLASSIFICATION,
                        status = HttpStatus.FORBIDDEN
                    )
            }
        }
        return findings
    }

    // el archivo de un alta que ya tiene otra evidencia: una lectura acotada por el id del archivo, como quien llama. la guarda
    // corre antes de guardar, así que la evidencia nueva no se cuenta. un id que no es UUID lo rechaza wasichai-files
    private suspend fun fileInUse(change: RecordWrite): List<Finding> {
        val fileId = Records.uuid(EvidenceRules.fileId(change.attributes.orEmpty()[EvidenceRules.FILE])) ?: return emptyList()
        if (records.count(EvidenceRules.EVIDENCE, criteria = listOf(Criteria.anyOf(EvidenceRules.FILE, listOf(fileId)))) == 0L) return emptyList()
        return listOf(
            Finding(
                "EVIDENCE_FILE_IN_USE",
                "Ese archivo ya es de otra evidencia: suba el archivo de nuevo o reemplace esa evidencia con una versión nueva",
                EvidenceRules.FILE,
                status = HttpStatus.CONFLICT
            )
        )
    }
}
