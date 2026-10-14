package sgspe.scope

import org.springframework.stereotype.Component
import sgspe.common.ModelCatalog
import sgspe.common.Records
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordWrite
import java.util.UUID

// a qué proyecto pertenece un registro (domain-model §4.7): las raíces llevan `project`; los hijos lo heredan de su raíz
// por la cadena de sus padres; `project` es su propio proyecto. los objetos que también pueden ser del tenant (export_log y,
// desde F3, evidence, desde F4, early_warning_rule, desde F7, ai_suggestion y ai_interaction y, desde F8, indicator_measurement) tienen
// `project` opcional y solo se acotan cuando lo traen. un hijo puede tener varios padres posibles (exactamente uno, ExactlyOneGuard) y su padre
// puede ser otro hijo, siempre que la cadena llegue a una raíz en a lo sumo MAX_HOPS saltos (ASM-114; ArchitectureTest). las
// fases siguientes agregan sus objetos aquí
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
            "export_log",
            // F3: evidence también puede ser del tenant (project opcional), como export_log
            "commitment",
            "commitment_candidate",
            "agreement",
            "task",
            "evidence",
            // F4: early_warning_rule también puede ser del tenant (project opcional): una regla sin proyecto no se acota
            "grievance",
            "inquiry",
            "stakeholder_request",
            "social_risk",
            "potential_impact",
            "incident",
            "media_mention",
            "early_warning_signal",
            "early_warning_rule",
            "early_warning",
            // F5: aportes y programas (BC-10)
            "social_contribution",
            "social_program",
            // F7: las salidas de la IA y su registro (BC-18), que también pueden ser del tenant (project opcional)
            "ai_suggestion",
            "ai_interaction",
            // F8 (plan 8.2a): la cifra oficial de un periodo, de un proyecto o, sin project, de todo el tenant
            "indicator_measurement"
        )

    // hijo -> sus padres posibles, en orden: (campo RELATION, objeto padre). el padre es una raíz o un hijo con un único padre
    // obligatorio; con varios padres, sus relaciones son opcionales y forman un grupo de ExactlyOneGuard (ArchitectureTest)
    val CHILDREN: Map<String, List<Pair<String, String>>> =
        mapOf(
            "area_of_influence_member" to listOf("area_of_influence" to "area_of_influence"),
            "stakeholder_assessment" to listOf("stakeholder" to "stakeholder"),
            "network_node" to listOf("community_network" to "community_network"),
            "engagement_participant" to listOf("engagement" to "engagement"),
            "engagement_question" to listOf("engagement" to "engagement"),
            "field_observation" to listOf("field_visit" to "field_visit"),
            "planned_activity" to listOf("engagement_plan" to "engagement_plan"),
            "study_instrument" to listOf("study" to "study"),
            "perception" to listOf("study" to "study"),
            "locality_profile" to listOf("study" to "study"),
            "commitment_action" to listOf("commitment" to "commitment"),
            "commitment_milestone" to listOf("commitment" to "commitment"),
            "commitment_reschedule" to listOf("commitment" to "commitment"),
            "commitment_dependency" to listOf("commitment" to "commitment"),
            // desde F5, la entrega de un compromiso o de un aporte: exactamente uno (ExactlyOneGuard), leída por el que tenga
            "delivery" to listOf("commitment" to "commitment", "social_contribution" to "social_contribution"),
            "agreement_party" to listOf("agreement" to "agreement"),
            "agreement_clause" to listOf("agreement" to "agreement"),
            "risk_assessment" to listOf("social_risk" to "social_risk"),
            "mitigation_measure" to listOf("social_risk" to "social_risk"),
            // F5: los ítems y los comprobantes por su aporte; los datos del programa por su programa, y las mediciones por su
            // indicador, que cuelga del programa (dos saltos)
            "contribution_item" to listOf("social_contribution" to "social_contribution"),
            "receipt" to listOf("social_contribution" to "social_contribution"),
            "social_initiative" to listOf("social_program" to "social_program"),
            "beneficiary_group" to listOf("social_program" to "social_program"),
            "program_budget" to listOf("social_program" to "social_program"),
            "program_indicator" to listOf("social_program" to "social_program"),
            "program_measurement" to listOf("program_indicator" to "program_indicator"),
            "program_milestone" to listOf("social_program" to "social_program")
        )

    // los saltos de una cadena, como mucho: ProjectReadCondition anida una subconsulta por salto (elección 1 de F5)
    const val MAX_HOPS = 2

    // quien escribe una asignación con permiso sobre ella forma los equipos: no se le pide asignación (security.md §9.2)
    val EXEMPT: Set<String> = setOf("project_assignment")

    // los maestros del tenant (ASM-107, decisión 3 de la especificación 4.4): territorio, actores, línea base y el catálogo
    // topic. se comparten entre los proyectos del cliente y ProjectReadScope no los acota. mining_concession se vincula a los
    // proyectos por M2M (project_concession) pero es un maestro. las fases siguientes clasifican aquí sus objetos nuevos
    val TENANT: Set<String> =
        setOf(
            "administrative_unit",
            "locality",
            "community",
            "community_sector",
            "mining_concession",
            "land_parcel",
            "land_right",
            "environmental_feature",
            "person",
            "organization",
            "contact_method",
            "position",
            "actor_relationship",
            "school",
            "health_facility",
            "road_access",
            "festivity",
            "state_program",
            "local_conflict",
            "topic",
            // F8 (plan 8.2a): el catálogo de los KPI con sus metas (dashboards.md §10), compartido por los proyectos
            "indicator"
        )

    fun covers(objectName: String): Boolean = objectName == "project" || objectName in ROOTS || objectName in CHILDREN

    // cómo se lee un objeto (security.md §9.7, ASM-106): el grupo decide la condición de ProjectReadCondition
    fun readGroup(objectName: String): ReadGroup =
        when (objectName) {
            PROJECT -> ReadGroup.PROJECT
            ASSIGNMENT -> ReadGroup.ASSIGNMENT
            in ROOTS -> ReadGroup.ROOT
            in CHILDREN -> ReadGroup.CHILD
            in TENANT -> ReadGroup.TENANT
            else -> ReadGroup.UNCLASSIFIED
        }

    // los saltos de `child` por su padre `field` hasta la raíz, en orden: [(campo, objeto)], y el último objeto es una raíz.
    // vacía si `field` no es un padre de `child` o si la cadena no llega a una raíz en MAX_HOPS saltos (falla cerrado)
    fun chain(
        child: String,
        field: String
    ): List<Pair<String, String>> {
        var link = CHILDREN[child]?.firstOrNull { it.first == field } ?: return emptyList()
        val hops = mutableListOf(link)
        while (link.second !in ROOTS) {
            // un padre que es hijo tiene un solo padre posible (ArchitectureTest, regla 5)
            link = CHILDREN[link.second]?.singleOrNull() ?: return emptyList()
            hops += link
            if (hops.size > MAX_HOPS) return emptyList()
        }
        return hops
    }

    // el padre presente de un registro de `objectName` con sus `attributes`: el primero de CHILDREN que trae un id, o null
    fun parentOf(
        objectName: String,
        attributes: Map<String, Any?>
    ): Pair<String, String>? = CHILDREN[objectName]?.firstOrNull { Records.uuid(attributes[it.first]) != null }

    // el proyecto al que llega el padre `id` de un registro de `child` por su campo `field`, subiendo la cadena con `read` (los
    // atributos de un registro; null si no existe o no se lee). null si un salto falta o no lleva su campo
    suspend fun projectOf(
        child: String,
        field: String,
        id: UUID,
        read: suspend (String, UUID) -> Map<String, Any?>?
    ): UUID? {
        val hops = chain(child, field)
        if (hops.isEmpty()) return null
        var current = id
        for ((index, hop) in hops.withIndex()) {
            val attributes = read(hop.second, current) ?: return null
            val next = if (index == hops.lastIndex) PROJECT else hops[index + 1].first
            current = Records.uuid(attributes[next]) ?: return null
        }
        return current
    }

    const val PROJECT = "project"
    const val ASSIGNMENT = "project_assignment"
}

