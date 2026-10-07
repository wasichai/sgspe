package sgspe.common

import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordWrite

// el motivo obligatorio en las transiciones de devolver, rechazar, suspender, cancelar, retirar y reabrir de los objetos
// sin requiresReason (RQ-AUD-002, ASM-058): la tabla «Transiciones con motivo obligatorio» de workflows.md, tal cual. en
// los objetos requiresReason lo exige wasichai antes de llegar aquí (WAS-ADR-0041)
@Component
@Order(GuardOrder.REASON)
class TransitionReasonGuard : RuleGuard() {
    override suspend fun findings(
        change: RecordWrite,
        dryRun: Boolean
    ): List<Finding> {
        if (change.kind != RecordChangeKind.TRANSITIONED) return emptyList()
        val transition = change.transition ?: return emptyList()
        if (transition !in REQUIRED[change.objectName].orEmpty()) return emptyList()
        if (!dryRun && !change.reason.isNullOrBlank()) return emptyList()
        return listOf(
            Finding(
                code = "REASON_REQUIRED",
                message = "La transición $transition de ${change.objectName} exige un motivo (X-Change-Reason)",
                field = "reason",
                blocking = !dryRun,
                status = HttpStatus.BAD_REQUEST
            )
        )
    }

    companion object {
        val REQUIRED: Map<String, Set<String>> =
            mapOf(
                "project" to setOf("return_to_draft", "suspend", "cancel_draft", "cancel_in_review"),
                "stakeholder" to setOf("retire_identified", "retire_profiled", "retire_assessed", "reinstate"),
                "engagement" to setOf("postpone", "return_minutes", "cancel_planned", "cancel_scheduled", "cancel_postponed"),
                "social_program" to
                    setOf("return_to_draft", "suspend", "cancel_draft", "cancel_pending_approval", "cancel_planned", "cancel_suspended"),
                "early_warning" to setOf("dismiss"),
                "commitment_candidate" to setOf("reject", "mark_duplicate"),
                "ai_suggestion" to setOf("reject", "mark_duplicate"),
                "field_visit" to setOf("cancel"),
                "task" to setOf("cancel_open", "cancel_in_progress", "cancel_blocked")
            )
    }
}
