package sgspe.deadline

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import sgspe.commitment.Commitments
import sgspe.common.Attention
import sgspe.common.Criteria
import sgspe.common.LIMA
import sgspe.common.ProjectParameters
import sgspe.common.Records
import sgspe.common.Values
import sgspe.notification.ConfidentialReaders
import sgspe.notification.NoticeTexts
import sgspe.notification.Notices
import sgspe.scope.Assignment
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

// un aviso de plazo planeado: su clave, su tipo, su texto y a quién va, antes de resolver los destinatarios. `level`: el nivel
// de escalamiento de un aviso de vencido (null en uno por vencer); el nivel 2 de una QCS confidencial suma el aviso genérico
data class PlannedNotice(
    val key: String,
    val kind: NotificationKind,
    val text: Pair<String, String>,
    val audience: NoticeAudience,
    val level: Int? = null
)

// qué avisos de plazo corresponden a un registro, puro (workflows.md, «Avisos» y «Destinatarios por rol», ASM-030, ASM-070):
// el responsable al estar por vencer y al vencer (nivel 0); los roles de la transición de verificación o revisión en el nivel
// 1; los de approve_senior de WF-D en el nivel 2. cada nivel alcanzado sigue abierto mientras dure el atraso
object DeadlineNoticePlan {
    // la transición de referencia del nivel 1 de una tarea, por su registro vinculado (workflows.md); F4 suma las QCS (verify
    // de WF-E1, como el nivel 1 de sus plazos) y el plan 4.2b, los riesgos
    val TASK_REFERENCE =
        linkedMapOf(
            Commitments.COMMITMENT to (Commitments.COMMITMENT to "verify"),
            "engagement" to ("engagement" to "approve_minutes"),
            "agreement" to ("agreement" to "activate"),
            "stakeholder" to ("stakeholder" to "assess"),
            Attention.GRIEVANCE to (Attention.GRIEVANCE to "verify"),
            Attention.INQUIRY to (Attention.GRIEVANCE to "verify"),
            Attention.REQUEST to (Attention.GRIEVANCE to "verify")
        )

    // la transición que sigue en cada estado con plazo de una QCS: sus roles reciben el nivel 0 de una QCS sin responsable
    val QCS_NEXT: Map<String, Map<String, String>> =
        mapOf(
            Attention.GRIEVANCE to
                mapOf(
                    "received" to "acknowledge",
                    "acknowledged" to "assess",
                    "in_assessment" to "start_action",
                    "in_investigation" to "start_action_after_investigation",
                    "in_action" to "resolve"
                ),
            Attention.INQUIRY to mapOf("received" to "answer")
        )

    // cómo nombra el aviso a una QCS: su tipo y su código
    val QCS_LABEL: Map<String, String> = mapOf(Attention.GRIEVANCE to "Queja", Attention.INQUIRY to "Consulta")

