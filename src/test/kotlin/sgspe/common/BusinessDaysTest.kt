package sgspe.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.core.env.EnumerablePropertySource
import org.springframework.core.io.ClassPathResource
import java.time.LocalDate

// ASM-096: días hábiles de lunes a viernes menos los feriados configurados; el día de recepción no cuenta; un año sin
// lista usa lunes a viernes y lo advierte una vez
class BusinessDaysTest {
    private fun date(text: String) = LocalDate.parse(text)

    private val calendar = HolidayCalendar.of(mapOf("2026" to listOf(date("2026-07-28"), date("2026-07-29"), date("2026-08-06"))))

    @Test
    fun `el dia de recepcion no cuenta y el fin de semana tampoco`() {
        // lunes 5 de octubre de 2026: el quinto día hábil es el lunes 12
        assertEquals(date("2026-10-12"), BusinessDays.addBusinessDays(date("2026-10-05"), 5, calendar))
        // recibida el sábado 3: cuenta desde el lunes 5, así que el quinto día hábil es el viernes 9
        assertEquals(date("2026-10-09"), BusinessDays.addBusinessDays(date("2026-10-03"), 5, calendar))
        assertEquals(date("2026-10-05"), BusinessDays.addBusinessDays(date("2026-10-05"), 0, calendar))
    }

    @Test
    fun `un feriado no es habil`() {
        // lunes 27 de julio de 2026: el 28 y el 29 son Fiestas Patrias, así que el cuarto día hábil es el martes 4 de agosto
        assertEquals(date("2026-08-04"), BusinessDays.addBusinessDays(date("2026-07-27"), 4, calendar))
        assertFalse(BusinessDays.isBusinessDay(date("2026-08-06"), calendar))
        assertTrue(BusinessDays.isBusinessDay(date("2026-08-07"), calendar))
    }

    @Test
    fun `un anio sin lista usa lunes a viernes y advierte una sola vez`() {
        val warned = mutableListOf<Int>()
        val partial = HolidayCalendar.of(mapOf("2026" to emptyList()), missing = { warned += it })
        assertEquals(date("2028-01-07"), BusinessDays.addBusinessDays(date("2027-12-31"), 5, partial))
        BusinessDays.addBusinessDays(date("2028-02-01"), 3, partial)
        assertEquals(listOf(2028), warned)
        assertEquals("sgspe.calendar: sin feriados configurados para 2028; se usan lunes a viernes", HolidayCalendar.warning(2028))
    }

    @Test
    fun `una fecha de otro anio, repetida o una clave que no es un anio impiden arrancar`() {
        assertThrows<IllegalStateException> { HolidayCalendar.of(mapOf("2026" to listOf(date("2027-01-01")))) }
        assertThrows<IllegalStateException> { HolidayCalendar.of(mapOf("2026" to listOf(date("2026-01-01"), date("2026-01-01")))) }
        assertThrows<IllegalStateException> { HolidayCalendar.of(mapOf("dos mil" to emptyList())) }
    }

    // la configuración de application.yml: 2026 completo (16 feriados) y cada año que operación agregue después, válido. el
    // calendario se configura por año cuando se publica la lista oficial: un año que falta cuenta de lunes a viernes
    @Test
    fun `application yml trae los feriados de 2026 y cada anio agregado es valido`() {
        val prefix = "sgspe.calendar.holidays."
        val byYear =
            YamlPropertySourceLoader()
                .load("application", ClassPathResource("application.yml"))
                .filterIsInstance<EnumerablePropertySource<*>>()
                .flatMap { source -> source.propertyNames.filter { it.startsWith(prefix) }.map { it to source.getProperty(it).toString() } }
                .groupBy({ (name, _) -> name.removePrefix(prefix).substringBefore("[") }, { (_, value) -> LocalDate.parse(value) })
        assertEquals(16, byYear["2026"]?.size, byYear.toString())
        val calendar = HolidayCalendar.of(byYear)
        assertFalse(BusinessDays.isBusinessDay(date("2026-04-03"), calendar))
        assertFalse(BusinessDays.isBusinessDay(date("2026-12-09"), calendar))
    }
}
