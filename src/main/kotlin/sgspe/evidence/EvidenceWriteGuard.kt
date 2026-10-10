package sgspe.evidence

import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Component
import sgspe.common.Attention
import sgspe.common.Callers
import sgspe.common.Finding
import sgspe.common.GuardOrder
import sgspe.common.Permissions
import sgspe.common.Records
import sgspe.common.RuleGuard
import sgspe.common.Transactions
import sgspe.common.WriteView
import sgspe.common.isLink
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordWrite
import wasichai.core.metadata.MetadataService
import wasichai.core.platform.SqlIdentifier
import wasichai.core.platform.WasichaiSchemas
import java.util.UUID

// la evidencia inmutable (RF-DOC-001, ASM-013) y su clasificación (RF-DOC-003, ASM-071): una evidencia no se borra, su archivo,
// la versión a la que reemplaza y su importación no cambian, y bajar la clasificación de una restringida exige
// DOWNLOAD_RESTRICTED, para que reclasificar no abra la descarga. evidence es apiOnly: la API genérica ya no la escribe; la
// guarda vale igual para los comandos de sgspe.evidence, /admin y cualquier otra puerta (WAS-ADR-0040). el motivo al cambiar
// la clasificación lo exige FieldChangeReasonGuard. un archivo pertenece a una sola evidencia: adjuntarlo a otra abriría la
// descarga de una restringida con otra clasificación (ASM-071) y un reintento sin Idempotency-Key dejaría un duplicado que
// no se borra. desde F4, una evidencia que no es CONFIDENCIAL ni DATOS PERSONALES no se vincula a una QCS confidencial
// (409 EVIDENCE_NOT_RESTRICTED, ASM-095): su descarga no exigiría DOWNLOAD_RESTRICTED y abriría el caso reservado
@Component
@Order(GuardOrder.DOMAIN)
class EvidenceWriteGuard(
    private val view: WriteView,
    private val callers: Callers,
    private val permissions: Permissions,
    private val transactions: Transactions,
    private val metadata: MetadataService,
    private val schemas: WasichaiSchemas,
    private val db: DatabaseClient,
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
        if (change.kind == RecordChangeKind.CREATED) return fileInUse(change, dryRun)
        if (change.kind == RecordChangeKind.UPDATED && change.isLink()) return confidentialLinks(change)
        if (change.kind != RecordChangeKind.UPDATED) return emptyList()
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

    // el vínculo de una evidencia no restringida con una QCS confidencial, en el extremo de la evidencia (wasichai llama a las
    // guardas con cada extremo). la QCS se lee como quien vincula: wasichai ya comprobó que la lee. deshacer un vínculo no se juzga
    private suspend fun confidentialLinks(change: RecordWrite): List<Finding> {
        if (EvidenceRules.restricted(change.before.orEmpty()[EvidenceRules.CLASSIFICATION])) return emptyList()
        return Attention.OBJECTS.mapNotNull { target ->
            val id = Records.uuid(change.attributes.orEmpty()["rel:${EvidenceRules.linkRelationship(target)}"]) ?: return@mapNotNull null
            val qcs = records.find(target, id) ?: return@mapNotNull null
            if (!Attention.isConfidential(qcs.attributes)) return@mapNotNull null
            Finding(
                "EVIDENCE_NOT_RESTRICTED",
                "Una evidencia vinculada a una QCS confidencial debe ser CONFIDENCIAL o DATOS PERSONALES: cambie su clasificación antes",
                EvidenceRules.CLASSIFICATION,
                status = HttpStatus.CONFLICT
            )
        }
    }

    // el archivo de un alta que ya tiene otra evidencia. la comprobación es de la base y no de quien llama, como en
    // DependentsDeleteGuard: con Records solo vería las evidencias que quien llama lee (una de un proyecto del que ya no es
    // miembro no contaría), y asPlatform no se usa dentro de una petición. por eso es un SELECT 1 … LIMIT 1 sobre la columna
    // uuid del campo FILE en la tabla del tenant, con el índice que wasichai-files le pone. la escritura toma antes el candado
    // del archivo (`evidence-file:<id>`): la guarda corre dentro de la transacción del alta (Evidences.create la abre y
    // RecordService se une a ella), así que el candado dura hasta el commit y dos altas simultáneas con el mismo archivo
    // quedan en fila; la segunda ve la primera. la consulta en seco no lo toma. la evidencia nueva no se cuenta: la guarda
    // corre antes de guardar. un id que no es UUID lo rechaza wasichai-files
    private suspend fun fileInUse(
        change: RecordWrite,
        dryRun: Boolean
    ): List<Finding> {
        val fileId = Records.uuid(EvidenceRules.fileId(change.attributes.orEmpty()[EvidenceRules.FILE])) ?: return emptyList()
        val used = if (dryRun) attached(change, fileId) else transactions.locked("$FILE_LOCK:$fileId") { attached(change, fileId) }
        if (!used) return emptyList()
        return listOf(
            Finding(
                "EVIDENCE_FILE_IN_USE",
                "Ese archivo ya es de otra evidencia: suba el archivo de nuevo o reemplace esa evidencia con una versión nueva",
                EvidenceRules.FILE,
                status = HttpStatus.CONFLICT
            )
        )
    }

    // si alguna evidencia del tenant ya guarda `fileId` en su campo FILE
    private suspend fun attached(
        change: RecordWrite,
        fileId: UUID
    ): Boolean {
        val definition = metadata.loadDefinition(change.organizationId, EvidenceRules.EVIDENCE)
        val column = definition.fields.first { it.name == EvidenceRules.FILE }.columnName
        val table = schemas.dataTable(definition.obj.physicalTable)
        return db
            .sql("SELECT 1 AS one FROM $table WHERE organization_id = :organizationId AND ${SqlIdentifier.quote(column)} = :fileId LIMIT 1")
            .bind("organizationId", change.organizationId)
            .bind("fileId", fileId)
            .fetch()
            .first()
            .awaitSingleOrNull() != null
    }

    companion object {
        // el candado de un archivo mientras un alta lo adjunta (Transactions.locked le antepone `sgspe.`)
        const val FILE_LOCK = "evidence-file"
    }
}
