package sgspe.scope

import org.springframework.stereotype.Component
import sgspe.common.Records
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordWrite
import java.util.UUID

// a qué proyecto pertenece un registro (domain-model §4.7): las raíces llevan `project`; los hijos lo heredan de su raíz
// por su relación; `project` es su propio proyecto. los objetos que también pueden ser del tenant (export_log y, desde F3,
// evidence…) tienen `project` opcional y solo se acotan cuando lo traen. las fases siguientes agregan sus objetos aquí
object ProjectScope {
    val ROOTS: Set<String> =
        setOf(
            "project_party",
            "exploration_campaign",
            "area_of_influence",
            "project_component",
            "stakeholder",
            "prioritization_rule",
            "community_network",
            "engagement",
            "field_visit",
            "engagement_plan",
            "key_message",
            "aligned_answer",
            "strategic_alliance",
            "study",
            "key_theme",
            "export_log"
        )

    // hijo -> (campo RELATION, objeto padre)
    val CHILDREN: Map<String, Pair<String, String>> =
        mapOf(
            "area_of_influence_member" to ("area_of_influence" to "area_of_influence"),
            "stakeholder_assessment" to ("stakeholder" to "stakeholder"),
            "network_node" to ("community_network" to "community_network"),
            "engagement_participant" to ("engagement" to "engagement"),
            "engagement_question" to ("engagement" to "engagement"),
            "field_observation" to ("field_visit" to "field_visit"),
            "planned_activity" to ("engagement_plan" to "engagement_plan"),
            "study_instrument" to ("study" to "study"),
            "perception" to ("study" to "study"),
            "locality_profile" to ("study" to "study")
        )

    // quien escribe una asignación con permiso sobre ella forma los equipos: no se le pide asignación (security.md §9.2)
    val EXEMPT: Set<String> = setOf("project_assignment")

    fun covers(objectName: String): Boolean = objectName == "project" || objectName in ROOTS || objectName in CHILDREN
}

// los proyectos que toca una escritura: el de antes y el de después (cambiar el proyecto exige asignación a los dos)
@Component
class ProjectResolver(
    private val records: Records
) {
    suspend fun projectsOf(
        change: RecordWrite,
        after: Map<String, Any?>
    ): Set<UUID> {
        val name = change.objectName
        if (name == "project") {
            return if (change.kind == RecordChangeKind.CREATED) emptySet() else setOfNotNull(change.recordId)
        }
        if (name in ProjectScope.ROOTS) {
            return setOfNotNull(Records.uuid(change.before?.get("project")), Records.uuid(after["project"]))
        }
        val (field, parent) = ProjectScope.CHILDREN[name] ?: return emptySet()
        val parents = setOfNotNull(Records.uuid(change.before?.get(field)), Records.uuid(after[field]))
        return parents.mapNotNull { projectOfParent(parent, it) }.toSet()
    }

    private suspend fun projectOfParent(
        parent: String,
        id: UUID
    ): UUID? {
        val record = records.find(parent, id) ?: return null
        return if (parent == "project") id else Records.uuid(record.attributes["project"])
    }
}
