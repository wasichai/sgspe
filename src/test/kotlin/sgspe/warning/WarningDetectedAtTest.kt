package sgspe.warning

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import wasichai.core.data.RecordChange
import wasichai.core.data.RecordChangeKind
import java.util.UUID

// la hora de detección de una alerta manual (ronda branchfix de 4.2b, R-I3), sin base: detected_at es de la plataforma, así
// que sgspe la pone en el alta de una persona que no la trae; JOB-04 (sin usuario) la escribe él mismo
class WarningDetectedAtTest {
    private fun change(
        after: Map<String, Any?>,
        user: UUID? = UUID.randomUUID(),
        objectName: String = "early_warning",
        kind: RecordChangeKind = RecordChangeKind.CREATED
    ) = RecordChange(UUID.randomUUID(), user, UUID.randomUUID(), objectName, UUID.randomUUID(), kind, null, after, "detected")

    @Test
    fun `el alta de una persona sin hora la recibe`() {
        assertTrue(WarningDetectedAt.needs(change(mapOf("title" to "Movilización anunciada", "detected_at" to null))))
        assertTrue(WarningDetectedAt.needs(change(mapOf("title" to "Movilización anunciada"))))
    }

    @Test
    fun `la plataforma, un cambio, otro objeto o un alta con hora no`() {
        assertFalse(WarningDetectedAt.needs(change(mapOf("title" to "Alerta"), user = null)))
        assertFalse(WarningDetectedAt.needs(change(mapOf("title" to "Alerta"), kind = RecordChangeKind.UPDATED)))
        assertFalse(WarningDetectedAt.needs(change(mapOf("title" to "Incidente"), objectName = "incident")))
        assertFalse(WarningDetectedAt.needs(change(mapOf("detected_at" to "2026-10-07T15:00:00Z"))))
    }
}
