package sgspe.common

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.MediaType
import org.springframework.http.codec.ServerCodecConfigurer
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import reactor.core.publisher.Mono
import wasichai.core.common.GlobalExceptionHandler
import wasichai.core.common.ValidationException
import java.time.Instant
import java.util.UUID

// api.md §3.6: If-Match con el updatedAt entre comillas; 412 si cambió, 428 si un comando lo exige y no vino
class IfMatchTest {
    private val read = Instant.parse("2026-10-07T14:03:11.482913Z")

    @Test
    fun `lee el instante entre comillas, debil o sin comillas, y asterisco o vacio no comparan`() {
        assertEquals(read, IfMatch.parse("\"2026-10-07T14:03:11.482913Z\""))
        assertEquals(read, IfMatch.parse("W/\"2026-10-07T14:03:11.482913Z\""))
        assertEquals(read, IfMatch.parse("2026-10-07T14:03:11.482913Z"))
        assertNull(IfMatch.parse("*"))
        assertNull(IfMatch.parse(null))
        assertNull(IfMatch.parse(" "))
    }

    @Test
    fun `un valor que no es un instante es un 400 sobre If-Match`() {
        val error = assertThrows<ValidationException> { IfMatch.parse("\"ayer\"") }
        assertEquals("If-Match", error.violations.single().field)
    }

    @Test
    fun `compara instantes, no textos, y un cambio es 412 con el valor actual`() {
        IfMatch.check(read, Instant.parse("2026-10-07T14:03:11.482913000Z"), "El registro")
        val error = assertThrows<PreconditionFailedException> { IfMatch.check(read, Instant.parse("2026-10-07T14:05:42.100371Z"), "El compromiso") }
        assertEquals(412, error.status.value())
        assertEquals("valor actual 2026-10-07T14:05:42.100371Z", error.violations.single().message)
        IfMatch.check(null, Instant.now(), "sin expectativa no compara")
    }

    @Test
    fun `un comando que lo exige sin el es 428`() {
        val error = assertThrows<PreconditionRequiredException> { IfMatch.required(null) }
        assertEquals(428, error.status.value())
        assertEquals(read, IfMatch.required("\"2026-10-07T14:03:11.482913Z\""))
    }

    // ---- Versions: la versión leída vale mientras lo posterior sean saltos de derivados (ADR-0027) ----

    private val derived = Instant.parse("2026-10-07T14:03:11.601877Z")
    private val other = Instant.parse("2026-10-07T14:03:11.655012Z")

    // las cadenas de mentira: desde `read`, un salto de derivados a `derived`; cuenta cuántas veces se le pregunta
    private class FakeChains(
        private val chains: Map<Instant, List<Instant>>
    ) : VersionChains {
        var asked = 0

        override suspend fun accepted(
            objectName: String,
            recordId: UUID,
            expected: Instant
        ): List<Instant> {
            asked++
            return chains[expected] ?: listOf(expected)
        }
    }

    private val id = UUID.fromString("8a0c3c64-1f0e-4a43-9a3e-1d6f2a1b7c55")

    private val handler = GlobalExceptionHandler("https://sgspe.test/problems")

    @Test
    fun `la version vigente pasa sin leer saltos, y una a la que se llega por derivados tambien`() {
        val chains = FakeChains(mapOf(read to listOf(read, derived)))
        val versions = Versions(chains)
        runBlocking { versions.check("engagement", id, read, read, "El registro") }
        assertEquals(0, chains.asked)
        runBlocking { versions.check("engagement", id, read, derived, "El registro") }
        assertEquals(1, chains.asked)
        runBlocking { versions.check("engagement", id, null, other, "sin expectativa no compara") }
    }

    // el fallo E: lo vigente es la escritura de otro, que no es un salto de derivados desde lo leído: 412 con el valor actual,
    // por cerca que haya caído en el tiempo
    @Test
    fun `una version a la que no se llega por derivados es 412 con el valor actual`() {
        val versions = Versions(FakeChains(mapOf(read to listOf(read, derived))))
        val error = assertThrows<PreconditionFailedException> { runBlocking { versions.check("engagement", id, read, other, "La interacción") } }
        assertEquals(412, error.status.value())
        assertEquals("valor actual $other", error.violations.single().message)
        assertThrows<PreconditionFailedException> { runBlocking { versions.check("engagement", id, read, null, "La interacción") } }
    }

