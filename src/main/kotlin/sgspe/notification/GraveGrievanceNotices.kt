package sgspe.notification

import kotlinx.coroutines.CancellationException
import org.slf4j.LoggerFactory
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import sgspe.common.Attention
import sgspe.common.ModelCatalog
import sgspe.common.ProjectParameters
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

// qué hace un cambio de una queja con su aviso de aprobación pendiente, puro (decisión 8 de F4, ASM-102, RN-GRV-002):
// publicarlo al entrar una queja grave en in_action o al subir a ALTA o CRITICA la severidad de una que ya está ahí, con
// policy_high_severity_requires_approver activo; resolverlo al salir de in_action o al bajar la severidad
object GraveGrievanceNotice {
    enum class Effect { PUBLISH, RESOLVE, NONE }

    const val IN_ACTION = "in_action"
    val ENTERS: Set<String> = setOf("start_action", "start_action_after_investigation")

    suspend fun effect(
        change: RecordChange,
        from: String?,
        policy: suspend () -> Boolean
    ): Effect {
        val before = change.before.orEmpty()
        val after = change.after ?: before
        return when (change.kind) {
            RecordChangeKind.TRANSITIONED ->
                when {
                    change.transition in ENTERS && grave(after) && policy() -> Effect.PUBLISH
                    from == IN_ACTION -> Effect.RESOLVE
                    else -> Effect.NONE
                }
            RecordChangeKind.UPDATED ->
                when {
                    change.state != IN_ACTION || Values.same(before[Attention.SEVERITY], after[Attention.SEVERITY]) -> Effect.NONE
                    grave(after) && !grave(before) && policy() -> Effect.PUBLISH
                    !grave(after) && grave(before) -> Effect.RESOLVE
                    else -> Effect.NONE
                }
            else -> Effect.NONE
        }
    }

    fun grave(attributes: Map<String, Any?>): Boolean = Values.text(attributes[Attention.SEVERITY]) in Attention.HIGH_SEVERITY

    fun key(id: UUID): String = "approval_pending:${Attention.GRIEVANCE}:$id"
}

// el aviso «aprobación pendiente» de una queja grave (decisión 8 de F4, workflows.md «Avisos»): el paso de F4 de los avisos de
// aprobación, en la fuente sgspe.approvals y con la clave approval_pending:grievance:<id>, para los asignados vigentes con un
// rol de resolve_high_severity salvo quien escribió. una queja confidencial solo avisa a quien la vería (ConfidentialReaders):
// quien tiene APPROVE ve las graves, así que el filtro no le quita el aviso. corre en la transacción de la escritura, como
// quien escribe; si no puede leer el proyecto o las asignaciones, lo registra y la escritura sigue
@Component
@Order(210)
class GraveGrievanceNoticeListener(
    private val notices: Notices,
    private val assignments: Assignments,
    private val catalog: ModelCatalog,
    private val readers: ConfidentialReaders,
    private val records: Records
) : RecordChangeListener {
    private val log = LoggerFactory.getLogger(javaClass)

    override suspend fun recordChanged(change: RecordChange) {
        if (change.objectName != Attention.GRIEVANCE) return
        if (change.kind == RecordChangeKind.UPDATED &&
            change.after
                .orEmpty()
                .keys
                .any { it.startsWith("rel:") }
        ) {
            return
        }
        val from = change.transition?.let { catalog.transition(Attention.GRIEVANCE, it)?.from }
        val attributes = change.after ?: change.before.orEmpty()
        val project = Records.uuid(attributes["project"]) ?: return
        try {
            val effect = GraveGrievanceNotice.effect(change, from) { policy(project) }
            when (effect) {
                GraveGrievanceNotice.Effect.PUBLISH -> publish(change, project, attributes)
                GraveGrievanceNotice.Effect.RESOLVE -> notices.resolve(change.organizationId, Notices.APPROVALS, GraveGrievanceNotice.key(change.recordId))
                GraveGrievanceNotice.Effect.NONE -> Unit
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: ForbiddenException) {
            log.error("GraveGrievanceNoticeListener: quien escribió la queja {} no lee el proyecto o las asignaciones; no hay aviso", change.recordId, e)
        }
    }

    private suspend fun policy(project: UUID): Boolean =
        ProjectParameters.policy(records.find("project", project)?.attributes.orEmpty(), "policy_high_severity_requires_approver")

    private suspend fun publish(
        change: RecordChange,
        project: UUID,
        attributes: Map<String, Any?>
    ) {
        val candidates = assignments.recipients(project, Attention.GRIEVANCE, RESOLVE_HIGH, change.userId).map { it.userId }
        val users = readers.filter(Attention.GRIEVANCE, attributes, candidates, assignments.ofProject(project), assignments.today()) ?: candidates
        if (users.isEmpty()) {
            log.warn("GraveGrievanceNoticeListener: la queja {} no tiene asignaciones vigentes con un rol de {}; no hay aviso", change.recordId, RESOLVE_HIGH)
            return
        }
        val label = "Queja ${Values.text(attributes["code"]) ?: change.recordId}"
        notices.publish(
            change.organizationId,
            Notices.APPROVALS,
            GraveGrievanceNotice.key(change.recordId),
            NotificationKind.ACTION,
            "Queja grave por resolver: $label" to "La queja es ${Values.text(attributes[Attention.SEVERITY])} y la resuelve quien tiene la aprobación.",
            users,
            NotificationLink.Record(Attention.GRIEVANCE, change.recordId)
        )
    }

    companion object {
        const val RESOLVE_HIGH = "resolve_high_severity"
    }
}
