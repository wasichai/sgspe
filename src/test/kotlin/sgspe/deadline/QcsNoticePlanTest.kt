package sgspe.deadline

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import sgspe.common.JobTasks
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
    fun `las tareas de escalamiento de una confidencial llevan la huella en la clave y un texto generico`() {
        val id = UUID.randomUUID().toString()
        val print = ConfidentialReaders.fingerprint("grievance", id, due)
        for (level in 1..2) {
            val key = DeadlineJob.confidentialEscalationKey("grievance", id, due, level)
            assertEquals("escalation:grievance:$print:2026-10-13:$level", key)
            assertFalse(key.contains(id))
            assertTrue(JobTasks.isConfidentialTask(key), key)
        }
        // la clave de acuse de una queja confidencial: solo la huella
        assertEquals("ack:grievance:$print", JobTasks.confidentialAckKey(print))
        assertTrue(JobTasks.isConfidentialTask(JobTasks.confidentialAckKey(print)))
        assertTrue(JobTasks.isConfidentialTask(JobTasks.confidentialEscalationKey("stakeholder_request", print, due, 1)))
        // la clave con id de una QCS no confidencial, un nivel 3, otro objeto y null no tienen esa forma
        val key = DeadlineJob.confidentialEscalationKey("grievance", id, due, 2)
        assertFalse(JobTasks.isConfidentialTask(DeadlineJob.escalationKey("grievance", id, due, 2)))
        assertFalse(JobTasks.isConfidentialTask(DeadlineJob.escalationKey("grievance", id, due, 1)))
        assertFalse(JobTasks.isConfidentialTask(key.removeSuffix(":2") + ":3"))
        assertFalse(JobTasks.isConfidentialTask(key.replace("grievance", "commitment")))
        assertFalse(JobTasks.isConfidentialTask("ack:grievance:$id"))
        assertFalse(JobTasks.isConfidentialTask("ack:inquiry:$print"))
        assertFalse(JobTasks.isConfidentialTask(null))
        for (level in 1..2) {
            val (title, description) = NoticeTexts.qcsConfidentialEscalation("Proyecto Ejemplo", level)
            assertEquals("QCS confidencial vencida en Proyecto Ejemplo", title)
            assertEquals("Una QCS confidencial superó su plazo (escalamiento de nivel $level). Pida el detalle al equipo de quejas.", description)
        }
        val (ackTitle, ackDescription) = NoticeTexts.qcsConfidentialAck("Proyecto Ejemplo")
        assertEquals("Acusar recibo de una QCS confidencial en Proyecto Ejemplo", ackTitle)
        assertEquals("Una QCS confidencial espera su acuse. Pida el detalle al equipo de quejas.", ackDescription)
        assertEquals("Proyecto Ejemplo", NoticeTexts.projectName(mapOf("name" to "Proyecto Ejemplo", "code" to "PE-1"), "p"))
        assertEquals("PE-1", NoticeTexts.projectName(mapOf("code" to "PE-1"), "p"))
        assertEquals("p", NoticeTexts.projectName(emptyMap(), "p"))
    }

    @Test
    fun `las transiciones de los avisos existen en los workflows`() {
        for ((objectName, next) in DeadlineNoticePlan.QCS_NEXT) {
            for ((state, transition) in next) assertEquals(state, catalog.transition(objectName, transition)?.from, "$objectName.$transition")
        }
        assertEquals(setOf("GRIEVANCE_MANAGER"), catalog.rolesGranting("inquiry", "READ_CONFIDENTIAL"))
    }
}
