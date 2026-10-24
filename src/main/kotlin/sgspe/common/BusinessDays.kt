package sgspe.common

import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap

// los feriados nacionales por año, por despliegue y no por organización (ASM-096, architecture.md §10.2): sgspe.calendar.holidays
// en application.yml, con el año entre comillas como clave. operación agrega cada año cuando se publica su lista oficial
// (docs/develop/README.md §4); mientras falte, ese año cuenta de lunes a viernes
@ConfigurationProperties("sgspe.calendar")
data class CalendarProperties(
    val holidays: Map<String, List<LocalDate>> = emptyMap()
)

// el calendario de feriados, validado al arrancar: cada fecha pertenece a su año y ninguna se repite; una fecha inválida impide
// arrancar. un año sin lista usa lunes a viernes y lo advierte una sola vez por año y proceso (`missing`)
class HolidayCalendar private constructor(
    private val holidays: Map<Int, Set<LocalDate>>,
    private val missing: (Int) -> Unit
) {
    private val warned = ConcurrentHashMap.newKeySet<Int>()

    fun configured(year: Int): Boolean = year in holidays

    // los feriados de `year`, ordenados, o null si el año no tiene lista; no advierte (es una lectura, no un cálculo de plazo)
    fun holidays(year: Int): List<LocalDate>? = holidays[year]?.sorted()

    fun isHoliday(date: LocalDate): Boolean {
        val days = holidays[date.year]
        if (days == null) {
            if (warned.add(date.year)) missing(date.year)
            return false
        }
        return date in days
    }

    companion object {
        private val log = LoggerFactory.getLogger(HolidayCalendar::class.java)

        fun warning(year: Int): String = "sgspe.calendar: sin feriados configurados para $year; se usan lunes a viernes"

        // valida la configuración: la clave es un año y cada fecha es de ese año, sin repetir
        fun of(
            configured: Map<String, List<LocalDate>>,
            missing: (Int) -> Unit = { log.warn(warning(it)) }
        ): HolidayCalendar {
            val byYear =
                configured.entries.associate { (key, dates) ->
                    val year = key.trim().toIntOrNull() ?: error("sgspe.calendar.holidays: la clave '$key' no es un año")
                    val wrong = dates.filter { it.year != year }
                    check(wrong.isEmpty()) { "sgspe.calendar.holidays.$year: $wrong no son de $year" }
                    val repeated =
                        dates
                            .groupingBy { it }
                            .eachCount()
                            .filterValues { it > 1 }
                            .keys
                    check(repeated.isEmpty()) { "sgspe.calendar.holidays.$year: $repeated se repiten" }
                    year to dates.toSet()
                }
            return HolidayCalendar(byYear, missing)
        }
    }
}

// los días hábiles, puros (RNF-I18N-002, ASM-096): de lunes a viernes y fuera de los feriados de su año
object BusinessDays {
    fun isBusinessDay(
        date: LocalDate,
        calendar: HolidayCalendar
    ): Boolean = date.dayOfWeek != DayOfWeek.SATURDAY && date.dayOfWeek != DayOfWeek.SUNDAY && !calendar.isHoliday(date)

    // el n-ésimo día hábil posterior a `start`: el día de recepción no cuenta (ASM-096). n en 0 es `start`
    fun addBusinessDays(
        start: LocalDate,
        n: Int,
        calendar: HolidayCalendar
    ): LocalDate {
        require(n >= 0) { "n no puede ser negativo: $n" }
        var date = start
        var counted = 0
        while (counted < n) {
            date = date.plusDays(1)
            if (isBusinessDay(date, calendar)) counted++
        }
        return date
    }
}

// el calendario como bean. al arrancar advierte si falta el año en curso de Lima (riesgo 4 de la especificación de F4)
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CalendarProperties::class)
class HolidayCalendarConfiguration {
    @Bean
    fun holidayCalendar(
        properties: CalendarProperties,
        clock: Clock
    ): HolidayCalendar {
        val calendar = HolidayCalendar.of(properties.holidays)
        val year = LocalDate.now(clock.withZone(LIMA)).year
        if (!calendar.configured(year)) calendar.isHoliday(LocalDate.of(year, 1, 1))
        return calendar
    }
}
