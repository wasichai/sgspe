package sgspe.attention

import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import sgspe.common.Attention
import sgspe.common.Callers
import sgspe.common.Finding
import sgspe.common.GuardOrder
import sgspe.common.NotReadable
import sgspe.common.Permissions
import sgspe.common.ProjectParameters
import sgspe.common.Records
import sgspe.common.RuleGuard
import sgspe.common.Values
import sgspe.common.WriteMark
import sgspe.common.WriteView
import sgspe.common.isLink
import sgspe.common.readable
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordWrite

// AttentionWriteGuard (sgspe.attention, especificación de F4 §3.2): las reglas de las QCS por cualquier puerta (la API
// genérica, los comandos y la consulta en seco). las de datos son AttentionRules; aquí se suman las que miran permisos o leen
// otros registros como quien escribe: la confidencialidad (READ_CONFIDENTIAL, ASM-095; desde D-3, marcar confidencial una QCS
// existente lo hace su receptor, su responsable o quien tiene READ_CONFIDENTIAL), la queja grave
// (policy_high_severity_requires_approver del proyecto, RN-GRV-002), la doble llave de resolve_high_severity (APPROVE) y el
// cierre sin ejecución (close_unfulfilled con el compromiso cancelado). una escritura sin usuario (la plataforma, una
// automatización) no tiene permisos que comprobar; el alta de la reclasificación copia el receptor del original y no lo
// juzga (AttentionRules.receiverCopied). lo que no se puede leer es 403 OBJECT_NOT_READABLE, nunca un pase
@Component
@Order(GuardOrder.DOMAIN)
class AttentionWriteGuard(
    private val records: Records,
    private val view: WriteView,
    private val callers: Callers,
    private val permissions: Permissions
) : RuleGuard() {
    override suspend fun findings(
        change: RecordWrite,
        dryRun: Boolean
    ): List<Finding> {
        if (change.objectName !in Attention.OBJECTS) return emptyList()
        return try {
            when {
                change.kind == RecordChangeKind.TRANSITIONED -> transition(change)
                (change.kind == RecordChangeKind.CREATED || change.kind == RecordChangeKind.UPDATED) && !change.isLink() -> record(change)
                else -> emptyList()
            }
        } catch (e: NotReadable) {
            listOf(
                Finding(
                    "OBJECT_NOT_READABLE",
                    "La regla necesita leer ${e.objectName}: el registro no existe o quien escribe no puede leerlo",
                    e.objectName,
                    status = HttpStatus.FORBIDDEN
                )
            )
        }
    }

    private suspend fun record(change: RecordWrite): List<Finding> {
        val after = view.after(change)
        val findings = AttentionRules.record(change.objectName, change.recordId, after).toMutableList()
        val writer = change.userId ?: return findings
        if (change.kind == RecordChangeKind.CREATED) {
            if (AttentionRules.foreignReceiver(after, writer) &&
                !AttentionRules.receiverCopied(change.objectName, after, WriteMark.present()) &&
                !holds(Attention.READ_CONFIDENTIAL, change.objectName)
            ) {
                findings +=
                    Finding(
                        "CONFIDENTIAL_RECEIVER_REQUIRED",
                        "Una QCS confidencial la recibe quien la registra: received_by_user_id debe ser quien escribe",
                        Attention.RECEIVER,
                        status = HttpStatus.BAD_REQUEST
                    )
            }
            return findings
        }
        if (AttentionRules.strangerMarks(change.before.orEmpty(), after, writer) && !holds(Attention.READ_CONFIDENTIAL, change.objectName)) {
            findings += AttentionRules.confidentialMarkForbidden()
        }
        val protected = AttentionRules.protectedChanges(change.before.orEmpty(), after)
        if (protected.isNotEmpty() && !holds(Attention.READ_CONFIDENTIAL, change.objectName)) {
            findings +=
                protected.map { field ->
                    Finding(
                        "ACTION_REQUIRED",
                        "Cambiar $field de una QCS confidencial exige la acción READ_CONFIDENTIAL sobre ${change.objectName}",
                        field,
                        status = HttpStatus.FORBIDDEN
                    )
                }
        }
        return findings
    }

    private suspend fun transition(change: RecordWrite): List<Finding> {
        val name = change.transition ?: return emptyList()
        val record = change.before.orEmpty()
        val findings = AttentionRules.transition(change.objectName, name, record).toMutableList()
        if (findings.any { it.blocking }) return findings
        if (change.objectName == Attention.GRIEVANCE && name == AttentionRules.RESOLVE_PLAIN && AttentionRules.grave(record)) {
            val project = Records.uuid(record["project"])?.let { records.readable("project", it).attributes }.orEmpty()
            if (ProjectParameters.policy(project, POLICY)) {
                findings +=
                    Finding(
                        "SENIOR_APPROVAL_REQUIRED",
                        "Una queja ${Values.text(record[Attention.SEVERITY])} se resuelve con resolve_high_severity, por quien tiene APPROVE (RN-GRV-002)",
                        status = HttpStatus.CONFLICT
                    )
            }
        }
        if (change.objectName == Attention.GRIEVANCE && name == AttentionRules.RESOLVE_HIGH && change.userId != null &&
            !holds(Attention.APPROVE, Attention.GRIEVANCE)
        ) {
            findings += Finding("ACTION_REQUIRED", "Resolver una queja grave exige la acción APPROVE sobre grievance", status = HttpStatus.FORBIDDEN)
        }
        if (change.objectName == Attention.REQUEST && name == AttentionRules.CLOSE_UNFULFILLED) {
            val commitment = Records.uuid(record[AttentionRules.CREATED_COMMITMENT])?.let { records.readable(COMMITMENT, it) }
            if (commitment?.state != CANCELLED) {
                findings +=
                    Finding(
                        "STATE_MISMATCH",
                        "close_unfulfilled cierra una solicitud cuyo compromiso está cancelado; el suyo está en ${commitment?.state ?: "ninguno"}",
                        AttentionRules.CREATED_COMMITMENT
                    )
            }
        }
        return findings
    }

    // las acciones de quien escribe; sin usuario identificable, no las tiene (falla cerrada)
    private suspend fun holds(
        action: String,
        objectName: String
    ): Boolean {
        val user = callers.userOrNull() ?: return false
        return permissions.has(user, action, objectName)
    }

    companion object {
        const val POLICY = "policy_high_severity_requires_approver"
        const val COMMITMENT = "commitment"
        const val CANCELLED = "cancelled"
    }
}
