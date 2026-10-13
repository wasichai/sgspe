package sgspe.ai

import com.embabel.agent.api.common.ExecutingOperationContext
import com.embabel.agent.api.dsl.agent
import com.embabel.agent.core.AgentPlatform
import com.embabel.agent.core.AgentProcess
import com.embabel.agent.core.Budget
import com.embabel.agent.core.ProcessOptions
import com.embabel.agent.core.support.InvalidLlmReturnFormatException
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
            // crear el proceso también va dentro: si falla, es UNAVAILABLE como cualquier otra falla y ModelCalls registra la llamada
            var started: AgentProcess? = null
            val (process, output) =
                try {
                    val created = platform.createAgentProcess(PLACEHOLDER, ProcessOptions(budget = Budget(actions = 1)), emptyMap())
                    started = created
                    val runner =
                        ExecutingOperationContext(NAME, created)
                            .ai()
                            .withLlm(LlmOptions.withModel(request.model).withMaxTokens(request.maxOutputTokens))
                    created to (request.system?.let { runner.withSystemPrompt(it) } ?: runner).createObject(request.prompt.text, request.outputClass)
                } catch (e: Exception) {
                    throw failure(if (truncated(e)) StructuredModelException.Reason.TRUNCATED else StructuredModelException.Reason.UNAVAILABLE, started, e)
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

        // sin margen (502 AI_RUN_TRUNCATED, ASM-131): las vueltas de herramientas se acabaron, o la salida no se pudo leer como su
        // clase, que es lo que deja un corte por el tope de tokens de salida (el JSON queda a medias y Embabel lo informa como
        // InvalidLlmReturnFormatException; decisión 2 del plan 7.2b). lo demás es un proveedor que falló (503)
        fun truncated(e: Throwable): Boolean =
            generateSequence(e) { it.cause }.any { it is MaxIterationsExceededException || it is InvalidLlmReturnFormatException }

        private fun failure(
            reason: StructuredModelException.Reason,
            process: AgentProcess?,
            cause: Exception
        ): StructuredModelException {
            val calls = process?.llmInvocations.orEmpty()
            return StructuredModelException(
                reason,
                calls.takeIf { it.isNotEmpty() }?.sumOf { it.usage.promptTokens ?: 0 },
                calls.takeIf { it.isNotEmpty() }?.sumOf { it.usage.completionTokens ?: 0 },
                cause::class.java.name
            )
        }
    }
}
