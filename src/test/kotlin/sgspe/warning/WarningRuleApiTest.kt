package sgspe.warning

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F4ApiTest

// las reglas de alerta con WarningWriteGuard (especificación de F4 §4, ASM-097): umbral entero, de 1 a 25 en NIVEL DE RIESGO,
// y ventana de 1 a 365 días, en el alta y en el cambio; una regla sin proyecto es del tenant
class WarningRuleApiTest : F4ApiTest() {
    private val manager by lazy { member("SOCIAL_RISK_MANAGER") }

    private fun create(attributes: Map<String, Any?>): Response =
        exchange(
            "POST",
            "/api/objects/early_warning_rule/records",
            mapOf(
                "attributes" to
                    mapOf("name" to "Regla de prueba", "rule_kind" to "CONTEO DE SEÑALES", "threshold" to 3, "enabled" to true, "project" to testProject()) +
                    attributes
            ),
            manager.token
        )

    @Test
    fun `un umbral con decimales o un puntaje mayor que 25 es OUT_OF_RANGE`() {
        val fractional = create(mapOf("threshold" to 2.5))
        assertEquals(HttpStatus.BAD_REQUEST, fractional.status, fractional.body)
        assertEquals("OUT_OF_RANGE", code(fractional))
        assertEquals(listOf("threshold"), problemFields(fractional.body))
        val score = create(mapOf("rule_kind" to "NIVEL DE RIESGO", "threshold" to 30))
        assertEquals(listOf("threshold"), problemFields(score.body))
        assertEquals(HttpStatus.CREATED, create(mapOf("rule_kind" to "NIVEL DE RIESGO", "threshold" to 25)).status)
    }

    @Test
    fun `una ventana fuera de 1 a 365 dias es OUT_OF_RANGE, tambien al cambiarla`() {
        assertEquals(listOf("window_days"), problemFields(create(mapOf("window_days" to 0)).body))
        val id = warningRule(testProject(), manager)
        val edited = edit("early_warning_rule", id, mapOf("window_days" to 400), manager.token)
        assertEquals(HttpStatus.BAD_REQUEST, edited.status, edited.body)
        assertEquals(listOf("window_days"), problemFields(edited.body))
    }

    @Test
    fun `una regla sin proyecto es del tenant`() {
        val tenant = warningRule(null, manager)
        assertEquals(true, record("early_warning_rule", tenant)["attributes"]["project"].let { it == null || it.isNull })
    }
}
