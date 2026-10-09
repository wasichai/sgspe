package sgspe.privacy

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import sgspe.common.Finding
import sgspe.common.LIMA
import sgspe.common.Records
import sgspe.common.Validity
import sgspe.common.Values
import sgspe.common.toCodedException
import wasichai.core.common.ForbiddenException
import wasichai.core.identity.AuthenticatedUser
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

// los proyectos de una exportación (api.md §4.13), la misma regla para todos los tipos: los pedidos o, si no se pide ninguno,
// «mis proyectos» (las asignaciones vigentes de quien exporta, hoy en Lima); el ADMIN, todos (null). con un solo proyecto
// pedido, quien no es ADMIN debe estar asignado a él (alcance opción A, security.md §9.2): si no, 403 NOT_ASSIGNED sobre
// filters.project. privacy solo depende de common (architecture.md §5.3): lee project_assignment con Records y aplica la
// vigencia de common. desde F3 lo comparten stakeholder-register y commitment-matrix
@Component
class ExportProjects(
    private val records: Records,
    private val clock: Clock
) {
    suspend fun resolve(
        user: AuthenticatedUser,
        requested: List<UUID>
    ): List<UUID>? =
        when {
            requested.isNotEmpty() -> requested.also { requireAssigned(user, it) }
            user.isAdmin -> null
            else -> assigned(user)
        }

    // sin READ sobre project_assignment no tiene asignaciones que leer
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

    // se comprueba antes de leer nada; ProjectScopeGuard lo vuelve a exigir al escribir la fila de export_log
    suspend fun requireAssigned(
        user: AuthenticatedUser,
        requested: List<UUID>
    ) {
        val single = requested.singleOrNull() ?: return
        if (user.isAdmin || single in assigned(user)) return
        throw Finding(
            "NOT_ASSIGNED",
            "Exportar un proyecto exige una asignación vigente a él",
            "filters.project",
            status = HttpStatus.FORBIDDEN
        ).toCodedException()
    }

    companion object {
        const val ASSIGNMENT = "project_assignment"
    }
}
