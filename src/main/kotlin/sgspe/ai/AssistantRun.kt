package sgspe.ai

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactor.ReactorContext
import kotlinx.coroutines.reactor.asCoroutineContext
import kotlinx.coroutines.withContext
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import reactor.util.context.Context
import wasichai.core.common.UnauthorizedException
import java.time.Instant
import java.util.UUID

// una pregunta al asistente (AI-05, ai-architecture.md §8): su tabla de equivalencias, el diccionario de nombres de quien pregunta,
// la versión del prompt, cuándo empezó y la ai_interaction que la registra (la escribe InteractionRunListener). nunca se guarda ni
// se registra, y su toString no dice nada
class AssistantRun(
    val question: String,
    val names: NameDictionary,
    val promptVersion: String,
    val startedAt: Instant,
    val table: PseudonymTable = PseudonymTable()
) {
    @Volatile
    var interaction: UUID? = null

    override fun toString(): String = "AssistantRun($promptVersion)"

    companion object {
        // la pregunta en curso, o null fuera de una: en la corrutina de la petición y en los hilos de Embabel por igual, porque
        // viaja en la autenticación que AgentService captura (decisión 1 del plan 7.2a)
        suspend fun current(): AssistantRun? = (ReactiveSecurityContextHolder.getContext().awaitFirstOrNull()?.authentication as? QuestionAuthentication)?.run

        // corre `block` con la pregunta en la autenticación del contexto de Reactor, que conserva todo lo demás. wasichai-agent corre
        // las herramientas y los filtros con un contexto nuevo que solo lleva la autenticación capturada por AgentService.caller():
        // un elemento del contexto de la corrutina no llegaría al filtro de resultados
        suspend fun <T> bind(
            run: AssistantRun,
            block: suspend () -> T
        ): T {
            val authentication =
                ReactiveSecurityContextHolder.getContext().awaitFirstOrNull()?.authentication
                    ?: throw UnauthorizedException("Authentication required")
            val reactor = currentCoroutineContext()[ReactorContext]?.context ?: Context.empty()
            val tagged = reactor.putAll(ReactiveSecurityContextHolder.withAuthentication(QuestionAuthentication(authentication, run)).readOnly())
            return withContext(tagged.asCoroutineContext()) { block() }
        }
    }
}

// la autenticación de quien pregunta, tal cual (el principal sigue siendo su Jwt: CurrentUser y los permisos no cambian), con la
// pregunta en curso. solo AssistantRun la pone en el contexto (ArchitectureTest)
class QuestionAuthentication(
    private val delegate: Authentication,
    val run: AssistantRun
) : Authentication by delegate {
    override fun toString(): String = "QuestionAuthentication(${delegate.name})"
}
