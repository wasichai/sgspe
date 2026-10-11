package sgspe.investment

import org.springframework.stereotype.Component
import sgspe.commitment.projectOf
import sgspe.common.Finding
import sgspe.common.ProjectParameters
import sgspe.common.Records
import sgspe.common.Values
import sgspe.common.readAs
import sgspe.common.readable
import wasichai.core.data.RecordWrite
import java.util.UUID

// los lineamientos de apoyo de un aporte, puros (decisión 3 y §3.4 de la especificación de F5): por cada política activa del
// proyecto, un hallazgo si el aporte se aparta. bloquea sin guideline_deviation_justification y es advertencia con ella: la
// justificación cubre todos los incumplidos a la vez (RN-INV-006). sin temas priorizados, la prioridad no se evalúa y solo se
// advierte. «caseríos sin beneficio laboral» no se evalúa: no hay dato (GAP-033)
object ContributionGuidelines {
    const val NO_PAID_LABOR = "policy_no_paid_labor"
    const val NON_MONETARY = "policy_non_monetary_donations"
    const val PRIORITY = "policy_priority_warning"
    const val LABOR = "MANO DE OBRA"
    const val MONEY = "DINERO"
    const val DONATION = "DONACION"

    // los niveles de parent_topic que se suben desde el tema del aporte (elección 8) y los temas priorizados que se leen
    const val MAX_TOPIC_DEPTH = 10
    const val MAX_PRIORITIES = 500

    // `lineage`: el tema del aporte y sus ancestros (vacío sin tema); `itemKinds`: item_kind de cada ítem
    data class Input(
        val project: Map<String, Any?>,
        val priorities: Set<UUID>,
        val lineage: List<UUID>,
        val itemKinds: List<String?>,
        val contributionType: String?,
        val justification: String?
    )

    fun evaluate(input: Input): List<Finding> {
        val justified = Values.text(input.justification) != null
        val findings = mutableListOf<Finding>()

        fun deviation(
            code: String,
            message: String,
            field: String
        ) {
            val tail =
                if (justified) {
                    "se aparta con la justificación registrada (RN-INV-006)"
                } else {
                    "informe guideline_deviation_justification para aprobarlo (RN-INV-006)"
                }
            findings += Finding(code, "$message: $tail", field, blocking = !justified)
        }
        if (ProjectParameters.policy(input.project, NO_PAID_LABOR) && LABOR in input.itemKinds) {
            deviation("PAID_LABOR", "Un ítem es mano de obra y el proyecto no financia mano de obra (RN-INV-001)", Contributions.ITEM)
        }
        if (ProjectParameters.policy(input.project, NON_MONETARY) && input.contributionType == DONATION && MONEY in input.itemKinds) {
            deviation("MONETARY_DONATION", "Una donación lleva un ítem de dinero y el proyecto pide donaciones no monetarias (RN-INV-002)", Contributions.ITEM)
        }
        if (ProjectParameters.policy(input.project, PRIORITY)) {
            if (input.priorities.isEmpty()) {
                findings +=
                    Finding(
                        "PRIORITIES_NOT_CONFIGURED",
                        "El proyecto no tiene temas priorizados para aportes: la prioridad del aporte no se evalúa (RN-INV-005)",
                        Contributions.TOPIC_FIELD,
                        blocking = false
                    )
            } else if (input.lineage.none { it in input.priorities }) {
                deviation(
                    "PRIORITY_NOT_MET",
                    "El tema del aporte no es uno de los priorizados del proyecto ni desciende de uno (RN-INV-005)",
                    Contributions.TOPIC_FIELD
                )
            }
        }
        return findings
    }
}

// P2 y P3 (§3.3): los lineamientos al aprobar, con los ítems leídos en la transacción (Records.itemsOf), los temas priorizados
// del proyecto (project_priority_topic) y la ascendencia del tema del aporte, todo como quien aprueba. un tema o una lista que
// no se lee es NotReadable, nunca un pase
@Component
class ContributionGuidelinesRule(
    private val records: Records
) : InvestmentRule {
    override val order = InvestmentRuleOrder.APPROVAL + 5

    override suspend fun findings(
        change: RecordWrite,
        dryRun: Boolean
    ): List<Finding> {
        val id = ContributionApproval.target(change, Contributions.APPROVALS) ?: return emptyList()
        val before = change.before.orEmpty()
        val project = records.projectOf(before)
        val projectId = Records.uuid(before["project"])
        val priorities =
            if (projectId != null && ProjectParameters.policy(project, ContributionGuidelines.PRIORITY)) priorities(projectId) else emptySet()
        val lineage = if (priorities.isEmpty()) emptyList() else lineage(Records.uuid(before[Contributions.TOPIC_FIELD]), priorities)
        return ContributionGuidelines.evaluate(
            ContributionGuidelines.Input(
                project,
                priorities,
                lineage,
                records.itemsOf(id).map { Values.text(it.attributes[Contributions.ITEM_KIND]) },
                Values.text(before[Contributions.TYPE]),
                Values.text(before[Contributions.JUSTIFICATION])
            )
        )
    }

    private suspend fun priorities(project: UUID): Set<UUID> =
        readAs(Contributions.TOPIC) {
            records.related(
                "project",
                project,
                Contributions.PRIORITY_TOPICS,
                max = ContributionGuidelines.MAX_PRIORITIES,
                field = Contributions.PRIORITY_TOPICS
            )
        }.map { UUID.fromString(it.id) }.toSet()

    // el tema y sus ancestros por parent_topic, hasta MAX_TOPIC_DEPTH niveles, sin ciclos y hasta dar con una prioridad
    private suspend fun lineage(
        topic: UUID?,
        priorities: Set<UUID>
    ): List<UUID> {
        val chain = mutableListOf<UUID>()
        var current = topic
        while (current != null && current !in chain && chain.size <= ContributionGuidelines.MAX_TOPIC_DEPTH) {
            chain += current
            if (current in priorities) break
            current = Records.uuid(records.readable(Contributions.TOPIC, current).attributes[Contributions.PARENT_TOPIC])
        }
        return chain
    }
}
