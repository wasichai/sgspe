package sgspe.ai

import com.embabel.agent.api.common.ExecutingOperationContext
import com.embabel.agent.api.dsl.agent
import com.embabel.agent.core.AgentPlatform
import com.embabel.agent.core.AgentProcess
import com.embabel.agent.core.Budget
import com.embabel.agent.core.ProcessOptions
import com.embabel.agent.spi.loop.MaxIterationsExceededException
import com.embabel.common.ai.model.LlmOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Component

// StructuredModel sobre la plataforma de agentes de Embabel 1.5.3 (ai-architecture.md §6.2): un proceso sin acciones por
// llamada, como el Ai que Embabel inyecta (InfrastructureInjectionConfiguration), para leer de él los tokens de la llamada
// (llmInvocations). la llamada bloquea, así que corre en Dispatchers.IO, como en wasichai-agent. sin clave no hay plataforma
// (EmbabelGate): UNAVAILABLE
@Component
class EmbabelStructuredModel(
    private val platform: ObjectProvider<AgentPlatform>
) : StructuredModel {
    override suspend fun <T : Any> complete(request: ModelRequest<T>): ModelResult<T> {
        val agentPlatform = platform.ifAvailable ?: throw StructuredModelException(StructuredModelException.Reason.UNAVAILABLE)
        return withContext(Dispatchers.IO) { call(agentPlatform, request) }
    }

    companion object {
        private const val NAME = "sgspe-structured-model"
        private val PLACEHOLDER = agent(name = NAME, description = "Una llamada de salida estructurada de sgspe.ai, sin acciones") {}

        fun <T : Any> call(
            platform: AgentPlatform,
            request: ModelRequest<T>
        ): ModelResult<T> {
            val process = platform.createAgentProcess(PLACEHOLDER, ProcessOptions(budget = Budget(actions = 1)), emptyMap())
            val output =
                try {
                    val runner =
                        ExecutingOperationContext(NAME, process)
                            .ai()
                            .withLlm(LlmOptions.withModel(request.model).withMaxTokens(request.maxOutputTokens))
                    (request.system?.let { runner.withSystemPrompt(it) } ?: runner).createObject(request.prompt.text, request.outputClass)
                } catch (e: MaxIterationsExceededException) {
                    throw failure(StructuredModelException.Reason.TRUNCATED, process, e)
                } catch (e: Exception) {
                    throw failure(StructuredModelException.Reason.UNAVAILABLE, process, e)
                }
            val calls = process.llmInvocations
            return ModelResult(
                output,
                calls
                    .map { it.llmMetadata.name }
                    .distinct()
                    .joinToString(",")
                    .ifEmpty { request.model },
                calls.takeIf { it.isNotEmpty() }?.sumOf { it.usage.promptTokens ?: 0 },
                calls.takeIf { it.isNotEmpty() }?.sumOf { it.usage.completionTokens ?: 0 }
            )
        }

        private fun failure(
            reason: StructuredModelException.Reason,
            process: AgentProcess,
            cause: Exception
        ): StructuredModelException {
            val calls = process.llmInvocations
            return StructuredModelException(
                reason,
                calls.takeIf { it.isNotEmpty() }?.sumOf { it.usage.promptTokens ?: 0 },
                calls.takeIf { it.isNotEmpty() }?.sumOf { it.usage.completionTokens ?: 0 },
                cause
            )
        }
    }
}
