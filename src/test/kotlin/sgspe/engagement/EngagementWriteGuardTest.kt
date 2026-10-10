package sgspe.engagement

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// lo puro de EngagementWriteGuard: qué cuenta como pregunta atendida y los conjuntos que comparten Tareas 8, 13, 21 y 22
class EngagementWriteGuardTest {
    @Test
    fun `una pregunta esta atendida si se respondio en el lugar o tiene fecha de respuesta`() {
        assertFalse(EngagementWriteGuard.answered(mapOf("question_text" to "¿Habrá empleo local?")))
        assertFalse(EngagementWriteGuard.answered(mapOf("answered_on_site" to false, "answered_at" to "  ")))
        assertTrue(EngagementWriteGuard.answered(mapOf("answered_on_site" to true)))
        assertTrue(EngagementWriteGuard.answered(mapOf("answered_at" to "2026-05-13T15:00:00Z")))
        // F4: derivada a una QCS
        assertTrue(EngagementWriteGuard.answered(mapOf("referred_at" to "2026-05-13T15:00:00Z")))
    }

    @Test
    fun `los tipos formales y los estados realizados son los de workflows md`() {
        // RN-ENG-004
        assertEquals(setOf("REUNION", "REUNION INFORMATIVA", "TALLER", "ASAMBLEA", "MESA DE DIALOGO"), EngagementWriteGuard.FORMAL_TYPES)
        // desde que se registró la realización (WF-C): todo estado posterior a held que no sea cancelado ni postergado
        assertEquals(
            setOf("held", "minutes_drafted", "minutes_in_review", "minutes_approved", "closed"),
            EngagementWriteGuard.HELD_STATES
        )
    }
}
