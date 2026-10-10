package sgspe.notification

import kotlinx.coroutines.CancellationException
import org.slf4j.LoggerFactory
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import sgspe.common.Records
import sgspe.common.Values
import sgspe.scope.Assignments
import wasichai.core.common.ForbiddenException
import wasichai.core.data.RecordChange
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordChangeListener
import wasichai.notifications.NotificationKind
import wasichai.notifications.NotificationLink
import java.util.UUID

// el aviso de alerta nueva, puro (workflows.md «Avisos»): qué hace un cambio de una alerta con él
object WarningNotice {
    enum class Effect { PUBLISH, RESOLVE, NONE }

    const val WARNING = "early_warning"
    const val START_TRIAGE = "start_triage"

    fun key(id: UUID): String = "warning_new:$id"

    // el alta lo publica; start_triage lo resuelve
    fun effect(change: RecordChange): Effect =
        when {
            change.objectName != WARNING -> Effect.NONE
            change.kind == RecordChangeKind.CREATED -> Effect.PUBLISH
            change.kind == RecordChangeKind.TRANSITIONED && change.transition == START_TRIAGE -> Effect.RESOLVE
            else -> Effect.NONE
        }
}

// WarningNoticeListener (sgspe.notification, workflows.md «Listeners» y «Avisos»): el alta de una early_warning, por una persona
// o por JOB-04 como la plataforma, publica warning_new:<id> en la fuente sgspe.warnings para su responsable, si lo tiene, y los
// asignados vigentes con un rol de start_triage, salvo quien la creó; start_triage lo resuelve. corre en la transacción del
// alta (JOB-04 abre la suya) como quien escribe; si no lee las asignaciones, lo registra y el alta sigue
@Component
@Order(230)
class WarningNoticeListener(
    private val notices: Notices,
    private val assignments: Assignments
) : RecordChangeListener {
    private val log = LoggerFactory.getLogger(javaClass)

    override suspend fun recordChanged(change: RecordChange) {
        try {
            when (WarningNotice.effect(change)) {
                WarningNotice.Effect.PUBLISH -> publish(change)
                WarningNotice.Effect.RESOLVE -> notices.resolve(change.organizationId, Notices.WARNINGS, WarningNotice.key(change.recordId))
                WarningNotice.Effect.NONE -> Unit
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: ForbiddenException) {
            log.error("WarningNoticeListener: quien creó la alerta {} no lee las asignaciones; no hay aviso", change.recordId, e)
        }
    }

    private suspend fun publish(change: RecordChange) {
        val attributes = change.after.orEmpty()
        val project = Records.uuid(attributes["project"]) ?: return
        val roles = assignments.recipients(project, WarningNotice.WARNING, WarningNotice.START_TRIAGE, change.userId).map { it.userId }
        val owner = Records.uuid(attributes["owner_user_id"])?.takeIf { it != change.userId }
        val users = (listOfNotNull(owner) + roles).distinct()
        if (users.isEmpty()) {
            log.warn(
                "WarningNoticeListener: la alerta {} no tiene responsable ni asignaciones vigentes con un rol de start_triage; no hay aviso",
                change.recordId
            )
            return
        }
        notices.publish(
            change.organizationId,
            Notices.WARNINGS,
            WarningNotice.key(change.recordId),
            NotificationKind.ACTION,
            NoticeTexts.warningNew(Values.text(attributes["title"]) ?: WarningNotice.WARNING, Values.text(attributes["severity"])),
            users,
            NotificationLink.Record(WarningNotice.WARNING, change.recordId)
        )
    }
}
