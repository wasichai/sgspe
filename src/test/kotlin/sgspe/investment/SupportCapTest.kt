package sgspe.investment

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal

// el tope de apoyo social de una campaña (RN-INV-003, ASM-063) y su ejecutado, sin base
class SupportCapTest {
    private fun approved(
        amount: String,
        currency: String? = "PEN"
    ) = SupportCap.Approved(BigDecimal(amount), currency)

    @Test
    fun `suma los aprobados de la moneda de la campana mas el que se aprueba`() {
        val within = SupportCap.evaluate(BigDecimal("1000"), "PEN", approved("400"), listOf(approved("600")))
        assertFalse(within.exceeded)
        assertEquals(0, BigDecimal("1000").compareTo(within.total))
        val over = SupportCap.evaluate(BigDecimal("1000"), "PEN", approved("400.01"), listOf(approved("600")))
        assertTrue(over.exceeded)
    }

    @Test
    fun `otra moneda no se suma y se cuenta, y un candidato en otra moneda no se compara`() {
        val mixed = SupportCap.evaluate(BigDecimal("1000"), "PEN", approved("500"), listOf(approved("600"), approved("9000", "USD")))
        assertTrue(mixed.exceeded)
        assertEquals(1, mixed.otherCurrency)
        val foreign = SupportCap.evaluate(BigDecimal("1000"), "PEN", approved("5000", "USD"), listOf(approved("999")))
        assertFalse(foreign.exceeded)
        assertTrue(foreign.incomparable)
        // P45: sin moneda no se supone PEN; ni se suma ni se compara, y se advierte
        assertTrue(SupportCap.evaluate(BigDecimal("1000"), "PEN", approved("10", null), emptyList()).incomparable)
        val nullApproved = SupportCap.evaluate(BigDecimal("1000"), "PEN", approved("10"), listOf(approved("999", null)))
        assertFalse(nullApproved.exceeded)
        assertEquals(1, nullApproved.otherCurrency)
        // el monto se normaliza (P44): 1000.00 y 1000 son el mismo tope
        assertFalse(SupportCap.evaluate(BigDecimal("1000"), "PEN", approved("400.00"), listOf(approved("600.0000"))).exceeded)
    }

    @Test
    fun `el ejecutado de la campana suma solo su moneda y cuenta los de otra`() {
        val result =
            CampaignExecution.of(
                "PEN",
                listOf(
                    CampaignExecution.Executed(BigDecimal("100"), "PEN"),
                    CampaignExecution.Executed(BigDecimal("50.5"), null),
                    CampaignExecution.Executed(BigDecimal("70"), "USD"),
                    CampaignExecution.Executed(BigDecimal.ZERO, "USD"),
                    CampaignExecution.Executed(null, "PEN")
                )
            )
        assertEquals(0, BigDecimal("100").compareTo(result["executed_social_amount"] as BigDecimal))
        // P45: el ejecutado sin moneda no se suma ni se supone PEN: se cuenta como de otra moneda
        assertEquals(2, result["other_currency_items_count"])
    }
}
