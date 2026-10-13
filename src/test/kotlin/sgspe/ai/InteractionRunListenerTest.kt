package sgspe.ai

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import wasichai.agent.AgentAnswer
import wasichai.agent.AgentStep
import wasichai.agent.AgentUsage
import java.time.Instant

// la ai_interaction de cada pregunta (ai-architecture.md §9.2, RF-AIK-009): modelo y tokens de AgentUsage, latencia, versión del
// prompt y la huella de la pregunta, también si la corrida se trunca o falla; el resumen, sin datos personales
class InteractionRunListenerTest {
    private val run = AssistantRun("¿Qué pidió Ana Ejemplo?", NameDictionary.EMPTY, "ai-05@1", Instant.parse("2026-10-13T15:00:00Z"))
    private val now = Instant.parse("2026-10-13T15:00:03.250Z")
    private val steps =
        listOf(
            AgentStep("query_records", mapOf("object" to "commitment"), "commitment: 2 of 2 records"),
            AgentStep("get_record", mapOf("object" to "commitment"), "commitment record 1"),
            AgentStep("get_record", mapOf("object" to "commitment"), "commitment record 2")
        )

    @Test
    fun `una respuesta queda con su modelo, sus tokens, su latencia y la huella de la pregunta`() {
        assertEquals(
            InteractionEntry(
                useCase = "AI-05",
                model = "claude-opus-4-8",
                promptVersion = "ai-05@1",
                startedAt = Instant.parse("2026-10-13T15:00:00Z"),
                inputReference = "sha256:" + Hashes.sha256("¿Qué pidió Ana Ejemplo?"),
                outputSummary = "Respuesta con 3 pasos; herramientas: query_records, get_record",
                inputTokens = 300,
                outputTokens = 60,
                latencyMs = 3_250
            ),
            InteractionRunListener.entry(
                run,
                AgentAnswer("Ana Ejemplo pidió dos.", steps, false),
                AgentUsage("claude-opus-4-8", 300, 60),
                null,
                now,
                "claude-haiku-4-5"
            )
        )
    }

    @Test
    fun `una corrida truncada o fallida tambien, y sin uso informado toma el modelo configurado`() {
        val truncated = InteractionRunListener.entry(run, AgentAnswer("x", steps, true), null, null, now, "claude-opus-4-8")
        assertEquals("Corrida truncada tras 3 pasos; herramientas: query_records, get_record", truncated.outputSummary)
        val failed = InteractionRunListener.entry(run, null, null, IllegalStateException("proveedor caído"), now, "claude-opus-4-8")
        assertEquals("Corrida fallida (IllegalStateException); sin respuesta", failed.outputSummary)
        assertEquals("claude-opus-4-8", failed.model)
        assertNull(failed.inputTokens)
    }

    @Test
    fun `nada de la pregunta ni de la respuesta queda en la interaccion`() {
        val entry = InteractionRunListener.entry(run, AgentAnswer("Ana Ejemplo pidió dos.", emptyList(), false), null, null, now, null)
        assertEquals("Respuesta con 0 pasos; herramientas: ninguna", entry.outputSummary)
        assertFalse("Ana" in entry.outputSummary || "Ana" in entry.inputReference.orEmpty())
        assertEquals("desconocido", entry.model)
    }
}
