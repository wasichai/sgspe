package sgspe.ai

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import org.springframework.http.HttpStatus
import sgspe.common.Callers
import sgspe.common.Records
import wasichai.core.common.WasichaiException
import wasichai.core.identity.AuthenticatedUser
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

// los atributos de una ai_interaction (ai-architecture.md §9.2, domain-model.md §6.18): una referencia a la entrada, nunca su
// contenido; quién la pidió o, sin nadie, la plataforma
class InteractionsTest {
    private val entry =
        InteractionEntry(
            useCase = "AI-05",
            model = "claude-opus-4-8",
            promptVersion = "ai-05@1",
            startedAt = Instant.parse("2026-10-13T15:00:00.734Z"),
            inputReference = "sha256:abc",
            outputSummary = "Respuesta con 2 pasos; herramientas: get_record",
            inputTokens = 200,
            outputTokens = 40,
            latencyMs = 2_400
        )
    private val person = AuthenticatedUser(UUID.fromString("00000000-0000-0000-0000-0000000000c1"), UUID.randomUUID(), "cro@sgspe.test", emptyList())

    @Test
    fun `una llamada de una persona queda a su nombre`() {
        assertEquals(
            mapOf(
                "use_case" to "AI-05",
                "provider" to "anthropic",
                "model" to "claude-opus-4-8",
                "prompt_version" to "ai-05@1",
                "started_at" to "2026-10-13T15:00:00Z",
                "input_reference" to "sha256:abc",
                "output_summary" to "Respuesta con 2 pasos; herramientas: get_record",
                "input_tokens" to 200,
                "output_tokens" to 40,
                "latency_ms" to 2_400,
                "requested_by_user_id" to "00000000-0000-0000-0000-0000000000c1",
                "requested_by_user_email" to "cro@sgspe.test",
                "run_by_platform" to false
            ),
            Interactions.attributes(entry, "anthropic", person)
        )
    }

    @Test
    fun `una llamada de la plataforma no nombra a nadie`() {
        val project = UUID.randomUUID()
        val attributes = Interactions.attributes(entry.copy(project = project, confidence = BigDecimal("0.8")), "anthropic", null)
        assertEquals(true, attributes["run_by_platform"])
        assertFalse("requested_by_user_id" in attributes)
        assertFalse("requested_by_user_email" in attributes)
        assertEquals(project.toString(), attributes["project"])
        assertEquals(BigDecimal("0.8"), attributes["confidence"])
    }

    @Test
    fun `la latencia no pasa de un entero`() {
        assertEquals(Int.MAX_VALUE, Interactions.attributes(entry.copy(latencyMs = Long.MAX_VALUE), "anthropic", null)["latency_ms"])
    }

    @Test
    fun `una cuenta de servicio no registra llamadas y es PERSON_REQUIRED`() {
        val service = AuthenticatedUser(UUID.randomUUID(), UUID.randomUUID(), "servicio@sgspe.test", emptyList(), "integracion")
        val records = mock(Records::class.java) { throw AssertionError("una cuenta de servicio no escribe ai_interaction") }
        val interactions = Interactions(records, mock(Callers::class.java) { service }, AiProperties())
        val refused = assertThrows<WasichaiException> { runBlocking { interactions.record(entry) } }
        assertEquals(HttpStatus.FORBIDDEN, refused.status)
        assertEquals("PERSON_REQUIRED", refused.violations.single().message)
    }
}
