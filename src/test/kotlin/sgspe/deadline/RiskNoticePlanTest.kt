package sgspe.deadline

import org.junit.jupiter.api.Assertions.assertEquals
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
    fun `una tarea de un riesgo o de una alerta escala a su transicion de revision`() {
        val catalog = ModelCatalog.fromClasspath()
        for (linked in listOf("social_risk", "early_warning")) {
            val (objectName, transition) = DeadlineNoticePlan.TASK_REFERENCE.getValue(linked)
            assertEquals(linked, objectName)
            assertEquals(true, catalog.transition(objectName, transition) != null, transition)
        }
    }
}
