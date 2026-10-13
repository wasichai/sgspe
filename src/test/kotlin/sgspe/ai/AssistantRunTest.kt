package sgspe.ai

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactor.ReactorContext
import kotlinx.coroutines.reactor.asCoroutineContext
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import wasichai.core.common.UnauthorizedException
import java.time.Instant

// la pregunta viaja en la autenticación del contexto de Reactor (decisión 1 del plan 7.2a): AgentService la captura y la lleva a
// las herramientas y a los filtros; el principal sigue siendo el Jwt de quien pregunta
class AssistantRunTest {
    private val run = AssistantRun("¿Qué pidió Ana Ejemplo?", NameDictionary.EMPTY, "ai-05@1", Instant.parse("2026-10-13T15:00:00Z"))

    @Test
    fun `fuera de una pregunta no hay corrida`() {
        assertNull(runBlocking(TestCallers.context()) { AssistantRun.current() })
        assertNull(runBlocking { AssistantRun.current() })
    }

    @Test
    fun `dentro de la pregunta la corrida viaja en la autenticacion, con la misma persona`() {
        val jwt = TestCallers.jwt(email = "cro@sgspe.test")
        val (current, principal) =
            runBlocking(TestCallers.context(jwt)) {
                AssistantRun.bind(run) {
                    AssistantRun.current() to
                        ReactiveSecurityContextHolder
                            .getContext()
                            .awaitFirstOrNull()
                            ?.authentication
                            ?.principal
                }
            }
        assertSame(run, current)
        assertSame(jwt, principal)
    }

    @Test
    fun `el resto del contexto de Reactor se conserva`() {
        val context = ReactiveSecurityContextHolder.withAuthentication(JwtAuthenticationToken(TestCallers.jwt())).put("sgspe.prueba", "valor")
        val kept =
            runBlocking(
                context.asCoroutineContext()
            ) { AssistantRun.bind(run) { currentCoroutineContext()[ReactorContext]!!.context.get<String>("sgspe.prueba") } }
        assertEquals("valor", kept)
    }

    @Test
    fun `sin autenticacion no hay pregunta`() {
        assertThrows<UnauthorizedException> { runBlocking { AssistantRun.bind(run) { } } }
    }

    @Test
    fun `dos preguntas no comparten corrida`() {
        val other = AssistantRun("¿Y la otra?", NameDictionary.EMPTY, "ai-05@1", Instant.parse("2026-10-13T15:00:00Z"))
        val seen =
            runBlocking(TestCallers.context()) {
                listOf(
                    async {
                        AssistantRun.bind(run) {
                            delay(20)
                            AssistantRun.current()
                        }
                    },
                    async { AssistantRun.bind(other) { AssistantRun.current() } }
                ).awaitAll()
            }
        assertSame(run, seen[0])
        assertSame(other, seen[1])
    }

    @Test
    fun `la autenticacion de la pregunta no se imprime con su tabla`() {
        val authentication = QuestionAuthentication(JwtAuthenticationToken(TestCallers.jwt()), run)
        assertFalse("Ana" in authentication.toString())
    }
}
