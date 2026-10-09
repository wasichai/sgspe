package sgspe.scope

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.codec.ServerCodecConfigurer
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import reactor.core.publisher.Mono
import sgspe.common.Finding
import wasichai.core.common.GlobalExceptionHandler
import java.net.URI
import java.util.UUID

// emitir un documento a mano exige UPDATE sobre el objeto y una asignación vigente al proyecto del registro (security.md §11.4,
// hallazgo I1 de la revisión final del plan 3.1, ruling P60): wasichai-documents 0.6.0 solo pide READ, y cada emisión archiva
// el documento vigente del tipo, también la minuta que AUT-05 congeló. el filtro mira solo POST
// /api/objects/{objeto}/records/{id}/documents/{tipo}; AUT-05 emite sin pasar por HTTP
class IssueDocumentFilterTest {
    private val id = "8a0c3c64-1f0e-4a43-9a3e-1d6f2a1b7c55"
    private val project = UUID.fromString("3f1d2c4b-5a6e-4f70-8a9b-0c1d2e3f4a5b")

    private class Access(
        private val refusal: Finding?
    ) : IssueAccess {
        val asked = mutableListOf<IssueTarget>()

        override suspend fun refusal(target: IssueTarget): Finding? {
            asked += target
            return refusal
        }
    }

    private fun filter(access: IssueAccess) = IssueDocumentFilter(access, GlobalExceptionHandler("https://sgspe.test/problems"), ServerCodecConfigurer.create())

    // el intercambio, el cuerpo y si la cadena siguió
    private fun through(
        access: IssueAccess,
        request: MockServerHttpRequest
    ): Triple<MockServerWebExchange, String, Boolean> {
        val exchange = MockServerWebExchange.from(request)
        var passed = false
        filter(access)
            .filter(exchange) {
                passed = true
                Mono.empty()
            }.block()
        // la respuesta de prueba solo tiene cuerpo si el filtro la escribió
        val body =
            if (passed) {
                ""
            } else {
                exchange.response.bodyAsString
                    .block()
                    .orEmpty()
            }
        return Triple(exchange, body, passed)
    }

    @Test
    fun `solo la emision manual es la ruta del filtro, con el objeto como lo lee wasichai`() {
        fun route(request: MockServerHttpRequest) = IssueDocumentFilter.targetOf(request)

        val target = IssueTarget("engagement", UUID.fromString(id))
        assertEquals(target, route(MockServerHttpRequest.post("/api/objects/engagement/records/$id/documents/meeting-minutes").build()))
        assertEquals(target, route(MockServerHttpRequest.post("/api/objects/Engagement/records/$id/documents/meeting-minutes").build()))
        assertEquals(
            target,
            route(MockServerHttpRequest.method(HttpMethod.POST, URI.create("/api/objects/%65ngagement/records/$id/documents/meeting-minutes")).build())
        )
        // un id que no es UUID no llega a la emisión: el controlador de wasichai responde su 400
        assertNull(route(MockServerHttpRequest.post("/api/objects/engagement/records/no-es-uuid/documents/meeting-minutes").build()))
        assertNull(route(MockServerHttpRequest.get("/api/objects/engagement/records/$id/documents/meeting-minutes").build()))
        assertNull(route(MockServerHttpRequest.post("/api/objects/engagement/records/$id/documents").build()))
        assertNull(route(MockServerHttpRequest.get("/api/objects/engagement/records/$id/documents").build()))
        assertNull(route(MockServerHttpRequest.post("/api/objects/engagement/records/$id/transitions/approve_minutes").build()))
        assertNull(route(MockServerHttpRequest.get("/api/documents/$id").build()))
    }

    @Test
    fun `una negativa es el problem+json de wasichai con su codigo y su campo, y la emision no llega a wasichai`() {
        val access = Access(Finding("NOT_ASSIGNED", "sin asignación", "project", status = HttpStatus.FORBIDDEN))
        val (exchange, body, passed) =
            through(access, MockServerHttpRequest.post("/api/objects/engagement/records/$id/documents/meeting-minutes").build())
        assertEquals(HttpStatus.FORBIDDEN, exchange.response.statusCode)
        assertTrue(MediaType.APPLICATION_PROBLEM_JSON.isCompatibleWith(exchange.response.headers.contentType), exchange.response.headers.toString())
        assertTrue(body.contains("\"NOT_ASSIGNED\"") && body.contains("\"project\""), body)
        assertFalse(passed)
        assertEquals(listOf(IssueTarget("engagement", UUID.fromString(id))), access.asked)
    }

    @Test
    fun `sin negativa pasa, y fuera de la ruta ni se pregunta`() {
        val allowed = Access(null)
        assertTrue(through(allowed, MockServerHttpRequest.post("/api/objects/engagement/records/$id/documents/attendance-list").build()).third)
        assertEquals(1, allowed.asked.size)

        val refused = Access(Finding("ACTION_REQUIRED", "sin UPDATE", "record", status = HttpStatus.FORBIDDEN))
        assertTrue(through(refused, MockServerHttpRequest.get("/api/objects/engagement/records/$id/documents").build()).third)
        assertTrue(through(refused, MockServerHttpRequest.put("/api/objects/engagement/records/$id").build()).third)
        assertEquals(emptyList<IssueTarget>(), refused.asked)
    }

    // ---- la decisión (P60): UPDATE, después el alcance del registro leído como quien llama ----

    private fun decide(
        canUpdate: Boolean = true,
        exempt: Boolean = false,
        scope: Resolution? = Resolution("project", setOf(project), false),
        assigned: Boolean = true
    ): Pair<String?, String>? {
        var scopeRead = false
        val finding =
            runBlocking {
                IssueAccessByScope.decide(
                    "engagement",
                    canUpdate,
                    exempt,
                    {
                        scopeRead = true
                        scope
                    },
                    { assigned }
                )
            }
        if (!canUpdate || exempt) assertFalse(scopeRead, "sin UPDATE o exento no se lee el registro")
        return finding?.let { it.field to it.code }
    }

    @Test
    fun `sin UPDATE sobre el objeto es ACTION_REQUIRED sobre record`() {
        assertEquals("record" to "ACTION_REQUIRED", decide(canUpdate = false))
    }

    @Test
    fun `con UPDATE y asignacion vigente al proyecto del registro pasa, y ADMIN o un objeto sin alcance no la necesitan`() {
        assertNull(decide())
        assertNull(decide(exempt = true, assigned = false))
        // un registro del tenant (una evidencia sin project) no tiene proyecto que exigir, como en ProjectScopeGuard
        assertNull(decide(scope = Resolution("project", emptySet(), false), assigned = false))
    }

    @Test
    fun `sin asignacion al proyecto es NOT_ASSIGNED sobre el campo de alcance`() {
        assertEquals("project" to "NOT_ASSIGNED", decide(assigned = false))
        assertEquals("engagement" to "NOT_ASSIGNED", decide(scope = Resolution("engagement", setOf(project), false), assigned = false))
    }

    @Test
    fun `un registro que no se puede leer o sin proyecto resoluble falla cerrado`() {
        assertEquals("record" to "OBJECT_NOT_READABLE", decide(scope = null))
        assertEquals("project" to "NOT_ASSIGNED", decide(scope = Resolution("project", emptySet(), true)))
    }
}
