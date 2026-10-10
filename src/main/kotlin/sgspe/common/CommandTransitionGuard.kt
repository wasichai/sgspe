package sgspe.common

import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordWrite

// las transiciones de comando solo llegan desde su endpoint, que escribe varios registros en una transacción y lleva la
// marca SgspeWrite (workflows.md, «Transiciones de comando»). por la API genérica de workflow, sin la marca: 403, como la
// puerta cerrada de un objeto apiOnly. las de F3 y F4 las agregan sus fases a COMMANDS
@Component
@Order(GuardOrder.COMMAND)
class CommandTransitionGuard : RuleGuard() {
    override suspend fun findings(
        change: RecordWrite,
        dryRun: Boolean
    ): List<Finding> {
        if (change.kind != RecordChangeKind.TRANSITIONED) return emptyList()
        val endpoint = COMMANDS[change.objectName]?.get(change.transition) ?: return emptyList()
        if (!dryRun && WriteMark.present()) return emptyList()
        return listOf(
            Finding(
                code = "COMMAND_ONLY",
                message = "La transición ${change.transition} de ${change.objectName} solo se aplica con $endpoint",
                status = HttpStatus.FORBIDDEN
            )
        )
    }

    companion object {
        val COMMANDS: Map<String, Map<String, String>> =
            mapOf(
                "stakeholder" to mapOf("assess" to "POST /api/sgspe/stakeholders/{id}/assessments"),
                "area_of_influence" to
                    mapOf(
                        "approve" to "POST /api/sgspe/areas-of-influence/{id}/approve",
                        "supersede" to "POST /api/sgspe/areas-of-influence/{id}/approve"
                    ),
                "commitment_candidate" to mapOf("accept" to "POST /api/sgspe/commitment-candidates/{id}/accept"),
                // F4: las reclasificaciones de WF-E1, WF-E2 y WF-E3 (plan 4.2a)
                "grievance" to
                    mapOf(
                        "close_reclassified_received" to "POST /api/sgspe/grievances/{id}/reclassify",
                        "close_reclassified_acknowledged" to "POST /api/sgspe/grievances/{id}/reclassify",
                        "close_reclassified_in_assessment" to "POST /api/sgspe/grievances/{id}/reclassify"
                    ),
                "inquiry" to mapOf("close_reclassified" to "POST /api/sgspe/inquiries/{id}/reclassify"),
                "stakeholder_request" to
                    mapOf(
                        "close_reclassified_received" to "POST /api/sgspe/stakeholder-requests/{id}/reclassify",
                        "close_reclassified_under_evaluation" to "POST /api/sgspe/stakeholder-requests/{id}/reclassify",
                        "accept" to "POST /api/sgspe/stakeholder-requests/{id}/accept",
                        "close_accepted" to "el cierre del compromiso creado (RequestClosureListener)"
                    ),
                // F4: la primera revisión de un riesgo (plan 4.2b)
                "social_risk" to mapOf("assess" to "POST /api/sgspe/social-risks/{id}/assessments")
            )
    }
}
