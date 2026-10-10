package sgspe.risk

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import sgspe.common.ModelCatalog
import sgspe.deadline.DeadlineJob

// la tarea de revisión de un riesgo que cierra el endpoint de revisiones (ronda branchfix de 4.2b, R-I1), sin base: se reconoce
// por la clave de JOB-01 y pasa a done desde cada estado abierto con las transiciones de WF-N
class RiskReviewTasksTest {
    @Test
    fun `la clave de la tarea de revision es la de JOB-01 y nombra su riesgo`() {
        val key = DeadlineJob.reviewKey("r1", "2026-10-14")
        assertTrue(RiskReviewTasks.isReviewTask(key))
        assertTrue(RiskReviewTasks.belongsTo(key, "r1"))
        assertFalse(RiskReviewTasks.belongsTo(key, "r10"))
        assertFalse(RiskReviewTasks.belongsTo(DeadlineJob.reviewKey("r10", "2026-10-14"), "r1"))
        assertFalse(RiskReviewTasks.isReviewTask(null))
        assertFalse(RiskReviewTasks.isReviewTask("escalation:commitment:c1:2026-10-14:1"))
    }

    @Test
    fun `cada estado abierto de la tarea llega a done`() {
        assertEquals(listOf("complete_open"), RiskReviewTasks.closing("open"))
        assertEquals(listOf("complete"), RiskReviewTasks.closing("in_progress"))
        assertEquals(listOf("unblock", "complete"), RiskReviewTasks.closing("blocked"))
        assertEquals(emptyList<String>(), RiskReviewTasks.closing("done"))
        assertEquals(emptyList<String>(), RiskReviewTasks.closing("cancelled"))
    }

    @Test
    fun `las transiciones existen en el workflow de la tarea`() {
        val catalog = ModelCatalog.fromClasspath()
        for (state in RiskReviewTasks.OPEN) {
            for (transition in RiskReviewTasks.closing(state)) assertTrue(catalog.transition("task", transition) != null, transition)
        }
    }
}
