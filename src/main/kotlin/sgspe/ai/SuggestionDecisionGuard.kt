package sgspe.ai

import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import sgspe.common.Callers
import sgspe.common.Finding
import sgspe.common.GuardOrder
import sgspe.common.RuleGuard
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordWrite
import wasichai.core.identity.AuthenticatedUser

// WF-K de ai_suggestion lo decide una persona (workflows.md, RQ-AI-003, RQ-AI-015): una cuenta de servicio no acepta, no rechaza
// ni marca duplicada una sugerencia, aunque tenga el rol, tampoco por la API genérica de workflow (403 PERSON_REQUIRED). la
// aceptación ya lo exige en su endpoint; rechazar y marcar duplicado solo pasan por aquí (decisión 9 del plan 7.2b). el correo de
// respaldo de una cuenta de servicio también la delata (WAS-ADR-0043), como en WarningWriteGuard
@Component
@Order(GuardOrder.COMMAND)
class SuggestionDecisionGuard(
    private val callers: Callers
) : RuleGuard() {
    override suspend fun findings(
        change: RecordWrite,
        dryRun: Boolean
    ): List<Finding> {
        if (change.objectName != SuggestionWriter.SUGGESTION || change.kind != RecordChangeKind.TRANSITIONED) return emptyList()
        return decide(callers.userOrNull())
    }

    companion object {
        const val SERVICE_ACCOUNT_DOMAIN = "@service-accounts.invalid"

        fun decide(user: AuthenticatedUser?): List<Finding> =
            if (user != null && (user.serviceAccount != null || user.email.endsWith(SERVICE_ACCOUNT_DOMAIN))) {
                listOf(
                    Finding(
                        "PERSON_REQUIRED",
                        "Una sugerencia de IA la decide una persona: una cuenta de servicio no la acepta, rechaza ni marca duplicada",
                        status = HttpStatus.FORBIDDEN
                    )
                )
            } else {
                emptyList()
            }
    }
}
