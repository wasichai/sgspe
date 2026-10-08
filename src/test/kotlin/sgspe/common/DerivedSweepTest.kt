package sgspe.common

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

// la vuelta de JOB-02 con recalculadores falsos: desde cuándo mira y cuándo da la vuelta por buena
class DerivedSweepTest {
    private class MovableClock(
        var now: Instant
    ) : Clock() {
        override fun getZone() = ZoneOffset.UTC

        override fun withZone(zone: java.time.ZoneId?) = this

        override fun instant(): Instant = now
    }

    private class Fake(
        override val sourceObject: String,
        private val failIn: Set<UUID> = emptySet()
    ) : DerivedRecalculator {
        val calls = mutableListOf<Pair<UUID, Instant?>>()

        override suspend fun recalculate(
            organizationId: UUID,
            since: Instant?
        ): Int {
            calls += organizationId to since
            check(organizationId !in failIn) { "falla a propósito" }
            return 1
        }
    }

    private val start = Instant.parse("2026-10-08T10:00:00Z")
    private val clock = MovableClock(start)
    private val orgA = UUID.randomUUID()
    private val orgB = UUID.randomUUID()

    private fun sweep(
        vararg recalculators: DerivedRecalculator,
        organizations: suspend (String) -> List<UUID> = { listOf(orgA, orgB) }
    ) = DerivedSweep(recalculators.toList(), organizations, { _, block -> block() }, clock)

    @Test
    fun `la primera vuelta horaria y la diaria miran todo, la horaria siguiente desde la anterior con traslape`() {
        val fake = Fake("engagement")
        val sweep = sweep(fake)
        runBlocking { sweep.run(full = false) }
        assertEquals(listOf(null, null), fake.calls.map { it.second })
        clock.now = start.plusSeconds(3600)
        runBlocking { sweep.run(full = false) }
        assertEquals(start.minus(Duration.ofMinutes(2)), fake.calls.last().second)
        runBlocking { sweep.run(full = true) }
        assertNull(fake.calls.last().second)
    }

    @Test
    fun `una vuelta con un fallo no avanza lastRun, y una organizacion que falla no detiene a las demas`() {
        val fake = Fake("engagement", failIn = setOf(orgA))
        val sweep = sweep(fake)
        assertEquals(1, runBlocking { sweep.run(full = false) })
        assertEquals(listOf(orgA, orgB), fake.calls.map { it.first })
        assertNull(sweep.lastRun)
        // la siguiente sigue mirando desde el principio (no hubo vuelta buena) y, al salir bien, lastRun avanza
        clock.now = start.plusSeconds(3600)
        val healthy = Fake("engagement")
        val next = sweep(healthy)
        runBlocking { next.run(full = false) }
        assertEquals(clock.now, next.lastRun)
    }

    @Test
    fun `tras un fallo la vuelta siguiente mira desde la ultima buena, nunca desde mas de 25 horas atras`() {
        val failing = mutableSetOf<UUID>()
        val fake = Fake("engagement", failIn = failing)
        val sweep = sweep(fake)
        runBlocking { sweep.run(full = false) }
        assertEquals(start, sweep.lastRun)
        failing += orgA
        clock.now = start.plusSeconds(3600)
        runBlocking { sweep.run(full = false) }
        assertEquals(start, sweep.lastRun)
        assertEquals(start.minus(Duration.ofMinutes(2)), fake.calls.last { it.first == orgA }.second)
        // tres días de fallos: la ventana no pasa de 25 horas
        clock.now = start.plus(Duration.ofDays(3))
        runBlocking { sweep.run(full = false) }
        assertEquals(clock.now.minus(Duration.ofHours(25)), fake.calls.last { it.first == orgA }.second)
        assertEquals(start, sweep.lastRun)
    }

    @Test
    fun `si no se pueden listar las organizaciones de un recalculador los demas corren y la vuelta no se da por buena`() {
        val broken = Fake("engagement")
        val other = Fake("area_of_influence")
        val sweep =
            sweep(broken, other) { objectName ->
                if (objectName == "engagement") error("la metadata no responde") else listOf(orgA)
            }
        assertEquals(1, runBlocking { sweep.run(full = true) })
        assertEquals(emptyList<Pair<UUID, Instant?>>(), broken.calls)
        assertEquals(listOf(orgA), other.calls.map { it.first })
        assertNull(sweep.lastRun)
    }
}
