package sgspe.common

import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordWrite

// el motivo al cambiar ciertos campos que no son estados: la etapa y la fase de exploración del proyecto cambian con
// motivo y nunca son estados de WF-A (RN-PRJ-001, CON-034). darles su primer valor no es cambiarlos
@Component
@Order(GuardOrder.FIELD_REASON)
class FieldChangeReasonGuard(
    private val view: WriteView
) : RuleGuard() {
    override suspend fun findings(
        change: RecordWrite,
        dryRun: Boolean
    ): List<Finding> {
        if (change.kind != RecordChangeKind.UPDATED || change.isLink()) return emptyList()
        val fields = FIELDS[change.objectName] ?: return emptyList()
        if (!change.reason.isNullOrBlank()) return emptyList()
        val before = change.before.orEmpty()
        val changed = view.changed(change).filter { it in fields && Values.text(before[it]) != null }.sorted()
        return changed.map { field ->
            Finding(
                code = "FIELD_REASON_REQUIRED",
                message = "Cambiar $field exige un motivo (X-Change-Reason)",
                field = "reason",
                status = HttpStatus.BAD_REQUEST
            )
        }
    }

    companion object {
        val FIELDS: Map<String, Set<String>> = mapOf("project" to setOf("stage", "exploration_phase"))
    }
}
