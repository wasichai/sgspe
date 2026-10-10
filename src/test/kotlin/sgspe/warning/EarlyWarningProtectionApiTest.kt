package sgspe.warning

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F4ApiTest
import java.time.Instant
import java.time.temporal.ChronoUnit

// lo que protege a las alertas de JOB-04 (especificación de F4 §3.1): generator_key solo lo escribe la plataforma, y una
// regla con alertas no se borra (se desactiva), para no perder el origen de sus alertas. desde la ronda branchfix de 4.2b
// (R-I3), early_warning_rule y detected_at también son de la plataforma (403 PLATFORM_ONLY): una alerta manual nace sin regla
// y con la hora de su alta
class EarlyWarningProtectionApiTest : F4ApiTest() {
    private val manager by lazy { member("SOCIAL_RISK_MANAGER") }

    @Test
    fun `una persona no fija ni cambia generator_key de una alerta`() {
        val key = uniqueName("warning_rule:")
        val created =
            exchange(
                "POST",
                "/api/objects/early_warning/records",
                mapOf("attributes" to mapOf("project" to testProject(), "title" to "Alerta", "generator_key" to key)),
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
    fun `una alerta manual nace sin regla y con la hora de su alta`() {
        val rule = warningRule(testProject(), manager)
        val linked =
            exchange(
                "POST",
                "/api/objects/early_warning/records",
                mapOf("attributes" to mapOf("project" to testProject(), "title" to "Alerta", "early_warning_rule" to rule)),
                manager.token
            )
        assertEquals(HttpStatus.FORBIDDEN, linked.status, linked.body)
        assertEquals("PLATFORM_ONLY", code(linked))
        assertEquals(listOf("early_warning_rule"), problemFields(linked.body))
        val dated =
            exchange(
                "POST",
                "/api/objects/early_warning/records",
                mapOf("attributes" to mapOf("project" to testProject(), "title" to "Alerta", "detected_at" to "2026-10-07T15:00:00Z")),
                manager.token
            )
        assertEquals(HttpStatus.FORBIDDEN, dated.status, dated.body)
        assertEquals("PLATFORM_ONLY", code(dated))
        assertEquals(listOf("detected_at"), problemFields(dated.body))
        val before = Instant.now().minus(1, ChronoUnit.MINUTES)
        val id = earlyWarning(testProject(), manager)
        val detected = Instant.parse(record("early_warning", id)["attributes"]["detected_at"].asString())
        assertTrue(detected.isAfter(before), detected.toString())
    }

    @Test
    fun `una persona no vincula, reapunta, vacia ni mueve la hora de una alerta de JOB-04`() {
        val rule = warningRule(testProject(), manager)
        val other = warningRule(testProject(), manager)
        val id = ruleWarning(testProject(), rule)
        for ((changes, field) in listOf(
            mapOf("early_warning_rule" to other) to "early_warning_rule",
            mapOf("early_warning_rule" to null) to "early_warning_rule",
            mapOf("detected_at" to Instant.now().toString()) to "detected_at",
            mapOf("detected_at" to null) to "detected_at"
        )) {
            val response = edit("early_warning", id, changes, manager.token)
            assertEquals(HttpStatus.FORBIDDEN, response.status, response.body)
            assertEquals("PLATFORM_ONLY", code(response))
            assertEquals(listOf(field), problemFields(response.body))
        }
        // un PUT que los omite también los cambia: vacía los dos
        val current = attributesOf(record("early_warning", id, manager.token)) - setOf("early_warning_rule", "detected_at", "generator_key")
        val omitted = exchange("PUT", "/api/objects/early_warning/records/$id", mapOf("attributes" to current), manager.token)
        assertEquals(HttpStatus.FORBIDDEN, omitted.status, omitted.body)
        assertEquals("PLATFORM_ONLY", code(omitted))
        // reenviarlos tal como se leyeron no es cambiarlos
        assertEquals(HttpStatus.OK, edit("early_warning", id, mapOf("title" to "Movilización confirmada"), manager.token).status)
        val after = record("early_warning", id)["attributes"]
        assertEquals(rule, after["early_warning_rule"].asString())
        assertEquals(Instant.parse("2026-10-07T15:00:00Z"), Instant.parse(after["detected_at"].asString()))
    }

    @Test
    fun `una regla con alertas no se borra y una sin alertas si`() {
        val used = warningRule(testProject(), manager)
        ruleWarning(testProject(), used)
        val refused = exchange("DELETE", "/api/objects/early_warning_rule/records/$used", null, manager.token)
        assertEquals(HttpStatus.CONFLICT, refused.status, refused.body)
        assertEquals("HAS_DEPENDENTS", code(refused))
        assertEquals(listOf("early_warning.early_warning_rule"), problemFields(refused.body))
        val unused = warningRule(testProject(), manager)
        assertEquals(HttpStatus.NO_CONTENT, exchange("DELETE", "/api/objects/early_warning_rule/records/$unused", null, manager.token).status)
    }
}
