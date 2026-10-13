package sgspe.ai

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpStatus
import wasichai.core.common.WasichaiException
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID

// la llamada de un caso con FakeModel y MockModel (ai-architecture.md §15, especificación de F7 §5): lo que sale hacia el modelo
// está seudonimizado y nunca lleva un documento, un teléfono ni un correo; la versión del prompt, el modelo y los límites del caso;
// y la ai_interaction de cada llamada hecha, también si falla (P6)
class ModelCallsTest {
    // un reloj que avanza 1,5 s cada vez que se lee: la latencia de una llamada es 1500 ms
    private class StepClock : Clock() {
        private var now = Instant.parse("2026-10-13T15:00:00Z")

        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId): Clock = this

        override fun instant(): Instant = now.also { now = now.plusMillis(1_500) }
    }

    private val ana = UUID.fromString("00000000-0000-0000-0000-0000000000a1")
    private val names = NameDictionary(listOf(KnownPerson(ana, "Ana", "Ejemplo")))
    private val properties =
        AiProperties(
            models = mapOf("prueba" to "modelo-de-prueba"),
            prompts = mapOf("prueba" to "1"),
            limits = AiLimits(inputChars = 300, outputTokens = 1_024)
        )
    private val log = RecordedInteractions()
    private val fixed = FakeModel { Proposal("Reparar el canal de [PERSONA_1]", 0.8) }
    private val source = "Ana Ejemplo (DNI 40123456, celular 987 654 321, ana.ejemplo@sgspe.test) pidió reparar el canal; firmó Rosa Ccori Ejemplo."

    private fun calls(model: StructuredModel) = ModelCalls(model, Prompts(properties), log, properties, StepClock())

    private fun call(text: String = source) =
        ModelCall(
            useCase = "PRUEBA",
            variables = mapOf("text" to text),
            names = names,
            outputClass = Proposal::class.java,
            inputReference = "evidence:00000000-0000-0000-0000-0000000000e1",
            restricted = listOf(RestrictedValue(Marker.PERSONA, "Rosa Ccori Ejemplo")),
            summary = { _: Proposal -> "1 propuesta recibida" }
        )

    @Test
    fun `lo que sale hacia el modelo esta seudonimizado y nunca lleva un documento, un telefono ni un correo`() {
        val mock = MockModel(fixed)
        runBlocking { calls(mock).call(call()) }
        val text =
            mock.sent
                .single()
                .prompt.text
        for (marker in listOf("[PERSONA_1]", "[DOCUMENTO_1]", "[TELEFONO_1]", "[CORREO_1]", "[PERSONA]")) assertTrue(marker in text, "$marker no está en $text")
        for (value in listOf("Ana", "40123456", "987 654 321", "ana.ejemplo@", "Rosa", "Ccori")) assertFalse(value in text, "$value salió hacia el modelo")
    }

    @Test
    fun `la version del prompt, el modelo y el tope de tokens del caso`() {
        val mock = MockModel(fixed)
        runBlocking { calls(mock).call(call()) }
        val request = mock.sent.single()
        assertEquals("prueba@1", request.prompt.reference)
        assertEquals("modelo-de-prueba", request.model)
        assertEquals(1_024, request.maxOutputTokens)
        assertEquals(Proposal::class.java, request.outputClass)
    }

    @Test
    fun `una entrada que pasa el limite no llama al modelo ni deja interaccion`() {
        val mock = MockModel(fixed)
        val failure = assertThrows<WasichaiException> { runBlocking { calls(mock).call(call("a".repeat(301))) } }
        assertEquals(422, failure.status.value())
        assertEquals("target" to "AI_INPUT_TOO_LARGE", failure.violations.single().let { it.field to it.message })
        assertTrue(mock.sent.isEmpty())
        assertTrue(log.entries.isEmpty())
    }

    @Test
    fun `la llamada queda en ai_interaction con sus tokens, su latencia y su version`() {
        val result = runBlocking { calls(fixed).call(call()) }
        val entry = log.entries.single()
        assertEquals("PRUEBA", entry.useCase)
        assertEquals("modelo-de-prueba", entry.model)
        assertEquals("prueba@1", entry.promptVersion)
        assertEquals(Instant.parse("2026-10-13T15:00:00Z"), entry.startedAt)
        assertEquals("evidence:00000000-0000-0000-0000-0000000000e1", entry.inputReference)
        assertEquals("1 propuesta recibida", entry.outputSummary)
        assertEquals(FakeModel.INPUT, entry.inputTokens)
        assertEquals(FakeModel.OUTPUT, entry.outputTokens)
        assertEquals(1_500L, entry.latencyMs)
        assertEquals(Proposal("Reparar el canal de [PERSONA_1]", 0.8), result.output)
        assertEquals("Reparar el canal de [PERSONA:$ana]", result.table.stable(result.output.text))
        assertTrue("[PERSONA_1]" in result.sent.text)
    }

    @Test
    fun `un proveedor que falla responde 503 y deja su interaccion con lo gastado`() {
        val failure = assertThrows<WasichaiException> { runBlocking { calls(FailingModel(StructuredModelException.Reason.UNAVAILABLE)).call(call()) } }
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, failure.status)
        assertEquals("provider" to "AI_UNAVAILABLE", failure.violations.single().let { it.field to it.message })
        val entry = log.entries.single()
        assertEquals(50, entry.inputTokens)
        assertEquals("Llamada fallida: el proveedor no respondió", entry.outputSummary)
    }

    @Test
    fun `una llamada sin margen responde 502 y deja su interaccion`() {
        val failure = assertThrows<WasichaiException> { runBlocking { calls(FailingModel(StructuredModelException.Reason.TRUNCATED)).call(call()) } }
        assertEquals(HttpStatus.BAD_GATEWAY, failure.status)
        assertEquals("useCase" to "AI_RUN_TRUNCATED", failure.violations.single().let { it.field to it.message })
        assertEquals("Llamada truncada: sin margen de tokens o de acciones", log.entries.single().outputSummary)
    }
}
