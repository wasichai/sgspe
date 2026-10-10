package sgspe.attention

import kotlinx.coroutines.CancellationException
import org.slf4j.LoggerFactory
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import sgspe.common.Attention
import sgspe.common.BusinessDays
import sgspe.common.Criteria
import sgspe.common.DerivedFieldsJob
import sgspe.common.DerivedRecalculator
import sgspe.common.HolidayCalendar
import sgspe.common.LIMA
import sgspe.common.ModelCatalog
import sgspe.common.ProjectParameters
import sgspe.common.Records
import sgspe.common.Transactions
import sgspe.common.Values
import wasichai.core.data.RecordChange
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordChangeListener
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

// los plazos de una QCS, puros (ASM-005, ASM-096, ASM-100; workflows.md «Plazos y escalamiento»): desde la fecha de recepción
// en Lima, el acuse de una queja en días hábiles (BusinessDays) y su resolución en días calendario; la respuesta de una
// consulta en días calendario; una solicitud no tiene plazo en el MVP y grievance.response_due_date queda vacío. se calculan
// al recibir (o los completa JOB-02) y no se recalculan si cambia la lista de feriados o un parámetro
object QcsDueDates {
    const val ACK = "ack_due_date"
    const val RESOLUTION = "resolution_due_date"
    const val RESPONSE = "response_due_date"

    // los plazos que mide cada objeto
    val FIELDS: Map<String, List<String>> =
        mapOf(Attention.GRIEVANCE to listOf(ACK, RESOLUTION), Attention.INQUIRY to listOf(RESPONSE), Attention.REQUEST to emptyList())

    // la fecha de recepción: received_at llevado a America/Lima
    fun receivedDate(receivedAt: Any?): LocalDate? {
        val text = Values.text(receivedAt) ?: return null
        val instant = runCatching { Instant.parse(text) }.getOrElse { runCatching { OffsetDateTime.parse(text).toInstant() }.getOrNull() } ?: return null
        return instant.atZone(LIMA).toLocalDate()
    }

    // los plazos de un registro con los parámetros de su proyecto; vacío sin fecha de recepción
    fun of(
        objectName: String,
        receivedAt: Any?,
        project: Map<String, Any?>,
        calendar: HolidayCalendar
    ): Map<String, String> {
        val received = receivedDate(receivedAt) ?: return emptyMap()
        return when (objectName) {
            Attention.GRIEVANCE ->
                mapOf(
                    ACK to BusinessDays.addBusinessDays(received, ProjectParameters.integer(project, "grievance_ack_days").toInt(), calendar).toString(),
                    RESOLUTION to received.plusDays(ProjectParameters.integer(project, "grievance_resolution_days")).toString()
                )
            Attention.INQUIRY -> mapOf(RESPONSE to received.plusDays(ProjectParameters.integer(project, "inquiry_response_days")).toString())
            else -> emptyMap()
        }
    }

    // de los plazos calculados, los que el registro todavía no tiene: nunca se pisa uno guardado
    fun missing(
        attributes: Map<String, Any?>,
        computed: Map<String, String>
    ): Map<String, String> = computed.filterKeys { Values.text(attributes[it]) == null }
}

// las fechas de las transiciones de una QCS, puras (dashboards.md §12): resolved_date al resolver una queja y vacía al
// reabrirla; closed_date al llegar a un estado final (closed y, en la queja, rejected y withdrawn)
object QcsTransitionDates {
    const val RESOLVED = "resolved_date"
    const val CLOSED = "closed_date"

    // los nombres de resolve y reopen de WF-E1, los mismos que AttentionRules.RESOLVE y AttentionRules.REOPEN (Tarea 6)
    private val RESOLVE_TRANSITIONS = setOf("resolve", "resolve_high_severity")
    private const val REOPEN_TRANSITION = "reopen"

    fun changes(
        objectName: String,
        transition: String?,
        to: String?,
        today: LocalDate
    ): Map<String, Any?> {
        val changes = linkedMapOf<String, Any?>()
        if (objectName == Attention.GRIEVANCE && transition in RESOLVE_TRANSITIONS) changes[RESOLVED] = today.toString()
        if (objectName == Attention.GRIEVANCE && transition == REOPEN_TRANSITION) changes[RESOLVED] = null
        if (to != null && to in Attention.FINAL_STATES.getValue(objectName)) changes[CLOSED] = today.toString()
        return changes
    }
}

