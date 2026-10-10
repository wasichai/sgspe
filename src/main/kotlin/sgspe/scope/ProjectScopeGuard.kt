package sgspe.scope

import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import sgspe.common.Callers
import sgspe.common.Finding
import sgspe.common.GuardOrder
import sgspe.common.RuleGuard
import sgspe.common.WriteView
import wasichai.core.data.RecordWrite

// la escritura acotada por asignación (security.md §9.2, RQ-SEC-006, CON-041): altas, cambios, bajas, transiciones y
// vínculos de los objetos de un proyecto solo para quien tiene una project_assignment vigente a ese proyecto. desde 4.4 la
// lectura la acota ProjectReadScope (§9.7) y esta guarda es la segunda línea: leer un proyecto no basta para escribir en
// él. el alcance de lectura ya rechaza el registro o el project que la persona no lee, pero deja pasar el alta o el cambio
// que apunta a un proyecto que lee sin estar asignada (READ_ALL_PROJECTS); eso lo frena esta guarda. exentos: ADMIN
// (isAdmin), la plataforma y las automatizaciones (sin usuario), el alta de un project (aún sin equipo) y las asignaciones
// (forman el equipo). decide con datos, nunca con nombres de rol (RQ-SEC-005)
@Component
@Order(GuardOrder.SCOPE)
class ProjectScopeGuard(
    private val callers: Callers,
    private val view: WriteView,
    private val resolver: ProjectResolver,
    private val assignments: Assignments
) : RuleGuard() {
    override suspend fun findings(
        change: RecordWrite,
        dryRun: Boolean
    ): List<Finding> {
        if (change.userId == null || change.objectName in ProjectScope.EXEMPT || !ProjectScope.covers(change.objectName)) return emptyList()
        // con userId la escritura es de una persona: si no se la puede identificar, se niega (falla cerrado)
        val user = callers.userOrNull() ?: return listOf(notAssigned("project", "No se pudo identificar a quien escribe, así que no se le puede dar alcance"))
        if (user.isAdmin) return emptyList()
        val scope = resolver.resolve(change, view.after(change))
        val findings = mutableListOf<Finding>()
        if (scope.unresolved) {
            findings +=
                notAssigned(
                    scope.field,
                    "No se pudo determinar el proyecto de este registro (${scope.field}): solo el administrador escribe en un registro sin proyecto"
                )
        }
        for (project in scope.projects) {
            if (!assignments.isAssigned(user.userId, project)) {
                findings += notAssigned(scope.field, "No tiene una asignación vigente al proyecto $project: solo su equipo escribe en él")
            }
        }
        return findings
    }

    // field: el campo de la relación de alcance (project en las raíces, el padre en los hijos), el que el portal pinta
    private fun notAssigned(
        field: String,
        message: String
    ) = Finding(code = "NOT_ASSIGNED", message = message, field = field, status = HttpStatus.FORBIDDEN)
}
