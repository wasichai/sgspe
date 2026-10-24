package sgspe.scope

import kotlinx.coroutines.ThreadContextElement
import kotlinx.coroutines.asContextElement
import kotlinx.coroutines.withContext
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.server.CoWebFilter
import org.springframework.web.server.CoWebFilterChain
import org.springframework.web.server.ServerWebExchange
import sgspe.common.Callers
import sgspe.common.ProjectReach
import wasichai.core.common.ValidationException
import wasichai.core.data.RecordCriterion
import wasichai.core.data.RecordQueryContributor
import wasichai.core.metadata.ObjectDefinition
import wasichai.core.platform.SqlIdentifier
import java.util.UUID

// «mis proyectos asignados» como filtro de la lista genérica (B-04 de la revisión de UX, wasichai/sgspe#162, decisión (a) del
// usuario del 2026-10-24): ?assigned=true deja los registros de los proyectos con asignación vigente de quien llama, también
// para el ADMIN y para quien tiene READ_ALL_PROJECTS, a quienes el alcance de lectura (ProjectReadScope) no acota. es la cola
// «Para tomar» del portal de esas cuentas, en una sola lectura en lugar de una por proyecto. a una cuenta normal no le cambia
// nada: el alcance ya la deja en sus proyectos. «vigente» es ProjectReach.assigned (Validity.inForce, hoy en Lima), la regla de
// Assignments y de ProjectReadCondition; nunca una copia. se compone por AND con los demás filtros y con el alcance
//
// un RecordQueryContributor no conoce a quien llama ni puede suspender, así que AssignedProjectsFilter resuelve los proyectos
// antes del controlador y los deja en el contexto de la corrutina de la petición (AssignedProjects); el controlador de wasichai
// llama a parse dentro de esa corrutina. fuera de una petición que los resolvió (el agente, un trabajo) no admite ningún
// registro: falla cerrado
@Component
class AssignedQuery : RecordQueryContributor {
    override val parameters: Set<String> = setOf(ASSIGNED)

    override fun parse(params: Map<String, String>): RecordCriterion? {
        val raw = params[ASSIGNED]?.trim()?.lowercase() ?: return null
        if (raw == "false") return null
        if (raw != "true") throw ValidationException("Filtro inválido", ASSIGNED, "debe ser true o false")
        val projects = AssignedProjects.current().orEmpty()
        return RecordCriterion { definition, bind ->
            val column = column(definition)
            if (projects.isEmpty()) NOTHING else "$column IN (${projects.joinToString(", ") { bind(it) }})"
        }
    }

    // en project, su id; en los demás, su relación `project` (las raíces y project_assignment). un hijo, que llega a su
    // proyecto por la cadena de sus padres, o un maestro del tenant, no tienen proyecto propio
    private fun column(definition: ObjectDefinition): String {
        if (definition.obj.name == ProjectScope.PROJECT) return ID_COLUMN
        val field =
            definition.fields.firstOrNull { it.name == ProjectScope.PROJECT }
                ?: throw ValidationException("Filtro inválido", ASSIGNED, "${definition.obj.name} no tiene proyecto o no puede leerlo")
        return SqlIdentifier.quote(field.columnName)
    }

    companion object {
        const val ASSIGNED = "assigned"

        // la condición que no admite ningún registro y la columna del id de un registro de wasichai (las mismas que
        // ProjectReadCondition, que solo usa ProjectReadScope: ArchitectureTest)
        private const val NOTHING = "false"
        private const val ID_COLUMN = "\"id\""
    }
}

// los proyectos con asignación vigente de quien llama, resueltos para la petición en curso (null: no se resolvieron). viajan en
// el contexto de la corrutina como un ThreadLocal que kotlinx restablece en cada reanudación
object AssignedProjects {
    private val local = ThreadLocal<List<UUID>?>()

    fun element(projects: List<UUID>?): ThreadContextElement<List<UUID>?> = local.asContextElement(projects)

    fun current(): List<UUID>? = local.get()
}

// resuelve los proyectos de ?assigned=true antes del controlador: solo en /api, solo con quien llama autenticado (después de la
// cadena de seguridad y de RequestMemoFilter) y solo cuando la petición lo pide, así que ninguna otra lectura paga la consulta
// de asignaciones. CoWebFilter pasa el contexto de la corrutina al controlador (COROUTINE_CONTEXT_ATTRIBUTE de Spring)
@Component
@Order(AssignedProjectsFilter.ORDER)
class AssignedProjectsFilter(
    private val callers: Callers,
    private val reach: ProjectReach
) : CoWebFilter() {
    override suspend fun filter(
        exchange: ServerWebExchange,
        chain: CoWebFilterChain
    ) {
        val path =
            exchange.request.path
                .pathWithinApplication()
                .value()
        val asked =
            exchange.request.queryParams
                .getFirst(AssignedQuery.ASSIGNED)
                ?.trim()
                ?.lowercase() == "true"
        val user = if (asked && path.startsWith("/api/")) callers.userOrNull() else null
        if (user == null) return chain.filter(exchange)
        val projects = reach.assigned(user)
        withContext(AssignedProjects.element(projects)) { chain.filter(exchange) }
    }

    companion object {
        // después de RequestMemoFilter (-98), que va después de la cadena de seguridad (-100)
        const val ORDER = -97
    }
}
