package sgspe.warning

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F4ApiTest

// lo que protege a las alertas de JOB-04 (especificación de F4 §3.1): generator_key solo lo escribe la plataforma, y una
// regla con alertas no se borra (se desactiva), para no perder el origen de sus alertas
class EarlyWarningProtectionApiTest : F4ApiTest() {
    private val manager by lazy { member("SOCIAL_RISK_MANAGER") }

    @Test
    fun `una persona no fija ni cambia generator_key de una alerta`() {
        val key = uniqueName("warning_rule:")
        val created =
            exchange(
                "POST",
                "/api/objects/early_warning/records",
                mapOf("attributes" to mapOf("project" to testProject(), "title" to "Alerta", "detected_at" to "2026-10-07T15:00:00Z", "generator_key" to key)),
                manager.token
            )
        assertEquals(HttpStatus.FORBIDDEN, created.status, created.body)
        assertEquals("GENERATOR_KEY_PLATFORM_ONLY", code(created))
        assertEquals(listOf("generator_key"), problemFields(created.body))
        val id = earlyWarning(testProject(), manager)
        assertEquals("GENERATOR_KEY_PLATFORM_ONLY", code(edit("early_warning", id, mapOf("generator_key" to key), manager.token)))
        // sin la clave, la alerta se edita con normalidad
        assertEquals(HttpStatus.OK, edit("early_warning", id, mapOf("title" to "Otra alerta"), manager.token).status)
    }

    @Test
    fun `una regla con alertas no se borra y una sin alertas si`() {
        val used = warningRule(testProject(), manager)
        earlyWarning(testProject(), manager, mapOf("early_warning_rule" to used))
        val refused = exchange("DELETE", "/api/objects/early_warning_rule/records/$used", null, manager.token)
        assertEquals(HttpStatus.CONFLICT, refused.status, refused.body)
        assertEquals("HAS_DEPENDENTS", code(refused))
        assertEquals(listOf("early_warning.early_warning_rule"), problemFields(refused.body))
        val unused = warningRule(testProject(), manager)
        assertEquals(HttpStatus.NO_CONTENT, exchange("DELETE", "/api/objects/early_warning_rule/records/$unused", null, manager.token).status)
    }
}