// QcsDueDatesListener (workflows.md, «Listeners»): al recibir una QCS escribe sus plazos, como quien la registra y con la marca.
// si no puede (no lee el proyecto, por ejemplo), lo registra y el alta sigue: JOB-02 los completa (QcsDueDatesRecalculator)
@Component
@Order(120)
class QcsDueDatesListener(
    private val records: Records,
    private val calendar: HolidayCalendar
) : RecordChangeListener {
    private val log = LoggerFactory.getLogger(javaClass)

    override suspend fun recordChanged(change: RecordChange) {
        if (change.kind != RecordChangeKind.CREATED || change.objectName !in Attention.OBJECTS) return
        if (QcsDueDates.FIELDS.getValue(change.objectName).isEmpty()) return
        try {
            val record = records.find(change.objectName, change.recordId) ?: return
            val project = Records.uuid(record.attributes["project"])?.let { records.find("project", it)?.attributes }.orEmpty()
            val missing = QcsDueDates.missing(record.attributes, QcsDueDates.of(change.objectName, record.attributes[Attention.RECEIVED_AT], project, calendar))
            if (missing.isNotEmpty()) records.patch(change.objectName, change.recordId, missing, REASON)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.warn("QcsDueDatesListener: no se escribieron los plazos de {} {}; JOB-02 los completará", change.objectName, change.recordId, e)
        }
    }

    companion object {
        const val REASON = "derivado: plazos de la QCS"
    }
}

// QcsTransitionDatesListener (workflows.md, «Listeners»): en la transacción de la transición escribe resolved_date y closed_date
// como quien la aplica y con la marca; si no puede, lanza y la transición se revierte
@Component
@Order(120)
class QcsTransitionDatesListener(
    private val records: Records,
    private val catalog: ModelCatalog,
    private val clock: Clock
) : RecordChangeListener {
    override suspend fun recordChanged(change: RecordChange) {
        if (change.kind != RecordChangeKind.TRANSITIONED || change.objectName !in Attention.OBJECTS) return
        val to = change.transition?.let { catalog.transition(change.objectName, it)?.to }
        val changes = QcsTransitionDates.changes(change.objectName, change.transition, to, LocalDate.now(clock.withZone(LIMA)))
        if (changes.isNotEmpty()) records.patch(change.objectName, change.recordId, changes, "derivado: fecha de la transición ${change.transition}")
    }
}

// el recalculador de JOB-02 para los plazos de las QCS: completa los que falten en las QCS abiertas cambiadas desde la vuelta
// anterior (o en todas, en la vuelta diaria); nunca recalcula uno guardado
@Component
class QcsDueDatesRecalculator(
    private val records: Records,
    private val catalog: ModelCatalog,
    private val transactions: Transactions,
    private val calendar: HolidayCalendar
) : DerivedRecalculator {
    private val log = LoggerFactory.getLogger(javaClass)

    override val sourceObject: String = Attention.GRIEVANCE

    override suspend fun recalculate(
        organizationId: UUID,
        since: Instant?
    ): Int {
        val projects = mutableMapOf<String, Map<String, Any?>>()
        var written = 0
        var failed = 0
        for ((objectName, fields) in QcsDueDates.FIELDS) {
            if (fields.isEmpty()) continue
            val open = Criteria.inState(catalog.states(objectName) - Attention.FINAL_STATES.getValue(objectName))
            val rows = records.all(objectName, criteria = listOfNotNull(open, since?.let { Criteria.updatedSince(it) }))
            for (row in rows.filter { r -> fields.any { Values.text(r.attributes[it]) == null } }) {
                try {
                    val projectId = Values.text(row.attributes["project"]) ?: continue
                    val project = projects.getOrPut(projectId) { records.find("project", UUID.fromString(projectId))?.attributes.orEmpty() }
                    val missing = QcsDueDates.missing(row.attributes, QcsDueDates.of(objectName, row.attributes[Attention.RECEIVED_AT], project, calendar))
                    if (missing.isEmpty()) continue
                    val id = UUID.fromString(row.id)
                    transactions.locked("$objectName:$id") { records.patch(objectName, id, missing, REASON) }
                    written++
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    failed++
                    log.warn("QcsDueDatesRecalculator: no se completaron los plazos de {} {}", objectName, row.id, e)
                }
            }
        }
        check(failed == 0) { "no se completaron los plazos de $failed QCS (se escribieron $written)" }
        return written
    }

    companion object {
        val REASON = DerivedFieldsJob.reason("plazos de la QCS")
    }
}
