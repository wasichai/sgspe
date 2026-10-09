package sgspe.deadline

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import sgspe.commitment.Commitments
import sgspe.common.Criteria
import sgspe.common.LIMA
import sgspe.common.ProjectParameters
import sgspe.common.Records
import sgspe.common.Values
import sgspe.notification.NoticeTexts
import sgspe.notification.Notices
import sgspe.scope.Assignments
import wasichai.core.common.NotFoundException
import wasichai.core.data.RecordResponse
import wasichai.notifications.NotificationDraft
import wasichai.notifications.NotificationKind
import wasichai.notifications.NotificationLink
import wasichai.notifications.NotificationSource
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

// a quién va un aviso de plazo: una persona (el responsable o el asignado) o los asignados con un rol de una transición
sealed interface NoticeAudience {
    data class Person(
        val userId: UUID
    ) : NoticeAudience

    data class Roles(
        val objectName: String,
        val transition: String
    ) : NoticeAudience
}

// un aviso de plazo planeado: su clave, su tipo, su texto y a quién va, antes de resolver los destinatarios
data class PlannedNotice(
    val key: String,
    val kind: NotificationKind,
    val text: Pair<String, String>,
    val audience: NoticeAudience
)

// qué avisos de plazo corresponden a un registro, puro (workflows.md, «Avisos» y «Destinatarios por rol», ASM-030, ASM-070):
// el responsable al estar por vencer y al vencer (nivel 0); los roles de la transición de verificación o revisión en el nivel
// 1; los de approve_senior de WF-D en el nivel 2. cada nivel alcanzado sigue abierto mientras dure el atraso
object DeadlineNoticePlan {
    // la transición de referencia del nivel 1 de una tarea, por su registro vinculado (workflows.md); F4 suma QCS y riesgos
    val TASK_REFERENCE =
        linkedMapOf(
            Commitments.COMMITMENT to (Commitments.COMMITMENT to "verify"),
            "engagement" to ("engagement" to "approve_minutes"),
            "agreement" to ("agreement" to "activate"),
            "stakeholder" to ("stakeholder" to "assess")
        )
    val SENIOR = NoticeAudience.Roles(Commitments.COMMITMENT, "approve_senior")

    fun commitment(
        id: String,
        label: String,
        status: String?,
        due: LocalDate,
        level: Int,
        owner: UUID?
    ): List<PlannedNotice> =
        when (status) {
            Deadlines.DUE_SOON ->
                listOfNotNull(
                    owner?.let {
                        PlannedNotice(
                            "commitment_due_soon:$id:$due",
                            NotificationKind.WARNING,
                            NoticeTexts.commitmentDueSoon(label, due),
                            NoticeAudience.Person(it)
                        )
                    }
                )
            Deadlines.OVERDUE ->
                (0..level).mapNotNull { reached ->
                    val audience =
                        when (reached) {
                            0 -> owner?.let { NoticeAudience.Person(it) }
                            1 -> NoticeAudience.Roles(Commitments.COMMITMENT, "verify")
                            else -> SENIOR
                        } ?: return@mapNotNull null
                    PlannedNotice("commitment_overdue:$id:$due:$reached", NotificationKind.ACTION, NoticeTexts.commitmentOverdue(label, due, reached), audience)
                }
            else -> emptyList()
        }

    // una tarea vencida: nivel 0 al asignado o, sin asignado, directamente el nivel 1; sin registro vinculado conocido, solo
    // el asignado y el nivel 2
    fun task(
        id: String,
        label: String,
        status: String?,
        due: LocalDate,
        level: Int,
        assignee: UUID?,
        linked: String?
    ): List<PlannedNotice> {
        if (status != Deadlines.OVERDUE) return emptyList()
        val reference = linked?.let { TASK_REFERENCE[it] }
        val effective = if (assignee == null) maxOf(level, 1) else level
        return (0..effective).mapNotNull { reached ->
            val audience =
                when (reached) {
                    0 -> assignee?.let { NoticeAudience.Person(it) }
                    1 -> reference?.let { NoticeAudience.Roles(it.first, it.second) }
                    else -> SENIOR
                } ?: return@mapNotNull null
            PlannedNotice("task_overdue:$id:$due:$reached", NotificationKind.ACTION, NoticeTexts.taskOverdue(label, due, reached), audience)
        }
    }
}

