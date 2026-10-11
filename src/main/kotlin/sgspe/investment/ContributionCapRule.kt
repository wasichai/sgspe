package sgspe.investment

import org.springframework.stereotype.Component
import sgspe.common.Finding
import sgspe.common.Records
import sgspe.common.Transactions
import sgspe.common.Values
import wasichai.core.data.RecordWrite

// P1 de WF-P (especificación de F5 §3.3 y §3.4): al aprobar un aporte con campaña, CampaignCap juzga el tope contra lo
// comprometido de la campaña con el candidato = la suma de line_amount de los ítems leídos en la transacción, en la moneda
// del aporte (nunca el total_amount guardado), y APPROVE_OVER_CAP sobre social_contribution (SOCIAL_MANAGER, ASM-119). es la
// última regla de la aprobación y la única que toma el candado de la campaña, después del del aporte (ContributionLockRule):
// el orden de candados es siempre aporte y después campaña. la consulta en seco no lo toma
@Component
class ContributionCapRule(
    private val records: Records,
    private val cap: CampaignCap,
    private val transactions: Transactions
) : InvestmentRule {
    override val order = InvestmentRuleOrder.APPROVAL + 6

    override suspend fun findings(
        change: RecordWrite,
        dryRun: Boolean
    ): List<Finding> {
        val id = ContributionApproval.target(change, Contributions.APPROVALS) ?: return emptyList()
        val before = change.before.orEmpty()
        val campaign = Records.uuid(before[Contributions.CAMPAIGN]) ?: return emptyList()
        val candidate =
            SupportCap.Approved(ContributionData.total(records.itemsOf(id).map { it.attributes }), Values.text(before[Contributions.CURRENCY]))
        return if (dryRun) {
            cap.findings(change, campaign, candidate)
        } else {
            transactions.locked(CampaignCap.lock(campaign)) { cap.findings(change, campaign, candidate) }
        }
    }
}
