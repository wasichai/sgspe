package sgspe.ai

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ServerWebExchange
import sgspe.common.Callers
import sgspe.common.CommandAnswer
import sgspe.common.Idempotency
import sgspe.common.IdempotencyKey
import sgspe.common.Records
import sgspe.common.StrictBody
import sgspe.common.StrictQuery
import wasichai.core.common.ValidationException
import wasichai.core.data.RecordResponse

// la respuesta de una corrida (api.md §4.14): la ai_interaction de su primera llamada (las de todas sus llamadas en
// `interactions`) y lo que guardó, como lo devuelve la API genérica, con marcadores estables
data class RunAnswer(
    val interaction: String?,
    val interactions: List<String>,
    val suggestions: List<RecordResponse>,
    val candidates: List<RecordResponse>
)

data class RunRequest(
    val useCase: String,
    val target: RunTarget
)

// POST /api/sgspe/ai/runs (api.md §4.14, especificación de F7 §3.4, ai-architecture.md §6.1). en este orden (decisión 4 del plan
// 7.2b): el cuerpo (400), la activación de la organización (503 AI_UNAVAILABLE organization), lo que exige el caso (403: una
// persona, CREATE sobre ai_interaction y sus salidas, READ sobre la fuente, los nombres de person), el proveedor (503 provider).
// después el caso (AiRunCase) lee la fuente como la persona (404, 403 ACTION_REQUIRED, 422 AI_NO_TEXT o AI_INPUT_TOO_LARGE),
// corre su agente con el diccionario de nombres de quien pide y el tope de llamadas (502 AI_RUN_TRUNCATED) y guarda lo validado en
// una transacción (SuggestionWriter). la IA nunca escribe un dato de dominio. cada llamada hecha queda en su ai_interaction, también si la corrida
// falla después (P6); un error antes de llamar no deja ninguna. el log solo lleva conteos y los ids de las ai_interaction
@Service
class AiRuns(
    private val callers: Callers,
    private val activation: AiActivation,
    private val cases: List<AiRunCase>
) {
    suspend fun run(body: Map<String, Any?>): CommandAnswer {
        val request = parse(body, cases.map { it.useCase })
        val case = cases.single { it.useCase == request.useCase }
        if (request.target.objectName !in case.objects) {
            throw ValidationException("Cuerpo inválido", "target.object", "${case.useCase} se pide sobre ${case.objects.sorted().joinToString(", ")}")
        }
        val user = callers.require()
        activation.requireOrganization()
        case.require(user, request.target)
        activation.requireProvider()
        val result = case.run(user, request.target)
        val interactions = result.interactions.map { it.toString() }
        LOG.info(
            "{} guardó {} sugerencias y {} candidatos y descartó {} propuestas; ai_interaction {}",
            case.useCase,
            result.suggestions.size,
            result.candidates.size,
            result.discarded,
            interactions.joinToString(",")
        )
        return CommandAnswer(HttpStatus.CREATED, RunAnswer(result.interaction?.toString(), interactions, result.suggestions, result.candidates))
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(AiRuns::class.java)
        const val USE_CASE = "useCase"
        const val TARGET = "target"

        // 400 si el cuerpo trae otra propiedad, si el caso no es uno de las corridas o si el objetivo no es { object, recordId }
        fun parse(
            body: Map<String, Any?>,
            known: Collection<String>
        ): RunRequest {
            StrictBody.requireOnly(body, setOf(USE_CASE, TARGET))
            val useCase = body[USE_CASE] as? String
            if (useCase == null || useCase !in known) {
                throw ValidationException("Cuerpo inválido", USE_CASE, "se admiten ${known.sorted().joinToString(", ")}")
            }
            val target = StrictBody.objectOf(body, TARGET)
            StrictBody.requireOnly(target, setOf("object", "recordId"), "$TARGET.")
            val objectName =
                (target["object"] as? String)?.trim()?.ifEmpty { null } ?: throw ValidationException("Cuerpo inválido", "$TARGET.object", "es obligatorio")
            val id = Records.uuid(target["recordId"]) ?: throw ValidationException("Cuerpo inválido", "$TARGET.recordId", "debe ser el id de un registro")
            return RunRequest(useCase, RunTarget(objectName, id))
        }
    }
}

// api.md §4.14: la corrida honra Idempotency-Key (RNF-INT-002). con clave corre dentro de la transacción de la clave; sus
// ai_interaction se escriben aparte (Transactions.independent) y quedan aunque la corrida falle. a lo más
// sgspe.ai.limits.max-concurrent-runs a la vez por instancia (RunGate)
@RestController
@RequestMapping("/api/sgspe/ai/runs")
class AiRunsController(
    private val runs: AiRuns,
    private val idempotency: Idempotency,
    private val gate: RunGate
) {
    @PostMapping
    suspend fun run(
        @RequestBody body: Map<String, Any?>,
        @RequestHeader(name = IdempotencyKey.HEADER, required = false) key: String?,
        exchange: ServerWebExchange
    ): ResponseEntity<ByteArray> {
        StrictQuery.requireOnly(exchange.request.queryParams, emptySet())
        // el tope va antes de la clave: con el tope lleno no se abre su transacción ni se toma una conexión (503 AI_BUSY)
        return gate.admit { idempotency.respond(key, "POST", exchange.request.path.value(), body) { runs.run(body) } }
    }
}
