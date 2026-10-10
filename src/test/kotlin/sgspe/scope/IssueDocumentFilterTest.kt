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
import wasichai.core.common.ForbiddenException
import wasichai.core.common.GlobalExceptionHandler
import wasichai.core.common.NotFoundException
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
        private val refusal: Finding?,
        private val failure: Exception? = null
    ) : IssueAccess {
        val asked = mutableListOf<IssueTarget>()

        override suspend fun refusal(target: IssueTarget): Finding? {
            asked += target
            if (failure != null) throw failure
            return refusal
        }
    }

    // la lectura de documentos: lanza `failure` si se da; anota lo que se le preguntó
    private class Reads(
        private val failure: Exception? = null
    ) : DocumentReadAccess {
        val asked = mutableListOf<DocumentRead>()

        override suspend fun requireReadable(read: DocumentRead) {
            asked += read
            if (failure != null) throw failure
        }
    }

    private fun filter(
        access: IssueAccess,
        reads: DocumentReadAccess = Reads()
    ) = IssueDocumentFilter(access, reads, GlobalExceptionHandler("https://sgspe.test/problems"), ServerCodecConfigurer.create())

    // el intercambio, el cuerpo y si la cadena siguió
    private fun through(
        access: IssueAccess,
        request: MockServerHttpRequest,
        reads: DocumentReadAccess = Reads()
    ): Triple<MockServerWebExchange, String, Boolean> {
        val exchange = MockServerWebExchange.from(request)
        var passed = false
        filter(access, reads)
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
        // instance, como la rellena WebFlux en el problem+json de un controlador: la ruta de la petición
        assertTrue(body.contains("\"instance\":\"/api/objects/engagement/records/$id/documents/meeting-minutes\""), body)
        assertFalse(passed)
        assertEquals(listOf(IssueTarget("engagement", UUID.fromString(id))), access.asked)
    }

    // un error al decidir (la base, por ejemplo) sale por el manejador de wasichai como problem+json, nunca como el 500 de Boot,
    // y la emisión no llega a wasichai
    @Test
    fun `un error al decidir es el problem+json del manejador de wasichai`() {
        val path = "/api/objects/engagement/records/$id/documents/meeting-minutes"
        val (failed, failedBody, failedPassed) = through(Access(null, IllegalStateException("la base no responde")), MockServerHttpRequest.post(path).build())
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, failed.response.statusCode)
        assertTrue(MediaType.APPLICATION_PROBLEM_JSON.isCompatibleWith(failed.response.headers.contentType), failed.response.headers.toString())
        assertTrue(failedBody.contains("Unexpected error") && !failedBody.contains("la base no responde"), failedBody)
        assertFalse(failedPassed)

        val (forbidden, _, forbiddenPassed) = through(Access(null, ForbiddenException("Missing permission READ")), MockServerHttpRequest.post(path).build())
        assertEquals(HttpStatus.FORBIDDEN, forbidden.response.statusCode)
        assertFalse(forbiddenPassed)
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

    // ---- la lectura de documentos emitidos (UPS-28, ASM-108): sus dos rutas GET, y el 404 de un registro que no se lee ----

    @Test
    fun `las dos lecturas de documentos son rutas del filtro, y la emision y lo demas no`() {
        val record = UUID.fromString(id)
        assertEquals(
            DocumentRead.OfRecord("engagement", record),
            IssueDocumentFilter.readOf(MockServerHttpRequest.get("/api/objects/Engagement/records/$id/documents").build())
        )
        assertEquals(DocumentRead.ById(record), IssueDocumentFilter.readOf(MockServerHttpRequest.get("/api/documents/$id").build()))
        assertNull(IssueDocumentFilter.readOf(MockServerHttpRequest.get("/api/documents/no-es-uuid").build()))
        assertNull(IssueDocumentFilter.readOf(MockServerHttpRequest.post("/api/objects/engagement/records/$id/documents/meeting-minutes").build()))
        assertNull(IssueDocumentFilter.readOf(MockServerHttpRequest.get("/api/objects/engagement/records/$id").build()))
        assertNull(IssueDocumentFilter.readOf(MockServerHttpRequest.get("/api/objects/engagement/document-types").build()))
    }

    @Test
    fun `un documento cuyo registro no se lee es el 404 de wasichai y no llega al controlador`() {
        val reads = Reads(NotFoundException("Record $id does not exist"))
        for (path in listOf("/api/objects/engagement/records/$id/documents", "/api/documents/$id")) {
            val (exchange, body, passed) = through(Access(null), MockServerHttpRequest.get(path).build(), reads)
            assertEquals(HttpStatus.NOT_FOUND, exchange.response.statusCode, body)
            assertTrue(MediaType.APPLICATION_PROBLEM_JSON.isCompatibleWith(exchange.response.headers.contentType), exchange.response.headers.toString())
            assertFalse(passed, path)
        }
        val allowed = Reads()
        assertTrue(through(Access(null), MockServerHttpRequest.get("/api/documents/$id").build(), allowed).third)
        assertEquals(listOf<DocumentRead>(DocumentRead.ById(UUID.fromString(id))), allowed.asked)
    }

    @Test
    fun `emitir sobre un registro que no se lee tambien es 404`() {
        val (exchange, _, passed) =
            through(
                Access(null, NotFoundException("Record $id does not exist")),
                MockServerHttpRequest.post("/api/objects/engagement/records/$id/documents/meeting-minutes").build()
            )
        assertEquals(HttpStatus.NOT_FOUND, exchange.response.statusCode)
        assertFalse(passed)
    }

    // ---- la decisión (P60 y P62): UPDATE, el alcance del registro leído como quien llama y, al final, EDIT_ANY ----

    private fun decide(
        canUpdate: Boolean = true,
        exempt: Boolean = false,
        scope: Resolution? = Resolution("project", setOf(project), false),
        assigned: Boolean = true,
        own: Finding? = null
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
                    { assigned },
                    { own }
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

    // P62: emitir es editar ese registro, así que también pide lo que EditAnyGuard pide para cambiarlo (OwnRecords), después
    // del alcance como en el orden de las guardas (SCOPE antes que EDIT_ANY); ADMIN, exento del alcance, igual pasa por aquí
    @Test
    fun `sin EDIT_ANY sobre un registro ajeno es la negativa de OwnRecords, despues del alcance`() {
        val notOwn = Finding("NOT_OWN_RECORD", "ajeno", status = HttpStatus.FORBIDDEN)
        assertEquals(null to "NOT_OWN_RECORD", decide(own = notOwn))
        assertEquals(null to "NOT_OWN_RECORD", decide(exempt = true, own = notOwn))
        assertEquals("project" to "NOT_ASSIGNED", decide(assigned = false, own = notOwn))
        assertEquals("record" to "ACTION_REQUIRED", decide(canUpdate = false, own = notOwn))
    }

    @Test
    fun `un registro que no se puede leer o sin proyecto resoluble falla cerrado`() {
        assertEquals("record" to "OBJECT_NOT_READABLE", decide(scope = null))
        assertEquals("project" to "NOT_ASSIGNED", decide(scope = Resolution("project", emptySet(), true)))
    }
}
