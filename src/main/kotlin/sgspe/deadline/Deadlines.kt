package sgspe.deadline

import sgspe.commitment.Commitments
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

    // un objeto que JOB-01 sigue: su campo derivado, la fecha que mide y los estados en que se calcula
    data class Tracked(
        val objectName: String,
        val field: String,
        val dateField: String,
        val states: Set<String>,
        val validity: Boolean = false
    )

    val TASK_OPEN = setOf("open", "in_progress", "blocked")

    // F3: compromisos, tareas y acuerdos; F4 suma QCS y riesgos
    val TRACKED =
        listOf(
            Tracked(Commitments.COMMITMENT, "deadline_status", "due_date", Commitments.PENDING),
            Tracked("task", "deadline_status", "due_date", TASK_OPEN),
            Tracked("agreement", "validity_status", "expiry_date", setOf("active", "suspended"), validity = true)
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
        val date = Values.text(attributes[tracked.dateField])?.let(LocalDate::parse)
        return if (tracked.validity) validity(date, today, dueSoonDays) else status(date, today, dueSoonDays)
    }

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
