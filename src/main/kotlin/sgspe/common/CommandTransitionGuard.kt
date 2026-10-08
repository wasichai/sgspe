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
                    )
            )
    }
}
