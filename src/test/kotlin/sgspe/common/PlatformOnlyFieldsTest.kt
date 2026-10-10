package sgspe.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus

// los campos que solo escribe la plataforma (GeneratorKeyGuard), sin base: generator_key de task y de early_warning con su
// código de siempre y, desde la ronda branchfix de 4.2b (R-I3), early_warning_rule y detected_at de una alerta con
// PLATFORM_ONLY. una persona que los fija, cambia o vacía recibe 403 sobre el campo; detected_at lo escribe además el alta
// manual de sgspe (con la marca)
class PlatformOnlyFieldsTest {
    private fun findings(
        objectName: String,
        changed: Set<String>,
        person: Boolean = true,
        marked: Boolean = false,
        before: Map<String, Any?> = emptyMap(),
        after: Map<String, Any?> = emptyMap()
    ) = PlatformOnlyFields.findings(objectName, changed, person, marked, before, after)

    @Test
    fun `generator_key sigue con su codigo en las tareas y en las alertas`() {
        for (objectName in listOf("task", "early_warning")) {
            val found = findings(objectName, setOf("generator_key", "title"))
            assertEquals(listOf("GENERATOR_KEY_PLATFORM_ONLY" to "generator_key"), found.map { it.code to it.field }, objectName)
            assertEquals(HttpStatus.FORBIDDEN, found.single().status)
        }
    }

    @Test
    fun `la regla y la hora de deteccion de una alerta son de la plataforma`() {
        assertEquals(listOf("PLATFORM_ONLY" to "early_warning_rule"), findings("early_warning", setOf("early_warning_rule")).map { it.code to it.field })
        assertEquals(listOf("PLATFORM_ONLY" to "detected_at"), findings("early_warning", setOf("detected_at")).map { it.code to it.field })
        assertTrue(findings("early_warning", setOf("detected_at", "early_warning_rule")).all { it.status == HttpStatus.FORBIDDEN })
        // la clave va primero: el alta que la fija junto con lo demás sigue respondiendo GENERATOR_KEY_PLATFORM_ONLY
        assertEquals("generator_key", findings("early_warning", setOf("detected_at", "early_warning_rule", "generator_key")).first().field)
    }

    @Test
    fun `la plataforma los escribe y la marca solo la hora del alta manual`() {
        assertTrue(findings("early_warning", setOf("generator_key", "early_warning_rule", "detected_at"), person = false).isEmpty())
        assertTrue(findings("early_warning", setOf("detected_at"), marked = true).isEmpty())
        // la marca solo fija la hora que falta: cambiarla o vaciarla sigue siendo de la plataforma (ronda 1 de branchfix)
        val dated = mapOf<String, Any?>("detected_at" to "2026-10-07T15:00:00Z")
        assertEquals(listOf("detected_at"), findings("early_warning", setOf("detected_at"), marked = true, before = dated).map { it.field })
        assertEquals(listOf("early_warning_rule"), findings("early_warning", setOf("early_warning_rule"), marked = true).map { it.field })
        assertEquals(listOf("generator_key"), findings("task", setOf("generator_key"), marked = true).map { it.field })
    }

    // D-1: el listener de las QCS confidenciales (ConfidentialTasksListener) escribe, como quien llama y con la marca, la
    // clave de una tarea confidencial; nada más
    @Test
    fun `la marca fija o cambia la clave de una tarea solo a una clave confidencial`() {
        val ack = mapOf<String, Any?>("generator_key" to "ack:grievance:0123456789abcdef")
        val escalation = mapOf<String, Any?>("generator_key" to "escalation:grievance:0123456789abcdef:2026-10-13:1")
        val stored = mapOf<String, Any?>("generator_key" to "escalation:grievance:g-1:2026-10-13:1")
        assertTrue(findings("task", setOf("generator_key"), marked = true, after = ack).isEmpty())
        assertTrue(findings("task", setOf("generator_key"), marked = true, before = stored, after = escalation).isEmpty())
        // con la marca, una clave normal; sin la marca, una confidencial; con la marca, vaciarla: 403
        val normal = mapOf<String, Any?>("generator_key" to "escalation:grievance:g-1:2026-10-13:2")
        assertEquals(listOf("GENERATOR_KEY_PLATFORM_ONLY"), findings("task", setOf("generator_key"), marked = true, after = normal).map { it.code })
        assertEquals(listOf("GENERATOR_KEY_PLATFORM_ONLY"), findings("task", setOf("generator_key"), after = ack).map { it.code })
        val emptied = mapOf<String, Any?>("generator_key" to null)
        assertEquals(
            listOf("GENERATOR_KEY_PLATFORM_ONLY"),
            findings("task", setOf("generator_key"), marked = true, before = ack, after = emptied).map { it.code }
        )
        // la excepción es solo de task: la clave de una alerta sigue siendo de la plataforma
        assertEquals(listOf("generator_key"), findings("early_warning", setOf("generator_key"), marked = true, after = ack).map { it.field })
    }

    @Test
    fun `sin cambio o en otro objeto no hay hallazgo`() {
        assertTrue(findings("early_warning", setOf("title", "severity")).isEmpty())
        assertTrue(findings("incident", setOf("detected_at", "early_warning_rule", "generator_key")).isEmpty())
    }

    // la tabla nombra campos y relaciones por texto: si el modelo los renombra, la guarda dejaría de vigilarlos en silencio
    @Test
    fun `cada campo de la tabla existe en el modelo`() {
        val catalog = ModelCatalog.fromClasspath()
        for ((objectName, fields) in PlatformOnlyFields.FIELDS) {
            val obj = catalog.objectOrNull(objectName)
            val relations = catalog.manyToOneFields(objectName).toMap()
            for (name in fields.keys) assertTrue(obj?.fields?.get(name) != null || name in relations, "$objectName.$name")
        }
        assertEquals("early_warning_rule", catalog.manyToOneFields("early_warning").toMap()["early_warning_rule"])
    }
}
