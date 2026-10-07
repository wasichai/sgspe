package sgspe.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

// las fechas de sgspe son las de Lima, UTC-5 todo el año, no las de la máquina
class LimaTest {
    @Test
    fun `la zona es la de Lima, sin horario de verano`() {
        assertEquals("America/Lima", LIMA.id)
        for (instant in listOf("2026-01-15T12:00:00Z", "2026-07-15T12:00:00Z")) {
            assertEquals(ZoneOffset.ofHours(-5), LIMA.rules.getOffset(Instant.parse(instant)), instant)
        }
    }

    @Test
    fun `el reloj de la app corre en Lima`() {
        assertEquals(LIMA, Clocks().clock().zone)
    }

    // el día de Lima cambia a las 05:00 UTC, no a medianoche UTC
    @Test
    fun `el dia cambia en Lima a las 05h00 UTC`() {
        assertEquals(LocalDate.of(2026, 1, 15), Instant.parse("2026-01-16T04:59:59Z").atZone(LIMA).toLocalDate())
        assertEquals(LocalDate.of(2026, 1, 16), Instant.parse("2026-01-16T05:00:00Z").atZone(LIMA).toLocalDate())
    }
}
