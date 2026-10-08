package sgspe.dashboard

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
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

    // un porcentaje sin denominador está disponible y sin cifra: value viaja siempre, en null, para que el portal no tenga
    // que distinguir una clave ausente de una cifra vacía; uno no disponible no lleva value
    @Test
    fun `en json, value siempre viaja en uno disponible y nunca en uno no disponible`() {
        val json = JsonMapper.builder().build()
        val empty = json.readTree(json.writeValueAsString(IndicatorResult(Numbers.percent(0, 0), 0, 0).toValue("KPI-STK-07", "Más de un contacto", "%")))
        assertTrue(empty["available"].asBoolean())
        assertTrue(empty.has("value") && empty["value"].isNull)
        assertEquals(0, empty["denominator"].asInt())
        assertFalse(empty.has("breakdowns"))
        assertFalse(empty.has("unavailableReason"))
        val hidden =
            json.readTree(
                json.writeValueAsString(
                    IndicatorResult.unavailable(UnavailableReason.FIELD_NOT_READABLE, listOf("stakeholder.stance")).toValue("KPI-STK-02", "Posturas")
                )
            )
        assertFalse(hidden.has("value"))
        assertEquals("FIELD_NOT_READABLE", hidden["unavailableReason"].asString())
        assertEquals("stakeholder.stance", hidden["missingFields"][0].asString())
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