// los avisos de plazo como fuente de wasichai-notifications (decisión D2, WAS-ADR-0046): cada hora, en una réplica y como la
// plataforma de cada organización, el módulo pide todo lo que debería estar abierto ahora; lo que deja de estar (un compromiso
// cumplido, una tarea hecha) se resuelve solo, y lo que no cambió no se vuelve a escribir. lee deadline_status, que mantiene
// JOB-01, así que un aviso llega a lo sumo una hora después del cambio de situación. un aviso a roles llega a los asignados
// vigentes del proyecto del registro con ese role_name (ASM-070); sin coincidencias no hay aviso (workflows.md)
@Component
class DeadlineNotices(
    private val records: Records,
    private val assignments: Assignments
) : NotificationSource {
    private val log = LoggerFactory.getLogger(javaClass)

    override val key: String = Notices.DEADLINES

    override val interval: Duration = Duration.ofHours(1)

    override suspend fun currentNotifications(
        organizationId: UUID,
        now: Instant
    ): List<NotificationDraft> {
        val today = now.atZone(LIMA).toLocalDate()
        val commitments =
            try {
                records.all(Commitments.COMMITMENT, criteria = listOf(Criteria.inState(Commitments.PENDING)))
            } catch (_: NotFoundException) {
                return emptyList()
            }
        val tasks = records.all(TASK, criteria = listOf(Criteria.inState(Deadlines.TASK_OPEN)))
        val projects = mutableMapOf<String, Map<String, Any?>>()
        val recipients = mutableMapOf<Triple<UUID, String, String>, List<UUID>>()

        suspend fun params(id: String): Map<String, Any?> = projects.getOrPut(id) { records.find("project", UUID.fromString(id))?.attributes.orEmpty() }

        suspend fun users(
            project: UUID,
            audience: NoticeAudience
        ): List<UUID> =
            when (audience) {
                is NoticeAudience.Person -> listOf(audience.userId)
                is NoticeAudience.Roles ->
                    recipients.getOrPut(Triple(project, audience.objectName, audience.transition)) {
                        assignments.recipients(project, audience.objectName, audience.transition, null).map { it.userId }
                    }
            }
        val drafts = mutableListOf<NotificationDraft>()

        suspend fun add(
            row: RecordResponse,
            objectName: String,
            plan: List<PlannedNotice>,
            due: LocalDate
        ) {
            val project = Records.uuid(row.attributes["project"]) ?: return
            for (notice in plan) {
                val users = users(project, notice.audience)
                if (users.isEmpty()) {
                    // sin coincidencias no hay aviso y queda la advertencia (workflows.md, «Destinatarios por rol», punto 4);
                    // la tarea de escalamiento, si la hay, ya la creó JOB-01
                    log.warn("DeadlineNotices: {} no tiene destinatarios ({}); no hay aviso", notice.key, notice.audience)
                    continue
                }
                val link = NotificationLink.Record(objectName, UUID.fromString(row.id))
                drafts += Notices.draft(notice.key, notice.kind, notice.text, users, link, due.atStartOfDay(LIMA).toInstant())
            }
        }
        for (row in commitments) {
            val due = Values.text(row.attributes["due_date"])?.let(LocalDate::parse) ?: continue
            val params = params(Values.text(row.attributes["project"]) ?: continue)
            val level = level(due, today, params)
            val label = listOfNotNull(Values.text(row.attributes["code"]), Values.text(row.attributes["title"])).joinToString(" · ")
            val plan =
                DeadlineNoticePlan.commitment(
                    row.id,
                    label,
                    Values.text(row.attributes["deadline_status"]),
                    due,
                    level,
                    Records.uuid(row.attributes["owner_user_id"])
                )
            add(row, Commitments.COMMITMENT, plan, due)
        }
        for (row in tasks) {
            val due = Values.text(row.attributes["due_date"])?.let(LocalDate::parse) ?: continue
            val params = params(Values.text(row.attributes["project"]) ?: continue)
            val linked = DeadlineNoticePlan.TASK_REFERENCE.keys.firstOrNull { Values.text(row.attributes[it]) != null }
            val plan =
                DeadlineNoticePlan.task(
                    row.id,
                    Values.text(row.attributes["title"]) ?: TASK,
                    Values.text(row.attributes["deadline_status"]),
                    due,
                    level(due, today, params),
                    Records.uuid(row.attributes["assignee_user_id"]),
                    linked
                )
            add(row, TASK, plan, due)
        }
        return drafts
    }

    private fun level(
        due: LocalDate,
        today: LocalDate,
        params: Map<String, Any?>
    ): Int =
        if (!today.isAfter(due)) {
            0
        } else {
            Deadlines.level(due, today, ProjectParameters.integer(params, "escalation_first_days"), ProjectParameters.integer(params, "escalation_second_days"))
        }

    companion object {
        const val TASK = "task"
    }
}
