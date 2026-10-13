package sgspe.ai

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Component
import wasichai.agent.AgentAnswer
import wasichai.agent.AgentProperties
import wasichai.agent.AgentRunListener
import wasichai.agent.AgentUsage
import wasichai.core.identity.AuthenticatedUser
import java.time.Clock
import java.time.Duration
import java.time.Instant

// la ai_interaction de cada pregunta al asistente (ai-architecture.md §8 y §9.2, RF-AIK-009): el modelo y los tokens de
// AgentUsage, la latencia desde que AssistantQuestions abrió la pregunta, la versión del prompt de AI-05 y el SHA-256 de la pregunta
// como input_reference, nunca su texto; también en una corrida truncada o fallida. la escribe Interactions como la persona que
// pregunta. si no puede registrar, la pregunta falla: wasichai no responde sin registro. fuera de una pregunta de AssistantQuestions
// (POST /api/agent/ask está cerrado por AgentHttpGate) lanza: nada corre sin su registro. el log, solo el id
@Component
class InteractionRunListener(
    private val interactions: InteractionLog,
    private val agent: ObjectProvider<AgentProperties>,
    private val clock: Clock
) : AgentRunListener {
    override suspend fun onRun(
        caller: AuthenticatedUser,
        question: String,
        answer: AgentAnswer?,
        usage: AgentUsage?,
        error: Throwable?
    ) {
        val run = AssistantRun.current() ?: throw IllegalStateException("una corrida del asistente fuera de POST /api/sgspe/ai/assistant/questions")
        val id = interactions.record(entry(run, answer, usage, error, clock.instant(), agent.ifAvailable?.model))
        run.interaction = id
        LOG.info("AI-05 registrada en ai_interaction {}", id)
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(InteractionRunListener::class.java)
        private const val UNKNOWN_MODEL = "desconocido"

        fun entry(
            run: AssistantRun,
            answer: AgentAnswer?,
            usage: AgentUsage?,
            error: Throwable?,
            now: Instant,
            configuredModel: String?
        ): InteractionEntry =
            InteractionEntry(
                useCase = AssistantUseCase.USE_CASE,
                model = usage?.model ?: configuredModel ?: UNKNOWN_MODEL,
                promptVersion = run.promptVersion,
                startedAt = run.startedAt,
                inputReference = "sha256:${Hashes.sha256(run.question)}",
                outputSummary = summary(answer, error),
                inputTokens = usage?.inputTokens,
                outputTokens = usage?.outputTokens,
                latencyMs = Duration.between(run.startedAt, now).toMillis()
            )

        // sin datos personales: el desenlace, cuántos pasos y qué herramientas
        fun summary(
            answer: AgentAnswer?,
            error: Throwable?
        ): String {
            if (answer == null) return "Corrida fallida (${error?.javaClass?.simpleName ?: "sin causa"}); sin respuesta"
            val tools =
                answer.steps
                    .map { it.tool }
                    .distinct()
                    .joinToString(", ")
                    .ifEmpty { "ninguna" }
            val prefix = if (answer.truncated) "Corrida truncada tras ${answer.steps.size} pasos" else "Respuesta con ${answer.steps.size} pasos"
            return "$prefix; herramientas: $tools"
        }
    }
}
