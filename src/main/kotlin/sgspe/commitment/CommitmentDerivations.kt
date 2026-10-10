package sgspe.commitment

import kotlinx.coroutines.CancellationException
import org.slf4j.LoggerFactory
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import sgspe.common.Criteria
import sgspe.common.DerivedFieldsJob
import sgspe.common.DerivedRecalculator
import sgspe.common.LIMA
import sgspe.common.NotReadable
import sgspe.common.Records
import sgspe.common.Transactions
import sgspe.common.Values
import wasichai.core.data.RecordChange
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordChangeListener
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

// las fechas de transición de un compromiso, puras (domain-model §7.4, dashboards.md §12): fulfilled_date, closed_date y
// cancelled_date son la fecha en Lima de su transición; reopen_execution vacía fulfilled_date
object TransitionDates {
    fun changes(
        transition: String?,
        today: LocalDate
    ): Map<String, Any?> =
        when {
            transition == "register_fulfillment" -> mapOf("fulfilled_date" to today.toString())
            transition == "reopen_execution" -> mapOf("fulfilled_date" to null)
            transition == "close" -> mapOf("closed_date" to today.toString())
            transition != null && transition.startsWith("cancel_") -> mapOf("cancelled_date" to today.toString())
            else -> emptyMap()
        }
}

// CommitmentDerivationsListener (domain-model §7.5): en la transacción de register_fulfillment, reopen_execution, close y las
// cancel_* escribe la fecha de la transición, como quien la aplica y con la marca; si no puede, lanza y la transición se
// revierte (workflows.md, «Listeners»). al crear un compromiso o cambiar su interacción de origen o su proyecto recalcula
// requires_senior_approval, que solo informa; si falla, lo registra y la escritura sigue: JOB-02 lo corrige. un vínculo de
// solicitantes (commitment_stakeholder) no llega a los listeners en wasichai 0.6.0 (RelatedRecordService solo audita y
// llama a las guardas): ese cambio lo recoge la vuelta diaria de JOB-02, y la guarda D5 nunca lee el derivado
@Component
@Order(120)
class CommitmentDerivationsListener(
    private val records: Records,
    private val senior: SeniorApprovalReader,
    private val clock: Clock
) : RecordChangeListener {
    private val log = LoggerFactory.getLogger(javaClass)

    override suspend fun recordChanged(change: RecordChange) {
        if (change.objectName != Commitments.COMMITMENT) return
        when (change.kind) {
            RecordChangeKind.TRANSITIONED -> {
                val changes = TransitionDates.changes(change.transition, LocalDate.now(clock.withZone(LIMA)))
                if (changes.isNotEmpty()) records.patch(Commitments.COMMITMENT, change.recordId, changes, reason(change.transition))
            }
            RecordChangeKind.CREATED, RecordChangeKind.UPDATED -> if (originChanged(change)) refreshSenior(change)
            else -> Unit
        }
    }

    // un alta, o el cambio de la interacción de origen o del proyecto (cuya política decide)
    private fun originChanged(change: RecordChange): Boolean {
        if (change.kind == RecordChangeKind.CREATED) return true
        val after = change.after.orEmpty()
        val before = change.before.orEmpty()
        return !Values.same(before["origin_engagement"], after["origin_engagement"]) || !Values.same(before["project"], after["project"])
    }

    private suspend fun refreshSenior(change: RecordChange) {
        try {
            val current = records.find(Commitments.COMMITMENT, change.recordId) ?: return
            val wanted = senior.required(change.recordId, current.attributes)
            if (Values.bool(current.attributes[FIELD]) != wanted) records.patch(Commitments.COMMITMENT, change.recordId, mapOf(FIELD to wanted), SENIOR_REASON)
        } catch (e: CancellationException) {
            throw e
        } catch (e: NotReadable) {
            // quien escribe no lee el proyecto, el origen o los solicitantes (P12): el derivado solo informa, así que no se
            // adivina ni se rompe su escritura; la vuelta de JOB-02, como la plataforma, lo lee todo y lo corrige
            log.info(
                "CommitmentDerivationsListener: quien escribe no lee {}; JOB-02 calculará requires_senior_approval del compromiso {}",
                e.objectName,
                change.recordId
            )
        } catch (e: Exception) {
            log.warn("CommitmentDerivationsListener: no se actualizó requires_senior_approval del compromiso {}", change.recordId, e)
        }
    }

    companion object {
        const val FIELD = "requires_senior_approval"
        const val SENIOR_REASON = "derivado: aprobación de nivel superior"

        fun reason(transition: String?): String = "derivado: fecha de la transición $transition"
    }
}

// el recalculador de JOB-02 para requires_senior_approval: los compromisos abiertos cambiados desde la vuelta anterior, o
// todos en la vuelta diaria. un vínculo de solicitantes, un cambio de política del proyecto o del grupo de influencia de un
// solicitante solo lo ve la vuelta diaria
@Component
class SeniorApprovalRecalculator(
    private val records: Records,
    private val senior: SeniorApprovalReader,
    private val transactions: Transactions
) : DerivedRecalculator {
    private val log = LoggerFactory.getLogger(javaClass)

    override val sourceObject: String = Commitments.COMMITMENT

    override suspend fun recalculate(
        organizationId: UUID,
        since: Instant?
    ): Int {
        val open = Criteria.inState(ALL_STATES - Commitments.CLOSED)
        val commitments =
            records.all(Commitments.COMMITMENT, criteria = listOfNotNull(open, since?.let { Criteria.updatedSince(it) }))
        var written = 0
        var failed = 0
        for (commitment in commitments) {
            try {
                val id = UUID.fromString(commitment.id)
                val wanted = senior.required(id, commitment.attributes)
                if (Values.bool(commitment.attributes[CommitmentDerivationsListener.FIELD]) == wanted) continue
                transactions.locked("${Commitments.COMMITMENT}:$id") {
                    records.patch(Commitments.COMMITMENT, id, mapOf(CommitmentDerivationsListener.FIELD to wanted), REASON)
                }
                written++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed++
                log.warn("SeniorApprovalRecalculator: no se recalculó el compromiso {}", commitment.id, e)
            }
        }
        check(failed == 0) { "no se recalcularon $failed de ${commitments.size} compromisos (se escribieron $written)" }
        return written
    }

    companion object {
        val REASON = DerivedFieldsJob.reason("aprobación de nivel superior")

        // los estados de WF-D (workflows.md)
        val ALL_STATES = setOf("draft", "pending_approval") + Commitments.COMMITTED + "cancelled"
    }
}
