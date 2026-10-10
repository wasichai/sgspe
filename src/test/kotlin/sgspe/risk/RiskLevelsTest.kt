package sgspe.risk

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import sgspe.common.ModelCatalog
import java.time.LocalDate

// el nivel, la tendencia, el escalamiento y la situación de revisión de un riesgo (RN-RSK-001, RN-RSK-002, ASM-004, ASM-099),
// sin base
class RiskLevelsTest {
    private val catalog = ModelCatalog.fromClasspath()

    private fun date(text: String) = LocalDate.parse(text)

    @Test
    fun `el nivel sale de los umbrales del proyecto, con 5, 10 y 15 por defecto`() {
        val defaults = RiskLevels.thresholds(emptyMap())
        assertEquals(RiskThresholds(5, 10, 15), defaults)
        assertEquals(listOf("BAJO", "MEDIO", "MEDIO", "ALTO", "ALTO", "CRITICO", "CRITICO"), listOf(4, 5, 9, 10, 14, 15, 25).map(defaults::level))
        val custom = RiskLevels.thresholds(mapOf("risk_medium_min_score" to 3, "risk_high_min_score" to 8, "risk_critical_min_score" to 20))
        assertEquals("ALTO", custom.level(12))
    }

    @Test
    fun `unos umbrales fuera de 1 a 25 o que no crecen no son validos`() {
        assertTrue(RiskThresholds(5, 10, 15).valid())
        assertFalse(RiskThresholds(10, 10, 15).valid())
        assertFalse(RiskThresholds(5, 10, 26).valid())
        assertFalse(RiskThresholds(0, 10, 15).valid())
    }

    @Test
    fun `la tendencia compara con la revision anterior del mismo tipo`() {
        assertNull(RiskLevels.trend(null, 12))
        assertEquals("AUMENTA", RiskLevels.trend(9, 12))
        assertEquals("SE MANTIENE", RiskLevels.trend(12, 12))
        assertEquals("DISMINUYE", RiskLevels.trend(16, 12))
    }

    @Test
    fun `escala al subir a ALTO o a CRITICO, nunca al bajar ni al quedarse`() {
        assertTrue(RiskLevels.escalates(null, "ALTO"))
        assertTrue(RiskLevels.escalates("MEDIO", "ALTO"))
        assertTrue(RiskLevels.escalates("ALTO", "CRITICO"))
        assertFalse(RiskLevels.escalates("ALTO", "ALTO"))
        assertFalse(RiskLevels.escalates("CRITICO", "ALTO"))
        assertFalse(RiskLevels.escalates("BAJO", "MEDIO"))
    }

    // decisión 16: review_every_days va de 1 a 365 (RiskWriteGuard), así que la cadencia ya no cae en silencio al proyecto
    @Test
    fun `la cadencia es la del riesgo o, vacia, la del proyecto`() {
        assertEquals(7L, RiskLevels.cadence(emptyMap(), emptyMap()))
        assertEquals(14L, RiskLevels.cadence(emptyMap(), mapOf("risk_review_days" to 14)))
        assertEquals(14L, RiskLevels.cadence(mapOf("review_every_days" to null), mapOf("risk_review_days" to 14)))
        assertEquals(30L, RiskLevels.cadence(mapOf("review_every_days" to 30), mapOf("risk_review_days" to 14)))
        assertEquals(365L, RiskLevels.cadence(mapOf("review_every_days" to "365"), mapOf("risk_review_days" to 14)))
    }

    // ASM-099: POR REVISAR desde el día anterior a la fecha y ese día; REVISION VENCIDA desde el día siguiente
    @Test
    fun `la situacion de revision cambia el dia anterior y el dia siguiente`() {
        val next = date("2026-10-14")
        assertEquals("AL DIA", RiskReview.status(next, date("2026-10-12")))
        assertEquals("POR REVISAR", RiskReview.status(next, date("2026-10-13")))
        assertEquals("POR REVISAR", RiskReview.status(next, date("2026-10-14")))
        assertEquals("REVISION VENCIDA", RiskReview.status(next, date("2026-10-15")))
        assertNull(RiskReview.status(null, date("2026-10-15")))
    }

    @Test
    fun `JOB-01 mueve la proxima revision con la cadencia y vacia la situacion fuera de los estados revisados`() {
        val stored = mapOf("next_review_date" to "2026-10-14")
        assertEquals(
            mapOf("next_review_date" to "2026-11-06", "review_status" to "AL DIA"),
            RiskReview.changes("monitoring", stored, date("2026-10-07"), 30, date("2026-10-20"))
        )
        assertEquals(
            mapOf("next_review_date" to "2026-10-14", "review_status" to "REVISION VENCIDA"),
            RiskReview.changes("assessed", stored, null, 7, date("2026-10-20"))
        )
        assertEquals(mapOf("review_status" to null), RiskReview.changes("closed", stored, date("2026-10-07"), 7, date("2026-10-20")))
    }

    // los nombres de Risks existen en el modelo: un renombre dejaría de revisar o de avisar en silencio
    @Test
    fun `los estados, las transiciones y los catalogos de Risks existen en el modelo`() {
        val states = catalog.states(Risks.SOCIAL_RISK)
        assertEquals(emptySet<String>(), Risks.REVIEWED - states.toSet())
        assertTrue(Risks.IDENTIFIED in states && Risks.CLOSED in states)
        for ((state, transition) in Risks.NEXT_TREATMENT.filterKeys { it != Risks.IDENTIFIED }) {
            assertEquals(state, catalog.transition(Risks.SOCIAL_RISK, transition)?.from, transition)
        }
        assertEquals(Risks.IDENTIFIED, catalog.transition(Risks.SOCIAL_RISK, Risks.ASSESS)?.from)
        assertEquals(Risks.LEVELS, catalog.enumOf(Risks.SOCIAL_RISK, Risks.LEVEL))
        assertEquals(listOf(RiskReview.ON_TIME, RiskReview.DUE, RiskReview.OVERDUE), catalog.enumOf(Risks.SOCIAL_RISK, Risks.REVIEW_STATUS))
        assertEquals(listOf(RiskLevels.UP, RiskLevels.SAME, RiskLevels.DOWN), catalog.enumOf(Risks.ASSESSMENT, "trend"))
        val derived = catalog.derivedFields(Risks.SOCIAL_RISK)
        assertTrue(derived.containsAll(listOf(Risks.LEVEL, Risks.SCORE, Risks.NEXT_REVIEW, Risks.REVIEW_STATUS)), derived.toString())
    }
}
