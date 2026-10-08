package sgspe.dashboard

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal

// la forma de un indicador (api.md §4.11) y los números que comparten los cálculos
class IndicatorsTest {
    @Test
    fun `un indicador disponible lleva su cifra y omite lo vacio`() {
        val value = IndicatorResult(BigDecimal("50.0"), 1, 2, listOf(Breakdown("x", emptyList()))).toValue("KPI-X", "Un indicador", "%")
        assertTrue(value.available)
        assertEquals(BigDecimal("50.0"), value.value)
        assertEquals("%", value.unit)
        assertNull(value.unavailableReason)
        assertNull(value.missingFields)
        assertNull(value.warnings)
        assertNull(value.series)
    }

    @Test
    fun `uno no disponible lleva la razon y los campos y ninguna cifra`() {
        val result = IndicatorResult.unavailable(UnavailableReason.FIELD_NOT_READABLE, listOf("stakeholder.stance"))
        val value = result.toValue("KPI-STK-02", "Posturas")
        assertFalse(value.available)
        assertEquals("FIELD_NOT_READABLE", value.unavailableReason)
        assertEquals(listOf("stakeholder.stance"), value.missingFields)
        assertNull(value.value)
        assertNull(value.breakdowns)
    }

    @Test
    fun `el porcentaje y el promedio redondean una sola vez desde los valores sin redondear`() {
        assertEquals(BigDecimal("66.7"), Numbers.percent(2, 3))
        assertEquals(BigDecimal("66.7"), Numbers.percent(2L, 3L))
        assertNull(Numbers.percent(1, 0))
        assertEquals(BigDecimal("3.33"), Numbers.average(listOf(3, 3, 4)))
        assertEquals(BigDecimal("15.00"), Numbers.average(listOf(10L, 20L)))
        assertNull(Numbers.average(emptyList()))
    }
}
