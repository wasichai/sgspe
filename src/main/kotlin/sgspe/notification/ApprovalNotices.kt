package sgspe.notification

import kotlinx.coroutines.CancellationException
import org.slf4j.LoggerFactory
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import sgspe.commitment.Commitments
import sgspe.commitment.NotReadable
import sgspe.commitment.SeniorApprovalReader
import sgspe.common.ModelCatalog
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

// un envío a aprobación y lo que lo resuelve (workflows.md, «Avisos» y «Destinatarios por rol»)
data class ApprovalStep(
    val objectName: String,
    val submit: String,
    val pending: String,
    val approvals: List<String>
)

// el aviso «aprobación pendiente» (RF-NTF-001): al enviar un proyecto, un acta o un compromiso a aprobación, un aviso ACTION
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
    private val senior: SeniorApprovalReader
) : RecordChangeListener {
    private val log = LoggerFactory.getLogger(javaClass)

    override suspend fun recordChanged(change: RecordChange) {
        if (change.kind != RecordChangeKind.TRANSITIONED) return
        val step = STEPS.firstOrNull { it.objectName == change.objectName } ?: return
        when {
            change.transition == step.submit -> publish(step, change)
            catalog.transition(step.objectName, change.transition ?: return)?.from == step.pending ->
                notices.resolve(change.organizationId, Notices.APPROVALS, key(step, change.recordId))
        }
    }

    private suspend fun publish(
        step: ApprovalStep,
        change: RecordChange
    ) {
        val attributes = change.after ?: change.before.orEmpty()
        val project = if (step.objectName == PROJECT) change.recordId else Records.uuid(attributes["project"]) ?: return
        val users =
            try {
                approvals(step, change.recordId, attributes).flatMap { assignments.recipients(project, step.objectName, it, change.userId) }.map { it.userId }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ForbiddenException) {
                log.error("ApprovalNoticeListener: quien envió {} {} no lee las asignaciones; no hay aviso", step.objectName, change.recordId, e)
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

    // las transiciones cuyos roles reciben el aviso: un compromiso que exige nivel superior, solo approve_senior. si quien envió
    // no puede leer lo que la regla necesita (NotReadable), no se sabe qué nivel exige: el aviso va a la unión de los roles de
    // approve y de approve_senior, nunca a nadie ni solo a uno de los dos. quien no corresponde recibe un aviso de más, que la
    // guarda de la aprobación (D5) deja sin efecto; callar o elegir un nivel dejaría el compromiso sin aprobador que lo sepa
    private suspend fun approvals(
        step: ApprovalStep,
        id: UUID,
        attributes: Map<String, Any?>
    ): List<String> {
        if (step.objectName != Commitments.COMMITMENT) return step.approvals
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

        val STEPS =
            listOf(
                ApprovalStep(PROJECT, "submit_for_review", "in_review", listOf("approve")),
                ApprovalStep("engagement", "submit_minutes", "minutes_in_review", listOf("approve_minutes")),
                ApprovalStep(Commitments.COMMITMENT, "submit", "pending_approval", listOf("approve", SENIOR))
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
                PROJECT -> listOfNotNull(Values.text(attributes["code"]), Values.text(attributes["name"]))
                Commitments.COMMITMENT -> listOfNotNull(Values.text(attributes["code"]), Values.text(attributes["title"]))
                else -> listOfNotNull(Values.text(attributes["title"]))
            }.joinToString(" · ").ifEmpty { objectName }
    }
}
