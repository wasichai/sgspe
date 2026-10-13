package sgspe.ai

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import wasichai.core.common.ForbiddenException
import wasichai.core.common.ValidationException
import wasichai.core.common.WasichaiException
import java.util.UUID

// el cuerpo de POST /api/sgspe/ai/knowledge/questions y quién puede preguntar a los documentos (api.md §4.14, decisión 8 del
// plan 7.2c)
class KnowledgeQuestionsTest {
    private val project = UUID.fromString("00000000-0000-0000-0000-0000000000b1")

    @Test
    fun `la pregunta y el proyecto, recortada la pregunta`() {
        assertEquals(
            "¿Qué pidió la asamblea?" to project,
            KnowledgeQuestions.parse(mapOf("question" to "  ¿Qué pidió la asamblea? ", "project" to "$project"), 4_000)
        )
    }

    @Test
    fun `un cuerpo incompleto, de otro tipo o con otra propiedad es 400 sobre el campo`() {
        val bodies =
            mapOf(
                mapOf("project" to "$project") to "question",
                mapOf("question" to " ", "project" to "$project") to "question",
                mapOf("question" to 3, "project" to "$project") to "question",
                mapOf("question" to "¿Qué?") to "project",
                mapOf("question" to "¿Qué?", "project" to "no-es-un-id") to "project",
                mapOf("question" to "¿Qué?", "project" to "$project", "scope" to "tenant") to "scope"
            )
        for ((body, field) in bodies) {
            val failure = assertThrows<ValidationException> { KnowledgeQuestions.parse(body, 4_000) }
            assertEquals(field, failure.violations.single().field, "$body")
        }
    }

    @Test
    fun `una pregunta que pasa el tope es 422 AI_INPUT_TOO_LARGE sobre question`() {
        val failure = assertThrows<WasichaiException> { KnowledgeQuestions.parse(mapOf("question" to "x".repeat(41), "project" to "$project"), 40) }
        assertEquals(422, failure.status.value())
        assertEquals("AI_INPUT_TOO_LARGE", failure.violations.single().message)
        assertEquals("question", failure.violations.single().field)
    }

    @Test
    fun `pregunta a los documentos una persona que crea interacciones, lee evidencias y lee los nombres`() {
        assertNull(KnowledgeUseCase.decide(serviceAccount = false, canCreateInteraction = true, canReadEvidence = true, readsNames = true))
        val service = KnowledgeUseCase.decide(serviceAccount = true, canCreateInteraction = true, canReadEvidence = true, readsNames = true)!!
        assertEquals("PERSON_REQUIRED", service.violations.single().message)
        for (missing in listOf(listOf(false, true, true), listOf(true, false, true), listOf(true, true, false))) {
            val failure = KnowledgeUseCase.decide(false, missing[0], missing[1], missing[2])
            assertTrue(failure is ForbiddenException, "$missing")
        }
        assertTrue("nombres de person" in KnowledgeUseCase.decide(false, true, true, false)!!.message.orEmpty())
    }
}
