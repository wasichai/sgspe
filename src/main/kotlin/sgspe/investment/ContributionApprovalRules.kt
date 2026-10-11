package sgspe.investment

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import sgspe.commitment.projectOf
import sgspe.common.Callers
import sgspe.common.Finding
import sgspe.common.NotReadable
import sgspe.common.Permissions
import sgspe.common.ProjectParameters
import sgspe.common.Records
import sgspe.common.Transactions
import sgspe.common.Values
import sgspe.common.readAs
import sgspe.common.readable
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordCriterion
import wasichai.core.data.RecordWrite
import java.util.UUID

// InvestmentWriteGuard, aprobación (especificación de F5 §3.3): el candado del aporte, P7, P9, P4 y P5 y P6, en ese orden;
// P2 y P3 (ContributionGuidelinesRule) y P1 (ContributionCapRule, la única que toma el candado de la campaña) van después.
// deciden con permisos y datos, nunca con nombres de rol
object ContributionApproval {
    // el campo de los hallazgos sobre quien aprueba (P4, P5): el mismo que toCodedException pone al escribir
    const val RECORD = "record"
    const val APPROVE_ACTION = "APPROVE"
    const val INTERNAL_APPROVAL = "policy_internal_approval"

    // el aporte de la transición si es una de `transitions`; si no, null y la regla no mira la escritura
    fun target(
        change: RecordWrite,
        transitions: Set<String>
    ): UUID? {
        if (change.objectName != Contributions.CONTRIBUTION || change.kind != RecordChangeKind.TRANSITIONED) return null
        if (change.transition !in transitions) return null
        return change.recordId
    }
}

// «acuerdo vigente» (elección 9 de la especificación de F5): en active y con validity_status distinto de VENCIDO. lo usan P6 y,
// desde el plan 5.2b, G3 del programa
object Synergy {
    const val POLICY = "policy_synergy_requires_agreement"
    const val ACTIVE = "active"
    const val EXPIRED = "VENCIDO"
    const val VALIDITY = "validity_status"

    fun live(
        state: String?,
        validity: String?
    ): Boolean = state == ACTIVE && validity != EXPIRED

    fun required(why: String): Finding =
        Finding(
            "SYNERGY_AGREEMENT_REQUIRED",
            "Una sinergia institucional exige un acuerdo vigente, en active y no VENCIDO: $why (RN-INV-004)",
            Contributions.AGREEMENT
        )
}

// el candado del aporte (§3.3, «Orden»; decisión 5 del plan 5.2a): lo toman las aprobaciones, antes de que P9, los
// lineamientos y el tope lean los ítems, register_delivery, antes de que P8 lea la entrega, y las anulaciones, que congelan la
// entrega. es el mismo de las escrituras de ítems y de la entrega. la guarda corre dentro de la transacción de
// WorkflowService.apply, así que el candado se suelta con su commit o su rollback. la consulta en seco no lo toma
@Component
class ContributionLockRule(
    private val transactions: Transactions
) : InvestmentRule {
    override val order = InvestmentRuleOrder.APPROVAL

    override suspend fun findings(
        change: RecordWrite,
        dryRun: Boolean
    ): List<Finding> {
        if (dryRun) return emptyList()
        val id =
            ContributionApproval.target(change, Contributions.APPROVALS + Contributions.REGISTER_DELIVERY + Contributions.CANCELS)
                ?: return emptyList()
        transactions.locked(Contributions.lock(id)) {}
        return emptyList()
    }
}

// P7: approve_direct solo en un proyecto sin aprobación interna (ASM-008). va primero: decide si la transición tiene sentido en
// el proyecto antes de pedir datos
@Component
class ContributionDirectApprovalRule(
    private val records: Records
) : InvestmentRule {
    override val order = InvestmentRuleOrder.APPROVAL + 1

    override suspend fun findings(
        change: RecordWrite,
        dryRun: Boolean
    ): List<Finding> {
        ContributionApproval.target(change, setOf(Contributions.APPROVE_DIRECT)) ?: return emptyList()
        if (!ProjectParameters.policy(records.projectOf(change.before.orEmpty()), ContributionApproval.INTERNAL_APPROVAL)) return emptyList()
        return listOf(
            Finding(
                "INTERNAL_APPROVAL_REQUIRED",
                "El proyecto tiene la aprobación interna activa: el aporte se aprueba con approve, con doble llave y segregación (P7, ASM-008)",
                "project"
            )
        )
    }
}

