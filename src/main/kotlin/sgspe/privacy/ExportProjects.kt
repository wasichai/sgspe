package sgspe.privacy

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import sgspe.common.Finding
import sgspe.common.ProjectReach
import sgspe.common.toCodedException
import wasichai.core.identity.AuthenticatedUser
import java.util.UUID

// los proyectos de una exportación (api.md §4.13), la misma regla para todos los tipos: los pedidos o, si no se pide ninguno,
// «mis proyectos» de ProjectReach (las asignaciones vigentes de quien exporta, hoy en Lima); quien lee todos los proyectos (el
// ADMIN y quien tiene READ_ALL_PROJECTS), todos (null). con un solo proyecto pedido, quien no lo alcanza (no lo lee todo ni está
// asignado a él) recibe 403 NOT_ASSIGNED sobre filters.project. las filas, además, las acota el alcance de lectura
// (ProjectReadScope). privacy solo depende de common (architecture.md §5.3). la comparten las cuatro exportaciones
@Component
class ExportProjects(
    private val reach: ProjectReach
) {
    suspend fun resolve(
        user: AuthenticatedUser,
        requested: List<UUID>
    ): List<UUID>? {
        if (requested.isNotEmpty()) {
            requireAssigned(user, requested)
            return requested
        }
        return reach.mine(user)
    }

    // se comprueba antes de leer nada; ProjectScopeGuard lo vuelve a exigir si la fila de export_log lleva el proyecto
    suspend fun requireAssigned(
        user: AuthenticatedUser,
        requested: List<UUID>
    ) {
        val single = requested.singleOrNull() ?: return
        if (reach.reaches(user, single)) return
        throw Finding(
            "NOT_ASSIGNED",
            "Exportar un proyecto exige una asignación vigente a él",
            "filters.project",
            status = HttpStatus.FORBIDDEN
        ).toCodedException()
    }
}
