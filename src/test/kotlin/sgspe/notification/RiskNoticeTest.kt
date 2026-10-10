package sgspe.notification

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import sgspe.risk.AssessmentRow
import java.time.Instant
import java.time.LocalDate

// el aviso de riesgo escalado por nivel (workflows.md «Avisos»), sin base: su clave, la revisión anterior que resuelve y su texto
class RiskNoticeTest {
    private fun row(
        id: String,
        date: String,
        level: String,
        residual: Boolean = false
    ) = AssessmentRow(id, LocalDate.parse(date), residual, 0, level, Instant.parse("${date}T15:00:00Z"))

    @Test
    fun `la clave lleva el riesgo, el nivel y la fecha de la revision`() {
        assertEquals("risk_escalated:r1:ALTO:2026-10-07", RiskNotice.key("r1", "ALTO", LocalDate.parse("2026-10-07")))
    }

    @Test
    fun `la anterior es la ultima no residual que no es la nueva`() {
        val history = listOf(row("a", "2026-09-30", "MEDIO"), row("b", "2026-10-06", "BAJO", residual = true), row("c", "2026-10-07", "ALTO"))
        assertEquals("a", RiskNotice.previous("c", history)?.id)
        assertNull(RiskNotice.previous("a", listOf(row("a", "2026-09-30", "MEDIO"))))
        assertEquals("c", RiskNotice.latest(history)?.id)
    }

    @Test
    fun `el texto nombra el riesgo por su codigo y su nivel, sin texto libre`() {
        assertEquals(
            "Riesgo escalado a CRITICO: Riesgo RS-1" to "La última revisión dejó el riesgo en nivel CRITICO: decida su tratamiento.",
            NoticeTexts.riskEscalated("Riesgo RS-1", "CRITICO")
        )
        assertEquals("sgspe.risks", Notices.RISKS)
    }
}
