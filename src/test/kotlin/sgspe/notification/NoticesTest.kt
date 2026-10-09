package sgspe.notification

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import wasichai.notifications.Audience
import wasichai.notifications.NotificationKind
import wasichai.notifications.NotificationLink
import java.time.LocalDate
import java.util.UUID

// los avisos que SGSPE publica en wasichai-notifications (decisión D2): textos, claves y destinatarios, sin base
class NoticesTest {
    private val due = LocalDate.parse("2026-10-09")

    @Test
    fun `los textos nombran el registro y la fecha, nunca a una persona`() {
        assertEquals("Compromiso por aprobar: C-001 · Agua" to "El compromiso espera su aprobación.", NoticeTexts.approvalPending("commitment", "C-001 · Agua"))
        assertEquals("Compromiso por vencer: C-001", NoticeTexts.commitmentDueSoon("C-001", due).first)
        assertTrue(NoticeTexts.commitmentDueSoon("C-001", due).second.contains("09/10/2026"))
        assertEquals("Compromiso vencido, escalamiento 2: C-001", NoticeTexts.commitmentOverdue("C-001", due, 2).first)
        assertEquals("Tarea vencida: Revisar acta", NoticeTexts.taskOverdue("Revisar acta", due, 0).first)
    }

    @Test
    fun `el borrador va a cada persona una vez, con su clave y su enlace`() {
        val user = UUID.randomUUID()
        val record = UUID.randomUUID()
        val draft =
            Notices.draft(
                "approval_pending:commitment:$record",
                NotificationKind.ACTION,
                "t" to "b",
                listOf(user, user),
                NotificationLink.Record("commitment", record),
                null
            )
        assertEquals(listOf<Audience>(Audience.User(user)), draft.audience)
        assertEquals("approval_pending:commitment:$record", draft.key)
        assertEquals(NotificationKind.ACTION, draft.kind)
        assertTrue(Regex("^[a-z][a-z0-9_.:-]{1,80}$").matches(Notices.APPROVALS))
        assertTrue(Regex("^[a-z][a-z0-9_.:-]{1,80}$").matches(Notices.DEADLINES))
    }

    @Test
    fun `cada envio a aprobacion tiene su clave estable y su etiqueta sin datos personales`() {
        val id = UUID.randomUUID()
        val step = ApprovalNoticeListener.STEPS.first { it.objectName == "commitment" }
        assertEquals("approval_pending:commitment:$id", ApprovalNoticeListener.key(step, id))
        assertEquals(listOf("approve", "approve_senior"), step.approvals)
        assertEquals("C-001 · Agua", ApprovalNoticeListener.label("commitment", mapOf("code" to "C-001", "title" to "Agua")))
        assertEquals("PRY-1 · Proyecto", ApprovalNoticeListener.label("project", mapOf("code" to "PRY-1", "name" to "Proyecto")))
        assertEquals("engagement", ApprovalNoticeListener.label("engagement", emptyMap()))
    }
}
