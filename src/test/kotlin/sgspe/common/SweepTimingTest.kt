package sgspe.common

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.slf4j.Logger
import java.util.UUID

// una línea de log por organización con su duración (especificación 4.4 §3.10), también cuando la organización falla
class SweepTimingTest {
    private val organization = UUID.fromString("00000000-0000-0000-0000-0000000000a0")

    // un reloj que avanza 7 ms entre la primera y la segunda lectura
    private fun ticks(): () -> Long {
        val readings = ArrayDeque(listOf(1_000_000L, 8_000_000L))
        return { readings.removeFirst() }
    }

    @Test
    fun `deja la duracion de la organizacion y devuelve lo del bloque`() {
        val log = mock(Logger::class.java)
        assertEquals(3, runBlocking { SweepTiming.timed(log, "DeadlineJob", organization, ticks()) { 3 } })
        verify(log).info("{}: organización {} en {} ms", "DeadlineJob", organization, 7L)
    }

    @Test
    fun `tambien la deja si la organizacion falla`() {
        val log = mock(Logger::class.java)
        assertThrows<IllegalStateException> { runBlocking { SweepTiming.timed(log, "WarningRulesJob", organization, ticks()) { error("falla") } } }
        verify(log).info("{}: organización {} en {} ms", "WarningRulesJob", organization, 7L)
    }
}
