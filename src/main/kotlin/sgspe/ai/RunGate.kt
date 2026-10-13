package sgspe.ai

import kotlinx.coroutines.sync.Semaphore

// el tope de corridas de IA a la vez por instancia (sgspe.ai.limits.max-concurrent-runs, api.md §4.14, ai-architecture.md §11).
// una corrida con Idempotency-Key corre dentro de la transacción de la clave, que retiene su conexión mientras llama al modelo,
// y toma otra conexión (REQUIRES_NEW) por cada ai_interaction: sin tope, unas pocas corridas lentas agotan el pool del resto de
// la API. como ImportGate de las importaciones, pero sin esperar: con el tope lleno la corrida es 503 AI_BUSY con Retry-After en
// el acto, antes de abrir la transacción de la clave y de registrar una interacción. el lugar se suelta al terminar, también si
// la corrida falla
class RunGate(
    maxConcurrent: Int
) {
    private val permits = Semaphore(maxConcurrent)

    suspend fun <T> admit(block: suspend () -> T): T {
        if (!permits.tryAcquire()) throw AiErrors.busy()
        try {
            return block()
        } finally {
            permits.release()
        }
    }
}
