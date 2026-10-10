package sgspe.deadline

import sgspe.attention.QcsDueDates
import sgspe.commitment.Commitments
import sgspe.common.Attention
import sgspe.common.Values
import java.time.LocalDate
import java.time.temporal.ChronoUnit

// la situación de plazo y de vigencia, pura (RN-WFL-001, ASM-006, workflows.md «Plazos y escalamiento»): un campo derivado,
// nunca un estado. se calcula solo en los estados de su tabla y queda vacío en los demás, así VENCIDO siempre significa
// «abierto y atrasado». la usan JOB-01 (DeadlineJob) y la reprogramación (DeadlineStatusListener): una sola regla
object Deadlines {
    const val ON_TIME = "AL DIA"
    const val DUE_SOON = "POR VENCER"
    const val OVERDUE = "VENCIDO"
    const val VALID = "VIGENTE"

    // un objeto que JOB-01 sigue: su campo derivado, la fecha que mide y los estados en que se calcula. desde F4: la fecha que
    // mide un estado si no es la de siempre (el acuse de una queja en `received`), si POR VENCER se acota a la mitad del plazo
    // medido desde la recepción (una QCS, ASM-100) y si escala con tareas (los compromisos y, desde F4, las QCS)
    data class Tracked(
        val objectName: String,
        val field: String,
        val dateField: String,
        val states: Set<String>,
        val validity: Boolean = false,
        val dateByState: Map<String, String> = emptyMap(),
        val halfFromReceipt: Boolean = false,
        val escalates: Boolean = false
    ) {
        fun dateFieldIn(state: String?): String = dateByState[state] ?: dateField
    }

    val TASK_OPEN = setOf("open", "in_progress", "blocked")

    // F3: compromisos, tareas y acuerdos; F4 suma las quejas y las consultas (las solicitudes no tienen plazo en el MVP,
    // ASM-100) y el plan 4.2b, los riesgos
    val TRACKED =
        listOf(
            Tracked(Commitments.COMMITMENT, "deadline_status", "due_date", Commitments.PENDING, escalates = true),
            Tracked("task", "deadline_status", "due_date", TASK_OPEN),
            Tracked("agreement", "validity_status", "expiry_date", setOf("active", "suspended"), validity = true),
            Tracked(
                Attention.GRIEVANCE,
                Attention.DEADLINE_STATUS,
                QcsDueDates.RESOLUTION,
                setOf("received", "acknowledged", "in_assessment", "in_investigation", "in_action"),
                dateByState = mapOf("received" to QcsDueDates.ACK),
                halfFromReceipt = true,
                escalates = true
            ),
            Tracked(Attention.INQUIRY, Attention.DEADLINE_STATUS, QcsDueDates.RESPONSE, setOf("received"), halfFromReceipt = true, escalates = true)
        )

    // AL DIA, POR VENCER (vence dentro de dueSoonDays días, hoy incluido) o VENCIDO (la fecha ya pasó); null sin fecha
    fun status(
        due: LocalDate?,
        today: LocalDate,
        dueSoonDays: Long
    ): String? =
        when {
            due == null -> null
            today.isAfter(due) -> OVERDUE
            !today.plusDays(dueSoonDays).isBefore(due) -> DUE_SOON
            else -> ON_TIME
        }

    // la vigencia de un acuerdo: sin fecha de fin, VIGENTE
    fun validity(
        expiry: LocalDate?,
        today: LocalDate,
        dueSoonDays: Long
    ): String = status(expiry, today, dueSoonDays)?.let { if (it == ON_TIME) VALID else it } ?: VALID

    // el valor que corresponde a un registro: vacío fuera de sus estados
    fun wanted(
        tracked: Tracked,
        state: String?,
        attributes: Map<String, Any?>,
        today: LocalDate,
        dueSoonDays: Long
    ): String? {
        if (state !in tracked.states) return null
        val date = due(tracked, state, attributes)
        if (tracked.validity) return validity(date, today, dueSoonDays)
        val soon =
            if (tracked.halfFromReceipt &&
                date != null
            ) {
                halfCap(dueSoonDays, QcsDueDates.receivedDate(attributes[Attention.RECEIVED_AT]), date)
            } else {
                dueSoonDays
            }
        return status(date, today, soon)
    }

    // la fecha que mide un registro en su estado
    fun due(
        tracked: Tracked,
        state: String?,
        attributes: Map<String, Any?>
    ): LocalDate? = Values.text(attributes[tracked.dateFieldIn(state)])?.let(LocalDate::parse)

    // POR VENCER de una QCS: due_soon_days, pero nunca más de la mitad del plazo medido en días calendario desde la recepción,
    // redondeada hacia abajo (ASM-100): el acuse de 5 días hábiles no nace POR VENCER
    fun halfCap(
        dueSoonDays: Long,
        received: LocalDate?,
        due: LocalDate
    ): Long = if (received == null) dueSoonDays else minOf(dueSoonDays, ChronoUnit.DAYS.between(received, due) / 2)

    // el compromiso cuya fecha de vencimiento cambió en esta escritura (desde approved, solo la reprogramación, RF-COM-003):
    // su situación se recalcula en la misma transacción, para que uno vencido que se reprograma no siga VENCIDO hasta la
    // próxima vuelta de JOB-01. null en cualquier otro cambio
    fun rescheduled(
        objectName: String,
        before: Map<String, Any?>?,
        after: Map<String, Any?>?
    ): Tracked? {
        if (objectName != Commitments.COMMITMENT || before == null || after == null) return null
        val tracked = TRACKED.first { it.objectName == objectName }
        return if (Values.same(before[tracked.dateField], after[tracked.dateField])) null else tracked
    }

    // los días de atraso
    fun daysOverdue(
        due: LocalDate,
        today: LocalDate
    ): Long = ChronoUnit.DAYS.between(due, today)

    // el nivel de escalamiento de un plazo vencido (RF-WFL-004, ASM-030): 0 al vencer, 1 desde escalation_first_days de atraso y
    // 2 desde escalation_second_days
    fun level(
        due: LocalDate,
        today: LocalDate,
        firstDays: Long,
        secondDays: Long
    ): Int {
        val days = daysOverdue(due, today)
        return when {
            days >= secondDays -> 2
            days >= firstDays -> 1
            else -> 0
        }
    }
}
