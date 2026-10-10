package sgspe.common

import org.springframework.stereotype.Component
import wasichai.core.common.ForbiddenException
import wasichai.core.identity.AuthenticatedUser
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

// «mis proyectos» o todos (elección 6 de la especificación 4.4, ASM-106): la única pieza que lo decide. la comparten el alcance
// de lectura (ProjectReadScope), los proyectos por defecto de tableros, reporte y exportaciones, y la exención de
// ExportProjects y de ExportLog. lee todos los proyectos el ADMIN y quien tiene la acción declarada READ_ALL_PROJECTS sobre
// project (en roles.json, AUDITOR y PROJECT_ADMIN); nunca compara nombres de rol (RQ-SEC-005). vive en common porque scope y
// privacy no se conocen (architecture.md §5.3)
@Component
class ProjectReach(
    private val records: Records,
    private val permissions: Permissions,
    private val clock: Clock
) {
    // si quien llama lee todos los proyectos del tenant. se pregunta una sola vez por petición de /api (RequestMemoFilter) o
    // dentro de un comando que abrió RequestMemo
    suspend fun readsAll(user: AuthenticatedUser): Boolean =
        user.isAdmin || RequestMemo.rememberForRequest(MEMO to user.userId) { permissions.has(user, READ_ALL_PROJECTS, PROJECT) }

    // las asignaciones vigentes de quien llama (Validity.inForce, hoy en Lima), sin repetidos. se leen como quien llama: el
    // alcance le deja ver las suyas aunque estén vencidas (ASM-106). sin READ sobre project_assignment, ninguna
    suspend fun assigned(user: AuthenticatedUser): List<UUID> {
        val today = LocalDate.now(clock.withZone(LIMA))
        val rows =
            try {
                records.all(ASSIGNMENT, filters = mapOf("member_user_id" to user.userId.toString()))
            } catch (_: ForbiddenException) {
                return emptyList()
            }
        return rows
            .filter { row ->
                Records.uuid(row.attributes["member_user_id"]) == user.userId &&
                    Validity.inForce(
                        Values.bool(row.attributes["active"]) == true,
                        Values.text(row.attributes["start_date"])?.let(LocalDate::parse),
                        Values.text(row.attributes["end_date"])?.let(LocalDate::parse),
                        today
                    )
            }.mapNotNull { Records.uuid(it.attributes["project"]) }
            .distinct()
    }

    // si quien llama alcanza `project`: lo lee todo o está asignado a él. la exención de ExportProjects
    suspend fun reaches(
        user: AuthenticatedUser,
        project: UUID
    ): Boolean {
        if (readsAll(user)) return true
        return project in assigned(user)
    }

    // los proyectos por defecto: null (todos los que lee) para quien lee todo; si no, sus asignaciones vigentes (quizá ninguna)
    suspend fun mine(user: AuthenticatedUser): List<UUID>? {
        if (readsAll(user)) return null
        return assigned(user)
    }

    companion object {
        const val READ_ALL_PROJECTS = "READ_ALL_PROJECTS"
        const val PROJECT = "project"
        const val ASSIGNMENT = "project_assignment"
        private const val MEMO = "project-reach"
    }
}