    // una queja o una consulta (workflows.md «Avisos», ASM-102): POR VENCER al responsable o, sin él, a los roles de la
    // transición que sigue; vencida, el nivel 0 igual, el 1 a los roles de verify de WF-E1 y el 2 a los de approve_senior de
    // WF-D, porque WF-E no tiene aprobación superior
    fun qcs(
        objectName: String,
        id: String,
        label: String,
        state: String?,
        status: String?,
        due: LocalDate,
        level: Int,
        owner: UUID?
    ): List<PlannedNotice> {
        val first = owner?.let { NoticeAudience.Person(it) } ?: QCS_NEXT[objectName]?.get(state)?.let { NoticeAudience.Roles(objectName, it) }
        val key = "qcs_deadline:$objectName:$id:$due"
        return when (status) {
            Deadlines.DUE_SOON ->
                listOfNotNull(
                    first?.let {
                        PlannedNotice("$key:${Deadlines.DUE_SOON}", NotificationKind.WARNING, NoticeTexts.qcsDueSoon(label, due), it)
                    }
                )
            Deadlines.OVERDUE ->
                (0..level).mapNotNull { reached ->
                    val audience =
                        when (reached) {
                            0 -> first
                            1 -> NoticeAudience.Roles(Attention.GRIEVANCE, "verify")
                            else -> SENIOR
                        } ?: return@mapNotNull null
                    PlannedNotice("$key:$reached", NotificationKind.ACTION, NoticeTexts.qcsOverdue(label, due, reached), audience, reached)
                }
            else -> emptyList()
        }
    }

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
// vigentes del proyecto del registro con ese role_name (ASM-070); sin coincidencias no hay aviso (workflows.md). desde F4 suma
// las quejas y las consultas (qcs_deadline) en las organizaciones que las tienen; el aviso de una QCS confidencial, y el de una
// tarea vinculada a ella, solo va a quien la vería (ConfidentialReaders, ASM-102), y en el nivel 2 los roles de approve_senior
// que no la ven reciben qcs_confidential_overdue, sin datos del registro ni enlace
@Component
class DeadlineNotices(
    private val records: Records,
    private val assignments: Assignments,
    private val readers: ConfidentialReaders
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
        val qcs = Deadlines.TRACKED.filter { it.objectName in Attention.OBJECTS }.associateWith { tracked -> qcsRows(tracked) }
        val projects = mutableMapOf<String, Map<String, Any?>>()
        val recipients = mutableMapOf<Triple<UUID, String, String>, List<UUID>>()
        val teams = mutableMapOf<UUID, List<Assignment>>()

        suspend fun team(project: UUID): List<Assignment> = teams.getOrPut(project) { assignments.ofProject(project) }

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

        // `confidential`: la QCS (su objeto y sus atributos) cuyo aviso solo va a quien la vería; null si no es confidencial
        suspend fun add(
            row: RecordResponse,
            objectName: String,
            plan: List<PlannedNotice>,
            due: LocalDate,
            confidential: Pair<String, Map<String, Any?>>? = null
        ) {
            val project = Records.uuid(row.attributes["project"]) ?: return
            for (notice in plan) {
                val all = users(project, notice.audience)
                val users = confidential?.let { (qcs, attributes) -> readers.filter(qcs, attributes, all, team(project), today) } ?: all
                if (confidential != null && notice.level == 2 && objectName == confidential.first) {
                    // el nivel 2 de una QCS confidencial: quienes no la ven reciben el aviso genérico (ASM-102)
                    val blind = all - users.toSet()
                    if (blind.isNotEmpty()) {
                        val name = Values.text(params(project.toString())["name"]) ?: Values.text(params(project.toString())["code"]) ?: project.toString()
                        val key = "qcs_confidential_overdue:$project:${ConfidentialReaders.fingerprint(objectName, row.id, due)}"
                        drafts += Notices.draft(key, NotificationKind.ACTION, NoticeTexts.qcsConfidentialOverdue(name), blind, null, null)
                    }
                }
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
        for ((tracked, rows) in qcs) {
            for (row in rows) {
                val due = Deadlines.due(tracked, row.state, row.attributes) ?: continue
                val params = params(Values.text(row.attributes["project"]) ?: continue)
                val label = "${DeadlineNoticePlan.QCS_LABEL.getValue(tracked.objectName)} ${Values.text(row.attributes["code"]) ?: row.id}"
                val plan =
                    DeadlineNoticePlan.qcs(
                        tracked.objectName,
                        row.id,
                        label,
                        row.state,
                        Values.text(row.attributes[tracked.field]),
                        due,
                        level(due, today, params),
                        Records.uuid(row.attributes[Attention.OWNER])
                    )
                val confidential = if (Attention.isConfidential(row.attributes)) tracked.objectName to row.attributes else null
                add(row, tracked.objectName, plan, due, confidential)
            }
        }
        // las QCS confidenciales a las que apunta una tarea: su aviso de vencida también se filtra (ASM-102)
        val linkedQcs = linkedConfidential(tasks)
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
            add(row, TASK, plan, due, linked?.let { linkedQcs[it to Values.text(row.attributes[it])] }?.let { linked to it })
        }
        return drafts
    }

    // las QCS de una organización con situación de plazo, en los estados que se miden; vacío si la organización no tiene el
    // modelo de F4
    private suspend fun qcsRows(tracked: Deadlines.Tracked): List<RecordResponse> =
        try {
            records
                .all(tracked.objectName, criteria = listOf(Criteria.inState(tracked.states)))
                .filter { Values.text(it.attributes[tracked.field]) in setOf(Deadlines.DUE_SOON, Deadlines.OVERDUE) }
        } catch (_: NotFoundException) {
            emptyList()
        }

    // las QCS confidenciales vinculadas a las tareas, por (objeto, id): una lectura por objeto
    private suspend fun linkedConfidential(tasks: List<RecordResponse>): Map<Pair<String, String?>, Map<String, Any?>> =
        Attention.OBJECTS
            .flatMap { objectName ->
                val ids = tasks.mapNotNull { Records.uuid(it.attributes[objectName]) }.distinct()
                if (ids.isEmpty()) return@flatMap emptyList()
                records
                    .all(objectName, ids = ids)
                    .filter { Attention.isConfidential(it.attributes) }
                    .map { (objectName to it.id) to it.attributes }
            }.toMap()

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
