package sgspe.investment

import kotlinx.coroutines.CancellationException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import sgspe.commitment.Commitments
import sgspe.common.Amounts
import sgspe.common.DerivedFieldsJob
import sgspe.common.DerivedRecalculator
import sgspe.common.Records
import sgspe.common.Transactions
import sgspe.common.Values
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

// el ejecutado de una campaña, puro (domain-model §7.4, ASM-063): executed_amount de sus compromisos en la moneda de la campaña;
// los que tienen ejecutado en otra moneda no se suman y se cuentan (other_currency_items_count). desde F5 se suman los aportes
// en delivered o closed
object CampaignExecution {
    data class Executed(
        val amount: BigDecimal?,
        val currency: String?
    )

    fun of(
        campaignCurrency: String?,
        commitments: List<Executed>
    ): Map<String, Any?> {
        val executed = commitments.filter { (it.amount ?: BigDecimal.ZERO).signum() > 0 }
        // P45: una moneda que falta no se supone PEN; el ejecutado sin moneda se cuenta como de otra
        val same = if (campaignCurrency == null) emptyList() else executed.filter { it.currency == campaignCurrency }
        return mapOf(
            "executed_social_amount" to same.mapNotNull { it.amount }.fold(BigDecimal.ZERO, BigDecimal::add),
            "other_currency_items_count" to (executed.size - same.size)
        )
    }
}

// el recalculador de JOB-02 para exploration_campaign.executed_social_amount y other_currency_items_count: desde cero en cada
// vuelta, solo escribe si el valor cambia (P11), con el motivo que nombra al trabajo (P16)
@Component
class CampaignExecutionRecalculator(
    private val records: Records,
    private val transactions: Transactions
) : DerivedRecalculator {
    private val log = LoggerFactory.getLogger(javaClass)

    override val sourceObject: String = Commitments.COMMITMENT

    override suspend fun recalculate(
        organizationId: UUID,
        since: Instant?
    ): Int {
        var written = 0
        var failed = 0
        val campaigns = records.all(CAMPAIGN)
        for (campaign in campaigns) {
            try {
                if (refresh(UUID.fromString(campaign.id))) written++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed++
                log.warn("CampaignExecutionRecalculator: no se recalculó la campaña {}", campaign.id, e)
            }
        }
        check(failed == 0) { "no se recalcularon $failed de ${campaigns.size} campañas (se escribieron $written)" }
        return written
    }

    // todo dentro del candado de la campaña: la moneda, los compromisos y la escritura se leen con la aprobación que los suma
    // ya confirmada o aún esperando, nunca a medias. una campaña sin moneda (null) no suma nada y cuenta todo lo ejecutado como
    // de otra moneda (P45)
    private suspend fun refresh(id: UUID): Boolean =
        transactions.locked("$CAMPAIGN:$id") {
            val current = records.find(CAMPAIGN, id)?.attributes ?: return@locked false
            val commitments =
                records
                    .all(Commitments.COMMITMENT, filters = mapOf(CAMPAIGN to id.toString()))
                    .map {
                        CampaignExecution.Executed(
                            Amounts.parse(it.attributes["executed_amount"], "executed_amount"),
                            Values.text(it.attributes["currency"])
                        )
                    }
            val wanted = CampaignExecution.of(Values.text(current["currency"]), commitments)
            val changes = wanted.filter { (field, value) -> !Values.same(current[field], value) }
            if (changes.isEmpty()) {
                false
            } else {
                records.patch(CAMPAIGN, id, changes, REASON)
                true
            }
        }

    companion object {
        const val CAMPAIGN = "exploration_campaign"
        val REASON = DerivedFieldsJob.reason("ejecutado de la campaña")
    }
}
