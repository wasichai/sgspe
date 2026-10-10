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
        marked: Boolean = false
    ) = PlatformOnlyFields.findings(objectName, changed, person, marked)

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
        assertEquals(listOf("early_warning_rule"), findings("early_warning", setOf("early_warning_rule"), marked = true).map { it.field })
        assertEquals(listOf("generator_key"), findings("task", setOf("generator_key"), marked = true).map { it.field })
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
