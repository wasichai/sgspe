package sgspe.deadline

import kotlinx.coroutines.CancellationException
import org.slf4j.LoggerFactory
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import sgspe.common.LIMA
import sgspe.common.ProjectParameters
import sgspe.common.Records
import sgspe.common.Values
import wasichai.core.data.RecordChange
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordChangeListener
import java.time.Clock
import java.time.LocalDate

// la situación de plazo de un compromiso reprogramado (RF-COM-003, decisión de la Tarea 17 del plan 3.2): cuando cambia
// due_date (desde approved, solo la reprogramación, dentro de su candado y de su transacción), recalcula deadline_status
// con la misma regla que JOB-01 (Deadlines.wanted), como quien reprograma y con la marca de Records.patch, que
// DerivedFieldsGuard deja pasar. así un compromiso vencido que se reprograma a una fecha futura no sigue VENCIDO hasta la
// próxima vuelta del trabajo. el estado se lee del registro, nunca de change.before (wasichai 0.6.0 no lo trae). si quien
// reprograma no lee el proyecto, se usan los parámetros por defecto: VENCIDO no depende de ellos, y JOB-01 corrige POR
// VENCER en su vuelta. si la escritura falla, se registra y la reprogramación sigue: JOB-01 la corrige a la hora
@Component
@Order(130)
class DeadlineStatusListener(
    private val records: Records,
    private val clock: Clock
) : RecordChangeListener {
    private val log = LoggerFactory.getLogger(javaClass)

    override suspend fun recordChanged(change: RecordChange) {
        if (change.kind != RecordChangeKind.UPDATED) return
        val tracked = Deadlines.rescheduled(change.objectName, change.before, change.after) ?: return
        try {
            val current = records.find(tracked.objectName, change.recordId) ?: return
            val today = LocalDate.now(clock.withZone(LIMA))
            val wanted = Deadlines.wanted(tracked, current.state, current.attributes, today, dueSoonDays(current.attributes["project"]))
            if (Values.same(current.attributes[tracked.field], wanted)) return
            records.patch(tracked.objectName, change.recordId, mapOf(tracked.field to wanted), REASON)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.warn("DeadlineStatusListener: no se recalculó {} de {} {}; JOB-01 lo corregirá", tracked.field, tracked.objectName, change.recordId, e)
        }
    }

    private suspend fun dueSoonDays(project: Any?): Long {
        val params =
            try {
                Records.uuid(project)?.let { records.find(PROJECT, it)?.attributes }.orEmpty()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.info("DeadlineStatusListener: quien reprograma no lee el proyecto {}; se usa due_soon_days por defecto", project)
                emptyMap()
            }
        return ProjectParameters.integer(params, DUE_SOON_DAYS)
    }

    companion object {
        const val PROJECT = "project"
        const val DUE_SOON_DAYS = "due_soon_days"
        const val REASON = "derivado: situación de plazo al reprogramar"
    }
}
