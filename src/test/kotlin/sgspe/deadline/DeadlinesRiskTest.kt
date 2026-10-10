package sgspe.deadline

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import sgspe.common.ModelCatalog
import java.time.LocalDate

// la situación de revisión de los riesgos en JOB-01 (workflows.md «Plazos y escalamiento», ASM-099), sin base: se mide en
// assessed, mitigating y monitoring contra next_review_date, no escala con tareas de escalamiento y su tarea tiene su clave
class DeadlinesRiskTest {
    private val risk = Deadlines.TRACKED.first { it.objectName == "social_risk" }

    private fun date(text: String) = LocalDate.parse(text)

    @Test
    fun `la revision se mide en los estados revisados y vacia en los demas`() {
        val next = mapOf("next_review_date" to "2026-10-14")
        assertEquals("POR REVISAR", Deadlines.wanted(risk, "mitigating", next, date("2026-10-13"), 7))
        assertEquals("REVISION VENCIDA", Deadlines.wanted(risk, "assessed", next, date("2026-10-15"), 7))
        assertEquals("AL DIA", Deadlines.wanted(risk, "monitoring", next, date("2026-10-01"), 7))
        assertNull(Deadlines.wanted(risk, "identified", next, date("2026-10-15"), 7))
        assertNull(Deadlines.wanted(risk, "closed", next, date("2026-10-15"), 7))
    }

    @Test
    fun `un riesgo no crea tareas de escalamiento y su tarea de revision lleva su clave`() {
        assertFalse(risk.escalates)
        assertEquals("risk_review:r1:2026-10-14", DeadlineJob.reviewKey("r1", "2026-10-14"))
    }

    @Test
    fun `los estados y los campos de la revision existen en el modelo`() {
        val catalog = ModelCatalog.fromClasspath()
        assertEquals(emptySet<String>(), risk.states - catalog.states(risk.objectName).toSet())
        assertEquals(emptySet<String>(), setOf(risk.field, risk.dateField) - catalog.derivedFields(risk.objectName))
    }
}
