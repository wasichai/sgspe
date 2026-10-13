package sgspe.ai

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ServerWebExchange
import sgspe.common.Callers
import sgspe.common.RuleException
import sgspe.common.StrictBody
import sgspe.common.StrictQuery
import wasichai.agent.AgentAnswer
import wasichai.agent.AgentFilterException
import wasichai.agent.AgentService
import wasichai.agent.AgentUnavailableException
import wasichai.core.common.ValidationException
import java.time.Clock
import java.time.Instant
import java.util.UUID

data class AssistantStep(
    val tool: String,
    val input: Map<String, Any?>,
    val summary: String
)

data class AssistantAnswer(
    val answer: String,
    val steps: List<AssistantStep>,
    val truncated: Boolean,
    // el id de la ai_interaction que registra la pregunta
    val interaction: String?
)

// POST /api/sgspe/ai/assistant/questions (api.md §4.14, ai-architecture.md §8, ASM-130): AI-05 por el asistente de solo lectura de
// wasichai-agent, en proceso y como quien pregunta. en este orden (decisión 7 del plan 7.2a): el cuerpo (400) y el largo de la
// pregunta (422 AI_INPUT_TOO_LARGE), la activación de la organización (503 AI_UNAVAILABLE organization), la persona y su permiso
// (403), el proveedor (503 AI_UNAVAILABLE provider). después abre la pregunta (AssistantRun: tabla nueva, el diccionario de nombres
// que quien pregunta lee y la hora), seudonimiza la pregunta, la arma con la versión en uso del prompt de AI-05 y llama a
// AgentService.ask con la pregunta en la autenticación. los filtros de sgspe.ai enmascaran y seudonimizan lo que sale hacia el
// modelo y restauran la respuesta; InteractionRunListener la registra. los pasos vuelven restaurados con la misma tabla, y la
// respuesta de una corrida truncada o vacía, en español. el log solo lleva el id de la ai_interaction
@RestController
@RequestMapping("/api/sgspe/ai/assistant/questions")
class AssistantQuestions(
    private val callers: Callers,
    private val activation: AiActivation,
    private val assistant: AssistantUseCase,
    private val names: PersonNames,
    private val prompts: Prompts,
    private val agent: ObjectProvider<AgentService>,
    private val properties: AiProperties,
    private val clock: Clock
) {
    @PostMapping
    suspend fun ask(
        @RequestBody body: Map<String, Any?>,
        exchange: ServerWebExchange
    ): AssistantAnswer {
        StrictQuery.requireOnly(exchange.request.queryParams, emptySet())
        val question = questionOf(body, properties.limits.questionChars)
        val user = callers.require()
        activation.requireOrganization()
        assistant.require(user)
        activation.requireProvider()
        val service = agent.ifAvailable ?: throw AiErrors.unavailable(AiErrors.PROVIDER)
        // la hora de la pregunta, antes de leer el diccionario de nombres
        val askedAt = clock.instant()
        return answer(service, prompts, question, names.readable(), askedAt)
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(AssistantQuestions::class.java)
        const val QUESTION = "question"
        const val TRUNCATED =
            "Me detuve después de demasiados pasos sin llegar a una respuesta. Pruebe con una pregunta más acotada o nombre el objeto que le interesa."
        const val EMPTY = "No pude armar una respuesta para esa pregunta."

        // la respuesta vacía de WasichaiAgent (wasichai-agent 1.2.0), que llega en inglés
        private const val WASICHAI_EMPTY = "I could not put an answer together for that."
        private const val FILTER_FAILED = "El asistente no pudo preparar los datos para el modelo: no se envió nada"

        // las etiquetas que envuelven la pregunta en el prompt de AI-05, en cualquier forma («</ PREGUNTA >», «<pregunta x="1">» o
        // sin cerrar): escritas en la pregunta, la cerrarían o abrirían otra. una etiqueta entera queda «(pregunta)» o
        // «(/pregunta)»; un comienzo suelto («<preguntame») solo cambia su «<» por «(», sin perder texto
        private val QUESTION_TAG = Regex("""<(\s*/?\s*)pregunta(\b[^<>]*>)?""", RegexOption.IGNORE_CASE)

        // la pregunta ya validada y autorizada: abre la corrida (AssistantRun: tabla nueva, el diccionario de quien pregunta y la
        // hora), la seudonimiza y la arma con el prompt en uso de AI-05, y llama a AgentService.ask con la pregunta en la
        // autenticación. AgentService nunca recibe la pregunta en claro
        suspend fun answer(
            service: AgentService,
            prompts: Prompts,
            question: String,
            dictionary: NameDictionary,
            startedAt: Instant
        ): AssistantAnswer {
            val table = PseudonymTable()
            val prompt = prompt(prompts, question, dictionary, table)
            val run = AssistantRun(question, dictionary, prompt.reference, startedAt, table)
            val answer =
                try {
                    AssistantRun.bind(run) { service.ask(prompt.text) }
                } catch (_: AgentUnavailableException) {
                    throw AiErrors.unavailable(AiErrors.PROVIDER)
                } catch (_: AgentFilterException) {
                    throw RuleException(HttpStatus.INTERNAL_SERVER_ERROR, FILTER_FAILED)
                }
            LOG.info("AI-05 respondió; ai_interaction {}", run.interaction)
            return response(answer, table, run.interaction)
        }

        // el prompt de AI-05 con la pregunta seudonimizada con `table` y sin etiquetas <pregunta> ni </pregunta> propias
        fun prompt(
            prompts: Prompts,
            question: String,
            dictionary: NameDictionary,
            table: PseudonymTable
        ): RenderedPrompt {
            val pseudonymized = Pseudonymizer.pseudonymize(question, dictionary, table)
            val neutral =
                QUESTION_TAG.replace(pseudonymized) { tag ->
                    if (tag.groupValues[2].isNotEmpty()) "(${if ('/' in tag.groupValues[1]) "/" else ""}pregunta)" else "(" + tag.value.substring(1)
                }
            return prompts.render(AssistantUseCase.USE_CASE, mapOf(QUESTION to neutral))
        }

        // 400 si falta, no es texto, está en blanco o el cuerpo trae otra propiedad; 422 AI_INPUT_TOO_LARGE si pasa el tope
        fun questionOf(
            body: Map<String, Any?>,
            limit: Int
        ): String {
            StrictBody.requireOnly(body, setOf(QUESTION))
            val raw = body[QUESTION]
            if (raw != null && raw !is String) throw ValidationException("Cuerpo inválido", QUESTION, "debe ser un texto")
            val question = raw?.trim().orEmpty()
            if (question.isEmpty()) throw ValidationException("Cuerpo inválido", QUESTION, "es obligatoria")
            if (question.length > limit) throw AiErrors.inputTooLarge(QUESTION, limit)
            return question
        }

        fun response(
            answer: AgentAnswer,
            table: PseudonymTable,
            interaction: UUID?
        ): AssistantAnswer =
            AssistantAnswer(
                answer =
                    when {
                        answer.truncated -> TRUNCATED
                        answer.answer == WASICHAI_EMPTY -> EMPTY
                        else -> answer.answer
                    },
                steps = answer.steps.map { step -> AssistantStep(step.tool, step.input.mapValues { restored(it.value, table) }, table.restore(step.summary)) },
                truncated = answer.truncated,
                interaction = interaction?.toString()
            )

        private fun restored(
            node: Any?,
            table: PseudonymTable
        ): Any? =
            when (node) {
                is String -> table.restore(node)
                is Map<*, *> -> node.entries.associate { (k, v) -> k.toString() to restored(v, table) }
                is List<*> -> node.map { restored(it, table) }
                else -> node
            }
    }
}
