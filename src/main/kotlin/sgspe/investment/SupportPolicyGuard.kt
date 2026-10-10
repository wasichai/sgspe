package sgspe.investment

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import sgspe.commitment.CommitmentRule
import sgspe.commitment.CommitmentRuleOrder
import sgspe.commitment.Commitments
import sgspe.common.Amounts
import sgspe.common.Callers
import sgspe.common.Criteria
import sgspe.common.Finding
import sgspe.common.NotReadable
import sgspe.common.Permissions
import sgspe.common.ProjectParameters
import sgspe.common.Records
import sgspe.common.Transactions
import sgspe.common.Values
import sgspe.common.readAs
import wasichai.core.common.WasichaiException
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordWrite
import java.math.BigDecimal
import java.util.UUID

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

// SupportPolicyGuard, D7 de WF-D (RF-INV-002): al aprobar un compromiso con campaña y tope, verifica el tope contra los
// compromisos comprometidos de la campaña (dashboards.md §3.2; desde F5, también los aportes). con la política
// policy_cap_blocks_approval activa (por defecto) superarlo es un 409 SUPPORT_CAP_EXCEEDED salvo para quien tiene la acción
// APPROVE_OVER_CAP; con la política apagada es una advertencia. la escritura toma el candado de la campaña
// (`exploration_campaign:<id>`) dentro de la transacción de la transición, así dos aprobaciones de la misma campaña no pasan
// el tope a la vez; la consulta en seco no lo toma. lee como quien aprueba: una campaña, un proyecto o unos compromisos que
// no lee son NotReadable (OBJECT_NOT_READABLE), nunca un pase. va al final de las reglas de aprobación (P49) porque es la
// única que toma un candado. una campaña sin moneda nunca bloquea: el tope no se compara y solo se advierte (CAP_OTHER_CURRENCY,
// P45; lo registra la Tarea 23). un monto guardado que Amounts rechaza (en otro compromiso o en el tope) es un error de datos,
// no de quien aprueba: la fila se omite, se registra con su id y se advierte CAP_DATA_INVALID; con la política que bloquea y
// sin APPROVE_OVER_CAP es un 409 sobre exploration_campaign
@Component
class SupportPolicyGuard(
    private val records: Records,
    private val callers: Callers,
    private val permissions: Permissions,
    private val transactions: Transactions
) : CommitmentRule {
    private val log = LoggerFactory.getLogger(javaClass)

    override val order = CommitmentRuleOrder.APPROVAL + 5

    override suspend fun findings(
        change: RecordWrite,
        dryRun: Boolean
    ): List<Finding> {
        if (change.objectName != Commitments.COMMITMENT || change.kind != RecordChangeKind.TRANSITIONED) return emptyList()
        if (change.transition !in Commitments.APPROVALS) return emptyList()
        val before = change.before.orEmpty()
        val campaign = Records.uuid(before["exploration_campaign"]) ?: return emptyList()
        return if (dryRun) {
            evaluate(change, before, campaign)
        } else {
            transactions.locked("$CAMPAIGN:$campaign") { evaluate(change, before, campaign) }
        }
    }

    private suspend fun evaluate(
        change: RecordWrite,
        before: Map<String, Any?>,
        campaign: UUID
    ): List<Finding> {
        val record = readAs(CAMPAIGN) { records.find(CAMPAIGN, campaign) } ?: throw NotReadable(CAMPAIGN)
        val cap =
            try {
                Amounts.parse(record.attributes["social_support_cap"], "social_support_cap") ?: return emptyList()
            } catch (e: WasichaiException) {
                log.warn("SupportPolicyGuard: el tope de la campaña {} no es un monto válido: {}", campaign, e.message)
                return listOf(invalid(before, "El tope de la campaña no es un monto válido: corrija los datos de la campaña"))
            }
        val currency = Values.text(record.attributes["currency"])
        if (currency == null) {
            return listOf(
                Finding("CAP_OTHER_CURRENCY", "La campaña no tiene moneda: el tope no se compara (ASM-063)", CAMPAIGN_CURRENCY, blocking = false)
            )
        }
        var invalidCount = 0
        val approved =
            readAs(Commitments.COMMITMENT) {
                records.all(
                    Commitments.COMMITMENT,
                    filters = mapOf(CAMPAIGN to campaign.toString()),
                    criteria = listOf(Criteria.inState(Commitments.COMMITTED)),
                    max = MAX_COMMITMENTS
                )
            }.filter { it.id != change.recordId?.toString() }
                .mapNotNull {
                    try {
                        SupportCap.Approved(Amounts.parse(it.attributes["amount"], "amount"), Values.text(it.attributes["currency"]))
                    } catch (e: WasichaiException) {
                        invalidCount++
                        log.warn("SupportPolicyGuard: el monto del compromiso {} de la campaña {} no es válido: {}", it.id, campaign, e.message)
                        null
                    }
                }
        val candidate = SupportCap.Approved(Amounts.parse(before["amount"], "amount"), Values.text(before["currency"]))
        val outcome = SupportCap.evaluate(cap, currency, candidate, approved)
        val findings = mutableListOf<Finding>()
        if (invalidCount > 0) {
            findings += invalid(before, "$invalidCount compromisos de la campaña tienen un monto guardado no válido y no se suman al tope")
        }
        if (outcome.otherCurrency > 0 || outcome.incomparable) {
            findings +=
                Finding(
                    "CAP_OTHER_CURRENCY",
                    "Hay montos en una moneda distinta de la de la campaña ($currency): no se suman al tope (ASM-063)",
                    CAMPAIGN_CURRENCY,
                    blocking = false
                )
        }
        if (outcome.exceeded) {
            findings +=
                Finding(
                    "SUPPORT_CAP_EXCEEDED",
                    "La aprobación supera el tope de apoyo social de la campaña: $cap $currency, con ${outcome.total} comprometidos (RN-INV-003)",
                    CAMPAIGN,
                    blocking = blocks(before) && !overCap()
                )
        }
        return findings
    }

    // el error de datos: bloquea como el tope (política que bloquea y sin APPROVE_OVER_CAP); si no, advierte
    private suspend fun invalid(
        before: Map<String, Any?>,
        message: String
    ): Finding = Finding("CAP_DATA_INVALID", message, CAMPAIGN, blocking = blocks(before) && !overCap())

    private suspend fun overCap(): Boolean {
        val user = callers.userOrNull()
        return user != null && permissions.has(user, OVER_CAP, Commitments.COMMITMENT)
    }

    // la política del proyecto del compromiso (ASM-019); un proyecto que no se lee es NotReadable, nunca la política por defecto
    private suspend fun blocks(before: Map<String, Any?>): Boolean {
        val id = Records.uuid(before["project"]) ?: return ProjectParameters.policy(emptyMap(), POLICY)
        val project = readAs("project") { records.find("project", id) }?.attributes ?: throw NotReadable("project")
        return ProjectParameters.policy(project, POLICY)
    }

    companion object {
        const val CAMPAIGN = "exploration_campaign"
        const val CAMPAIGN_CURRENCY = "currency"
        const val OVER_CAP = "APPROVE_OVER_CAP"
        const val POLICY = "policy_cap_blocks_approval"

        // el tope de compromisos de una campaña que se leen al aprobar
        const val MAX_COMMITMENTS = 20_000
    }
}
