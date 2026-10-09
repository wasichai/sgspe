package sgspe.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal

// los montos de un comando se acotan antes de sumarlos (la lección de la Tarea 17 de F2: un exponente enorme agota la memoria)
class AmountsTest {
    private fun code(value: Any?): String = assertThrows<RuleException> { Amounts.parse(value, "attributes.delivered_amount") }.violations.single().message

    @Test
    fun `un monto valido pasa como decimal, y vacio es null`() {
        assertEquals(BigDecimal("1500.5"), Amounts.parse("1500.50", "x"))
        assertEquals(0, BigDecimal("1500.5").compareTo(Amounts.parse(1500.5, "x")))
        assertEquals(BigDecimal("999999999999999.9999"), Amounts.parse("999999999999999.9999", "x"))
        assertNull(Amounts.parse(null, "x"))
        assertNull(Amounts.parse("  ", "x"))
    }

    @Test
    fun `lo que no es un numero es INVALID_NUMBER y lo que se sale de las cotas, OUT_OF_RANGE`() {
        assertEquals("INVALID_NUMBER", code("mil"))
        assertEquals("OUT_OF_RANGE", code("-1"))
        assertEquals("OUT_OF_RANGE", code("1e999999999"))
        assertEquals("OUT_OF_RANGE", code(1e300))
        assertEquals("OUT_OF_RANGE", code("1000000000000000"))
        assertEquals("OUT_OF_RANGE", code("1.00001"))
        assertEquals("OUT_OF_RANGE", code("1".repeat(41)))
        assertEquals("OUT_OF_RANGE", code("1e-999999999"))
        assertEquals("OUT_OF_RANGE", code("1E+999999999"))
    }

    @Test
    fun `un exponente que desborda un int, NaN e infinito son INVALID_NUMBER sin agotar memoria`() {
        assertEquals("INVALID_NUMBER", code("1e99999999999"))
        assertEquals("INVALID_NUMBER", code("1e-99999999999"))
        assertEquals("INVALID_NUMBER", code("NaN"))
        assertEquals("INVALID_NUMBER", code("Infinity"))
        assertEquals("INVALID_NUMBER", code(Double.NaN))
        assertEquals("INVALID_NUMBER", code(Double.POSITIVE_INFINITY))
    }

    @Test
    fun `los ceros finales se aceptan y el monto sale normalizado, con escala de 0 o mas`() {
        assertEquals(BigDecimal("1.5"), Amounts.parse("1.50000", "x"))
        assertEquals(BigDecimal("1"), Amounts.parse("1.0000000", "x"))
        assertEquals(BigDecimal("100"), Amounts.parse("1E+2", "x"))
        assertEquals(0, Amounts.parse("1E+2", "x")!!.scale())
        assertEquals(BigDecimal.ZERO, Amounts.parse("0.0000", "x"))
        assertEquals(BigDecimal("0.0001"), Amounts.parse("0.00010000", "x"))
    }

    @Test
    fun `mas decimales de los permitidos tras quitar los ceros siguen siendo OUT_OF_RANGE`() {
        assertEquals("OUT_OF_RANGE", code("0.00001"))
        assertEquals("OUT_OF_RANGE", code("1.000010"))
    }
}
