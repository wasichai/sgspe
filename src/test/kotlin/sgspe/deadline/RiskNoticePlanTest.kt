package sgspe.deadline

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import sgspe.common.ModelCatalog
import sgspe.notification.NoticeTexts
import java.time.LocalDate
import java.util.UUID

// los avisos de la revisión vencida de un riesgo (workflows.md «Avisos» y «Destinatarios por rol», ASM-099), sin base
class RiskNoticePlanTest {
    private val due = LocalDate.parse("2026-10-14")

    @Test
    fun `cada nivel va al responsable, a la transicion de tratamiento que sigue y a approve_senior`() {
        val owner = UUID.randomUUID()
        val plan = DeadlineNoticePlan.risk("r1", "Riesgo RS-1", "mitigating", due, 2, owner)
        assertEquals((0..2).map { "risk_escalated:r1:$it:2026-10-14" }, plan.map { it.key })
        assertEquals(
            listOf(NoticeAudience.Person(owner), NoticeAudience.Roles("social_risk", "start_monitoring"), DeadlineNoticePlan.SENIOR),
            plan.map { it.audience }
        )
        // sin responsable no hay nivel 0
        assertEquals(listOf("risk_escalated:r1:1:2026-10-14"), DeadlineNoticePlan.risk("r1", "Riesgo RS-1", "assessed", due, 1, null).map { it.key })
    }

    @Test
    fun `el texto nombra el riesgo por su codigo y la fecha`() {
        assertEquals(
            "Revisión de riesgo vencida: Riesgo RS-1" to "La revisión debía hacerse el 14/10/2026.",
            NoticeTexts.riskReviewOverdue("Riesgo RS-1", due, 0)
        )
    }

    @Test
    fun `una tarea de una alerta escala a confirm, y una de un riesgo ya no tiene transicion de referencia`() {
        val catalog = ModelCatalog.fromClasspath()
        val (objectName, transition) = DeadlineNoticePlan.TASK_REFERENCE.getValue("early_warning")
        assertEquals("early_warning", objectName)
        assertEquals(true, catalog.transition(objectName, transition) != null, transition)
        // la revisión vencida de un riesgo avisa solo por risk_escalated (ronda branchfix de 4.2b, R-I1)
        assertFalse("social_risk" in DeadlineNoticePlan.TASK_REFERENCE)
    }

    @Test
    fun `la tarea de revision de un riesgo no entra en la cadena de tarea vencida`() {
        assertFalse(DeadlineNoticePlan.escalatesAsTask(DeadlineJob.reviewKey("r1", "2026-10-14")))
        assertTrue(DeadlineNoticePlan.escalatesAsTask(null))
        assertTrue(DeadlineNoticePlan.escalatesAsTask(DeadlineJob.escalationKey("commitment", "c1", LocalDate.parse("2026-10-14"), 1)))
        assertTrue(DeadlineNoticePlan.escalatesAsTask("follow_up:s1:2026-10-14"))
    }
}
