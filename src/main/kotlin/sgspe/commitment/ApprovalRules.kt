package sgspe.commitment

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import sgspe.common.Callers
import sgspe.common.Finding
import sgspe.common.Permissions
import sgspe.common.ProjectParameters
import sgspe.common.Records
import sgspe.common.Values
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordCriterion
import wasichai.core.data.RecordWrite
import java.util.UUID

// D1 y D2 de WF-D, puras: los campos mínimos de la matriz de DOC-01 §9.3.1.2 (RF-COM-001) y el responsable (RF-COM-005,
// ASM-023), en el orden en que se informan. execution_time es opcional y amount ya es obligatorio desde el alta (RN-COM-004)
object ApprovalData {
    val MATRIX =
        listOf(
            "description",
            "objective",
            "resource_type",
            "quantity",
            "unit",
            "request_medium",
            "agreed_date",
            "due_date",
            "beneficiaries_count",
            "currency"
        )
    val OWNER = listOf("owner_user_id", "owner_user_email")

    // el primer campo que falta, o null
    fun firstMissing(attributes: Map<String, Any?>): String? = (MATRIX + OWNER).firstOrNull { Values.text(attributes[it]) == null }
}

// CommitmentWriteGuard, aprobación (workflows.md WF-D): D1 y D2 al enviar y en cada aprobación, porque pending_approval admite
// cambios; D3 (la acción APPROVE, doble llave, RF-WFL-002) y D4 (quien aprueba no registró el compromiso, RN-COM-005) en
// approve y approve_senior; D5 (approve y approve_direct rechazan el compromiso que exige nivel superior, RN-ENG-002); D8
// (approve_direct solo sin aprobación interna, ASM-008). D6 (políticas de empleo y servidumbres) espera el dato que GAP-016
// deja fuera de alcance (ASM-089) y D7 es SupportPolicyGuard. decide con permisos y datos, nunca con nombres de rol
@Component
class ApprovalRules(
    private val records: Records,
    private val callers: Callers,
    private val permissions: Permissions,
    private val senior: SeniorApprovalReader
) : CommitmentRule {
    override suspend fun findings(
        change: RecordWrite,
        dryRun: Boolean
    ): List<Finding> {
        if (change.objectName != Commitments.COMMITMENT || change.kind != RecordChangeKind.TRANSITIONED) return emptyList()
        val transition = change.transition ?: return emptyList()
        if (transition != SUBMIT && transition !in Commitments.APPROVALS) return emptyList()
        val id = change.recordId ?: return emptyList()
        val before = change.before.orEmpty()
        val findings = mutableListOf<Finding>()
        ApprovalData.firstMissing(before)?.let { field ->
            findings += Finding("FIELD_REQUIRED", "Falta $field: la matriz de compromisos lo exige antes de enviar y de aprobar (D1, D2)", field)
        }
        if (stakeholders(id) == 0) {
            findings += Finding("STAKEHOLDER_REQUIRED", "Falta al menos un grupo de interés solicitante (D1)", Commitments.STAKEHOLDERS)
        }
        if (transition == SUBMIT) return findings
        val user = callers.userOrNull()
        if (transition in DOUBLE_KEY) {
            if (user == null || !permissions.has(user, APPROVE, Commitments.COMMITMENT)) {
                findings += Finding("ACTION_REQUIRED", "Aprobar un compromiso exige la acción APPROVE sobre commitment (D3)", status = HttpStatus.FORBIDDEN)
            }
            if (user == null || createdBy(id, user.userId)) {
                findings +=
                    Finding(
                        "SEGREGATION_OF_DUTIES",
                        "Quien aprueba un compromiso no puede ser quien lo registró (D4, RN-COM-005)",
                        status = HttpStatus.FORBIDDEN
                    )
            }
        }
        if (transition != SENIOR && senior.required(id, before)) {
            findings +=
                Finding(
                    "SENIOR_APPROVAL_REQUIRED",
                    "Este compromiso exige la aprobación de nivel superior (approve_senior, RN-ENG-002)",
                    "origin_engagement"
                )
        }
        if (transition == DIRECT && ProjectParameters.policy(project(before), "policy_internal_approval")) {
            findings +=
                Finding(
                    "INTERNAL_APPROVAL_REQUIRED",
                    "El proyecto tiene la aprobación interna activa: el compromiso se envía a aprobación (D8, ASM-008)",
                    "project"
                )
        }
        return findings
    }

    private suspend fun stakeholders(id: UUID): Int = readAs("stakeholder") { records.related(Commitments.COMMITMENT, id, Commitments.STAKEHOLDERS) }.size

    // created_by es una columna de sistema: se compara en la base como quien llama, como EditAnyGuard. falla cerrada: si quien
    // aprueba no ve el compromiso, el conteo con created_by daría 0 y la segregación pasaría en silencio; es NotReadable
    private suspend fun createdBy(
        id: UUID,
        userId: UUID
    ): Boolean =
        readAs(Commitments.COMMITMENT) {
            if (records.count(Commitments.COMMITMENT, ids = listOf(id)) == 0L) throw NotReadable(Commitments.COMMITMENT)
            records.count(Commitments.COMMITMENT, criteria = listOf(RecordCriterion { _, bind -> "created_by = ${bind(userId)}" }), ids = listOf(id)) > 0
        }

    // el proyecto del compromiso, para sus políticas (ASM-019); fuera del alcance, NotReadable (nunca la política por defecto)
    private suspend fun project(attributes: Map<String, Any?>): Map<String, Any?> {
        val id = Records.uuid(attributes["project"]) ?: return emptyMap()
        return readAs("project") { records.find("project", id) }?.attributes ?: throw NotReadable("project")
    }

    companion object {
        const val SUBMIT = "submit"
        const val SENIOR = "approve_senior"
        const val DIRECT = "approve_direct"
        const val APPROVE = "APPROVE"
        val DOUBLE_KEY = setOf("approve", SENIOR)
    }
}
