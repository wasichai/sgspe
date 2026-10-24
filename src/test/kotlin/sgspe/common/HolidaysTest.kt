package sgspe.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

// los feriados de un año para el calendario del portal (#159, ASM-096): ordenados, y un año sin lista no está configurado
class HolidaysTest {
    private val calendar =
        HolidayCalendar.of(
            mapOf("2026" to listOf(LocalDate.parse("2026-12-25"), LocalDate.parse("2026-01-01"), LocalDate.parse("2026-12-08"))),
            missing = {}
        )

    @Test
    fun `un anio configurado devuelve sus fechas ordenadas`() {
        val response = Holidays.of(calendar, 2026)
        assertEquals(2026, response.year)
        assertEquals(true, response.configured)
        assertEquals(listOf("2026-01-01", "2026-12-08", "2026-12-25"), response.holidays.map { it.toString() })
    }

    @Test
    fun `un anio sin lista no esta configurado y no tiene feriados`() {
        val response = Holidays.of(calendar, 2030)
        assertEquals(HolidaysResponse(2030, false, emptyList()), response)
    }

    @Test
    fun `el anio es obligatorio, numerico y de 2000 a 2100`() {
        assertEquals(2026, Holidays.year("2026"))
        assertEquals(2000, Holidays.year("2000"))
        assertEquals(2100, Holidays.year("2100"))
        listOf(null, "", "abc", "1999", "2101", "2026.5").forEach { raw ->
            val error = runCatching { Holidays.year(raw) }.exceptionOrNull()
            assertEquals(UnprocessableContentException::class, error?.let { it::class }, "year=$raw")
        }
    }
}
