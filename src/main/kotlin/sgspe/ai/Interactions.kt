package sgspe.ai

import org.springframework.stereotype.Component
import sgspe.common.Callers
import sgspe.common.Records
import sgspe.common.Transactions
import wasichai.core.identity.AuthenticatedUser
import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

// lo que se registra de una llamada a un modelo (ai-architecture.md §9.2, RF-AIK-009, RQ-AUD-003): una referencia a la entrada,
// nunca su contenido; un resumen sin datos personales; el modelo, la versión del prompt, los tokens y la latencia
data class InteractionEntry(
    val useCase: String,
    val model: String,
    val promptVersion: String,
    val startedAt: Instant,
    val inputReference: String?,
    val outputSummary: String,
    val inputTokens: Int?,
    val outputTokens: Int?,
    val latencyMs: Long,
    val confidence: BigDecimal? = null,
    val project: UUID? = null,
    val modelVersion: String? = null
)

// quien registra las llamadas: Interactions en la app; RecordedInteractions en las pruebas unitarias
fun interface InteractionLog {
    suspend fun record(entry: InteractionEntry): UUID
}

// una ai_interaction por llamada, escrita con Records (RecordService con la marca SgspeWrite) como la persona que pidió la llamada,
// o como la plataforma dentro de Records.asPlatform: sin persona, requested_by_* vacíos y run_by_platform verdadero (lo escribe
// sgspe.ai, que es su único escritor). el objeto es apiOnly y appendOnly: la API genérica no lo escribe y nadie lo cambia después.
// una cuenta de servicio no pide llamadas a la IA (403 PERSON_REQUIRED, decisión 8 del plan 7.2a). desde 7.2b se escribe en una
// transacción propia (Transactions.independent): una corrida de POST /ai/runs con Idempotency-Key corre en la transacción de la
// clave, y si falla después de llamar al modelo (502, 503) la llamada hecha queda registrada igual (P6, decisión 3 del plan 7.2b)
@Component
class Interactions(
    private val records: Records,
    private val callers: Callers,
    private val properties: AiProperties,
    private val transactions: Transactions
) : InteractionLog {
    override suspend fun record(entry: InteractionEntry): UUID {
        val requester = callers.userOrNull()
        if (requester?.serviceAccount != null) throw AiErrors.personRequired()
        val created =
            transactions.independent {
                records.create(OBJECT, attributes(entry, properties.provider, requester), "registro de la llamada a la IA (${entry.useCase})")
            }
        return UUID.fromString(created.id)
    }

    companion object {
        const val OBJECT = "ai_interaction"

        fun attributes(
            entry: InteractionEntry,
            provider: String,
            requester: AuthenticatedUser?
        ): Map<String, Any?> =
            mapOf(
                "use_case" to entry.useCase,
                "provider" to provider,
                "model" to entry.model,
                "model_version" to entry.modelVersion,
                "prompt_version" to entry.promptVersion,
                "started_at" to entry.startedAt.truncatedTo(ChronoUnit.SECONDS).toString(),
                "input_reference" to entry.inputReference,
                "output_summary" to entry.outputSummary,
                "input_tokens" to entry.inputTokens,
                "output_tokens" to entry.outputTokens,
                "latency_ms" to entry.latencyMs.coerceIn(0, Int.MAX_VALUE.toLong()).toInt(),
                "confidence" to entry.confidence,
                "project" to entry.project?.toString(),
                "requested_by_user_id" to requester?.userId?.toString(),
                "requested_by_user_email" to requester?.email,
                "run_by_platform" to (requester == null)
            ).filterValues { it != null }
    }
}
