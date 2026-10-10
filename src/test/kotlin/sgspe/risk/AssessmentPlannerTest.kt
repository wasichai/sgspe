package sgspe.risk

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import wasichai.core.common.ValidationException
import wasichai.core.common.WasichaiException
import java.time.Instant
import java.time.LocalDate

// el cuerpo y el plan de una revisión de riesgo (api.md §4.7, ASM-099), sin base: escalas de 1 a 5, fecha no futura, orden por
// tipo, la primera no residual, nivel con los umbrales del proyecto, tendencia y la fecha de la que corre la cadencia
class AssessmentPlannerTest {
    private val today = LocalDate.parse("2026-10-09")
    private val defaults = RiskThresholds(5, 10, 15)

    private fun input(
        probability: Any? = 3,
        impact: Any? = 4,
        date: String? = "2026-10-07",
        residual: Any? = null
    ) = RiskAssessmentInput.parse(
        mapOf("attributes" to mapOf("assessment_date" to date, "probability" to probability, "impact" to impact, "residual" to residual)),
        today
    )

    private fun row(
        date: String,
        score: Int,
        residual: Boolean = false,
        created: String = "${date}T15:00:00Z"
    ) = AssessmentRow(date, LocalDate.parse(date), residual, score, defaults.level(score), Instant.parse(created))

    private fun code(error: WasichaiException) = error.violations.single().message to error.violations.single().field

    @Test
    fun `probabilidad e impacto son enteros de 1 a 5 y la fecha no es futura`() {
        assertEquals(12, input().score)
        assertEquals("OUT_OF_RANGE" to "attributes.probability", code(assertThrows<WasichaiException> { input(probability = 6) }))
        assertEquals("OUT_OF_RANGE" to "attributes.impact", code(assertThrows<WasichaiException> { input(impact = 2.5) }))
        assertEquals("OUT_OF_RANGE" to "attributes.probability", code(assertThrows<WasichaiException> { input(probability = "3") }))
        assertEquals("OUT_OF_RANGE" to "attributes.assessment_date", code(assertThrows<WasichaiException> { input(date = "2026-10-10") }))
        assertEquals("FIELD_REQUIRED" to "attributes.impact", code(assertThrows<WasichaiException> { input(impact = null) }))
        assertThrows<ValidationException> { input(residual = "si") }
        // una propiedad desconocida es un 400 que la nombra
        val unknown = assertThrows<ValidationException> { RiskAssessmentInput.parse(mapOf("attributes" to mapOf("score" to 12)), today) }
        assertEquals("attributes.score", unknown.violations.single().field)
    }

    @Test
    fun `la primera revision calcula nivel y no tiene tendencia`() {
        val plan = AssessmentPlanner.plan(input(), emptyList(), defaults)
        assertEquals("ALTO", plan.stored["risk_level"])
        assertEquals(12, plan.stored["score"])
        assertNull(plan.stored["trend"])
        assertEquals(mapOf("probability" to 3, "impact" to 4, "score" to 12, "risk_level" to "ALTO"), plan.current)
        assertEquals(LocalDate.parse("2026-10-07"), plan.lastDate)
    }

    @Test
    fun `la primera revision no es residual`() {
        val error = assertThrows<WasichaiException> { AssessmentPlanner.plan(input(residual = true), emptyList(), defaults) }
        assertEquals(409, error.status.value())
        assertEquals("FIRST_ASSESSMENT_NOT_RESIDUAL" to "attributes.residual", code(error))
    }

    @Test
    fun `la tendencia y el orden son por tipo, y la cadencia corre desde la ultima revision de cualquiera`() {
        val history = listOf(row("2026-09-30", 9), row("2026-10-08", 4, residual = true))
        val up = AssessmentPlanner.plan(input(date = "2026-10-07"), history, defaults)
        assertEquals("AUMENTA", up.stored["trend"])
        assertEquals(LocalDate.parse("2026-10-08"), up.lastDate)
        val residual = AssessmentPlanner.plan(input(probability = 2, impact = 2, date = "2026-10-09", residual = true), history, defaults)
        assertEquals("SE MANTIENE", residual.stored["trend"])
        assertEquals(mapOf("residual_probability" to 2, "residual_impact" to 2, "residual_score" to 4, "residual_level" to "BAJO"), residual.current)
        val late = assertThrows<WasichaiException> { AssessmentPlanner.plan(input(date = "2026-09-29"), history, defaults) }
        assertEquals("ASSESSMENT_OUT_OF_ORDER" to "attributes.assessment_date", code(late))
        // a igual fecha que la última de su tipo pasa: se corrige con otra revisión del mismo día
        assertEquals("DISMINUYE", AssessmentPlanner.plan(input(probability = 2, impact = 2, date = "2026-09-30"), history, defaults).stored["trend"])
    }
}
