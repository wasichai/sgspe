package sgspe.ai

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import wasichai.core.common.RetryLaterException
import java.time.Duration

// el tope de corridas a la vez por instancia (sgspe.ai.limits.max-concurrent-runs): una corrida con Idempotency-Key retiene la
// conexión de la clave mientras llama al modelo y toma otra por ai_interaction, así que pasarlo es 503 AI_BUSY con Retry-After en
// el acto, sin esperar un lugar ni registrar una interacción; el lugar se suelta al terminar, también si la corrida falla
class RunGateTest {
    @Test
    fun `con el tope lleno la corrida es 503 AI_BUSY con Retry-After sin correr`() {
        runBlocking {
            val gate = RunGate(1)
            val started = CompletableDeferred<Unit>()
            val finish = CompletableDeferred<Unit>()
            val first =
                async {
                    gate.admit {
                        started.complete(Unit)
                        finish.await()
                        "primera"
                    }
                }
            started.await()
            var ran = false
            val busy =
                runCatching {
                    gate.admit {
                        ran = true
                        "segunda"
                    }
                }.exceptionOrNull()
            assertTrue(busy is RetryLaterException, "$busy")
            busy as RetryLaterException
            assertEquals(HttpStatus.SERVICE_UNAVAILABLE, busy.status)
            assertEquals("useCase" to "AI_BUSY", busy.violations.single().let { it.field to it.message })
            assertEquals(Duration.ofSeconds(10), busy.retryAfter)
            assertEquals(false, ran)
            finish.complete(Unit)
            assertEquals("primera", first.await())
            assertEquals("tercera", gate.admit { "tercera" })
        }
    }

    @Test
    fun `una corrida que falla suelta su lugar`() {
        runBlocking {
            val gate = RunGate(1)
            val failed = runCatching { gate.admit<String> { throw IllegalStateException("falla") } }
            assertTrue(failed.isFailure)
            assertEquals("despues", gate.admit { "despues" })
        }
    }

    @Test
    fun `varias corridas caben hasta el tope`() {
        runBlocking {
            val gate = RunGate(2)
            val finish = CompletableDeferred<Unit>()
            val started = CompletableDeferred<Unit>()
            val first = async { gate.admit { started.complete(Unit).also { finish.await() } } }
            started.await()
            assertEquals("segunda", gate.admit { "segunda" })
            finish.complete(Unit)
            first.await()
        }
    }
}
