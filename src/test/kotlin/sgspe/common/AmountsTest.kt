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
        assertEquals(BigDecimal("1500.50"), Amounts.parse("1500.50", "x"))
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
    }
}
