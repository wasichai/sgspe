package sgspe.ai

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import java.io.File

// una versión publicada de un prompt no cambia (ai-architecture.md §10, RQ-AI-006): su SHA-256 está en prompts/manifest.json, y
// editarla rompe el build. un cambio es una versión nueva con su propia huella
class PromptManifestTest {
    private val root = File("src/main/resources/sgspe/ai/prompts")

    private fun published(): Map<String, File> =
        root
            .walkTopDown()
            .filter { it.isFile && it.extension == "md" }
            .associateBy { it.relativeTo(root).invariantSeparatorsPath }

    @Test
    fun `cada version publicada es la de su huella en el manifiesto`() {
        val manifest =
            JsonMapper
                .builder()
                .build()
                .readValue(File(root, "manifest.json"), Map::class.java)
                .entries
                .associate { (path, hash) -> path.toString() to hash.toString() }
        val files = published()
        assertTrue(files.isNotEmpty(), "no hay prompts publicados en $root")
        assertEquals(manifest.keys, files.keys, "toda versión publicada tiene su huella, y toda huella su versión")
        for ((path, file) in files) {
            assertEquals(manifest[path], Hashes.sha256(file.readBytes()), "$path cambió: una versión publicada no se edita, se publica la siguiente")
        }
    }

    @Test
    fun `la cabecera de cada version es la de su ruta`() {
        for ((path, file) in published()) {
            val prompt = PromptFile.parse(file.parentFile.name, file.nameWithoutExtension, file.readText())
            assertEquals("es", prompt.language, path)
            assertTrue(prompt.body.isNotBlank(), path)
        }
    }
}
