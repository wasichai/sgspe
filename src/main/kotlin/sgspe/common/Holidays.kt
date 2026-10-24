package sgspe.common

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ServerWebExchange
import wasichai.core.common.FieldViolation
import java.time.LocalDate

// los feriados de un año, para marcarlos en el calendario del portal (#159, ASM-096): los mismos de HolidayCalendar, por
// despliegue y no por organización. un año sin lista no está configurado y no tiene feriados (cuenta de lunes a viernes)
data class HolidaysResponse(
    val year: Int,
    val configured: Boolean,
    val holidays: List<LocalDate>
)

object Holidays {
    const val YEAR = "year"
    const val MIN_YEAR = 2000
    const val MAX_YEAR = 2100

    fun of(
        calendar: HolidayCalendar,
        year: Int
    ): HolidaysResponse {
        val dates = calendar.holidays(year)
        return HolidaysResponse(year, dates != null, dates.orEmpty())
    }

    // year es obligatorio, entero y de MIN_YEAR a MAX_YEAR; si no, 422 con el parámetro en errors[0].field
    fun year(raw: String?): Int {
        val value = raw?.trim()?.toIntOrNull()
        if (value == null || value < MIN_YEAR || value > MAX_YEAR) {
            throw UnprocessableContentException(
                listOf(FieldViolation(YEAR, "es obligatorio y debe ser un año de $MIN_YEAR a $MAX_YEAR")),
                "Parámetro de consulta inválido"
            )
        }
        return value
    }
}

// GET /api/sgspe/calendar/holidays?year=AAAA (api.md §4): autenticado (lo exige la seguridad de wasichai para /api/**), sin
// permiso propio
@RestController
@RequestMapping("/api/sgspe/calendar/holidays")
class HolidaysController(
    private val calendar: HolidayCalendar
) {
    @GetMapping
    suspend fun holidays(exchange: ServerWebExchange): HolidaysResponse {
        val params = exchange.request.queryParams
        StrictQuery.requireOnly(params, setOf(Holidays.YEAR))
        return Holidays.of(calendar, Holidays.year(params.getFirst(Holidays.YEAR)))
    }
}
