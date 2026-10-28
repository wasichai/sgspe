package sgspe.ai

import com.embabel.agent.api.annotation.support.AgentMetadataReader
import com.embabel.agent.api.event.LlmRequestEvent
import com.embabel.agent.core.Action
import com.embabel.agent.core.AgentPlatform
import com.embabel.agent.core.AgentProcess
import com.embabel.agent.core.LlmInvocation
import com.embabel.agent.core.Usage
import com.embabel.agent.core.internal.LlmOperations
import com.embabel.agent.core.support.LlmInteraction
import com.embabel.agent.test.integration.IntegrationTestUtils
import com.embabel.agent.test.integration.ScriptedLlmOperations
import com.embabel.chat.Message
import com.embabel.common.ai.model.LlmMetadata
import com.embabel.common.ai.model.LlmOptions
import com.embabel.common.core.thinking.ThinkingResponse
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger

// el modelo de las pruebas que llegan hasta Embabel (el StubModel de ai-architecture.md §15): ScriptedLlmOperations de Embabel
// 1.5.4, que viene en su jar principal, detrás de un grabador que cuenta las llamadas, anota los tokens que informaría un
// proveedor (ScriptedLlmOperations no anota ninguno) y puede fallar como uno, después de anotarlos. la plataforma resuelve
// LlmOperations una vez al arrancar y un guion se gasta: en una prueba de integración el bean es un conmutador y cada prueba
// cuelga un guion nuevo con fresh(). como AgentExtensionsTest y AgentEmbabelTest de wasichai 1.2.0
class RecordingLlm : LlmOperations {
    @Volatile
    var script: ScriptedLlmOperations = ScriptedLlmOperations()
        private set

    val calls = AtomicInteger()

    @Volatile
    var failure: RuntimeException? = null

    // las opciones de la última llamada, para ver qué recibiría el proveedor
    @Volatile
    var options: LlmOptions? = null
        private set

    fun fresh(): ScriptedLlmOperations {
        script = ScriptedLlmOperations()
        calls.set(0)
        failure = null
        options = null
        return script
    }

    private fun book(
        process: AgentProcess,
        interaction: LlmInteraction
    ) {
        calls.incrementAndGet()
        options = interaction.llm
        process.recordLlmInvocation(
            LlmInvocation(LlmMetadata.create(MODEL, "anthropic"), Usage(INPUT, OUTPUT, null), "sgspe", Instant.now(), Duration.ZERO)
        )
        failure?.let { throw it }
    }

    override fun supportsThinking(options: LlmOptions): Boolean = script.supportsThinking(options)

    override fun <O> createObject(
        messages: List<Message>,
        interaction: LlmInteraction,
        outputClass: Class<O>,
        agentProcess: AgentProcess,
        action: Action?
    ): O {
        book(agentProcess, interaction)
        return script.createObject(messages, interaction, outputClass, agentProcess, action)
    }

    override fun <O> createObjectIfPossible(
        messages: List<Message>,
        interaction: LlmInteraction,
        outputClass: Class<O>,
        agentProcess: AgentProcess,
        action: Action?
    ): Result<O> {
        book(agentProcess, interaction)
        return script.createObjectIfPossible(messages, interaction, outputClass, agentProcess, action)
    }

    override fun <O> doTransform(
        messages: List<Message>,
        interaction: LlmInteraction,
        outputClass: Class<O>,
        llmRequestEvent: LlmRequestEvent<O>?
    ): O = script.doTransform(messages, interaction, outputClass, llmRequestEvent)

    override fun <O> createObjectWithThinking(
        messages: List<Message>,
        interaction: LlmInteraction,
        outputClass: Class<O>,
        agentProcess: AgentProcess,
        action: Action?
    ): ThinkingResponse<O> {
        book(agentProcess, interaction)
        return script.createObjectWithThinking(messages, interaction, outputClass, agentProcess, action)
    }

    override fun <O> createObjectIfPossibleWithThinking(
        messages: List<Message>,
        interaction: LlmInteraction,
        outputClass: Class<O>,
        agentProcess: AgentProcess,
        action: Action?
    ): Result<ThinkingResponse<O>> {
        book(agentProcess, interaction)
        return script.createObjectIfPossibleWithThinking(messages, interaction, outputClass, agentProcess, action)
    }

    override fun <O> doTransformWithThinking(
        messages: List<Message>,
        interaction: LlmInteraction,
        outputClass: Class<O>,
        llmRequestEvent: LlmRequestEvent<O>?
    ): ThinkingResponse<O> = script.doTransformWithThinking(messages, interaction, outputClass, llmRequestEvent)

    override fun <O> doTransformWithThinkingIfPossible(
        messages: List<Message>,
        interaction: LlmInteraction,
        outputClass: Class<O>,
        llmRequestEvent: LlmRequestEvent<O>?
    ): Result<ThinkingResponse<O>> = script.doTransformWithThinkingIfPossible(messages, interaction, outputClass, llmRequestEvent)

    // el modelo de un contexto de integración con plataforma de agentes (AssistantRunApiTest): @Primary gana al de Anthropic
    @TestConfiguration
    class Config {
        @Bean
        @Primary
        fun recordingLlm(): RecordingLlm = RecordingLlm()
    }

    companion object {
        const val MODEL = "claude-haiku-5-5"
        const val INPUT = 100
        const val OUTPUT = 20

        // la plataforma de agentes de Embabel en memoria con este modelo, sin Spring, con los agentes dados desplegados
        fun platform(
            llm: LlmOperations,
            vararg agents: Any
        ): AgentPlatform =
            IntegrationTestUtils.dummyAgentPlatform(llm).also { platform ->
                agents.forEach { platform.deploy(AgentMetadataReader().createAgentMetadata(it)!!) }
            }
    }
}
