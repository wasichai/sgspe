package sgspe.investment

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import sgspe.commitment.Fulfillment
import sgspe.common.Finding
import sgspe.common.Readability
import sgspe.common.Records
import sgspe.common.Transactions
import sgspe.common.Values
import sgspe.common.WriteView
import sgspe.common.isLink
import sgspe.common.readAs
import sgspe.common.readable
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordWrite
import wasichai.core.metadata.MetadataService
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

    // los estados de WF-P en que el comprobante de un aporte todavía cambia: antes de la entrega
    val RECEIPT_OPEN: Set<String> = setOf(Contributions.REQUESTED, Contributions.APPROVED, Contributions.PROCURED)

    // el comprobante de un aporte (alta, cambio o baja) queda fijo desde delivered y en los estados finales, con el mismo
    // código y el mismo criterio que la entrega congelada (decisión del usuario del 2026-10-11)
    fun receiptFrozen(state: String?): Finding? =
        if (state in RECEIPT_OPEN) {
            null
        } else {
            Finding(
                "DELIVERY_FROZEN",
                "El comprobante de un aporte solo cambia antes de su entrega; este está en ${state ?: "ningún estado"} (decisión del usuario del 2026-10-11)",
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

    // P8: la evidencia mínima de la entrega de un aporte [DOC-02 p.4], del proyecto del aporte
    val REQUIRED_TYPES: List<String> = listOf("ACTA DE RECEPCION", "FOTO", "COMPROBANTE")

    // las evidencias de la entrega que P8 lee como mucho (las lecturas M2M son acotadas): más es un 400 TOO_MANY_RECORDS
    const val MAX_EVIDENCE = 500

    fun missing(types: Set<String>): List<String> = REQUIRED_TYPES.filter { it !in types }

    // P8 sobre la única entrega del aporte (null si no tiene): con conformidad y quiénes la dieron, y las tres evidencias
    fun registration(delivery: Fulfillment.DeliveryEvidence?): Finding? {
        if (delivery == null || !Fulfillment.conforms(delivery)) {
            return Finding(
                "DELIVERY_REQUIRED",
                "Registrar la entrega exige la entrega del aporte con conformidad del grupo de interés y quiénes la dieron (P8)",
                Contributions.DELIVERY
            )
        }
        val missing = missing(delivery.evidenceTypes)
        if (missing.isEmpty()) return null
        return Finding(
            "EVIDENCE_REQUIRED",
            "A la entrega le falta evidencia vinculada del proyecto del aporte: ${missing.joinToString(", ")} (P8)",
            EVIDENCE_DELIVERY
        )
    }
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

// InvestmentWriteGuard, el comprobante de un aporte por cualquier puerta (decisión del usuario del 2026-10-11): un alta, un
// cambio o una baja de receipt con su aporte en delivered o después (closed, rejected, cancelled) es DELIVERY_FROZEN sobre
// social_contribution. un cambio que mueve el comprobante a otro aporte se juzga contra los dos, en orden ascendente del UUID
// como texto (Transactions). el estado se lee como quien escribe (sin lectura, NotReadable) y, al escribir, con el candado del
// aporte, el mismo de la entrega, de register_delivery y de las anulaciones; la consulta en seco no lo toma
@Component
class ContributionReceiptRule(
    private val records: Records,
    private val view: WriteView,
    private val transactions: Transactions
) : InvestmentRule {
    override val order = InvestmentRuleOrder.RECORD + 2

    override suspend fun findings(
        change: RecordWrite,
        dryRun: Boolean
    ): List<Finding> {
        if (change.objectName != Contributions.RECEIPT) return emptyList()
        val before = Records.uuid(change.before?.get(Contributions.CONTRIBUTION))
        val contributions =
            when (change.kind) {
                RecordChangeKind.CREATED -> listOfNotNull(Records.uuid(view.after(change)[Contributions.CONTRIBUTION]))
                RecordChangeKind.UPDATED -> listOfNotNull(before, Records.uuid(view.after(change)[Contributions.CONTRIBUTION]))
                RecordChangeKind.DELETED -> listOfNotNull(before)
                else -> return emptyList()
            }.distinct().sortedBy { it.toString() }
        return contributions.mapNotNull { id ->
            val state = if (dryRun) stateOf(id) else transactions.locked(Contributions.lock(id)) { stateOf(id) }
            ContributionDelivery.receiptFrozen(state)
        }
    }

    private suspend fun stateOf(contribution: UUID): String? = records.readable(Contributions.CONTRIBUTION, contribution).state
}

// P8 de WF-P (§3.3): register_delivery con la entrega del aporte, su conformidad y sus tres evidencias del proyecto del aporte
// (Fulfillment.countedTypes: una evidencia de otro proyecto o sin proyecto no cuenta). lee la entrega y sus evidencias como
// quien aplica la transición: sin lectura, OBJECT_NOT_READABLE; sin lectura de conformity_by (restringido), FIELD_NOT_READABLE.
// el candado del aporte ya lo tomó ContributionLockRule, así que la entrega no cambia mientras se juzga
@Component
class ContributionDeliveryRule(
    private val records: Records,
    private val metadata: MetadataService
) : InvestmentRule {
    override val order = InvestmentRuleOrder.DELIVERY

    override suspend fun findings(
        change: RecordWrite,
        dryRun: Boolean
    ): List<Finding> {
        val id = ContributionApproval.target(change, setOf(Contributions.REGISTER_DELIVERY)) ?: return emptyList()
        val delivery =
            readAs(Contributions.DELIVERY) {
                records.all(
                    Contributions.DELIVERY,
                    filters = mapOf(Contributions.CONTRIBUTION to id.toString()),
                    max = 1,
                    field = Contributions.DELIVERY
                )
            }.firstOrNull() ?: return listOfNotNull(ContributionDelivery.registration(null))
        if (!Readability(metadata).canRead("${Contributions.DELIVERY}.${ContributionDelivery.CONFORMITY_BY}")) {
            return listOf(
                Finding(
                    "FIELD_NOT_READABLE",
                    "Registrar la entrega exige leer quiénes dieron la conformidad de la entrega",
                    "${Contributions.DELIVERY}.${ContributionDelivery.CONFORMITY_BY}",
                    status = HttpStatus.FORBIDDEN
                )
            )
        }
        val row =
            Fulfillment.DeliveryEvidence(
                Values.bool(delivery.attributes["conformity_given"]) == true,
                Values.text(delivery.attributes[ContributionDelivery.CONFORMITY_BY]),
                emptySet()
            )
        if (!Fulfillment.conforms(row)) return listOfNotNull(ContributionDelivery.registration(row))
        val evidence =
            readAs(ContributionDeliveries.EVIDENCE) {
                records.related(
                    Contributions.DELIVERY,
                    UUID.fromString(delivery.id),
                    ContributionDelivery.EVIDENCE_DELIVERY,
                    max = ContributionDelivery.MAX_EVIDENCE,
                    field = ContributionDelivery.EVIDENCE_DELIVERY
                )
            }
        val counted = row.copy(evidenceTypes = Fulfillment.countedTypes(Records.uuid(change.before.orEmpty()["project"]), evidence.map { it.attributes }))
        return listOfNotNull(ContributionDelivery.registration(counted))
    }
}
