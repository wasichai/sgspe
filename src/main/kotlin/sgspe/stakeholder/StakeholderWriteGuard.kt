package sgspe.stakeholder

import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import sgspe.common.Finding
import sgspe.common.GuardOrder
import sgspe.common.Records
import sgspe.common.RuleGuard
import sgspe.common.Values
import sgspe.common.WriteView
import sgspe.common.isLink
import wasichai.core.common.ForbiddenException
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordWrite

// las reglas de BC-03 que la metadata no expresa (domain-model §7.2): caracterizar un grupo de interés exige tipo,
// ámbito, motivo de inclusión y responsable (RF-STK-003); un cargo sin persona solo puede estar VACANTE o PREVISTO
// (RF-STK-002); y en la matriz de priorización no hay dos filas sin tipo para la misma combinación, porque el único
// compuesto no lo impide con NULL (RF-STK-011, WAS-ADR-0037).
// `profile` mira solo lo guardado del grupo de interés (tipo, ámbito, motivo, responsable), nunca `stance`, que algunos
// roles no pueden leer. las filas hermanas de la matriz se leen como quien llama, no con asPlatform: quien no puede leerlas
// no puede verificar la regla, y es un 403 que lo dice (como EngagementWriteGuard) y no un falso verde
@Component
@Order(GuardOrder.DOMAIN)
class StakeholderWriteGuard(
    private val records: Records,
    private val view: WriteView
) : RuleGuard() {
    override suspend fun findings(
        change: RecordWrite,
        dryRun: Boolean
    ): List<Finding> =
        when (change.objectName) {
            STAKEHOLDER -> if (change.kind == RecordChangeKind.TRANSITIONED && change.transition == "profile") profile(change.before.orEmpty()) else emptyList()
            POSITION -> if (writes(change)) position(view.after(change)) else emptyList()
            RULE -> if (writes(change)) rule(change, view.after(change)) else emptyList()
            else -> emptyList()
        }

    private fun writes(change: RecordWrite): Boolean = (change.kind == RecordChangeKind.CREATED || change.kind == RecordChangeKind.UPDATED) && !change.isLink()

    private fun profile(stakeholder: Map<String, Any?>): List<Finding> {
        val missing = PROFILE_FIELDS.filter { Values.text(stakeholder[it]) == null }
        if (missing.isEmpty()) return emptyList()
        return listOf(Finding("PROFILE_INCOMPLETE", "Caracterizar exige ${missing.joinToString(", ")}", missing.first()))
    }

    private fun position(after: Map<String, Any?>): List<Finding> {
        if (Values.text(after["person"]) != null) return emptyList()
        val status = Values.text(after["position_status"])
        if (status in WITHOUT_PERSON) return emptyList()
        return listOf(
            Finding(
                "POSITION_WITHOUT_PERSON",
                "Un cargo sin persona solo puede estar VACANTE o PREVISTO",
                "position_status",
                status = HttpStatus.BAD_REQUEST
            )
        )
    }

    private suspend fun rule(
        change: RecordWrite,
        after: Map<String, Any?>
    ): List<Finding> {
        if (Values.text(after["stakeholder_type"]) != null) return emptyList()
        if (RULE_KEY.any { Values.text(after[it]) == null }) return emptyList()
        val twins =
            try {
                records.all(RULE, filters = RULE_KEY.associateWith { Values.text(after[it])!! })
            } catch (_: ForbiddenException) {
                return listOf(
                    Finding(
                        "OBJECT_NOT_READABLE",
                        "No tiene permiso para leer $RULE, así que no se puede verificar que la fila no se repita",
                        RULE,
                        status = HttpStatus.FORBIDDEN
                    )
                )
            }.filter { Values.text(it.attributes["stakeholder_type"]) == null && it.id != change.recordId?.toString() }
        if (twins.isEmpty()) return emptyList()
        return listOf(
            Finding(
                "DUPLICATE_RULE",
                "Ya hay una fila para cualquier tipo de actor con ese grupo de influencia, postura y ámbito",
                "stakeholder_type"
            )
        )
    }

    companion object {
        const val STAKEHOLDER = "stakeholder"
        const val POSITION = "position"
        const val RULE = "prioritization_rule"
        val PROFILE_FIELDS = listOf("stakeholder_type", "stakeholder_scope", "inclusion_reason", "owner_user_id")
        val WITHOUT_PERSON = setOf("VACANTE", "PREVISTO")

        // la combinación que el único compuesto de prioritization_rule no protege cuando stakeholder_type es NULL
        private val RULE_KEY = listOf("project", "influence_group", "stance", "stakeholder_scope")
    }
}
