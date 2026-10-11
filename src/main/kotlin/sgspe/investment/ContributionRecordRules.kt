package sgspe.investment

import org.springframework.stereotype.Component
import sgspe.common.Finding
import sgspe.common.Records
import sgspe.common.Transactions
import sgspe.common.WriteView
import sgspe.common.isLink
import sgspe.common.readable
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordWrite
import java.util.UUID

// InvestmentWriteGuard, registro (especificación de F5 §3.3, «Otras reglas»): beneficiaries_count entero no negativo
// (elección 11); fuera de requested, los términos que juzgó la aprobación no cambian (elección 7, CONTRIBUTION_FROZEN) ni sus
// ítems se crean, cambian o borran (decisión 1, ITEMS_FROZEN); un ítem nunca cambia de aporte. la escritura de un ítem toma
// el candado del aporte, el mismo de la aprobación; la API genérica no abre transacción, así que ese candado acota la
// ventana pero no la cierra (decisión 1 del plan 5.2a). el estado del aporte no viene en `before`: se lee como quien escribe,
// y sin lectura falla cerrado (NotReadable)
@Component
class ContributionRecordRules(
    private val records: Records,
    private val view: WriteView,
    private val transactions: Transactions
) : InvestmentRule {
    override val order = InvestmentRuleOrder.RECORD

    override suspend fun findings(
        change: RecordWrite,
        dryRun: Boolean
    ): List<Finding> =
        when (change.objectName) {
            Contributions.CONTRIBUTION -> contribution(change)
            Contributions.ITEM -> item(change, dryRun)
            else -> emptyList()
        }

    private suspend fun contribution(change: RecordWrite): List<Finding> {
        if (change.kind != RecordChangeKind.CREATED && change.kind != RecordChangeKind.UPDATED) return emptyList()
        if (change.isLink()) return emptyList()
        val after = view.after(change)
        val changed = view.changed(change, after)
        val findings = listOfNotNull(InvestmentCounts.nonNegativeInteger(Contributions.BENEFICIARIES, after, changed)).toMutableList()
        val id = change.recordId
        if (change.kind == RecordChangeKind.UPDATED && id != null && Contributions.FROZEN_TERMS.any { it in changed }) {
            findings += ContributionData.frozenTerms(stateOf(id), changed)
        }
        return findings
    }

    private suspend fun item(
        change: RecordWrite,
        dryRun: Boolean
    ): List<Finding> {
        val before = Records.uuid(change.before?.get(Contributions.CONTRIBUTION))
        val after =
            when (change.kind) {
                RecordChangeKind.CREATED, RecordChangeKind.UPDATED -> {
                    if (change.isLink()) return emptyList()
                    Records.uuid(view.after(change)[Contributions.CONTRIBUTION])
                }
                RecordChangeKind.DELETED -> before
                else -> return emptyList()
            }
        if (change.kind == RecordChangeKind.UPDATED && before != after) return listOf(ContributionData.itemMoved())
        val contribution = after ?: return emptyList()
        val state = if (dryRun) stateOf(contribution) else transactions.locked(Contributions.lock(contribution)) { stateOf(contribution) }
        return listOfNotNull(ContributionData.itemsFrozen(state))
    }

    private suspend fun stateOf(id: UUID): String? = records.readable(Contributions.CONTRIBUTION, id).state
}
