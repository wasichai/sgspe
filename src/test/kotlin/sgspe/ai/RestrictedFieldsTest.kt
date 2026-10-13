package sgspe.ai

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import java.io.File

// una sola fuente de los campos restringidos para la IA (especificación de F7 §2.2.2, ASM-130): la tabla de security.md §7.9,
// leída como la lee model/design_docs.py (restricted_fields), y los permisos de campo de model/roles.json, que test_apply_roles.py
// ya compara con esa tabla. un campo nuevo en §7.9 rompe esta prueba hasta que se le da su marcador
class RestrictedFieldsTest {
    private fun documented(): Set<Pair<String, String>> {
        val lines = File("docs/security.md").readLines()
        val start = lines.indexOfFirst { it.startsWith("| Campo | Dato | Roles que conservan acceso |") }
        assertTrue(start >= 0, "security.md perdió la tabla de §7.9")
        val pairs = mutableSetOf<Pair<String, String>>()
        for (line in lines.drop(start + 2)) {
            if (!line.startsWith("|")) break
            val names = TICKS.findAll(line.split("|")[1]).map { it.groupValues[1] }.toList()
            if ("." !in names.first()) {
                // «`person` de `grievance`, `inquiry` y `stakeholder_request`»
                names.drop(1).forEach { pairs += it to names.first() }
            } else {
                var current = ""
                for (token in names) {
                    if ("." in token) current = token.substringBefore(".")
                    pairs += current to token.substringAfter(".")
                }
            }
        }
        return pairs
    }

    private fun listed(): Set<Pair<String, String>> = RestrictedFields.FIELDS.flatMap { (obj, fields) -> fields.keys.map { obj to it } }.toSet()

    @Test
    fun `la lista es la tabla de security md 7_9`() {
        assertEquals(documented(), listed())
    }

    @Test
    fun `la lista es la de los permisos de campo de roles json`() {
        val roles = JsonMapper.builder().build().readTree(File("model/roles.json"))
        val pairs =
            roles["restrictedFields"]
                .values()
                .flatMap { entry -> entry["fields"].values().map { entry["object"].asString() to it.asString() } }
                .toSet()
        assertEquals(pairs, listed())
    }

    @Test
    fun `cada campo tiene su marcador tipado`() {
        assertEquals(Marker.DOCUMENTO, RestrictedFields.of("person")["id_document_number"])
        assertEquals(Marker.PERSONA, RestrictedFields.of(" Grievance ")["person"])
        assertEquals(Marker.TELEFONO, RestrictedFields.of("engagement_participant")["phone"])
        assertEquals(Marker.CONTACTO, RestrictedFields.of("contact_method")["contact_value"])
        assertEquals(Marker.DATO_SENSIBLE, RestrictedFields.of("stakeholder")["stance"])
        assertEquals(Marker.CITA, RestrictedFields.of("ai_suggestion")["evidence_quote"])
        assertEquals(emptyMap<String, Marker>(), RestrictedFields.of("engagement"))
        assertEquals(emptyMap<String, Marker>(), RestrictedFields.of(null))
    }

    companion object {
        private val TICKS = Regex("`([^`]+)`")
    }
}
