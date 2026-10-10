package sgspe.warning

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import sgspe.common.ModelCatalog
import wasichai.core.identity.AuthenticatedUser
import java.util.UUID

// las reglas de WF-J, WF-M y de las reglas de alerta (especificación de F4 §3.7 y §4, ASM-097), sin base
class WarningGuardRulesTest {
    private fun user(
        email: String = "persona@sgspe.test",
        service: String? = null
    ) = AuthenticatedUser(UUID.randomUUID(), UUID.randomUUID(), email, listOf("SOCIAL_RISK_MANAGER"), service)

    private fun codes(findings: List<sgspe.common.Finding>) = findings.map { it.code to it.field }

    @Test
    fun `confirmar o descartar lo decide una persona con CONFIRM_ALERT`() {
        assertEquals(emptyList<Pair<String, String?>>(), codes(WarningGuardRules.decision(user(), holdsConfirm = true)))
        assertEquals(listOf("ACTION_REQUIRED" to null), codes(WarningGuardRules.decision(user(), holdsConfirm = false)))
        assertEquals(listOf("HUMAN_DECISION_REQUIRED" to null), codes(WarningGuardRules.decision(null, holdsConfirm = true)))
        assertEquals(listOf("HUMAN_DECISION_REQUIRED" to null), codes(WarningGuardRules.decision(user(service = "integracion"), holdsConfirm = true)))
        assertEquals(
            listOf("HUMAN_DECISION_REQUIRED" to null),
            codes(WarningGuardRules.decision(user(email = "${UUID.randomUUID()}@service-accounts.invalid"), holdsConfirm = true))
        )
        assertEquals(
            403,
            WarningGuardRules
                .decision(null, true)
                .single()
                .status
                .value()
        )
    }

    @Test
    fun `cerrar un incidente exige su respuesta y sus consecuencias`() {
        assertEquals(
            listOf("FIELD_REQUIRED" to "immediate_response", "FIELD_REQUIRED" to "consequences"),
            codes(WarningGuardRules.incidentClose(emptyMap()))
        )
        assertEquals(emptyList<Pair<String, String?>>(), codes(WarningGuardRules.incidentClose(mapOf("immediate_response" to "a", "consequences" to "b"))))
    }

    @Test
    fun `el umbral es entero y la ventana va de 1 a 365`() {
        fun rule(vararg pairs: Pair<String, Any?>) =
            codes(WarningGuardRules.rule(mapOf("rule_kind" to "CONTEO DE SEÑALES") + pairs, setOf("threshold", "window_days", "rule_kind")))
        assertEquals(emptyList<Pair<String, String?>>(), rule("threshold" to 3, "window_days" to null))
        assertEquals(emptyList<Pair<String, String?>>(), rule("threshold" to "3.00", "window_days" to 365))
        assertEquals(listOf("OUT_OF_RANGE" to "threshold"), rule("threshold" to "2.5"))
        assertEquals(listOf("OUT_OF_RANGE" to "threshold"), rule("threshold" to null))
        assertEquals(listOf("OUT_OF_RANGE" to "threshold"), rule("threshold" to 0))
        assertEquals(listOf("OUT_OF_RANGE" to "window_days"), rule("threshold" to 3, "window_days" to 366))
        assertEquals(listOf("OUT_OF_RANGE" to "window_days"), rule("threshold" to 3, "window_days" to 0))
    }

    @Test
    fun `el umbral de NIVEL DE RIESGO es un puntaje de 1 a 25, tambien al cambiar solo el tipo`() {
        val after = mapOf("rule_kind" to "NIVEL DE RIESGO", "threshold" to 30)
        assertEquals(listOf("OUT_OF_RANGE" to "threshold"), codes(WarningGuardRules.rule(after, setOf("rule_kind"))))
        assertEquals(emptyList<Pair<String, String?>>(), codes(WarningGuardRules.rule(after + ("threshold" to 25), setOf("threshold"))))
        // un cambio que no toca el umbral, la ventana ni el tipo no se juzga: un valor viejo no bloquea renombrar la regla
        assertEquals(emptyList<Pair<String, String?>>(), codes(WarningGuardRules.rule(after, setOf("name"))))
    }

    // los nombres de Warnings existen en el modelo: un renombre dejaría de vigilarlos en silencio
    @Test
    fun `los tipos de regla, las transiciones y la accion existen en el modelo`() {
        val catalog = ModelCatalog.fromClasspath()
        assertEquals(Warnings.KINDS, catalog.enumOf(Warnings.RULE, WarningGuardRules.KIND))
        for (name in Warnings.DECISIONS) assertEquals("in_triage", catalog.transition(Warnings.WARNING, name)?.from, name)
        assertEquals("responding", catalog.transition(Warnings.INCIDENT, Warnings.INCIDENT_CLOSE)?.from)
        assertEquals(true, Warnings.CONFIRM_ALERT in catalog.objectOrNull(Warnings.WARNING)!!.actions)
        assertEquals(emptySet<String>(), Warnings.FINISHED - catalog.states(Warnings.WARNING).toSet())
    }
}
