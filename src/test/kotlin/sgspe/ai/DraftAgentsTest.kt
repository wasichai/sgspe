package sgspe.ai

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal

// AI-02 y AI-04 (ai-architecture.md §4.2): un borrador Markdown sin HTML, con marcadores estables y sin cita; uno vacío no se guarda
// pero su llamada queda registrada
class DraftAgentsTest {
    private val log = RecordedInteractions()

    private fun agents(answer: DraftText) = DraftAgents(RunFixtures.calls(FakeModel { answer }, log))

    @Test
    fun `el borrador de acta apunta a la interaccion, sin HTML y con marcadores estables`() {
        val draft = DraftText("## Datos de la reunión\n\n<b>[PERSONA_1]</b> pidió reparar el canal.<script>alert(1)</script>", 0.7)
        val result = runBlocking { agents(draft).minutes(RunFixtures.minutes(), RunFixtures.NAMES, RunBudget(8)) }
        val suggestion = result.suggestions.single()
        assertEquals("BORRADOR DE ACTA", suggestion.kind)
        assertEquals("engagement" to RunFixtures.ENGAGEMENT, suggestion.targetObject to suggestion.targetRecordId)
        assertEquals("## Datos de la reunión\n\n[PERSONA:${RunFixtures.ANA}] pidió reparar el canal.alert(1)", suggestion.suggestedValue)
        assertNull(suggestion.evidenceQuote)
        assertEquals(BigDecimal("0.70"), suggestion.confidence)
        assertEquals("ai-02@1", log.entries.single().promptVersion)
    }

    @Test
    fun `el resumen ejecutivo apunta al proyecto`() {
        val source = RunFixtures.minutes().copy(objectName = "project", id = RunFixtures.PROJECT, inputReference = "project:${RunFixtures.PROJECT}")
        val result = runBlocking { agents(DraftText("## Situación general\n\nSin novedades.")).summary(source, RunFixtures.NAMES, RunBudget(8)) }
        val suggestion = result.suggestions.single()
        assertEquals("RESUMEN EJECUTIVO", suggestion.kind)
        assertEquals("project" to RunFixtures.PROJECT, suggestion.targetObject to suggestion.targetRecordId)
        assertNull(suggestion.confidence)
        assertEquals("ai-04@1", log.entries.single().promptVersion)
    }

    @Test
    fun `un borrador vacio no se guarda, pero su llamada queda`() {
        val result = runBlocking { agents(DraftText("  <p></p> ")).minutes(RunFixtures.minutes(), RunFixtures.NAMES, RunBudget(8)) }
        assertTrue(result.suggestions.isEmpty())
        assertEquals(1, result.discarded)
        assertEquals(1, result.interactions.size)
        assertEquals("Un borrador de 10 caracteres", log.entries.single().outputSummary)
    }
}
