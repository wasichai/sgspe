package sgspe.ai

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

// los límites del RAG se validan al arrancar: una configuración imposible falla de inmediato, no en cada vuelta de JOB-07
class KnowledgePropertiesTest {
    @Test
    fun `los valores por defecto y unos coherentes valen`() {
        assertDoesNotThrow { KnowledgeProperties() }
        assertDoesNotThrow { KnowledgeProperties(chunkChars = 200, overlapChars = 0, maxChars = 200, maxBytes = 1, candidates = 1, results = 1) }
    }

    @Test
    fun `una configuracion imposible falla al crearse`() {
        val wrong =
            listOf(
                { KnowledgeProperties(chunkChars = 0) },
                { KnowledgeProperties(overlapChars = -1) },
                { KnowledgeProperties(chunkChars = 200, overlapChars = 200) },
                // Segmenter exige un solape menor que la mitad del fragmento
                { KnowledgeProperties(chunkChars = 200, overlapChars = 100) },
                { KnowledgeProperties(chunkChars = 200, maxChars = 199) },
                { KnowledgeProperties(maxBytes = 0) },
                { KnowledgeProperties(results = 0) },
                { KnowledgeProperties(candidates = 4, results = 5) }
            )
        for ((i, build) in wrong.withIndex()) {
            val error = assertThrows<IllegalArgumentException>("caso $i") { build() }
            assertEquals(true, error.message?.startsWith("sgspe.ai.knowledge"), error.message)
        }
    }
}
