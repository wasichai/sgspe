package sgspe.warning

import org.springframework.stereotype.Component
import sgspe.common.Records
import sgspe.common.Values
import wasichai.core.data.RecordChange
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordChangeListener
import java.time.Clock
import java.time.Instant

// la hora de detección de una alerta manual (ronda branchfix de 4.2b, R-I3): detected_at lo escribe solo la plataforma
// (PlatformOnlyFields, 403 PLATFORM_ONLY), así que una persona da de alta la alerta sin él y sgspe le pone la hora del alta,
// en la misma escritura de la persona: como quien la crea, con la marca y el motivo de abajo. JOB-04 lo escribe él mismo (sin
// usuario). si la escritura falla, el alta se revierte: una alerta no queda sin hora
@Component
class WarningDetectedAt(
    private val records: Records,
    private val clock: Clock
) : RecordChangeListener {
    override suspend fun recordChanged(change: RecordChange) {
        if (!needs(change)) return
        records.patch(Warnings.WARNING, change.recordId, mapOf(WarningRulesJob.DETECTED_AT to Instant.now(clock).toString()), REASON)
    }

    companion object {
        const val REASON = "hora de detección de la alerta: la del alta"

        // el alta de una alerta por una persona que no trae la hora
        fun needs(change: RecordChange): Boolean =
            change.objectName == Warnings.WARNING &&
                change.kind == RecordChangeKind.CREATED &&
                change.userId != null &&
                Values.text(change.after?.get(WarningRulesJob.DETECTED_AT)) == null
    }
}
