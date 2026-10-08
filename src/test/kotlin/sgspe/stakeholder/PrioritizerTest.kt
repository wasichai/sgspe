package sgspe.stakeholder

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

// RF-STK-011: la fila con el tipo del grupo de interés gana a la que vale para cualquier tipo
class PrioritizerTest {
    private val rules =
        listOf(
            Prioritizer.Rule("ESTRATEGICO", "EN CONTRA", "AIDS", null, "MUY ALTA"),
            Prioritizer.Rule("ESTRATEGICO", "EN CONTRA", "AIDS", "AUTORIDAD LOCAL", "ALTA"),
            Prioritizer.Rule("POLITICO", "NEUTRAL", "AIIS", null, "MEDIA")
        )

    @Test
    fun `gana la fila del tipo, si no la de cualquier tipo`() {
        assertEquals("ALTA", Prioritizer.priority(rules, "ESTRATEGICO", "EN CONTRA", "AIDS", "AUTORIDAD LOCAL"))
        assertEquals("MUY ALTA", Prioritizer.priority(rules, "ESTRATEGICO", "EN CONTRA", "AIDS", "IGLESIA"))
        assertEquals("MUY ALTA", Prioritizer.priority(rules, "ESTRATEGICO", "EN CONTRA", "AIDS", null))
    }

    @Test
    fun `sin postura, sin ambito o sin fila que coincida, no hay prioridad`() {
        assertNull(Prioritizer.priority(rules, "ESTRATEGICO", null, "AIDS", null))
        assertNull(Prioritizer.priority(rules, "ESTRATEGICO", "EN CONTRA", null, null))
        assertNull(Prioritizer.priority(rules, "VULNERABLE", "A FAVOR", "AIDS", null))
    }
}
