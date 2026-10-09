package sgspe.common

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import wasichai.core.common.ConflictException
import java.time.Instant
import wasichai.core.common.PreconditionFailedException as StaleWrite

// ADR-0027: la versión que leyó el cliente vale mientras lo escrito después sean solo derivados, y eso se sabe por los
// saltos exactos que deja cada escritura de derivados (de qué updatedAt a qué updatedAt), no por la hora de la auditoría
class DerivedVersionsTest {
    private val v0 = Instant.parse("2026-10-09T12:13:36.096978Z")
    private val v1 = Instant.parse("2026-10-09T12:13:36.334189Z")
    private val v2 = Instant.parse("2026-10-09T12:13:36.391002Z")
    private val v3 = Instant.parse("2026-10-09T12:13:37.004417Z")

    @Test
    fun `sin saltos solo vale la version leida`() {
        assertEquals(listOf(v0), DerivedChain.from(v0, emptyList()))
    }

    @Test
    fun `la cadena sigue los saltos de derivados desde la version leida, en orden y sin importar el orden de las filas`() {
        val jumps = listOf(DerivedJump(v1, v2), DerivedJump(v0, v1))
        assertEquals(listOf(v0, v1, v2), DerivedChain.from(v0, jumps))
        assertEquals(listOf(v1, v2), DerivedChain.from(v1, jumps))
        assertEquals(listOf(v2), DerivedChain.from(v2, jumps))
    }

    // el fallo E de la CI: el listener del alta deja v0 -> v1 y B escribe v1 -> v2 sin salto. quien leyó v0 no alcanza v2,
    // por cerca que caigan las horas; y un derivado posterior a B empieza en v2, así que no reconecta la cadena
    @Test
    fun `la escritura de otro corta la cadena aunque caiga a milisegundos del derivado`() {
        assertFalse(v2 in DerivedChain.from(v0, listOf(DerivedJump(v0, v1))))
        assertEquals(listOf(v0, v1), DerivedChain.from(v0, listOf(DerivedJump(v0, v1), DerivedJump(v2, v3))))
    }

    // un If-Match que no es el origen de ningún salto (otro instante, aunque sea un microsegundo después) no hereda nada
    @Test
    fun `solo cuenta el instante exacto de la version leida`() {
        val near = v0.plusNanos(1_000)
        assertEquals(listOf(near), DerivedChain.from(near, listOf(DerivedJump(v0, v1))))
    }

    @Test
    fun `el tope corta la cadena y lo que queda fuera no vale`() {
        val jumps = (0L until 10L).map { DerivedJump(v0.plusSeconds(it), v0.plusSeconds(it + 1)) }
        val chain = DerivedChain.from(v0, jumps, max = 3)
        assertEquals((0L..3L).map { v0.plusSeconds(it) }, chain)
        assertFalse(v0.plusSeconds(4) in chain)
        assertEquals(11, DerivedChain.from(v0, jumps).size)
    }

    @Test
    fun `un ciclo, que con relojes que avanzan no puede darse, no cuelga la vuelta`() {
        assertEquals(listOf(v0, v1), DerivedChain.from(v0, listOf(DerivedJump(v0, v1), DerivedJump(v1, v0))))
    }

    // ---- la escritura de derivados condicionada a la versión que leyó ----

    // un registro de mentira: su versión y lo que se le escribió; stale fuerza que las primeras escrituras condicionadas
    // pierdan contra otra, como si alguien hubiera escrito entre la lectura y el UPDATE
    private class FakeRecord(
        var version: Instant,
        var staleAttempts: Int = 0,
        val next: MutableList<Instant>
    ) {
        val writes = mutableListOf<Instant?>()

        fun write(expected: Instant?): Instant {
            writes += expected
            if (expected != null && staleAttempts > 0) {
                staleAttempts--
                // otro escribió antes: la versión cambió y el UPDATE condicionado no encuentra fila
                version = next.removeAt(0)
                throw StaleWrite("Record changed since you read it")
            }
            if (expected != null && expected != version) throw StaleWrite("Record changed since you read it")
            version = next.removeAt(0)
            return version
        }
    }

    private fun conditioned(
        record: FakeRecord,
        jumps: MutableList<DerivedJump>
    ): Instant =
        runBlocking {
            DerivedWrite.conditioned(
                stored = { record.version },
                versionOf = { it },
                write = { _, expected -> record.write(expected) },
                writtenVersion = { it },
                record = { jumps += it }
            )
        }

    @Test
    fun `escribe condicionada a la version que leyo y registra el salto que produjo`() {
        val record = FakeRecord(v0, next = mutableListOf(v1))
        val jumps = mutableListOf<DerivedJump>()
        assertEquals(v1, conditioned(record, jumps))
        assertEquals(listOf<Instant?>(v0), record.writes)
        assertEquals(listOf(DerivedJump(v0, v1)), jumps)
    }

    // B escribió entre la lectura del derivado y su UPDATE: el derivado no pisa a B ni registra v0 -> algo; vuelve a leer y
    // su salto sale de la versión de B
    @Test
    fun `si otra escritura gano, vuelve a leer y el salto sale de la nueva lectura`() {
        val record = FakeRecord(v0, staleAttempts = 1, next = mutableListOf(v1, v2))
        val jumps = mutableListOf<DerivedJump>()
        assertEquals(v2, conditioned(record, jumps))
        assertEquals(listOf<Instant?>(v0, v1), record.writes)
        assertEquals(listOf(DerivedJump(v1, v2)), jumps)
        assertTrue(DerivedChain.from(v0, jumps) == listOf(v0), "quien leyó v0 no alcanza la versión de B")
    }

    // con un registro que no deja de cambiar, el derivado se escribe igual, como antes, pero sin salto: quien tenga una
    // versión anterior recibe 412 y recarga (falla cerrado)
    @Test
    fun `tras los intentos escribe sin condicion y no registra salto`() {
        val record = FakeRecord(v0, staleAttempts = DerivedWrite.ATTEMPTS, next = mutableListOf(v1, v2, v3, v3.plusSeconds(1)))
        val jumps = mutableListOf<DerivedJump>()
        assertEquals(v3.plusSeconds(1), conditioned(record, jumps))
        assertEquals(DerivedWrite.ATTEMPTS + 1, record.writes.size)
        assertEquals(null, record.writes.last())
        assertTrue(jumps.isEmpty())
    }

    @Test
    fun `otro error sale tal cual y no registra nada`() {
        val jumps = mutableListOf<DerivedJump>()
        assertThrows<ConflictException> {
            runBlocking {
                DerivedWrite.conditioned<Instant, Instant>(
                    stored = { v0 },
                    versionOf = { it },
                    write = { _, _ -> throw ConflictException("appendOnly") },
                    writtenVersion = { it },
                    record = { jumps += it }
                )
            }
        }
        assertTrue(jumps.isEmpty())
    }

    // sin updatedAt leído (no debería pasar) no hay de dónde condicionar ni salto que registrar
    @Test
    fun `sin version leida escribe sin condicion y sin salto`() {
        val jumps = mutableListOf<DerivedJump>()
        val written =
            runBlocking {
                DerivedWrite.conditioned(
                    stored = { null as Instant? },
                    versionOf = { it },
                    write = { _, expected ->
                        assertEquals(null, expected)
                        v1
                    },
                    writtenVersion = { it },
                    record = { jumps += it }
                )
            }
        assertEquals(v1, written)
        assertTrue(jumps.isEmpty())
    }
}
