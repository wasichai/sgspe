package sgspe.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import reactor.core.publisher.Mono
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

    @Test
    fun `despues de la version leida, solo derivados y vinculos no invalidan el If-Match`() {
        val created = Instant.parse("2026-10-07T14:00:00Z")
        val derived = setOf("attendees_total_count")
        val writes =
            listOf(
                LaterWrite(created.plusMillis(1), setOf("title", "engagement_type")),
                LaterWrite(created.plusSeconds(1), setOf("rel:engagement_topic")),
                LaterWrite(created.plusSeconds(2), setOf("attendees_total_count"))
            )
        assertTrue(IfMatch.onlyDerivedSince(created, writes, derived))
        val edited = writes + LaterWrite(created.plusSeconds(3), setOf("title"))
        assertFalse(IfMatch.onlyDerivedSince(created, edited, derived))
        assertFalse(IfMatch.onlyDerivedSince(created.plusSeconds(9), writes, derived))
        // un If-Match que no es la versión de ninguna escritura (anterior al alta) no pasa
        assertFalse(IfMatch.onlyDerivedSince(Instant.parse("2020-01-01T00:00:00Z"), writes, derived))
    }

    @Test
    fun `dos escrituras del mismo instante son la version en cualquier orden`() {
        val created = Instant.parse("2026-10-07T14:00:00Z")
        val derived = setOf("attendees_total_count")
        val own = LaterWrite(created, setOf("title"))
        val listener = LaterWrite(created, setOf("attendees_total_count"))
        assertTrue(IfMatch.onlyDerivedSince(created, listOf(own, listener), derived))
        assertTrue(IfMatch.onlyDerivedSince(created, listOf(listener, own), derived))
        val other = LaterWrite(created.plusSeconds(1), setOf("title"))
        assertFalse(IfMatch.onlyDerivedSince(created, listOf(own, listener, other), derived))
        assertFalse(IfMatch.onlyDerivedSince(created, listOf(listener, own, other), derived))
    }

    @Test
    fun `la version debe caer a lo sumo 2 segundos despues de lo leido`() {
        val read = Instant.parse("2026-10-07T14:00:00Z")
        val derived = setOf("attendees_total_count")
        val later = LaterWrite(read.plusSeconds(5), setOf("attendees_total_count"))
        assertTrue(IfMatch.onlyDerivedSince(read, listOf(LaterWrite(read.plusSeconds(2), setOf("title")), later), derived))
        assertFalse(IfMatch.onlyDerivedSince(read, listOf(LaterWrite(read.plusSeconds(2).plusNanos(1), setOf("title")), later), derived))
        assertFalse(IfMatch.onlyDerivedSince(read, listOf(later), derived))
        assertFalse(IfMatch.onlyDerivedSince(read, emptyList(), derived))
    }

    // desde wasichai 0.5.0 la entrada de una escritura dentro de una transacción cae antes de su updatedAt, y la de una fuera
    // de ella, después [WAS-ADR-0051]: la versión es la entrada más cercana a lo leído
    @Test
    fun `la version es la entrada mas cercana a lo leido, antes o despues`() {
        val read = Instant.parse("2026-10-07T14:00:00.500Z")
        val derived = setOf("calculated_area_ha")
        // lo leído es el área que escribió un listener en su transacción, 3 ms después de abrirla; luego otro cambió la
        // geometría por la API genérica y el listener volvió a escribir el área
        val listener = LaterWrite(read.minusMillis(3), setOf("calculated_area_ha"))
        val geometry = LaterWrite(read.plusMillis(105), emptySet())
        val area = LaterWrite(read.plusMillis(110), setOf("calculated_area_ha"))
        assertFalse(IfMatch.onlyDerivedSince(read, listOf(area, geometry, listener), derived))
        // lo leído es la respuesta del cambio de geometría del propio cliente, cuya entrada cae 2 ms después; antes quedó el
        // área del alta y después, la que recalculó el listener
        val created = LaterWrite(read.minusMillis(120), setOf("calculated_area_ha"))
        val own = LaterWrite(read.plusMillis(2), emptySet())
        val recalculated = LaterWrite(read.plusMillis(9), setOf("calculated_area_ha"))
        assertTrue(IfMatch.onlyDerivedSince(read, listOf(recalculated, own, created), derived))
        // a igual distancia gana la de antes, y la de después cuenta como posterior
        assertFalse(
            IfMatch.onlyDerivedSince(read, listOf(LaterWrite(read.minusMillis(4), setOf("title")), LaterWrite(read.plusMillis(4), emptySet())), derived)
        )
    }

    @Test
    fun `mas de 50 escrituras posteriores de solo derivados y vinculos no invalidan el If-Match`() {
        val created = Instant.parse("2026-10-07T14:00:00Z")
        val derived = setOf("attendees_total_count")
        val version = LaterWrite(created, setOf("title"))
        val later =
            (1..120).map { n ->
                LaterWrite(created.plusSeconds(n.toLong()), if (n % 2 == 0) setOf("attendees_total_count") else setOf("rel:engagement_topic"))
            }
        assertTrue(IfMatch.onlyDerivedSince(created, listOf(version) + later, derived))
        // el historial llega del más nuevo al más viejo
        assertTrue(IfMatch.onlyDerivedSince(created, (later + version).sortedByDescending { it.occurredAt }, derived))
        // una edición visible entre tantas sigue invalidando
        val edited = later + LaterWrite(created.plusSeconds(60).plusMillis(1), setOf("title"))
        assertFalse(IfMatch.onlyDerivedSince(created, listOf(version) + edited, derived))
    }

    @Test
    fun `una escritura sin campos visibles no se tolera`() {
        val created = Instant.parse("2026-10-07T14:00:00Z")
        val derived = setOf("attendees_total_count")
        val version = LaterWrite(created, setOf("title"))
        assertFalse(IfMatch.onlyDerivedSince(created, listOf(version, LaterWrite(created.plusSeconds(1), emptySet())), derived))
        assertFalse(
            IfMatch.onlyDerivedSince(
                created,
                listOf(version, LaterWrite(created.plusSeconds(1), setOf("attendees_total_count")), LaterWrite(created.plusSeconds(2), emptySet())),
                derived
            )
        )
        // una entrada de solo vínculos sí tiene campos: no es un conflicto
        assertTrue(IfMatch.onlyDerivedSince(created, listOf(version, LaterWrite(created.plusSeconds(1), setOf("rel:engagement_topic"))), derived))
    }

    // el filtro deja la expectativa en el contexto para IfMatchGuard y quita la cabecera de la petición que sigue, para que
    // la comparación estricta de wasichai 0.5.0 [WAS-ADR-0051] no pise la de sgspe; fuera de esas escrituras no toca nada
    @Test
    fun `el filtro guarda la expectativa y quita If-Match solo en las escrituras de un registro`() {
        val id = "8a0c3c64-1f0e-4a43-9a3e-1d6f2a1b7c55"
        val sent = "\"2026-10-07T14:03:11.482913Z\""

        fun through(request: MockServerHttpRequest): Pair<String?, IfMatchExpectation?> {
            var header: String? = null
            var expectation: IfMatchExpectation? = null
            IfMatchFilter()
                .filter(MockServerWebExchange.from(request)) { e ->
                    header = e.request.headers.getFirst(IfMatch.HEADER)
                    Mono.deferContextual { context ->
                        expectation = context.getOrDefault<IfMatchExpectation>(IfMatchFilter.KEY, null)
                        Mono.empty<Void>()
                    }
                }.block()
            return header to expectation
        }

        val expected = IfMatchExpectation("stakeholder", UUID.fromString(id), sent)
        for (request in listOf(
            MockServerHttpRequest.put("/api/objects/stakeholder/records/$id"),
            MockServerHttpRequest.patch("/api/objects/stakeholder/records/$id"),
            MockServerHttpRequest.delete("/api/objects/stakeholder/records/$id"),
            MockServerHttpRequest.post("/api/objects/stakeholder/records/$id/transitions/approve")
        )) {
            assertEquals(null to expected, through(request.header(IfMatch.HEADER, sent).build()))
        }
        assertEquals(sent to null, through(MockServerHttpRequest.get("/api/objects/stakeholder/records/$id").header(IfMatch.HEADER, sent).build()))
        assertEquals(sent to null, through(MockServerHttpRequest.post("/api/sgspe/commitments/$id/close").header(IfMatch.HEADER, sent).build()))
    }
}