    // la guarda usa las versiones que el filtro ya pidió, así compara contra lo mismo que la escritura de wasichai
    @Test
    fun `con las versiones aceptadas ya leidas no vuelve a preguntar`() {
        val chains = FakeChains(emptyMap())
        val versions = Versions(chains)
        runBlocking { versions.check("engagement", id, read, derived, "El registro", accepted = listOf(read, derived)) }
        assertThrows<PreconditionFailedException> {
            runBlocking { versions.check("engagement", id, read, other, "El registro", accepted = listOf(read, derived)) }
        }
        assertEquals(0, chains.asked)
    }

    // ---- IfMatchFilter ----

    // en las escrituras de un registro por la API genérica, el filtro deja la expectativa en el contexto para IfMatchGuard y
    // cambia If-Match por la lista de versiones aceptadas (la leída y las que la siguieron por derivados), así wasichai la
    // compara en el propio UPDATE [WAS-ADR-0051]. un valor que no es un instante, o `*`, sale de la petición: la guarda
    // responde el 400 con Problem Details, o no compara. fuera de esas escrituras no toca nada
    @Test
    fun `el filtro guarda la expectativa y cambia If-Match por las versiones aceptadas solo en las escrituras de un registro`() {
        val sent = "\"2026-10-07T14:03:11.482913Z\""
        val chains = FakeChains(mapOf(read to listOf(read, derived)))

        fun through(request: MockServerHttpRequest): Pair<String?, IfMatchExpectation?> {
            var header: String? = null
            var expectation: IfMatchExpectation? = null
            IfMatchFilter(chains, handler, ServerCodecConfigurer.create())
                .filter(MockServerWebExchange.from(request)) { e ->
                    header = e.request.headers.getFirst(IfMatch.HEADER)
                    Mono.deferContextual { context ->
                        expectation = context.getOrDefault<IfMatchExpectation>(IfMatchFilter.KEY, null)
                        Mono.empty<Void>()
                    }
                }.block()
            return header to expectation
        }

        val accepted = "\"$read\", \"$derived\""
        val expected = IfMatchExpectation("stakeholder", id, sent, listOf(read, derived))
        for (request in listOf(
            MockServerHttpRequest.put("/api/objects/stakeholder/records/$id"),
            MockServerHttpRequest.patch("/api/objects/stakeholder/records/$id"),
            MockServerHttpRequest.delete("/api/objects/stakeholder/records/$id"),
            MockServerHttpRequest.post("/api/objects/stakeholder/records/$id/transitions/approve")
        )) {
            assertEquals(accepted to expected, through(request.header(IfMatch.HEADER, sent).build()))
        }
        // un instante sin comillas o débil vale como en IfMatch.parse: a wasichai le llega la etiqueta fuerte
        val weak = through(MockServerHttpRequest.put("/api/objects/stakeholder/records/$id").header(IfMatch.HEADER, "W/$sent").build())
        assertEquals(accepted, weak.first)
        for (raw in listOf("\"ayer\"", "*")) {
            val put = MockServerHttpRequest.put("/api/objects/stakeholder/records/$id").header(IfMatch.HEADER, raw).build()
            assertEquals(null to IfMatchExpectation("stakeholder", id, raw), through(put))
        }
        val asked = chains.asked
        assertEquals(sent to null, through(MockServerHttpRequest.get("/api/objects/stakeholder/records/$id").header(IfMatch.HEADER, sent).build()))
        assertEquals(sent to null, through(MockServerHttpRequest.post("/api/sgspe/commitments/$id/close").header(IfMatch.HEADER, sent).build()))
        assertEquals(asked, chains.asked)
    }

    // un fallo al leer los saltos (la base) no sale del filtro como un error sin cuerpo: es el 500 de wasichai con su
    // problem+json, y la petición no sigue
    @Test
    fun `un fallo al leer las versiones es el 500 de wasichai en problem+json`() {
        val failing =
            object : VersionChains {
                override suspend fun accepted(
                    objectName: String,
                    recordId: UUID,
                    expected: Instant
                ): List<Instant> = error("la base no responde")
            }
        val exchange =
            MockServerWebExchange.from(
                MockServerHttpRequest.put("/api/objects/stakeholder/records/$id").header(IfMatch.HEADER, "\"$read\"").build()
            )
        var reached = false
        IfMatchFilter(failing, handler, ServerCodecConfigurer.create())
            .filter(exchange) {
                reached = true
                Mono.empty()
            }.block()
        assertFalse(reached)
        assertEquals(500, exchange.response.statusCode?.value())
        assertEquals(MediaType.APPLICATION_PROBLEM_JSON, exchange.response.headers.contentType)
        val body = exchange.response.bodyAsString.block()!!
        assertTrue(body.contains("\"status\":500"), body)
        assertTrue(body.contains("Unexpected error"), body)
    }
}
