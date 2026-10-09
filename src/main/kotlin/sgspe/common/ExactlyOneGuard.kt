package sgspe.common

import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordWrite

// «exactamente uno de»: varias relaciones opcionales de las que el registro lleva una y solo una (domain-model §4.4, las
// fichas de database-model.md con su línea «Exactamente uno de»). se juzga el registro como queda después de la escritura.
// los grupos de F3 en adelante (delivery, agreement_party, task) los agrega su fase
@Component
@Order(GuardOrder.EXACTLY_ONE)
class ExactlyOneGuard(
    private val view: WriteView
) : RuleGuard() {
    override suspend fun findings(
        change: RecordWrite,
        dryRun: Boolean
    ): List<Finding> {
        if (change.kind != RecordChangeKind.CREATED && change.kind != RecordChangeKind.UPDATED) return emptyList()
        if (change.isLink()) return emptyList()
        val groups = GROUPS[change.objectName] ?: return emptyList()
        val after = view.after(change)
        return groups.mapNotNull { group -> violation(group, after) }
    }

    companion object {
        val GROUPS: Map<String, List<List<String>>> =
            mapOf(
                "area_of_influence_member" to listOf(listOf("locality", "community", "administrative_unit")),
                "land_right" to listOf(listOf("holder_person", "holder_organization")),
                "stakeholder" to listOf(listOf("person", "organization")),
                "contact_method" to listOf(listOf("person", "organization")),
                "actor_relationship" to listOf(listOf("from_person", "from_organization"), listOf("to_person", "to_organization")),
                "network_node" to listOf(listOf("person", "organization")),
                // F3. los orígenes de F4, F5 y F8 (QCS, riesgos, alertas, aportes, programas, entregables) los agrega su fase
                "delivery" to listOf(listOf("commitment")),
                "agreement_party" to listOf(listOf("organization", "person")),
                "task" to listOf(listOf("commitment", "engagement", "agreement", "stakeholder"))
            )

        // pura, para las pruebas unitarias: null si el grupo tiene exactamente uno informado
        fun violation(
            group: List<String>,
            after: Map<String, Any?>
        ): Finding? {
            val informed = group.filter { Values.text(after[it]) != null }
            if (informed.size == 1) return null
            val message =
                if (informed.isEmpty()) {
                    "Informe exactamente uno de ${group.joinToString(", ")}"
                } else {
                    "Solo uno de ${group.joinToString(", ")} puede estar informado; vienen ${informed.joinToString(", ")}"
                }
            return Finding(code = "EXACTLY_ONE", message = message, field = group.first(), status = HttpStatus.BAD_REQUEST)
        }
    }
}
