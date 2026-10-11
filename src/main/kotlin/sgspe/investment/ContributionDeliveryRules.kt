package sgspe.investment

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import sgspe.common.Finding
import sgspe.common.Records
import sgspe.common.Transactions
import sgspe.common.Values
import sgspe.common.WriteView
import sgspe.common.isLink
import sgspe.common.readAs
import sgspe.common.readable
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordWrite
import java.util.UUID

// las reglas de la entrega de un aporte, puras (especificación de F5 §3.5, decisión 2, ASM-111)
object ContributionDelivery {
    const val AMOUNT = "delivered_amount"
    const val CONFORMITY_BY = "conformity_by"
    const val EVIDENCE_DELIVERY = "evidence_delivery"

    fun exists(): Finding =
        Finding(
            "DELIVERY_EXISTS",
            "El aporte ya tiene su entrega: corríjala; lo que se entrega en partes son aportes distintos (ASM-111)",
            Contributions.CONTRIBUTION
        )

    // la entrega y sus vínculos de evidencia cambian solo con el aporte en procured; desde delivered (y en cancelled) quedan fijos
    fun frozen(state: String?): Finding? =
        if (state == Contributions.PROCURED) {
            null
        } else {
            Finding(
                "DELIVERY_FROZEN",
                "La entrega de un aporte solo cambia con el aporte en procured; este está en ${state ?: "ningún estado"} (ASM-111)",
                Contributions.CONTRIBUTION
            )
        }

    fun amountNotAllowed(field: String = AMOUNT): Finding =
        Finding(
            "FIELD_NOT_ALLOWED",
            "La entrega de un aporte no lleva delivered_amount: el ejecutado del aporte es su total_amount (ASM-063)",
            field,
            status = HttpStatus.BAD_REQUEST
        )
}

// InvestmentWriteGuard, la entrega de un aporte por cualquier puerta (§3.5): el alta de una segunda entrega del mismo aporte
// (DELIVERY_EXISTS, con el candado del aporte; el único del modelo es la última barrera), delivered_amount informado
// (FIELD_NOT_ALLOWED) y, con el aporte fuera de procured, un cambio de la entrega o un vínculo de evidencia que se agrega o se
// quita en el extremo delivery (DELIVERY_FROZEN, con el candado del aporte, el mismo de register_delivery y de las
// anulaciones). las entregas de compromisos (sin social_contribution) no se miran: son de CommitmentRecordRules. el estado
// del aporte se lee como quien escribe; sin lectura, NotReadable. la consulta en seco no toma el candado
@Component
class ContributionDeliveryRecordRules(
    private val records: Records,
    private val view: WriteView,
    private val transactions: Transactions
) : InvestmentRule {
    override val order = InvestmentRuleOrder.RECORD + 1

    override suspend fun findings(
        change: RecordWrite,
        dryRun: Boolean
    ): List<Finding> {
        if (change.objectName != Contributions.DELIVERY) return emptyList()
        return when (change.kind) {
            RecordChangeKind.CREATED -> created(change, dryRun)
            RecordChangeKind.UPDATED, RecordChangeKind.DELETED -> changed(change, dryRun)
            else -> emptyList()
        }
    }

    private suspend fun created(
        change: RecordWrite,
        dryRun: Boolean
    ): List<Finding> {
        val after = view.after(change)
        val contribution = Records.uuid(after[Contributions.CONTRIBUTION]) ?: return emptyList()
        if (Values.text(after[ContributionDelivery.AMOUNT]) != null) return listOf(ContributionDelivery.amountNotAllowed())
        val taken = if (dryRun) hasDelivery(contribution) else transactions.locked(Contributions.lock(contribution)) { hasDelivery(contribution) }
        return if (taken) listOf(ContributionDelivery.exists()) else emptyList()
    }

    private suspend fun changed(
        change: RecordWrite,
        dryRun: Boolean
    ): List<Finding> {
        val contribution = Records.uuid(change.before?.get(Contributions.CONTRIBUTION)) ?: return emptyList()
        if (change.kind == RecordChangeKind.UPDATED && !change.isLink()) {
            val after = view.after(change)
            if (ContributionDelivery.AMOUNT in view.changed(change, after) && Values.text(after[ContributionDelivery.AMOUNT]) != null) {
                return listOf(ContributionDelivery.amountNotAllowed())
            }
        }
        val state = if (dryRun) stateOf(contribution) else transactions.locked(Contributions.lock(contribution)) { stateOf(contribution) }
        return listOfNotNull(ContributionDelivery.frozen(state))
    }

    private suspend fun hasDelivery(contribution: UUID): Boolean =
        readAs(Contributions.DELIVERY) {
            records.count(Contributions.DELIVERY, filters = mapOf(Contributions.CONTRIBUTION to contribution.toString()))
        } > 0

    private suspend fun stateOf(contribution: UUID): String? = records.readable(Contributions.CONTRIBUTION, contribution).state
}
