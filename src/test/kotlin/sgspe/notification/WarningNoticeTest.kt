package sgspe.notification

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import sgspe.common.ModelCatalog
import sgspe.notification.WarningNotice.Effect
import wasichai.core.data.RecordChange
import wasichai.core.data.RecordChangeKind
import java.util.UUID

// el aviso de alerta nueva (workflows.md «Avisos»), sin base: cuándo se publica y se resuelve, su clave y su texto
class WarningNoticeTest {
    private fun change(
        objectName: String,
        kind: RecordChangeKind,
        transition: String? = null
    ) = RecordChange(UUID.randomUUID(), null, UUID.randomUUID(), objectName, UUID.randomUUID(), kind, null, emptyMap(), null, transition)

    @Test
    fun `el alta publica, start_triage resuelve y lo demas no toca el aviso`() {
        assertEquals(Effect.PUBLISH, WarningNotice.effect(change("early_warning", RecordChangeKind.CREATED)))
        assertEquals(Effect.RESOLVE, WarningNotice.effect(change("early_warning", RecordChangeKind.TRANSITIONED, "start_triage")))
        assertEquals(Effect.NONE, WarningNotice.effect(change("early_warning", RecordChangeKind.TRANSITIONED, "confirm")))
        assertEquals(Effect.NONE, WarningNotice.effect(change("incident", RecordChangeKind.CREATED)))
    }

    @Test
    fun `la clave lleva el id y el texto el titulo y la severidad`() {
        val id = UUID.randomUUID()
        assertEquals("warning_new:$id", WarningNotice.key(id))
        assertEquals(
            "Alerta nueva: Señales: CONTEO DE SEÑALES" to "Severidad ALTO. Inicie su triaje.",
            NoticeTexts.warningNew("Señales: CONTEO DE SEÑALES", "ALTO")
        )
        assertEquals("Alerta nueva: A" to "Inicie su triaje.", NoticeTexts.warningNew("A", null))
        assertEquals("sgspe.warnings", Notices.WARNINGS)
    }

    @Test
    fun `start_triage sale de detected`() {
        assertEquals("detected", ModelCatalog.fromClasspath().transition(WarningNotice.WARNING, WarningNotice.START_TRIAGE)?.from)
    }
}
