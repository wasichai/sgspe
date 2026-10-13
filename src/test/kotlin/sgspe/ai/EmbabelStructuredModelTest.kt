package sgspe.ai

import com.embabel.agent.core.AgentPlatform
import com.embabel.agent.core.support.InvalidLlmReturnFormatException
import com.embabel.agent.spi.loop.MaxIterationsExceededException
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import org.springframework.beans.factory.support.StaticListableBeanFactory

// una salida estructurada de las pruebas de la IA
data class Proposal(
    val text: String = "",
    val confidence: Double = 0.0
)

// StructuredModel sobre la plataforma de agentes de Embabel 1.5.3 en memoria, con el modelo guionado: la salida tipada, los
// tokens que informó el proveedor y los dos modos de fallar (ai-architecture.md §6.2)
class EmbabelStructuredModelTest {
    private val llm = RecordingLlm()

    private fun request() =
        ModelRequest("PRUEBA", "claude-opus-4-8", RenderedPrompt("prueba", "1", "Proposal", "Texto con [PERSONA_1]"), Proposal::class.java, 2_048)

    @Test
    fun `devuelve la salida tipada con los tokens del proveedor`() {
        llm.script.returnObject(Proposal("Reparar el canal", 0.8))
        val result = EmbabelStructuredModel.call(RecordingLlm.platform(llm), request())
        assertEquals(Proposal("Reparar el canal", 0.8), result.output)
        assertEquals(RecordingLlm.MODEL, result.model)
        assertEquals(RecordingLlm.INPUT, result.inputTokens)
        assertEquals(RecordingLlm.OUTPUT, result.outputTokens)
        assertEquals(listOf("Texto con [PERSONA_1]"), llm.script.promptsReceived)
    }

    @Test
    fun `sin plataforma de agentes el proveedor no esta disponible`() {
        val model = EmbabelStructuredModel(StaticListableBeanFactory().getBeanProvider(AgentPlatform::class.java))
        val failure = assertThrows<StructuredModelException> { runBlocking { model.complete(request()) } }
        assertEquals(StructuredModelException.Reason.UNAVAILABLE, failure.reason)
    }

    @Test
    fun `un proveedor que falla es UNAVAILABLE, con lo gastado y sin su texto`() {
        llm.script.returnObject(Proposal())
        llm.failure = IllegalStateException("proveedor caído: Texto con [PERSONA_1]")
        val failure = assertThrows<StructuredModelException> { EmbabelStructuredModel.call(RecordingLlm.platform(llm), request()) }
        assertEquals(StructuredModelException.Reason.UNAVAILABLE, failure.reason)
        assertEquals(RecordingLlm.INPUT, failure.inputTokens)
        assertFalse("Texto" in failure.message.orEmpty())
        assertNull(failure.cause)
        assertEquals(IllegalStateException::class.java.name, failure.causeType)
        assertFalse("Texto" in failure.causeType.orEmpty())
    }

    @Test
    fun `una plataforma que no crea el proceso es UNAVAILABLE, sin su texto`() {
        val platform = mock(AgentPlatform::class.java) { throw IllegalStateException("sin proceso: Texto con [PERSONA_1]") }
        val failure = assertThrows<StructuredModelException> { EmbabelStructuredModel.call(platform, request()) }
        assertEquals(StructuredModelException.Reason.UNAVAILABLE, failure.reason)
        assertNull(failure.inputTokens)
        assertFalse("Texto" in failure.message.orEmpty())
        assertEquals(IllegalStateException::class.java.name, failure.causeType)
    }

    @Test
    fun `una llamada sin margen es TRUNCATED`() {
        llm.script.returnObject(Proposal())
        llm.failure = MaxIterationsExceededException(20)
        val failure = assertThrows<StructuredModelException> { EmbabelStructuredModel.call(RecordingLlm.platform(llm), request()) }
        assertEquals(StructuredModelException.Reason.TRUNCATED, failure.reason)
        assertEquals(RecordingLlm.INPUT, failure.inputTokens)
        assertEquals(RecordingLlm.OUTPUT, failure.outputTokens)
        assertNull(failure.cause)
    }

    // una salida cortada por el tope de tokens de salida queda como JSON a medias: es una corrida sin margen (502), no un proveedor
    // caído (503) (decisión 2 del plan 7.2b)
    @Test
    fun `una salida cortada por el tope de tokens es TRUNCATED`() {
        llm.script.returnObject(Proposal())
        llm.failure = InvalidLlmReturnFormatException("{\"text\": \"Reparar el ca", Proposal::class.java, IllegalArgumentException("fin inesperado"))
        val failure = assertThrows<StructuredModelException> { EmbabelStructuredModel.call(RecordingLlm.platform(llm), request()) }
        assertEquals(StructuredModelException.Reason.TRUNCATED, failure.reason)
        assertEquals(RecordingLlm.INPUT, failure.inputTokens)
        assertNull(failure.cause)
        assertFalse("Reparar" in failure.message.orEmpty())
    }
}
