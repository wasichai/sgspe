package sgspe.attention

import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import sgspe.common.Attention
import sgspe.common.Finding
import sgspe.common.GuardOrder
import sgspe.common.RuleGuard
import sgspe.common.WriteMark
import sgspe.common.WriteView
import sgspe.common.isLink
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordWrite

// los vínculos de las QCS que solo escriben sus comandos (AttentionRules.COMMAND_FIELDS): reclassified_from_* y
// created_commitment. una persona sin la marca SgspeWrite que los fija, cambia o vacía (también con un PUT que los omite)
// recibe 403 COMMAND_ONLY sobre el campo, antes que las demás reglas, como la transición de comando; reenviarlos tal como se
// leyeron no es cambiarlos (WriteView.changed). la plataforma (sin usuario) y los comandos (con la marca) sí los escriben
@Component
@Order(GuardOrder.COMMAND)
class AttentionCommandFieldsGuard(
    private val view: WriteView
) : RuleGuard() {
    override suspend fun findings(
        change: RecordWrite,
        dryRun: Boolean
    ): List<Finding> {
        if (change.objectName !in Attention.OBJECTS || change.isLink()) return emptyList()
        if (change.kind != RecordChangeKind.CREATED && change.kind != RecordChangeKind.UPDATED) return emptyList()
        if (change.userId == null || WriteMark.present()) return emptyList()
        return listOfNotNull(AttentionRules.commandOnly(change.objectName, view.changed(change), person = true, marked = false))
    }
}
