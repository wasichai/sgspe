package sgspe.ai

// el puerto propio hacia el modelo (ai-architecture.md §6.2 y §15, especificación de F7 §2.2.5): recibe el prompt ya seudonimizado
// con su versión, el modelo y el tope de tokens, y devuelve la salida tipada con los tokens que informó el proveedor.
// EmbabelStructuredModel lo implementa sobre la plataforma de agentes; FakeModel, MockModel y FailingModel lo sustituyen en las
// pruebas
interface StructuredModel {
    suspend fun <T : Any> complete(request: ModelRequest<T>): ModelResult<T>
}

data class ModelRequest<T : Any>(
    val useCase: String,
    val model: String,
    val prompt: RenderedPrompt,
    val outputClass: Class<T>,
    val maxOutputTokens: Int,
    // instrucciones de sistema opcionales; el prompt versionado lleva las suyas en su texto
    val system: String? = null
)

data class ModelResult<T : Any>(
    val output: T,
    // el modelo que informó el proveedor (varios, separados por comas, si hubo más de uno)
    val model: String,
    val inputTokens: Int?,
    val outputTokens: Int?
)

// la llamada no dio salida: el proveedor no está o falló (UNAVAILABLE, 503) o se quedó sin margen (TRUNCATED, 502). lleva los
// tokens que se gastaron, para su ai_interaction. su mensaje es solo el motivo, nunca un texto del proveedor ni del prompt
class StructuredModelException(
    val reason: Reason,
    val inputTokens: Int? = null,
    val outputTokens: Int? = null,
    cause: Throwable? = null
) : RuntimeException(reason.name, cause) {
    enum class Reason { UNAVAILABLE, TRUNCATED }
}
