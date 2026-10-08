package sgspe.scope

import org.springframework.stereotype.Component
import sgspe.common.LIMA
import sgspe.common.ModelCatalog
import sgspe.common.Records
import sgspe.common.RequestMemo
import sgspe.common.Validity
import sgspe.common.Values
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

// una fila de project_assignment, como la leen el alcance y los destinatarios (RF-PRJ-005, ASM-070)
data class Assignment(
    val id: UUID,
    val project: UUID,
    val memberUserId: UUID,
    val memberUserEmail: String,
    val roleName: String,
    val active: Boolean,
    val startDate: LocalDate?,
    val endDate: LocalDate?
) {
    // vigente: active y, si tiene fechas, hoy (en Lima) entre ellas (security.md §9.2, workflows.md «Destinatarios»)
    fun inForce(today: LocalDate): Boolean = Validity.inForce(active, startDate, endDate, today)

    companion object {
        fun of(
            id: String,
            attributes: Map<String, Any?>
        ): Assignment? {
            val project = Records.uuid(attributes["project"]) ?: return null
            val member = Records.uuid(attributes["member_user_id"]) ?: return null
            return Assignment(
                id = UUID.fromString(id),
                project = project,
                memberUserId = member,
                memberUserEmail = Values.text(attributes["member_user_email"]).orEmpty(),
                roleName = Values.text(attributes["role_name"]).orEmpty(),
                active = Values.bool(attributes["active"]) ?: false,
                startDate = Values.text(attributes["start_date"])?.let(LocalDate::parse),
                endDate = Values.text(attributes["end_date"])?.let(LocalDate::parse)
            )
        }
    }
}

// quien recibe un aviso dirigido a los roles de una transición: su usuario y su correo
data class Recipient(
    val userId: UUID,
    val email: String
)

// las asignaciones a proyectos: la fuente del alcance de escritura (ProjectScopeGuard) y de los destinatarios por rol
// (ASM-070). se leen con Records: como quien llama (que tiene READ sobre project_assignment, security.md §5.8) o como la
// plataforma en un trabajo de fondo
@Component
class Assignments(
    private val records: Records,
    private val catalog: ModelCatalog,
    private val clock: Clock
) {
    fun today(): LocalDate = todayIn(clock)

    suspend fun ofProject(project: UUID): List<Assignment> =
        records
            .all(OBJECT, filters = mapOf("project" to project.toString()))
            .mapNotNull { Assignment.of(it.id, it.attributes) }
            .filter { it.project == project }

    // los proyectos con una asignación vigente del usuario: el filtro por defecto de los tableros (RF-PRJ-005)
    suspend fun projectsOf(userId: UUID): List<UUID> =
        records
            .all(OBJECT, filters = mapOf("member_user_id" to userId.toString()))
            .mapNotNull { Assignment.of(it.id, it.attributes) }
            .filter { it.memberUserId == userId && it.inForce(today()) }
            .map { it.project }
            .distinct()

    // si el usuario tiene una asignación vigente al proyecto. dentro de un comando que abrió RequestMemo (la confirmación de
    // una importación) se lee una vez por usuario y proyecto, no una vez por registro escrito
    suspend fun isAssigned(
        userId: UUID,
        project: UUID
    ): Boolean =
        RequestMemo.remember("$OBJECT:$userId:$project") {
            records
                .all(OBJECT, filters = mapOf("project" to project.toString(), "member_user_id" to userId.toString()))
                .mapNotNull { Assignment.of(it.id, it.attributes) }
                // los filtros ya acotan la consulta; aquí se vuelve a comprobar porque esto es una frontera de seguridad y un
                // filtro ignorado daría alcance a cualquiera
                .any { it.project == project && it.memberUserId == userId && it.inForce(today()) }
        }

    // las asignaciones vigentes del proyecto cuyo role_name está entre los roles de la transición, salvo quien la aplicó
    // y las cuentas de servicio (su correo de respaldo, WAS-ADR-0043). los roles salen de model/workflows/<objeto>.json,
    // nunca de un literal (RQ-SEC-005). sin coincidencias, lista vacía: quien pidió el aviso lo registra (workflows.md)
    suspend fun recipients(
        project: UUID,
        objectName: String,
        transition: String,
        exclude: UUID?
    ): List<Recipient> {
        val roles =
            catalog
                .transition(objectName, transition)
                ?.roles
                ?.toSet()
                .orEmpty()
        if (roles.isEmpty()) return emptyList()
        val today = today()
        return ofProject(project)
            .filter { it.inForce(today) && it.roleName in roles && it.memberUserId != exclude }
            .filterNot { it.memberUserEmail.endsWith(SERVICE_ACCOUNT_DOMAIN) }
            .map { Recipient(it.memberUserId, it.memberUserEmail) }
            .distinctBy { it.userId }
    }

    companion object {
        // el día de Lima del reloj: a las 03:00 UTC todavía es el día anterior
        fun todayIn(clock: Clock): LocalDate = LocalDate.now(clock.withZone(LIMA))

        const val OBJECT = "project_assignment"
        const val SERVICE_ACCOUNT_DOMAIN = "@service-accounts.invalid"
    }
}
