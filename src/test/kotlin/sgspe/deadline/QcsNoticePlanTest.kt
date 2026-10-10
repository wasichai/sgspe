package sgspe.deadline

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import sgspe.common.ModelCatalog
import sgspe.notification.ConfidentialReaders
import sgspe.notification.NoticeTexts
import sgspe.scope.Assignment
import java.time.LocalDate
import java.util.UUID

// los avisos de plazo de una QCS (workflows.md «Avisos» y «Destinatarios por rol», ASM-102), sin base: a quién va cada nivel,
// quién vería una confidencial y que el aviso genérico del nivel 2 no lleva datos del registro
class QcsNoticePlanTest {
    private val due = LocalDate.parse("2026-10-13")
    private val today = LocalDate.parse("2026-11-01")
    private val owner = UUID.randomUUID()
    private val catalog = ModelCatalog.fromClasspath()
    private val readers = ConfidentialReaders(catalog)

    private fun assignment(role: String) = Assignment(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "persona@sgspe.test", role, true, null, null)

    @Test
    fun `por vencer va al responsable o, sin el, a los roles de la transicion que sigue`() {
        val withOwner = DeadlineNoticePlan.qcs("grievance", "q", "Queja QJ-1", "received", "POR VENCER", due, 0, owner)
        assertEquals(listOf("qcs_deadline:grievance:q:2026-10-13:POR VENCER"), withOwner.map { it.key })
        assertEquals(NoticeAudience.Person(owner), withOwner.single().audience)
        val without = DeadlineNoticePlan.qcs("inquiry", "c", "Consulta CO-1", "received", "POR VENCER", due, 0, null)
        assertEquals(NoticeAudience.Roles("inquiry", "answer"), without.single().audience)
    }

    @Test
    fun `vencida escala a verify de WF-E1 y a approve_senior de WF-D`() {
        val plan = DeadlineNoticePlan.qcs("grievance", "q", "Queja QJ-1", "in_action", "VENCIDO", due, 2, null)
        assertEquals((0..2).map { "qcs_deadline:grievance:q:2026-10-13:$it" }, plan.map { it.key })
        assertEquals(
            listOf(NoticeAudience.Roles("grievance", "resolve"), NoticeAudience.Roles("grievance", "verify"), DeadlineNoticePlan.SENIOR),
            plan.map { it.audience }
        )
        assertEquals(listOf(0, 1, 2), plan.map { it.level })
    }

    @Test
    fun `una confidencial solo la ven su responsable, quien la recibio, READ_CONFIDENTIAL y en una grave APPROVE`() {
        val receiver = UUID.randomUUID()
        val manager = assignment("GRIEVANCE_MANAGER")
        val approver = assignment("APPROVER")
        val social = assignment("SOCIAL_MANAGER")
        val team = listOf(manager, approver, social)
        val users = listOf(owner, receiver, manager.memberUserId, approver.memberUserId, social.memberUserId)
        val low = mapOf("confidential" to true, "owner_user_id" to owner.toString(), "received_by_user_id" to receiver.toString(), "severity" to "BAJA")
        assertEquals(listOf(owner, receiver, manager.memberUserId), readers.filter("grievance", low, users, team, today))
        val high = low + ("severity" to "CRITICA")
        assertEquals(listOf(owner, receiver, manager.memberUserId, approver.memberUserId), readers.filter("grievance", high, users, team, today))
        assertEquals(null, readers.filter("grievance", low + ("confidential" to false), users, team, today))
    }

    @Test
    fun `el aviso generico solo lleva el nombre del proyecto y su clave una huella`() {
        val (title, body) = NoticeTexts.qcsConfidentialOverdue("Proyecto Ejemplo")
        assertEquals("QCS confidencial fuera de plazo", title)
        assertEquals("Una QCS confidencial del proyecto Proyecto Ejemplo superó su plazo. Pida el detalle al equipo de quejas.", body)
        val id = UUID.randomUUID().toString()
        val print = ConfidentialReaders.fingerprint("grievance", id, due)
        assertEquals(16, print.length)
        assertTrue(print.all { it in "0123456789abcdef" })
        assertFalse(print.contains(id.take(8)))
        assertEquals(print, ConfidentialReaders.fingerprint("grievance", id, due))
    }

    @Test
    fun `las transiciones de los avisos existen en los workflows`() {
        for ((objectName, next) in DeadlineNoticePlan.QCS_NEXT) {
            for ((state, transition) in next) assertEquals(state, catalog.transition(objectName, transition)?.from, "$objectName.$transition")
        }
        assertEquals(setOf("GRIEVANCE_MANAGER"), catalog.rolesGranting("inquiry", "READ_CONFIDENTIAL"))
    }
}
