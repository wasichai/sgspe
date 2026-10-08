package sgspe.stakeholder

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.math.BigDecimal

// RF-STK-006 a RF-STK-009 con los pesos iguales de ASM-031 y el umbral 3 de ASM-003: el ejemplo de api.md §4.3
class InfluenceClassifierTest {
    private val example =
        mapOf(
            "affect_environment" to 4,
            "affect_livelihood" to 3,
            "affect_property_status" to 2,
            "opportunity_work" to 4,
            "opportunity_development" to 3,
            "opportunity_benefits" to 2,
            "power_legal_action" to 2,
            "power_credibility" to 3,
            "power_permit_control" to 4,
            "power_action_scope" to 2,
            "power_organization_level" to 3,
            "power_convening_capacity" to 3,
            "power_network_membership" to 2,
            "power_group_mobilization" to 3,
            "power_other_pressure" to 2
        )

    @Test
    fun `el ejemplo de api md da interes 3,0, poder 2,67, influencia 5,67 y PARA MONITOREO`() {
        val scores = InfluenceClassifier.scores(example)!!
        assertEquals(BigDecimal("3.00"), scores.interestIndex)
        assertEquals(BigDecimal("2.67"), scores.powerIndex)
        assertEquals(BigDecimal("5.67"), scores.influenceScore)
        assertEquals(BigDecimal("2.67"), scores.components["power_action_capacity"])
        assertEquals(BigDecimal("2.33"), scores.components["power_network_mobilization"])
        assertEquals("PARA MONITOREO", InfluenceClassifier.group(scores.interestIndex, scores.powerIndex, BigDecimal(3), BigDecimal(3)))
    }

    @Test
    fun `los cuatro cuadrantes, con el umbral incluido como alto`() {
        val three = BigDecimal(3)
        assertEquals("ESTRATEGICO", InfluenceClassifier.group(three, three, three, three))
        assertEquals("POLITICO", InfluenceClassifier.group(BigDecimal("2.99"), BigDecimal(4), three, three))
        assertEquals("PARA MONITOREO", InfluenceClassifier.group(BigDecimal(5), BigDecimal(1), three, three))
        assertEquals("VULNERABLE", InfluenceClassifier.group(BigDecimal(1), BigDecimal(1), three, three))
        assertEquals("VULNERABLE", InfluenceClassifier.group(three, three, BigDecimal("3.5"), BigDecimal("3.5")))
    }

    @Test
    fun `un componente sin indicadores no cuenta, y sin indicadores de un indice no hay calculo`() {
        val onlyAffect = mapOf("affect_environment" to 5, "power_legal_action" to 1)
        val scores = InfluenceClassifier.scores(onlyAffect)!!
        assertEquals(BigDecimal("5.00"), scores.interestIndex)
        assertEquals(BigDecimal("1.00"), scores.powerIndex)
        assertNull(scores.components["interest_opportunity"])
        assertNull(InfluenceClassifier.scores(mapOf("affect_environment" to 5)))
    }
}
