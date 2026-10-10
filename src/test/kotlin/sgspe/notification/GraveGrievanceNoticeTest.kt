package sgspe.notification

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import sgspe.notification.GraveGrievanceNotice.Effect
import wasichai.core.data.RecordChange
import wasichai.core.data.RecordChangeKind
import java.util.UUID

// cuándo se publica y cuándo se resuelve el aviso de una queja grave (decisión 8 de F4), sin base
class GraveGrievanceNoticeTest {
    private fun change(
        kind: RecordChangeKind,
        before: Map<String, Any?>,
        after: Map<String, Any?> = before,
        state: String? = null,
        transition: String? = null
    ) = RecordChange(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "grievance", UUID.randomUUID(), kind, before, after, state, transition)

    private fun effect(
        change: RecordChange,
        from: String?,
        policy: () -> Boolean
    ): Effect = runBlocking { GraveGrievanceNotice.effect(change, from) { policy() } }

    private val high = mapOf("severity" to "ALTA")
    private val low = mapOf("severity" to "MEDIA")

    @Test
    fun `entrar en in_action con una queja grave publica, salvo con la politica apagada`() {
        assertEquals(
            Effect.PUBLISH,
            effect(change(RecordChangeKind.TRANSITIONED, high, transition = "start_action"), "in_assessment") { true }
        )
        assertEquals(
            Effect.NONE,
            effect(change(RecordChangeKind.TRANSITIONED, high, transition = "start_action"), "in_assessment") { false }
        )
        assertEquals(
            Effect.NONE,
            effect(change(RecordChangeKind.TRANSITIONED, low, transition = "start_action"), "in_assessment") { true }
        )
    }

    @Test
    fun `salir de in_action resuelve`() {
        assertEquals(
            Effect.RESOLVE,
            effect(
                change(RecordChangeKind.TRANSITIONED, high, transition = "resolve_high_severity"),
                "in_action"
            ) {
                true
            }
        )
        assertEquals(
            Effect.RESOLVE,
            effect(change(RecordChangeKind.TRANSITIONED, high, transition = "withdraw_in_action"), "in_action") { true }
        )
    }

    @Test
    fun `subir la severidad en in_action publica y bajarla resuelve`() {
        assertEquals(Effect.PUBLISH, effect(change(RecordChangeKind.UPDATED, low, high, "in_action"), null) { true })
        assertEquals(Effect.RESOLVE, effect(change(RecordChangeKind.UPDATED, high, low, "in_action"), null) { true })
        assertEquals(Effect.NONE, effect(change(RecordChangeKind.UPDATED, low, high, "in_assessment"), null) { true })
        assertEquals(
            Effect.NONE,
            effect(change(RecordChangeKind.UPDATED, high, mapOf("severity" to "CRITICA"), "in_action"), null) { true }
        )
    }
}
