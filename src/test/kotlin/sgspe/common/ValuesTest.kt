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

    @Test
    fun `NaN e infinito no son iguales a nada y no revientan`() {
        assertFalse(Values.same(Double.NaN, 1))
        assertFalse(Values.same(Double.POSITIVE_INFINITY, "x"))
        assertFalse(Values.same(Double.NaN, Double.NaN))
        assertFalse(Values.same(Double.NaN, null))
    }

    // plan 8.2b: un PUT que reenvía el descriptor de un FILE derivado tal como se leyó no lo cambia (DerivedFieldsGuard)
    @Test
    fun `un descriptor de archivo es igual a su id y dos mapas sin id se comparan enteros`() {
        val id = "5d4c3b2a-1f0e-4d9c-8b7a-6f5e4d3c2b1a"
        val descriptor = mapOf("id" to id, "name" to "borrador.pdf", "contentType" to "application/pdf", "size" to 10, "sha256" to "a".repeat(64))
        assertTrue(Values.same(descriptor, id))
        assertTrue(Values.same(id, descriptor))
        assertTrue(Values.same(descriptor, descriptor.toMap()))
        assertFalse(Values.same(descriptor, "11111111-1111-1111-1111-111111111111"))
        assertTrue(Values.same(mapOf("name" to "x"), mapOf("name" to "x")))
        assertFalse(Values.same(mapOf("name" to "x"), mapOf("name" to "y")))
        assertFalse(Values.same(mapOf("name" to "x"), "x"))
    }
}
