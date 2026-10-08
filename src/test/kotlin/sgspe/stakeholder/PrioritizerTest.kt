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

    @Test
    fun `sin grupo de influencia no hay prioridad`() {
        assertNull(Prioritizer.priority(rules, null, "EN CONTRA", "AIDS", null))
    }

    @Test
    fun `solo hay reglas con tipo y el grupo de interes tiene otro tipo o ninguno, no hay prioridad`() {
        val typed = listOf(Prioritizer.Rule("ESTRATEGICO", "EN CONTRA", "AIDS", "AUTORIDAD LOCAL", "ALTA"))
        assertNull(Prioritizer.priority(typed, "ESTRATEGICO", "EN CONTRA", "AIDS", "IGLESIA"))
        assertNull(Prioritizer.priority(typed, "ESTRATEGICO", "EN CONTRA", "AIDS", null))
    }

    // en los datos no ocurre: el único compuesto y StakeholderWriteGuard impiden dos filas de la misma especificidad
    @Test
    fun `con dos filas de la misma especificidad gana la primera de la lista`() {
        val tie =
            listOf(
                Prioritizer.Rule("POLITICO", "NEUTRAL", "AIIS", null, "MEDIA"),
                Prioritizer.Rule("POLITICO", "NEUTRAL", "AIIS", null, "BAJA"),
                Prioritizer.Rule("POLITICO", "NEUTRAL", "AIIS", "IGLESIA", "ALTA"),
                Prioritizer.Rule("POLITICO", "NEUTRAL", "AIIS", "IGLESIA", "MUY ALTA")
            )
        assertEquals("MEDIA", Prioritizer.priority(tie, "POLITICO", "NEUTRAL", "AIIS", null))
        assertEquals("ALTA", Prioritizer.priority(tie, "POLITICO", "NEUTRAL", "AIIS", "IGLESIA"))
    }
}
