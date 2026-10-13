package sgspe.ai

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

// los prompts versionados (ai-architecture.md §10, RQ-AI-006): la versión en uso de cada caso, su cabecera y sus variables
class PromptsTest {
    private val prompts = Prompts(AiProperties(prompts = mapOf("ai-05" to "1", "prueba" to "1")))

    @Test
    fun `la version en uso de un caso, con sus variables`() {
        val prompt = prompts.render("PRUEBA", mapOf("text" to "[PERSONA_1] pidió el canal"))
        assertEquals("prueba@1", prompt.reference)
        assertEquals("Proposal", prompt.output)
        assertTrue(prompt.text.contains("<texto>\n[PERSONA_1] pidió el canal\n</texto>"), prompt.text)
        assertFalse("{{" in prompt.text)
    }

    @Test
    fun `una variable que falta o que sobra no arma el prompt`() {
        assertThrows<IllegalStateException> { prompts.render("PRUEBA", emptyMap()) }
        assertThrows<IllegalStateException> { prompts.render("PRUEBA", mapOf("text" to "x", "otra" to "y")) }
    }

    @Test
    fun `un valor con llaves no se vuelve a expandir`() {
        val prompt = prompts.render("PRUEBA", mapOf("text" to "{{text}} y {{otra}}"))
        assertTrue(prompt.text.contains("<texto>\n{{text}} y {{otra}}\n</texto>"), prompt.text)
    }

    @Test
    fun `el prompt del asistente`() {
        val prompt = prompts.render("AI-05", mapOf("question" to "¿Qué pidió [PERSONA_1]?"))
        assertEquals("ai-05@1", prompt.reference)
        assertEquals("ai-05@1", prompts.reference("AI-05"))
        assertTrue(prompt.text.endsWith("<pregunta>\n¿Qué pidió [PERSONA_1]?\n</pregunta>"), prompt.text)
    }

    @Test
    fun `una version configurada que no existe impide arrancar`() {
        assertThrows<IllegalStateException> { Prompts(AiProperties(prompts = mapOf("ai-05" to "9"))) }
    }

    @Test
    fun `una cabecera que no es la de su ruta se rechaza`() {
        val content = "---\nid: ai-05\nversion: 2\nuse_case: AI-05\noutput: text\nlanguage: es\n---\nCuerpo\n"
        assertEquals("Cuerpo", PromptFile.parse("ai-05", "2", content).body)
        assertThrows<IllegalStateException> { PromptFile.parse("ai-05", "1", content) }
        assertThrows<IllegalStateException> { PromptFile.parse("ai-05", "2", content.replace("language: es", "language: en")) }
        assertThrows<IllegalStateException> { PromptFile.parse("ai-05", "2", content.replace("use_case: AI-05", "use_case: AI-06")) }
        assertThrows<IllegalStateException> { PromptFile.parse("ai-05", "2", "Sin cabecera") }
    }

    @Test
    fun `un prompt armado no se imprime con su texto`() {
        assertFalse("[PERSONA_1]" in prompts.render("PRUEBA", mapOf("text" to "[PERSONA_1]")).toString())
    }
}
