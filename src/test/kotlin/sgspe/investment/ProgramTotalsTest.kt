package sgspe.investment

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal

// total_budget de un programa (especificación de F5 §3.6, decisión 4, ASM-063), puro
class ProgramTotalsTest {
    private fun budget(
        amount: String?,
        currency: String?
    ) = ProgramTotals.Budget(amount?.let(::BigDecimal), currency)

    @Test
    fun `suma solo los presupuestos en la moneda del programa`() {
        val total = ProgramTotals.total("PEN", listOf(budget("1000", "PEN"), budget("500.50", "PEN"), budget("300", "USD"), budget(null, "PEN")))
        assertEquals(0, BigDecimal("1500.50").compareTo(total))
    }

    @Test
    fun `sin moneda del programa no hay total y sin presupuestos en ella es cero`() {
        assertNull(ProgramTotals.total(null, listOf(budget("1000", "PEN"))))
        assertEquals(0, BigDecimal.ZERO.compareTo(ProgramTotals.total("USD", listOf(budget("1000", "PEN")))))
        assertEquals(0, BigDecimal.ZERO.compareTo(ProgramTotals.total("PEN", emptyList())))
    }

    @Test
    fun `un cambio de presupuesto mueve el total solo si toca programa, monto asignado o moneda`() {
        val stored = mapOf<String, Any?>("social_program" to "p1", "allocated_amount" to 1000, "currency" to "PEN", "executed_amount" to 10)
        assertTrue(ProgramTotals.affects(null, stored))
        assertTrue(ProgramTotals.affects(stored, null))
        assertTrue(ProgramTotals.affects(stored, stored + ("allocated_amount" to "1000.5")))
        assertTrue(ProgramTotals.affects(stored, stored + ("social_program" to "p2")))
        assertFalse(ProgramTotals.affects(stored, stored + ("executed_amount" to 500)))
        assertFalse(ProgramTotals.affects(stored, stored + ("allocated_amount" to "1000.00")))
    }
}
