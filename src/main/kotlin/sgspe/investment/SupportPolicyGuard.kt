package sgspe.investment

import org.springframework.stereotype.Component
import sgspe.commitment.CommitmentRule
import sgspe.commitment.CommitmentRuleOrder
import sgspe.commitment.Commitments
import sgspe.common.Amounts
import sgspe.common.Finding
import sgspe.common.Records
import sgspe.common.Transactions
import sgspe.common.Values
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordWrite
import java.math.BigDecimal

// el tope de apoyo social de una campaña, puro (RN-INV-003, ASM-063): la suma de los montos aprobados de la campaña en su
// moneda, más el que se aprueba, contra el tope. un monto en otra moneda no se suma y se advierte. P45: una moneda que falta
// no se supone PEN; el monto no se suma ni se compara y se cuenta como de otra moneda
object SupportCap {
    data class Approved(
        val amount: BigDecimal?,
        val currency: String?
    )

    data class Outcome(
        val exceeded: Boolean,
        val total: BigDecimal,
        val otherCurrency: Int,
        // el que se aprueba está en otra moneda (o sin ella): no se compara
        val incomparable: Boolean
    )

    fun evaluate(
        cap: BigDecimal,
        campaignCurrency: String,
        candidate: Approved,
        approved: List<Approved>
    ): Outcome {
        val same = approved.filter { it.currency == campaignCurrency }
        val other = approved.size - same.size
        val base = same.mapNotNull { it.amount }.fold(BigDecimal.ZERO, BigDecimal::add)
        if (candidate.currency != campaignCurrency) return Outcome(false, base, other, incomparable = true)
        val total = base + (candidate.amount ?: BigDecimal.ZERO)
        return Outcome(total.compareTo(cap) > 0, total, other, incomparable = false)
    }
}

// SupportPolicyGuard, D7 de WF-D (RF-INV-002): al aprobar un compromiso con campaña y tope, CampaignCap juzga el tope contra
// lo comprometido de la campaña (CampaignCommitted: compromisos y, desde F5, aportes; elección 6 de la especificación de F5),
// con APPROVE_OVER_CAP sobre commitment. la escritura toma el candado de la campaña (CampaignCap.lock) dentro de la
// transacción de la transición, así dos aprobaciones de la misma campaña, también la de un aporte, no pasan el tope a la vez;
// la consulta en seco no lo toma. va al final de las reglas de aprobación (P49) porque es la única que toma un candado
@Component
class SupportPolicyGuard(
    private val cap: CampaignCap,
    private val transactions: Transactions
) : CommitmentRule {
    override val order = CommitmentRuleOrder.APPROVAL + 5

    override suspend fun findings(
        change: RecordWrite,
        dryRun: Boolean
    ): List<Finding> {
        if (change.objectName != Commitments.COMMITMENT || change.kind != RecordChangeKind.TRANSITIONED) return emptyList()
        if (change.transition !in Commitments.APPROVALS) return emptyList()
        val before = change.before.orEmpty()
        val campaign = Records.uuid(before[CampaignCap.CAMPAIGN]) ?: return emptyList()
        val candidate = SupportCap.Approved(Amounts.parse(before["amount"], "amount"), Values.text(before["currency"]))
        return if (dryRun) {
            cap.findings(change, campaign, candidate)
        } else {
            transactions.locked(CampaignCap.lock(campaign)) { cap.findings(change, campaign, candidate) }
        }
    }
}
