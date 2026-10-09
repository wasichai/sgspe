package sgspe.privacy

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
import wasichai.core.common.GlobalExceptionHandler
import java.net.URI

// emitir un documento a mano exige UPDATE sobre el objeto (security.md §11.4, hallazgo I1 de la revisión final del plan 3.1):
// wasichai-documents 0.6.0 solo pide READ, y cada emisión archiva el documento vigente del tipo, también la minuta que AUT-05
// congeló. el filtro mira solo POST /api/objects/{objeto}/records/{id}/documents/{tipo}; AUT-05 emite sin pasar por HTTP
class IssueDocumentFilterTest {
    private val id = "8a0c3c64-1f0e-4a43-9a3e-1d6f2a1b7c55"

    private class Access(
        private val allowed: Boolean
    ) : IssueAccess {
        val asked = mutableListOf<String>()

        override suspend fun canIssue(objectName: String): Boolean {
            asked += objectName
            return allowed
        }
    }

    private fun filter(access: IssueAccess) = IssueDocumentFilter(access, GlobalExceptionHandler("https://sgspe.test/problems"), ServerCodecConfigurer.create())

    // el estado, el cuerpo y si la cadena siguió
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
        fun route(request: MockServerHttpRequest) = IssueDocumentFilter.objectOf(request)

        assertEquals("engagement", route(MockServerHttpRequest.post("/api/objects/engagement/records/$id/documents/meeting-minutes").build()))
        assertEquals("engagement", route(MockServerHttpRequest.post("/api/objects/Engagement/records/$id/documents/meeting-minutes").build()))
        assertEquals(
            "engagement",
            route(MockServerHttpRequest.method(HttpMethod.POST, URI.create("/api/objects/%65ngagement/records/$id/documents/meeting-minutes")).build())
        )
        assertNull(route(MockServerHttpRequest.get("/api/objects/engagement/records/$id/documents/meeting-minutes").build()))
        assertNull(route(MockServerHttpRequest.post("/api/objects/engagement/records/$id/documents").build()))
        assertNull(route(MockServerHttpRequest.get("/api/objects/engagement/records/$id/documents").build()))
        assertNull(route(MockServerHttpRequest.post("/api/objects/engagement/records/$id/transitions/approve_minutes").build()))
        assertNull(route(MockServerHttpRequest.get("/api/documents/$id").build()))
    }

    @Test
    fun `sin UPDATE sobre el objeto es un 403 ACTION_REQUIRED sobre record y la emision no llega a wasichai`() {
        val access = Access(allowed = false)
        val (exchange, body, passed) =
            through(access, MockServerHttpRequest.post("/api/objects/engagement/records/$id/documents/meeting-minutes").build())
        assertEquals(HttpStatus.FORBIDDEN, exchange.response.statusCode)
        assertTrue(MediaType.APPLICATION_PROBLEM_JSON.isCompatibleWith(exchange.response.headers.contentType), exchange.response.headers.toString())
        assertTrue(body.contains("\"ACTION_REQUIRED\"") && body.contains("\"record\""), body)
        assertFalse(passed)
        assertEquals(listOf("engagement"), access.asked)
    }

    @Test
    fun `con UPDATE pasa, y fuera de la ruta ni se pregunta`() {
        val allowed = Access(allowed = true)
        assertTrue(through(allowed, MockServerHttpRequest.post("/api/objects/engagement/records/$id/documents/attendance-list").build()).third)
        assertEquals(listOf("engagement"), allowed.asked)

        val refused = Access(allowed = false)
        assertTrue(through(refused, MockServerHttpRequest.get("/api/objects/engagement/records/$id/documents").build()).third)
        assertTrue(through(refused, MockServerHttpRequest.put("/api/objects/engagement/records/$id").build()).third)
        assertEquals(emptyList<String>(), refused.asked)
    }
}
