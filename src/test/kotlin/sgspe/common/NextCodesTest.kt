package sgspe.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

// el código propuesto (decisión 6 de la revisión de UX): el siguiente número del prefijo y el año, con tres cifras como mínimo;
// los códigos de otro año, de otro prefijo o con otra forma no cuentan
class NextCodesTest {
    @Test
    fun `sin codigos del anio empieza en 001`() {
        assertEquals("COM-2026-001", NextCodes.next("COM", 2026, emptyList()))
        assertEquals("COM-2026-001", NextCodes.next("COM", 2026, listOf("COM-2025-040", "ACU-2026-003", "C-001", null)))
    }

    @Test
    fun `sigue al mayor del anio y no rellena huecos`() {
        assertEquals("COM-2026-012", NextCodes.next("COM", 2026, listOf("COM-2026-004", " COM-2026-011 ", "COM-2026-002")))
    }

    @Test
    fun `pasado el 999 sigue con cuatro cifras`() {
        assertEquals("QUE-2026-1000", NextCodes.next("QUE", 2026, listOf("QUE-2026-999")))
    }

    @Test
    fun `cada objeto con codigo tiene su prefijo`() {
        assertEquals(
            setOf("commitment", "agreement", "grievance", "inquiry", "stakeholder_request", "social_risk", "incident", "social_contribution", "social_program"),
            NextCodes.PREFIXES.keys
        )
        assertEquals(
            NextCodes.PREFIXES.size,
            NextCodes.PREFIXES.values
                .toSet()
                .size
        )
    }
}