// los grupos de la lectura por proyecto: el proyecto mismo, las asignaciones, las raíces con `project`, los hijos por su
// padre, los maestros del tenant y lo que nadie clasificó (falla cerrado; ArchitectureTest impide que llegue a main)
enum class ReadGroup { PROJECT, ASSIGNMENT, ROOT, CHILD, TENANT, UNCLASSIFIED }

// a qué proyectos pertenece el registro que una escritura toca. `field` es el campo de la relación de alcance del objeto
// (`project` en las raíces, el padre en los hijos): el que un error nombra. `unresolved`: el alcance no se pudo determinar
// (el registro guardado no tiene la relación obligatoria o el padre no da proyecto), y quien no es ADMIN no escribe en lo
// que no tiene dueño
data class Resolution(
    val field: String,
    val projects: Set<UUID>,
    val unresolved: Boolean
)

// los proyectos que toca una escritura: el de antes y el de después (cambiar el proyecto exige asignación a los dos). en un
// hijo, el padre presente antes y después, de cualquiera de sus padres posibles, y su cadena hasta la raíz, leída como quien
// escribe. wasichai deja las relaciones en NULL cuando se borra el registro al que apuntan (ON DELETE SET NULL): un hijo o una
// raíz huérfanos no resuelven proyecto, y eso se informa como `unresolved` para que la guarda falle cerrado
@Component
class ProjectResolver(
    private val records: Records,
    private val catalog: ModelCatalog
) {
    suspend fun resolve(
        change: RecordWrite,
        after: Map<String, Any?>
    ): Resolution = resolve(change, after, catalog::isRequiredRelation) { name, id -> records.find(name, id)?.attributes }

    companion object {
        // la decisión, sin lecturas de base: `required` dice si la relación (objeto, campo) es obligatoria en el modelo y
        // `read` da los atributos de un registro (null si no existe o quien escribe no lo lee), con los que se sube la cadena
        suspend fun resolve(
            change: RecordWrite,
            after: Map<String, Any?>,
            required: (String, String) -> Boolean,
            read: suspend (String, UUID) -> Map<String, Any?>?
        ): Resolution {
            val name = change.objectName
            val created = change.kind == RecordChangeKind.CREATED
            if (name == ProjectScope.PROJECT) {
                // el alta de un proyecto aún no tiene equipo; cualquier otra escritura es sobre un proyecto que existe
                return Resolution(ProjectScope.PROJECT, if (created) emptySet() else setOfNotNull(change.recordId), !created && change.recordId == null)
            }
            if (name in ProjectScope.ROOTS) {
                val beforeId = Records.uuid(change.before?.get(ProjectScope.PROJECT))
                val afterId = Records.uuid(after[ProjectScope.PROJECT])
                // `project` es obligatorio salvo en las raíces que pueden ser del tenant (export_log, evidence,
                // early_warning_rule, ai_suggestion, ai_interaction): solo ahí vacío es válido. lo que la escritura deja
                // vacío no es de alcance: lo rechaza wasichai con su 400 «is required»
                val unresolved = required(name, ProjectScope.PROJECT) && !created && beforeId == null
                return Resolution(ProjectScope.PROJECT, setOfNotNull(beforeId, afterId), unresolved)
            }
            val parents = ProjectScope.CHILDREN[name] ?: return Resolution(ProjectScope.PROJECT, emptySet(), false)
            val stored = change.before.orEmpty()
            val before = ProjectScope.parentOf(name, stored)
            val later = ProjectScope.parentOf(name, after)
            val field = (later ?: before ?: parents.first()).first
            // un registro existente sin padre no se toca: es del tenant solo con un único padre opcional (locality_profile);
            // con uno obligatorio, o con varios padres (delivery desde F5), quedó huérfano y solo lo escribe ADMIN. un alta
            // sin padre la rechazan antes wasichai (400 «is required») o ExactlyOneGuard (400 EXACTLY_ONE)
            var unresolved = !created && before == null && (parents.size > 1 || required(name, parents.single().first))
            val touched =
                listOfNotNull(
                    before?.let { it.first to Records.uuid(stored[it.first])!! },
                    later?.let { it.first to Records.uuid(after[it.first])!! }
                ).distinct()
            val projects = mutableSetOf<UUID>()
            for ((parentField, id) in touched) {
                val project = ProjectScope.projectOf(name, parentField, id, read)
                if (project == null) unresolved = true else projects += project
            }
            return Resolution(field, projects, unresolved)
        }
    }
}
