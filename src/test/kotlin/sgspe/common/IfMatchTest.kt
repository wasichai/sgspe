package sgspe.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import wasichai.core.common.ValidationException
import java.time.Instant

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
        assertEquals(true, IfMatch.onlyDerivedSince(created, writes, derived))
        val edited = writes + LaterWrite(created.plusSeconds(3), setOf("title"))
        assertEquals(false, IfMatch.onlyDerivedSince(created, edited, derived))
        assertEquals(false, IfMatch.onlyDerivedSince(created.plusSeconds(9), writes, derived))
        // un If-Match que no es la versión de ninguna escritura (anterior al alta) no pasa
        assertEquals(false, IfMatch.onlyDerivedSince(Instant.parse("2020-01-01T00:00:00Z"), writes, derived))
    }
}
