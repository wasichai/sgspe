package sgspe.ai

import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

// una llamada de un caso a su modelo: las variables del prompt (texto en claro, como lo lee quien pide), el diccionario de nombres
// y los valores restringidos que la llamada conoce, la clase de la salida, la referencia de la entrada y el resumen de la salida
// para ai_interaction (sin datos personales). inputField es el campo del 422 (target en POST /ai/runs)
data class ModelCall<T : Any>(
    val useCase: String,
    val variables: Map<String, String>,
    val names: NameDictionary,
    val outputClass: Class<T>,
    val inputReference: String,
    val project: UUID? = null,
    val restricted: List<RestrictedValue> = emptyList(),
    val inputField: String = "target",
    val summary: (T) -> String,
    // el prompt de la llamada: por defecto el del caso; AI-04 de sección usa otro del mismo caso (ai-04-seccion, plan 8.2c)
    val prompt: String = useCase
)

data class ModelCallResult<T : Any>(
    val output: T,
    // la tabla de la llamada: 7.2b pasa la salida a marcadores estables con ella antes de guardar
    val table: PseudonymTable,
    // el prompt tal como salió
    val sent: RenderedPrompt,
    val interaction: UUID,
    // cada variable del prompt ya seudonimizada, tal como la recibió el modelo: CitationCheck busca la cita en la de la fuente y no
    // en el prompt entero, que también lleva las instrucciones (decisión 6 del plan 7.2b)
    val inputs: Map<String, String> = emptyMap(),
    // el modelo que informó el proveedor: el `model` de la sugerencia (RQ-AI-004)
    val model: String = ""
)

// la llamada de un caso a su modelo (ai-architecture.md §6.1, §6.2 y §9.2), la base de cada agente de 7.2b: la entrada pasa el
// límite de tamaño (422 AI_INPUT_TOO_LARGE antes de llamar, sin interacción), se seudonimiza con una tabla nueva, se arma con la
// versión en uso del prompt del caso y se envía con su modelo y su tope de tokens. con éxito o sin él queda su ai_interaction, con
// lo gastado (P6); después, 503 AI_UNAVAILABLE si el proveedor falló o 502 AI_RUN_TRUNCATED si se quedó sin margen. lo que sale
// hacia el modelo es solo el prompt seudonimizado
@Component
class ModelCalls(
    private val model: StructuredModel,
    private val prompts: Prompts,
    private val interactions: InteractionLog,
    private val properties: AiProperties,
    private val clock: Clock
) {
    suspend fun <T : Any> call(call: ModelCall<T>): ModelCallResult<T> {
        val limit = properties.limits.inputChars
        if (call.variables.values.sumOf { it.length } > limit) throw AiErrors.inputTooLarge(call.inputField, limit)
        val table = PseudonymTable()
        val inputs = call.variables.mapValues { (_, text) -> Pseudonymizer.pseudonymize(text, call.names, table, call.restricted) }
        val sent = prompts.render(call.prompt, inputs)
        val modelName = properties.modelFor(call.useCase)
        val started = clock.instant()
        val result =
            try {
                model.complete(ModelRequest(call.useCase, modelName, sent, call.outputClass, properties.limits.outputTokens))
            } catch (e: StructuredModelException) {
                record(call, sent, modelName, started, e.inputTokens, e.outputTokens, failureSummary(e.reason))
                throw if (e.reason == StructuredModelException.Reason.TRUNCATED) AiErrors.truncated() else AiErrors.unavailable(AiErrors.PROVIDER)
            }
        val interaction = record(call, sent, result.model, started, result.inputTokens, result.outputTokens, call.summary(result.output))
        return ModelCallResult(result.output, table, sent, interaction, inputs, result.model)
    }

    private suspend fun record(
        call: ModelCall<*>,
        sent: RenderedPrompt,
        model: String,
        started: Instant,
        inputTokens: Int?,
        outputTokens: Int?,
        summary: String
    ): UUID =
        interactions.record(
            InteractionEntry(
                useCase = call.useCase,
                model = model,
                promptVersion = sent.reference,
                startedAt = started,
                inputReference = call.inputReference,
                outputSummary = summary,
                inputTokens = inputTokens,
                outputTokens = outputTokens,
                latencyMs = Duration.between(started, clock.instant()).toMillis(),
                project = call.project
            )
        )

    companion object {
        fun failureSummary(reason: StructuredModelException.Reason): String =
            when (reason) {
                StructuredModelException.Reason.UNAVAILABLE -> "Llamada fallida: el proveedor no respondió"
                StructuredModelException.Reason.TRUNCATED -> "Llamada truncada: sin margen de tokens o de acciones"
            }
    }
}
