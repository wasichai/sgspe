package sgspe.ai

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import sgspe.F7ApiTest
import tools.jackson.databind.JsonNode
import wasichai.core.common.ForbiddenException
import java.time.Instant
import java.util.UUID

// Interactions contra wasichai (ai-architecture.md §9.2, RF-AIK-009): una persona registra su llamada a su nombre, la plataforma
// sin nombrar a nadie, y quien no crea interacciones no registra
class InteractionsApiTest : F7ApiTest() {
    @Autowired
    private lateinit var interactions: Interactions

    private fun entry(project: UUID? = null) =
        InteractionEntry(
            useCase = "AI-05",
            model = AI_MODEL,
            promptVersion = "ai-05@1",
            startedAt = Instant.parse("2026-10-13T15:00:00Z"),
            inputReference = "sha256:0f3a",
            outputSummary = "Respuesta con 1 paso; herramientas: count_records",
            inputTokens = 200,
            outputTokens = 40,
            latencyMs = 2_400,
            project = project
        )

    private fun stored(id: UUID): JsonNode = tree(send("GET", "/api/objects/ai_interaction/records/$id", null, HttpStatus.OK))["attributes"]

    @Test
    fun `una persona registra su llamada y queda a su nombre`() {
        val cro = member("COMMUNITY_RELATIONS_OFFICER")
        val attributes = stored(asUser(cro) { interactions.record(entry()) })
        assertEquals("AI-05", attributes["use_case"].asString())
        assertEquals("anthropic", attributes["provider"].asString())
        assertEquals("ai-05@1", attributes["prompt_version"].asString())
        assertEquals(200, attributes["input_tokens"].asInt())
        assertEquals(cro.id, attributes["requested_by_user_id"].asString())
        assertEquals(cro.email, attributes["requested_by_user_email"].asString())
        assertFalse(attributes["run_by_platform"].asBoolean())
    }

    @Test
    fun `una llamada del proyecto queda en su proyecto`() {
        val cro = member("COMMUNITY_RELATIONS_OFFICER")
        val attributes = stored(asUser(cro) { interactions.record(entry(UUID.fromString(testProject()))) })
        assertEquals(testProject(), attributes["project"].asString())
    }

    @Test
    fun `la plataforma registra sin nombrar a nadie`() {
        val attributes = stored(runBlocking { platformService.asPlatform(organizationId()) { interactions.record(entry()) } })
        assertTrue(attributes["run_by_platform"].asBoolean())
        assertTrue(attributes["requested_by_user_id"]?.isNull ?: true)
    }

    @Test
    fun `quien no crea interacciones no registra`() {
        val reader = member("READ_ONLY")
        assertThrows<ForbiddenException> { asUser(reader) { interactions.record(entry()) } }
    }
}
