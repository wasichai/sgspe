package sgspe.common

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import wasichai.core.common.ConflictException
import wasichai.core.common.FieldViolation
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

    // un registro de mentira: su versión, sus campos y lo que se le escribió. staleAttempts fuerza que las primeras
    // escrituras condicionadas pierdan contra otra, como si alguien hubiera escrito entre la lectura y el UPDATE; esa otra
    // escritura deja su nombre en `fields`
    private class FakeRecord(
        var version: Instant,
        var staleAttempts: Int = 0,
        val next: MutableList<Instant>
    ) {
        val writes = mutableListOf<Instant?>()
        val fields = mutableMapOf<String, Any?>("name" to "de A", "calculated_area_ha" to 1)

        // la mezcla de wasichai: solo las claves enviadas, condicionada a `expected` si viene
        fun patch(
            expected: Instant?,
            changes: Map<String, Any?>
        ): Instant {
            writes += expected
            if (expected != null && staleAttempts > 0) {
                staleAttempts--
                otherWrites()
                throw stale()
            }
            if (expected != null && expected != version) throw stale()
            fields += changes
            version = next.removeAt(0)
            return version
        }

        // B escribe su nombre y mueve la versión
        fun otherWrites() {
            fields["name"] = "de B ${writes.size}"
            version = next.removeAt(0)
        }

        fun stale() = StaleWrite("Record r changed since you read it", listOf(FieldViolation("If-Match", "does not match")))
    }

    private val area = mapOf("calculated_area_ha" to 2)

    private fun conditioned(
        record: FakeRecord,
        jumps: MutableList<DerivedJump>,
        beforeFallback: () -> Unit = {}
    ): Instant =
        runBlocking {
            DerivedWrite.conditioned(
                stored = { record.version },
                versionOf = { it },
                write = { _, expected -> record.patch(expected, area) },
                writtenVersion = { it },
                record = { jumps += it },
                fallback = {
                    beforeFallback()
                    record.patch(null, area)
                },
                stale = { it.violations.any { v -> v.field == "If-Match" } }
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
        assertEquals("de B 1", record.fields["name"])
        assertEquals(listOf(v0), DerivedChain.from(v0, jumps), "quien leyó v0 no alcanza la versión de B")
    }

    // P61: con un registro que no deja de cambiar, el derivado se escribe igual, sin condición y sin salto (quien tenga una
    // versión anterior recibe 412), y como mezcla de solo los derivados: B escribe otra vez justo antes, y su nombre queda
    @Test
    fun `tras los intentos escribe solo los derivados sin condicion, no registra salto y no pisa a B`() {
        val record = FakeRecord(v0, staleAttempts = DerivedWrite.ATTEMPTS, next = mutableListOf(v1, v2, v3, v3.plusSeconds(1), v3.plusSeconds(2)))
        val jumps = mutableListOf<DerivedJump>()
        assertEquals(v3.plusSeconds(2), conditioned(record, jumps, beforeFallback = { record.otherWrites() }))
        assertEquals(DerivedWrite.ATTEMPTS + 1, record.writes.size)
        assertEquals(null, record.writes.last())
        assertTrue(jumps.isEmpty())
        assertEquals("de B 3", record.fields["name"])
        assertEquals(2, record.fields["calculated_area_ha"])
    }

    // un 412 que no es el de este UPDATE (por ejemplo, el de un listener de la propia escritura) sale tal cual
    @Test
    fun `un 412 que no es el de la comparacion propia sale tal cual`() {
        val jumps = mutableListOf<DerivedJump>()
        assertThrows<StaleWrite> {
            runBlocking {
                DerivedWrite.conditioned<Instant, Instant>(
                    stored = { v0 },
                    versionOf = { it },
                    write = { _, _ -> throw StaleWrite("otro registro") },
                    writtenVersion = { it },
                    record = { jumps += it },
                    fallback = { error("no debe caer") },
                    stale = { it.violations.any { v -> v.field == "If-Match" } }
                )
            }
        }
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
                    record = { jumps += it },
                    fallback = { error("no debe caer") }
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
                DerivedWrite.conditioned<Instant?, Instant>(
                    stored = { null },
                    versionOf = { it },
                    write = { _, _ -> error("sin versión no se condiciona") },
                    writtenVersion = { it },
                    record = { jumps += it },
                    fallback = { v1 }
                )
            }
        assertEquals(v1, written)
        assertTrue(jumps.isEmpty())
    }
}
