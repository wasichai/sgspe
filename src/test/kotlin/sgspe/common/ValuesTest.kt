package sgspe.common

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal

// lo guardado y lo que manda un cliente se comparan sin falsos cambios: números como números, textos como textos
class ValuesTest {
    @Test
    fun `numeros de distinto tipo y escala son iguales`() {
        assertTrue(Values.same(3L, 3.0))
        assertTrue(Values.same(BigDecimal("3.00"), 3))
        assertTrue(Values.same(BigDecimal("2.5"), "2.50"))
        assertFalse(Values.same(BigDecimal("2.5"), 2.6))
    }

    @Test
    fun `los textos se comparan como texto, y null y vacio son lo mismo`() {
        assertFalse(Values.same("0012", "12"))
        assertTrue(Values.same(null, ""))
        assertTrue(Values.same("  ", null))
        assertFalse(Values.same("A FAVOR", null))
        assertTrue(Values.same("2026-05-12", "2026-05-12"))
    }
}
