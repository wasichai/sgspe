package sgspe.privacy

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.io.File

// wasichai-documents congela en cada documento emitido el registro entero y todas las columnas de cada tabla relacionada que
// nombra la plantilla, sin permisos de campo, y GET /api/documents/{id} lo devuelve a todo lector del objeto (UPS-23). por eso
// un tipo de documento solo se declara sobre un objeto sin campos restringidos de roles.json y sin tablas relacionadas de
// objetos que los tengan (security.md §11.4, ASM-090): la regla de model/test_model.py, del lado de la aplicación. lee los
// archivos versionados, como ArchitectureTest lee roles.json
class IssuedDocumentsPrivacyTest {
    private val json = JsonMapper.builder().build()
    private val model = json.readTree(File("model/model.json"))
    private val restricted: Set<String> =
        json
            .readTree(File("model/roles.json"))["restrictedFields"]
            .values()
            .map { it["object"].asString() }
            .toSet()

    private fun nodes(node: JsonNode): Sequence<JsonNode> =
        sequenceOf(node) +
            node
                .path("content")
                .values()
                .asSequence()
                .flatMap { nodes(it) }

    // el otro extremo de una relación vista desde el objeto de la plantilla
    private fun otherEnd(
        objectName: String,
        relationship: String
    ): String? {
        val rel = model["relationships"].values().firstOrNull { it["name"].asString() == relationship } ?: return null
        val source = rel["source"].asString()
        val target = rel["target"].asString()
        return if (source == objectName) target else source
    }

    private fun violations(): List<String> {
        val files = File("model/document-types").listFiles { f -> f.extension == "json" }.orEmpty().sortedBy { it.name }
        assertTrue(files.isNotEmpty(), "no hay tipos de documento en model/document-types: la prueba pasaría en vacío")
        return files.flatMap { file ->
            val objectName = file.nameWithoutExtension
            val own = if (objectName in restricted) listOf("${file.name}: $objectName tiene campos restringidos") else emptyList()
            own +
                json.readTree(file).values().flatMap { type ->
                    nodes(type["template"])
                        .filter { it.path("type").asString() == "relatedTable" }
                        .mapNotNull { node ->
                            val other = otherEnd(objectName, node.path("attrs")["relationship"].asString())
                            if (other == null || other in restricted) {
                                "${file.name}: ${type["name"].asString()} congela la tabla de ${other ?: "una relación que no existe"}"
                            } else {
                                null
                            }
                        }.toList()
                }
        }
    }

    @Test
    fun `ningun tipo de documento congela un campo restringido`() {
        assertEquals(emptyList<String>(), violations())
    }
}
