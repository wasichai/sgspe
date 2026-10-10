package sgspe.notification

import kotlinx.coroutines.CancellationException
import org.slf4j.LoggerFactory
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import sgspe.common.ModelCatalog
import sgspe.common.Records
import sgspe.common.Values
import sgspe.risk.AssessmentRow
import sgspe.risk.RiskLevels
import sgspe.risk.Risks
import sgspe.scope.Assignments
import wasichai.core.common.ForbiddenException
import wasichai.core.data.RecordChange
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordChangeListener
import wasichai.notifications.NotificationKind
import wasichai.notifications.NotificationLink
import java.util.UUID

// el aviso de riesgo escalado por nivel, puro (workflows.md «Avisos», RF-NTF-001): su clave y qué hace una revisión con él
object RiskNotice {
    fun key(
        risk: String,
        level: String?,
        date: Any?
    ): String = "risk_escalated:$risk:$level:$date"

    // la revisión no residual anterior a `current` (la más reciente de las otras); null si es la primera
    fun previous(
        current: String,
        history: List<AssessmentRow>
    ): AssessmentRow? = history.filter { !it.residual && it.id != current }.maxWithOrNull(AssessmentRow.ORDER)

    // la última revisión no residual de un riesgo: la que deja abierto el aviso que su cierre resuelve
    fun latest(history: List<AssessmentRow>): AssessmentRow? = history.filter { !it.residual }.maxWithOrNull(AssessmentRow.ORDER)
}

// RiskNoticeListener (sgspe.notification, workflows.md «Listeners» y «Avisos»): una revisión no residual que sube el nivel del
// riesgo a ALTO o CRITICO (de uno menor, o de ALTO a CRITICO) publica risk_escalated:<riesgo>:<nivel>:<fecha de la revisión>
// en la fuente sgspe.risks, para el responsable del riesgo y los asignados vigentes con un rol de la transición de tratamiento
// que sigue, salvo quien revisó. toda revisión no residual resuelve el aviso de la anterior, y cerrar el riesgo resuelve el de
// la última. corre en la transacción del endpoint de revisiones o de la transición, como quien escribe; si no lee las
// revisiones, el riesgo o las asignaciones, lo registra y la escritura sigue
@Component
@Order(220)
class RiskNoticeListener(
    private val notices: Notices,
    private val assignments: Assignments,
    private val records: Records,
    private val catalog: ModelCatalog
) : RecordChangeListener {
    private val log = LoggerFactory.getLogger(javaClass)

    override suspend fun recordChanged(change: RecordChange) {
        try {
            when {
                change.objectName == Risks.ASSESSMENT && change.kind == RecordChangeKind.CREATED -> assessed(change)
                change.objectName == Risks.SOCIAL_RISK && change.kind == RecordChangeKind.TRANSITIONED &&
                    change.transition?.let { catalog.transition(Risks.SOCIAL_RISK, it)?.to } == Risks.CLOSED -> closed(change)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: ForbiddenException) {
            log.error("RiskNoticeListener: quien escribió {} {} no lee lo que el aviso necesita; no hay aviso", change.objectName, change.recordId, e)
        }
    }

    private suspend fun assessed(change: RecordChange) {
        val after = change.after.orEmpty()
        if (Values.bool(after["residual"]) == true) return
        val risk = Values.text(after[Risks.SOCIAL_RISK]) ?: return
        val previous = RiskNotice.previous(change.recordId.toString(), history(risk))
        if (previous != null) notices.resolve(change.organizationId, Notices.RISKS, RiskNotice.key(risk, previous.level, previous.date))
        val level = Values.text(after[Risks.LEVEL])
        if (!RiskLevels.escalates(previous?.level, level)) return
        publish(change, risk, level!!, Values.text(after["assessment_date"]))
    }

    private suspend fun closed(change: RecordChange) {
        val latest = RiskNotice.latest(history(change.recordId.toString())) ?: return
        notices.resolve(change.organizationId, Notices.RISKS, RiskNotice.key(change.recordId.toString(), latest.level, latest.date))
    }

    private suspend fun history(risk: String): List<AssessmentRow> =
        records.all(Risks.ASSESSMENT, filters = mapOf(Risks.SOCIAL_RISK to risk)).mapNotNull(AssessmentRow::of)

    private suspend fun publish(
        change: RecordChange,
        risk: String,
        level: String,
        date: String?
    ) {
        val record = records.find(Risks.SOCIAL_RISK, UUID.fromString(risk)) ?: return
        val project = Records.uuid(record.attributes["project"]) ?: return
        val roles =
            Risks.NEXT_TREATMENT[record.state]
                ?.let { assignments.recipients(project, Risks.SOCIAL_RISK, it, change.userId) }
                .orEmpty()
                .map { it.userId }
        val owner = Records.uuid(record.attributes[Risks.OWNER])?.takeIf { it != change.userId }
        val users = (listOfNotNull(owner) + roles).distinct()
        if (users.isEmpty()) {
            log.warn("RiskNoticeListener: el riesgo {} no tiene responsable ni asignaciones vigentes que avisar; no hay aviso", risk)
            return
        }
        val label = "Riesgo ${Values.text(record.attributes["code"]) ?: risk}"
        notices.publish(
            change.organizationId,
            Notices.RISKS,
            RiskNotice.key(risk, level, date),
            NotificationKind.ACTION,
            NoticeTexts.riskEscalated(label, level),
            users,
            NotificationLink.Record(Risks.SOCIAL_RISK, UUID.fromString(risk))
        )
    }
}
