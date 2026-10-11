package sgspe.notification

import kotlinx.coroutines.CancellationException
import org.slf4j.LoggerFactory
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import sgspe.commitment.Commitments
import sgspe.commitment.SeniorApprovalReader
import sgspe.common.ModelCatalog
import sgspe.common.NotReadable
import sgspe.common.ProjectParameters
import sgspe.common.Records
import sgspe.common.Values
import sgspe.common.readable
import sgspe.investment.Programs
import sgspe.scope.Assignments
import wasichai.core.common.ForbiddenException
import wasichai.core.data.RecordChange
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordChangeListener
import wasichai.notifications.NotificationKind
import wasichai.notifications.NotificationLink
import java.util.UUID

// un envío a aprobación y lo que lo resuelve (workflows.md, «Avisos» y «Destinatarios por rol»). `submit` null: el aviso sale
// con el alta del registro (WF-P, elección 14 de la especificación de F5) y `pending` es el estado inicial. `direct`: las
// transiciones cuyos roles también reciben el aviso si el proyecto no tiene aprobación interna (approve_direct de WF-P)
data class ApprovalStep(
    val objectName: String,
    val submit: String?,
    val pending: String,
    val approvals: List<String>,
    val direct: List<String> = emptyList()
)

// el aviso «aprobación pendiente» (RF-NTF-001): al enviar un proyecto, un acta o un compromiso a aprobación y al registrar un aporte (F5), un aviso ACTION
// para los asignados vigentes al proyecto cuyo role_name está entre los roles de la transición de aprobación que sigue
// (ASM-070), salvo quien envió y las cuentas de servicio (Assignments.recipients). los roles salen de model/workflows, nunca
// de un literal (RQ-SEC-005). un compromiso que exige nivel superior (RN-ENG-002) avisa solo a los roles de approve_senior,
// con la regla evaluada en el momento. cualquier transición que sale del estado pendiente resuelve el aviso: la bandeja
// muestra lo que sigue siendo cierto. corre en la transacción de la transición, como quien la aplica; si no puede leer las
// asignaciones, o no hay destinatarios, lo registra y la transición sigue (workflows.md, «Sin coincidencias»)
@Component
@Order(200)
class ApprovalNoticeListener(
    private val notices: Notices,
    private val assignments: Assignments,
    private val catalog: ModelCatalog,
    private val senior: SeniorApprovalReader,
    private val records: Records
) : RecordChangeListener {
    private val log = LoggerFactory.getLogger(javaClass)

    override suspend fun recordChanged(change: RecordChange) {
        val step = STEPS.firstOrNull { it.objectName == change.objectName } ?: return
        when (change.kind) {
            RecordChangeKind.CREATED -> if (step.submit == null) publish(step, change)
            RecordChangeKind.TRANSITIONED -> {
                val name = change.transition ?: return
                when {
                    step.submit != null && name == step.submit -> publish(step, change)
                    catalog.transition(step.objectName, name)?.from == step.pending ->
                        notices.resolve(change.organizationId, Notices.APPROVALS, key(step, change.recordId))
                }
            }
            else -> Unit
        }
    }

    private suspend fun publish(
        step: ApprovalStep,
        change: RecordChange
    ) {
        val attributes = change.after ?: change.before.orEmpty()
        val project = if (step.objectName == PROJECT) change.recordId else Records.uuid(attributes["project"]) ?: return
        val roles = approvals(step, change.recordId, attributes)
        // solo la lectura de las asignaciones: un Forbidden de las lecturas de la regla de nivel superior no es este caso
        val users =
            try {
                roles
                    .flatMap { assignments.recipients(project, step.objectName, it, change.userId) }
                    .map { it.userId }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ForbiddenException) {
                log.error(
                    "ApprovalNoticeListener: quien envió {} {} no lee las asignaciones; no hay aviso",
                    step.objectName,
                    change.recordId,
                    e
                )
                return
            }
        if (users.isEmpty()) {
            log.warn(
                "ApprovalNoticeListener: {} {} no tiene asignaciones vigentes con un rol de {}; no hay aviso",
                step.objectName,
                change.recordId,
                step.approvals
            )
            return
        }
        notices.publish(
            change.organizationId,
            Notices.APPROVALS,
            key(step, change.recordId),
            NotificationKind.ACTION,
            NoticeTexts.approvalPending(step.objectName, label(step.objectName, attributes)),
            users,
            NotificationLink.Record(step.objectName, change.recordId)
        )
    }

    // las transiciones cuyos roles reciben el aviso. un compromiso que exige nivel superior, solo approve_senior; si quien envió
    // no puede leer lo que la regla necesita (NotReadable), no se sabe qué nivel exige: el aviso va a la unión de los roles de
    // approve y de approve_senior, nunca a nadie ni solo a uno de los dos. un paso con `direct` (WF-P) suma sus roles si el
    // proyecto no tiene aprobación interna; si quien crea no lee el proyecto, también los suma (decisión 11 del plan 5.2a).
    // quien no corresponde recibe un aviso de más, que la guarda de la aprobación deja sin efecto; callar dejaría el registro
    // sin aprobador que lo sepa
    private suspend fun approvals(
        step: ApprovalStep,
        id: UUID,
        attributes: Map<String, Any?>
    ): List<String> {
        if (step.objectName == Commitments.COMMITMENT) return commitmentApprovals(step, id, attributes)
        if (step.direct.isEmpty()) return step.approvals
        val project = Records.uuid(attributes[PROJECT]) ?: return step.approvals + step.direct
        val internal =
            try {
                ProjectParameters.policy(records.readable(PROJECT, project).attributes, INTERNAL_APPROVAL)
            } catch (e: NotReadable) {
                log.warn("ApprovalNoticeListener: quien registró {} {} no lee su proyecto; el aviso va también a {}", step.objectName, id, step.direct)
                return step.approvals + step.direct
            }
        return if (internal) step.approvals else step.approvals + step.direct
    }

    private suspend fun commitmentApprovals(
        step: ApprovalStep,
        id: UUID,
        attributes: Map<String, Any?>
    ): List<String> {
        val required =
            try {
                senior.required(id, attributes)
            } catch (e: NotReadable) {
                log.warn(
                    "ApprovalNoticeListener: nivel de {} {} no evaluable ({}); el aviso va a todos los roles de {}",
                    step.objectName,
                    id,
                    e.message,
                    step.approvals
                )
                return step.approvals
            }
        return if (required) listOf(SENIOR) else step.approvals
    }

    companion object {
        const val PROJECT = "project"
        const val SENIOR = "approve_senior"
        const val CONTRIBUTION = "social_contribution"
        const val INTERNAL_APPROVAL = "policy_internal_approval"

        val STEPS =
            listOf(
                ApprovalStep(PROJECT, "submit_for_review", "in_review", listOf("approve")),
                ApprovalStep("engagement", "submit_minutes", "minutes_in_review", listOf("approve_minutes")),
                ApprovalStep(Commitments.COMMITMENT, "submit", "pending_approval", listOf("approve", SENIOR)),
                ApprovalStep(CONTRIBUTION, null, "requested", listOf("approve"), direct = listOf("approve_direct")),
                // F5: WF-G (especificación §3.8): al enviar, para los roles de approve; se resuelve al salir de pending_approval
                ApprovalStep(Programs.PROGRAM, Programs.SUBMIT, Programs.PENDING, listOf(Programs.APPROVE))
            )

        // una clave por registro: volver a enviarlo reabre el mismo aviso (es noticia otra vez, WAS-ADR-0060)
        fun key(
            step: ApprovalStep,
            id: UUID
        ): String = "approval_pending:${step.objectName}:$id"

        // cómo se nombra el registro en el aviso: código y nombre o título, sin datos personales
        fun label(
            objectName: String,
            attributes: Map<String, Any?>
        ): String =
            when (objectName) {
                PROJECT, Programs.PROGRAM -> listOfNotNull(Values.text(attributes["code"]), Values.text(attributes["name"]))
                Commitments.COMMITMENT -> listOfNotNull(Values.text(attributes["code"]), Values.text(attributes["title"]))
                CONTRIBUTION -> listOfNotNull(Values.text(attributes["code"]), Values.text(attributes["contribution_type"]))
                else -> listOfNotNull(Values.text(attributes["title"]))
            }.joinToString(" · ").ifEmpty { objectName }
    }
}
