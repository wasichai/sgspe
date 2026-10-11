package sgspe.investment

import org.springframework.stereotype.Component
import sgspe.commitment.projectOf
import sgspe.common.Callers
import sgspe.common.Finding
import sgspe.common.NotReadable
import sgspe.common.Permissions
import sgspe.common.ProjectParameters
import sgspe.common.Records
import sgspe.common.Values
import sgspe.common.readAs
import sgspe.common.readable
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordCriterion
import wasichai.core.data.RecordWrite
import java.util.UUID

// InvestmentWriteGuard, WF-G (especificación de F5 §3.6, workflows.md WF-G): G4 (plan_direct solo sin aprobación interna,
// ASM-008) primero; G1 (la acción APPROVE, doble llave, RF-WFL-002) y G2 (quien aprueba no registró el programa, ASM-008) en
// approve; G3 (con socio y la política activa, un acuerdo en active, no VENCIDO y con el socio como parte, RN-INV-004) en
// approve y plan_direct, al final porque es la que más lee. lee como quien transiciona: lo que no lee es NotReadable
// (OBJECT_NOT_READABLE en InvestmentWriteGuard), nunca un pase. la escritura se detiene en el primer bloqueo; la consulta en
// seco los junta todos. decide con permisos y datos, nunca con nombres de rol
@Component
class ProgramApprovalRule(
    private val records: Records,
    private val callers: Callers,
    private val permissions: Permissions
) : InvestmentRule {
    override val order = InvestmentRuleOrder.PROGRAM

    override suspend fun findings(
        change: RecordWrite,
        dryRun: Boolean
    ): List<Finding> {
        if (change.objectName != Programs.PROGRAM || change.kind != RecordChangeKind.TRANSITIONED) return emptyList()
        val name = change.transition
        if (name != Programs.APPROVE && name != Programs.PLAN_DIRECT) return emptyList()
        val id = change.recordId ?: return emptyList()
        val before = change.before.orEmpty()
        val findings = mutableListOf<Finding>()
        if (name == Programs.PLAN_DIRECT && ProjectParameters.policy(records.projectOf(before), Programs.INTERNAL_POLICY)) {
            findings += ProgramRules.internalApproval()
            if (!dryRun) return findings
        }
        if (name == Programs.APPROVE) {
            val user = callers.userOrNull()
            if (user == null || !permissions.has(user, Programs.APPROVE_ACTION, Programs.PROGRAM)) {
                findings += ProgramRules.actionRequired()
                if (!dryRun) return findings
            }
            if (user == null || createdBy(id, user.userId)) {
                findings += ProgramRules.segregation()
                if (!dryRun) return findings
            }
        }
        synergy(before)?.let { findings += it }
        return findings
    }

    // G3: null si el programa no tiene socio, si la política está apagada o si el acuerdo cumple
    private suspend fun synergy(before: Map<String, Any?>): Finding? {
        val partner = Records.uuid(before[Programs.PARTNER]) ?: return null
        if (!ProjectParameters.policy(records.projectOf(before), Synergy.POLICY)) return null
        val id = Records.uuid(before[Programs.AGREEMENT]) ?: return Synergy.required("el programa tiene socio y no tiene acuerdo")
        val agreement = records.readable(Programs.AGREEMENT, id)
        if (!Synergy.live(agreement.state, Values.text(agreement.attributes[Synergy.VALIDITY]))) {
            return Synergy.required("el acuerdo del programa no está vigente")
        }
        val parties =
            readAs(Programs.AGREEMENT_PARTY) {
                records.count(
                    Programs.AGREEMENT_PARTY,
                    mapOf(Programs.AGREEMENT to id.toString(), Programs.PARTY_ORGANIZATION to partner.toString())
                )
            }
        return if (parties > 0) null else Synergy.required("el socio del programa no es parte del acuerdo")
    }

    // created_by es una columna de sistema: se compara en la base como quien llama, como ApprovalKeyRule de compromisos. falla
    // cerrada: si quien aprueba no ve el programa, el conteo con created_by daría 0 y G2 pasaría en silencio; es NotReadable
    private suspend fun createdBy(
        id: UUID,
        userId: UUID
    ): Boolean =
        readAs(Programs.PROGRAM) {
            if (records.count(Programs.PROGRAM, ids = listOf(id)) == 0L) throw NotReadable(Programs.PROGRAM)
            records.count(Programs.PROGRAM, criteria = listOf(RecordCriterion { _, bind -> "created_by = ${bind(userId)}" }), ids = listOf(id)) > 0
        }
}
