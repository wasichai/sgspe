package sgspe.warning

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

// los seis tipos de regla de JOB-04 (workflows.md «Tipos de regla de JOB-04», ASM-097), sin base: la regla guardada, la
// precedencia de la del proyecto, la ventana, el umbral y la alerta que crea, sin datos personales
class WarningEvaluationTest {
    private val today = LocalDate.parse("2026-10-09")

    private fun rule(
        id: String,
        kind: String,
        project: String? = null,
        threshold: Long = 3,
        window: Long = 7
    ) = WarningRule(id, "Regla $id", kind, project, threshold, window, "MEDIO")

    @Test
    fun `una regla guardada se lee con sus valores por defecto y una invalida se salta`() {
        val parsed = WarningEvaluation.parse("r1", mapOf("name" to "Señales", "rule_kind" to "CONTEO DE SEÑALES", "threshold" to "3.00"))
        assertEquals(WarningRule("r1", "Señales", "CONTEO DE SEÑALES", null, 3, 7, "MEDIO"), parsed)
        assertNull(WarningEvaluation.parse("r2", mapOf("rule_kind" to "CAMBIO DE POSTURA", "threshold" to 3)))
        assertNull(WarningEvaluation.parse("r3", mapOf("rule_kind" to "NIVEL DE RIESGO", "threshold" to 30)))
        assertNull(WarningEvaluation.parse("r4", mapOf("rule_kind" to "INCIDENTES", "threshold" to 2.5)))
        assertNull(WarningEvaluation.parse("r5", mapOf("rule_kind" to "INCIDENTES", "threshold" to 2, "window_days" to 0)))
    }

    @Test
    fun `la regla del proyecto reemplaza a la del tenant del mismo tipo`() {
        val tenantSignals = rule("t1", Warnings.SIGNALS)
        val tenantIncidents = rule("t2", Warnings.INCIDENTS)
        val ownSignals = rule("p1", Warnings.SIGNALS, "P")
        val otherProject = rule("q1", Warnings.INCIDENTS, "Q")
        val rules = listOf(tenantSignals, tenantIncidents, ownSignals, otherProject)
        assertEquals(listOf(ownSignals, tenantIncidents), WarningEvaluation.applicable(rules, "P"))
        assertEquals(listOf(otherProject, tenantSignals), WarningEvaluation.applicable(rules, "Q"))
    }

    @Test
    fun `la ventana son los dias de Lima que terminan hoy y dispara al alcanzar el umbral`() {
        assertEquals(LocalDate.parse("2026-10-03"), WarningEvaluation.windowStart(today, 7))
        assertEquals(today, WarningEvaluation.windowStart(today, 1))
        assertTrue(WarningEvaluation.fires(3, 3))
        assertFalse(WarningEvaluation.fires(2, 3))
    }

    @Test
    fun `cada tipo describe su conteo, su ventana y su umbral, sin desglose`() {
        assertEquals("3 señales en 7 días (umbral 3)", WarningEvaluation.description(rule("a", Warnings.SIGNALS), 3))
        assertEquals("4 QCS recibidas en 7 días (umbral 3)", WarningEvaluation.description(rule("a", Warnings.QCS), 4))
        assertEquals("5 compromisos vencidos (umbral 3)", WarningEvaluation.description(rule("a", Warnings.OVERDUE_COMMITMENTS), 5))
        assertEquals("3 menciones negativas en 14 días (umbral 3)", WarningEvaluation.description(rule("a", Warnings.NEGATIVE_MENTIONS, window = 14), 3))
        assertEquals("3 incidentes en 7 días (umbral 3)", WarningEvaluation.description(rule("a", Warnings.INCIDENTS), 3))
        val risk = RiskHit("r", "RS-7", 16, "loc", null)
        assertEquals("Riesgo RS-7 con puntaje 16 (umbral 15)", WarningEvaluation.description(rule("a", Warnings.RISK_LEVEL, threshold = 15), 16, risk))
    }

    @Test
    fun `la alerta nace en detected con su clave y, en NIVEL DE RIESGO, con el riesgo y su territorio`() {
        val now = Instant.parse("2026-10-09T15:00:00Z")
        val signals = WarningEvaluation.alert(rule("a", Warnings.SIGNALS), "P", 3, null, now, today)
        assertEquals("Regla a: CONTEO DE SEÑALES", signals["title"])
        assertEquals("warning_rule:a:P:2026-10-09", signals["generator_key"])
        assertEquals(false, signals["ai_detected"])
        assertEquals(
            setOf("title", "trigger_description", "severity", "detected_at", "early_warning_rule", "project", "ai_detected", "generator_key"),
            signals.keys
        )
        val risk = WarningEvaluation.alert(rule("b", Warnings.RISK_LEVEL, threshold = 15), "P", 16, RiskHit("r", "RS-7", 16, "loc", "com"), now, today)
        assertEquals("warning_rule:b:P:r:2026-10-09", risk["generator_key"])
        assertEquals(listOf("r", "loc", "com"), listOf(risk["social_risk"], risk["locality"], risk["community"]))
    }

    // el silencio de JOB-04 (ASM-097, decisión 8): la ventana de una regla de 7 días que termina el 9 de octubre empieza el 3 a
    // las 00:00 de Lima (05:00Z); una alerta abierta silencia aunque sea de un día anterior, una descartada o cerrada solo
    // dentro de la ventana, y una fuera de ella no
    @Test
    fun `una alerta abierta o detectada dentro de la ventana silencia la regla`() {
        val from = WarningEvaluation.windowFrom(today, 7)
        assertEquals(Instant.parse("2026-10-03T05:00:00Z"), from)
        val inside = Instant.parse("2026-10-03T05:00:00Z")
        val outside = Instant.parse("2026-10-03T04:59:59Z")
        for (state in listOf("detected", "in_triage", "confirmed", "responding", "resolved")) {
            assertTrue(WarningEvaluation.silences(state, Instant.parse("2026-09-01T15:00:00Z"), from), state)
        }
        for (state in Warnings.FINISHED) {
            assertTrue(WarningEvaluation.silences(state, inside, from), state)
            assertFalse(WarningEvaluation.silences(state, outside, from), state)
            assertFalse(WarningEvaluation.silences(state, null, from), state)
        }
        assertEquals(inside, WarningEvaluation.instant("2026-10-03T00:00:00-05:00"))
        assertEquals(inside, WarningEvaluation.instant("2026-10-03T05:00:00Z"))
        assertNull(WarningEvaluation.instant("no es una fecha"))
    }
}
