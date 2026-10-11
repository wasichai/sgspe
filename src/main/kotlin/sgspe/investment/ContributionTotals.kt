package sgspe.investment

import kotlinx.coroutines.CancellationException
import org.slf4j.LoggerFactory
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import sgspe.common.Criteria
import sgspe.common.DerivedFieldsJob
import sgspe.common.DerivedRecalculator
import sgspe.common.Records
import sgspe.common.Transactions
import sgspe.common.Values
import wasichai.core.data.RecordChange
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordChangeListener
import java.time.Instant
import java.util.UUID

// ContributionTotalsListener (elección 12 de la especificación de F5, domain-model §7.5): total_amount = la suma de line_amount
// de los ítems del aporte, escrito como quien escribe, con la marca y solo si cambia. con cada alta, cambio o baja de un ítem
// (el total solo informa mientras el aporte está en requested: si falla, se registra y JOB-02 lo corrige) y en approve y
// approve_direct, en la transacción de la transición: si falla, lanza y la aprobación se revierte, así lo aprobado queda
// escrito (decisión 2 del plan 5.2a)
@Component
@Order(125)
class ContributionTotalsListener(
    private val records: Records
) : RecordChangeListener {
    private val log = LoggerFactory.getLogger(javaClass)

    override suspend fun recordChanged(change: RecordChange) {
        if (change.objectName == Contributions.ITEM && change.kind in ITEM_CHANGES) {
            val contributions =
                listOfNotNull(change.before?.get(Contributions.CONTRIBUTION), change.after?.get(Contributions.CONTRIBUTION))
                    .mapNotNull { Records.uuid(it) }
                    .distinct()
            for (id in contributions) {
                try {
                    refresh(id)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    log.warn("ContributionTotalsListener: no se actualizó total_amount del aporte {}; JOB-02 lo corregirá", id, e)
                }
            }
            return
        }
        if (change.objectName == Contributions.CONTRIBUTION && change.kind == RecordChangeKind.TRANSITIONED && change.transition in Contributions.APPROVALS) {
            refresh(change.recordId)
        }
    }

    private suspend fun refresh(id: UUID) {
        val current = records.find(Contributions.CONTRIBUTION, id) ?: return
        val total = ContributionData.total(records.itemsOf(id).map { it.attributes })
        if (!Values.same(current.attributes[Contributions.TOTAL], total)) {
            records.patch(Contributions.CONTRIBUTION, id, mapOf(Contributions.TOTAL to total), REASON)
        }
    }

    companion object {
        const val REASON = "derivado: total del aporte"
        private val ITEM_CHANGES = setOf(RecordChangeKind.CREATED, RecordChangeKind.UPDATED, RecordChangeKind.DELETED)
    }
}

// el recalculador de JOB-02 para total_amount: los aportes en requested cambiados desde la vuelta anterior, o con un ítem
// cambiado, y todos los de requested en la vuelta diaria (la baja de un ítem solo la ve la diaria). fuera de requested el
// total es lo aprobado y no se toca (decisión 1 del plan 5.2a). cada aporte, dentro de su candado
@Component
class ContributionTotalsRecalculator(
    private val records: Records,
    private val transactions: Transactions
) : DerivedRecalculator {
    private val log = LoggerFactory.getLogger(javaClass)

    override val sourceObject: String = Contributions.CONTRIBUTION

    override suspend fun recalculate(
        organizationId: UUID,
        since: Instant?
    ): Int {
        val requested = Criteria.inState(setOf(Contributions.REQUESTED))
        val ids =
            if (since == null) {
                records.all(Contributions.CONTRIBUTION, criteria = listOf(requested)).map { it.id }
            } else {
                val changed = records.all(Contributions.CONTRIBUTION, criteria = listOf(requested, Criteria.updatedSince(since))).map { it.id }
                val viaItems =
                    records
                        .all(Contributions.ITEM, criteria = listOf(Criteria.updatedSince(since)))
                        .mapNotNull { Records.uuid(it.attributes[Contributions.CONTRIBUTION]) }
                        .distinct()
                val stillRequested = records.all(Contributions.CONTRIBUTION, criteria = listOf(requested), ids = viaItems).map { it.id }
                (changed + stillRequested).distinct()
            }
        var written = 0
        var failed = 0
        for (raw in ids) {
            try {
                val id = UUID.fromString(raw)
                val changed =
                    transactions.locked(Contributions.lock(id)) {
                        val current = records.find(Contributions.CONTRIBUTION, id) ?: return@locked false
                        val total = ContributionData.total(records.itemsOf(id).map { it.attributes })
                        if (Values.same(current.attributes[Contributions.TOTAL], total)) {
                            false
                        } else {
                            records.patch(Contributions.CONTRIBUTION, id, mapOf(Contributions.TOTAL to total), REASON)
                            true
                        }
                    }
                if (changed) written++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed++
                log.warn("ContributionTotalsRecalculator: no se recalculó el aporte {}", raw, e)
            }
        }
        check(failed == 0) { "no se recalcularon $failed de ${ids.size} aportes (se escribieron $written)" }
        return written
    }

    companion object {
        val REASON = DerivedFieldsJob.reason("total del aporte")
    }
}
