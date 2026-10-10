package sgspe.scope

import org.springframework.stereotype.Component
import sgspe.common.ModelCatalog
import sgspe.common.Records
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordWrite
import java.util.UUID

// a qué proyecto pertenece un registro (domain-model §4.7): las raíces llevan `project`; los hijos lo heredan de su raíz
// por su relación; `project` es su propio proyecto. los objetos que también pueden ser del tenant (export_log y, desde F3,
// evidence y, desde F4, early_warning_rule) tienen `project` opcional y solo se acotan cuando lo traen. las fases siguientes agregan sus objetos aquí
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
            "early_warning"
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
            "locality_profile" to ("study" to "study"),
            "commitment_action" to ("commitment" to "commitment"),
            "commitment_milestone" to ("commitment" to "commitment"),
            "commitment_reschedule" to ("commitment" to "commitment"),
            "commitment_dependency" to ("commitment" to "commitment"),
            "delivery" to ("commitment" to "commitment"),
            "agreement_party" to ("agreement" to "agreement"),
            "agreement_clause" to ("agreement" to "agreement"),
            "risk_assessment" to ("social_risk" to "social_risk"),
            "mitigation_measure" to ("social_risk" to "social_risk")
        )

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
            "topic"
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

// los proyectos que toca una escritura: el de antes y el de después (cambiar el proyecto exige asignación a los dos).
// wasichai deja las relaciones en NULL cuando se borra el registro al que apuntan (ON DELETE SET NULL): un hijo o una raíz
// huérfanos no resuelven proyecto, y eso se informa como `unresolved` para que la guarda falle cerrado
@Component
class ProjectResolver(
    private val records: Records,
    private val catalog: ModelCatalog
) {
    suspend fun resolve(
        change: RecordWrite,
        after: Map<String, Any?>
    ): Resolution = resolve(change, after, catalog::isRequiredRelation, ::projectOfParent)

    private suspend fun projectOfParent(
        parent: String,
        id: UUID
    ): UUID? {
        val record = records.find(parent, id) ?: return null
        return if (parent == "project") id else Records.uuid(record.attributes["project"])
    }

    companion object {
        // la decisión, sin lecturas: `required` dice si la relación (objeto, campo) es obligatoria en el modelo y
        // `parentProject` da el proyecto de un padre (null si no existe o no lo tiene)
        suspend fun resolve(
            change: RecordWrite,
            after: Map<String, Any?>,
            required: (String, String) -> Boolean,
            parentProject: suspend (String, UUID) -> UUID?
        ): Resolution {
            val name = change.objectName
            val created = change.kind == RecordChangeKind.CREATED
            if (name == "project") {
                // el alta de un proyecto aún no tiene equipo; cualquier otra escritura es sobre un proyecto que existe
                return Resolution("project", if (created) emptySet() else setOfNotNull(change.recordId), !created && change.recordId == null)
            }
            val (field, parent) =
                if (name in ProjectScope.ROOTS) {
                    Pair<String, String?>("project", null)
                } else {
                    ProjectScope.CHILDREN[name] ?: return Resolution("project", emptySet(), false)
                }
            val beforeId = Records.uuid(change.before?.get(field))
            val afterId = Records.uuid(after[field])
            // la relación de alcance es obligatoria en el modelo salvo en export_log y locality_profile: solo ahí vacía es
            // válido. un registro existente sin dueño no se toca (el ON DELETE SET NULL de wasichai los deja así). lo que la
            // escritura deja vacío no es de alcance: un alta o una actualización sin la relación obligatoria la rechaza
            // wasichai con su 400 «is required» (y la base con NOT NULL), así que aquí no se la trata como NOT_ASSIGNED
            var unresolved = required(name, field) && !created && beforeId == null
            val projects = mutableSetOf<UUID>()
            for (id in setOfNotNull(beforeId, afterId)) {
                val project = if (parent == null) id else parentProject(parent, id)
                if (project == null) unresolved = true else projects += project
            }
            return Resolution(field, projects, unresolved)
        }
    }
}