// P9: al menos un ítem (el total de los ítems es lo que se aprueba, decisión 1) y contribution_type (elección 8), en ese orden
@Component
class ContributionItemsRule(
    private val records: Records
) : InvestmentRule {
    override val order = InvestmentRuleOrder.APPROVAL + 2

    override suspend fun findings(
        change: RecordWrite,
        dryRun: Boolean
    ): List<Finding> {
        val id = ContributionApproval.target(change, Contributions.APPROVALS) ?: return emptyList()
        val findings = mutableListOf<Finding>()
        if (records.itemsOf(id).isEmpty()) {
            findings +=
                Finding(
                    "ITEMS_REQUIRED",
                    "Aprobar un aporte exige al menos un ítem: el total de sus ítems es lo que se aprueba (P9, ASM-110)",
                    Contributions.ITEM
                )
            if (!dryRun) return findings
        }
        if (Values.text(change.before.orEmpty()[Contributions.TYPE]) == null) {
            findings +=
                Finding(
                    "FIELD_REQUIRED",
                    "Aprobar un aporte exige contribution_type: decide qué lineamientos se evalúan (P9, ASM-112)",
                    Contributions.TYPE,
                    status = HttpStatus.BAD_REQUEST
                )
        }
        return findings
    }
}

// P4 y P5: la doble llave de approve (la acción APPROVE sobre social_contribution, RF-WFL-002) y la segregación: quien aprueba
// no es created_by del aporte (quien lo registró o quien aceptó la solicitud que lo creó, ASM-008). approve_direct no las pide
@Component
class ContributionKeyRule(
    private val records: Records,
    private val callers: Callers,
    private val permissions: Permissions
) : InvestmentRule {
    override val order = InvestmentRuleOrder.APPROVAL + 3

    override suspend fun findings(
        change: RecordWrite,
        dryRun: Boolean
    ): List<Finding> {
        val id = ContributionApproval.target(change, setOf(Contributions.APPROVE)) ?: return emptyList()
        val user = callers.userOrNull()
        val findings = mutableListOf<Finding>()
        if (user == null || !permissions.has(user, ContributionApproval.APPROVE_ACTION, Contributions.CONTRIBUTION)) {
            findings +=
                Finding(
                    "ACTION_REQUIRED",
                    "Aprobar un aporte exige la acción APPROVE sobre social_contribution (P4)",
                    ContributionApproval.RECORD,
                    status = HttpStatus.FORBIDDEN
                )
            if (!dryRun) return findings
        }
        if (user == null || createdBy(id, user.userId)) {
            findings +=
                Finding(
                    "SEGREGATION_OF_DUTIES",
                    "Quien aprueba un aporte no puede ser quien lo registró o aceptó la solicitud que lo creó (P5, ASM-008)",
                    ContributionApproval.RECORD,
                    status = HttpStatus.FORBIDDEN
                )
        }
        return findings
    }

    // created_by es una columna de sistema: se compara en la base como quien llama, como D4. falla cerrada: si quien aprueba no
    // ve el aporte, el conteo con created_by daría 0 y la segregación pasaría en silencio; es NotReadable
    private suspend fun createdBy(
        id: UUID,
        userId: UUID
    ): Boolean =
        readAs(Contributions.CONTRIBUTION) {
            if (records.count(Contributions.CONTRIBUTION, ids = listOf(id)) == 0L) throw NotReadable(Contributions.CONTRIBUTION)
            records.count(Contributions.CONTRIBUTION, criteria = listOf(RecordCriterion { _, bind -> "created_by = ${bind(userId)}" }), ids = listOf(id)) > 0
        }
}

// P6: con has_institutional_synergy y la política activa, un acuerdo vigente (Synergy.live), con o sin justificación
// (elección 9). que el acuerdo sea del proyecto del aporte lo juzga ProjectLinkGuard; uno que no se lee es NotReadable
@Component
class ContributionSynergyRule(
    private val records: Records
) : InvestmentRule {
    override val order = InvestmentRuleOrder.APPROVAL + 4

    override suspend fun findings(
        change: RecordWrite,
        dryRun: Boolean
    ): List<Finding> {
        ContributionApproval.target(change, Contributions.APPROVALS) ?: return emptyList()
        val before = change.before.orEmpty()
        if (Values.bool(before[Contributions.SYNERGY]) != true) return emptyList()
        if (!ProjectParameters.policy(records.projectOf(before), Synergy.POLICY)) return emptyList()
        val id = Records.uuid(before[Contributions.AGREEMENT]) ?: return listOf(Synergy.required("el aporte no tiene acuerdo"))
        val agreement = records.readable(Contributions.AGREEMENT, id)
        val validity = Values.text(agreement.attributes[Synergy.VALIDITY])
        if (Synergy.live(agreement.state, validity)) return emptyList()
        return listOf(Synergy.required("el acuerdo está en ${agreement.state ?: "ningún estado"}${validity?.let { " y $it" } ?: ""}"))
    }
}
