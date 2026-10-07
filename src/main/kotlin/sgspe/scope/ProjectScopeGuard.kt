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

// la escritura acotada por asignación, opción A del checkpoint (security.md §9.2, RQ-SEC-006, CON-041): altas, cambios,
// bajas, transiciones y vínculos de los objetos de un proyecto solo para quien tiene una project_assignment vigente a ese
// proyecto. la lectura sigue siendo de todo el tenant hasta UPS-01. exentos: ADMIN (isAdmin), la plataforma y las
// automatizaciones (sin usuario), el alta de un project (aún sin equipo) y las asignaciones (forman el equipo). decide con
// datos, nunca con nombres de rol (RQ-SEC-005)
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
        val user = callers.userOrNull() ?: return emptyList()
        if (user.isAdmin) return emptyList()
        val projects = resolver.projectsOf(change, view.after(change))
        val outside = projects.filterNot { assignments.isAssigned(user.userId, it) }
        return outside.map { project ->
            Finding(
                code = "NOT_ASSIGNED",
                message = "No tienes una asignación vigente al proyecto $project: solo su equipo escribe en él",
                field = "project",
                status = HttpStatus.FORBIDDEN
            )
        }
    }
}
