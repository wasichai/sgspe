package sgspe.deadline

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

// a quién va cada aviso de plazo (workflows.md, «Avisos» y «Destinatarios por rol»), sin base
class DeadlineNoticePlanTest {
    private val due = LocalDate.parse("2026-10-01")
    private val owner = UUID.randomUUID()

    @Test
    fun `por vencer avisa al responsable, sin responsable no hay aviso`() {
        val plan = DeadlineNoticePlan.commitment("c", "C-1", "POR VENCER", due, 0, owner)
        assertEquals(listOf("commitment_due_soon:c:2026-10-01"), plan.map { it.key })
        assertEquals(NoticeAudience.Person(owner), plan.single().audience)
        assertEquals(emptyList<PlannedNotice>(), DeadlineNoticePlan.commitment("c", "C-1", "POR VENCER", due, 0, null))
        assertEquals(emptyList<PlannedNotice>(), DeadlineNoticePlan.commitment("c", "C-1", "AL DIA", due, 0, owner))
    }

    @Test
    fun `vencido avisa al responsable y, en cada nivel alcanzado, a verify y a approve_senior`() {
        val plan = DeadlineNoticePlan.commitment("c", "C-1", "VENCIDO", due, 2, owner)
        assertEquals((0..2).map { "commitment_overdue:c:2026-10-01:$it" }, plan.map { it.key })
        assertEquals(
            listOf(NoticeAudience.Person(owner), NoticeAudience.Roles("commitment", "verify"), NoticeAudience.Roles("commitment", "approve_senior")),
            plan.map { it.audience }
        )
    }

    @Test
    fun `una tarea sin asignado escala directo al nivel 1 por su registro vinculado`() {
        val unassigned = DeadlineNoticePlan.task("t", "Revisar", "VENCIDO", due, 0, null, "engagement")
        assertEquals(listOf(NoticeAudience.Roles("engagement", "approve_minutes")), unassigned.map { it.audience })
        val assigned = DeadlineNoticePlan.task("t", "Revisar", "VENCIDO", due, 2, owner, "agreement")
        assertEquals(
            listOf(NoticeAudience.Person(owner), NoticeAudience.Roles("agreement", "activate"), DeadlineNoticePlan.SENIOR),
            assigned.map { it.audience }
        )
        assertEquals(emptyList<PlannedNotice>(), DeadlineNoticePlan.task("t", "Revisar", "POR VENCER", due, 0, owner, "commitment"))
    }
}
