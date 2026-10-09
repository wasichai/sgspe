package sgspe.stakeholder

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.LocalDate

// JOB-03 desde F3: las tareas de evaluación periódica y de inactividad (RF-STK-010, RF-STK-014, ASM-022), sin base
class FollowUpTasksTest {
    private val today = LocalDate.parse("2026-10-09")

    @Test
    fun `la evaluacion periodica vence stakeholder_review_days despues de la ultima, solo en assessed`() {
        val planned = FollowUpTasks.review("s", "assessed", today.minusDays(30), 30, today)!!
        assertEquals("stakeholder_review:s:2026-10-09", planned.key)
        assertEquals(today, planned.due)
        assertNull(FollowUpTasks.review("s", "assessed", today.minusDays(29), 30, today))
        assertNull(FollowUpTasks.review("s", "profiled", today.minusDays(90), 30, today))
    }

    @Test
    fun `la inactividad cuenta desde el ultimo contacto o, sin contacto, desde el alta, nunca en un retirado`() {
        assertEquals("stakeholder_inactivity:s:2026-07-11", FollowUpTasks.inactivity("s", "identified", today.minusDays(90), null, 90, today)?.key)
        assertNull(FollowUpTasks.inactivity("s", "identified", today.minusDays(89), null, 90, today))
        assertEquals("stakeholder_inactivity:s:2026-01-01", FollowUpTasks.inactivity("s", "profiled", null, LocalDate.parse("2026-01-01"), 90, today)?.key)
        assertNull(FollowUpTasks.inactivity("s", "retired", null, LocalDate.parse("2026-01-01"), 90, today))
    }
}
