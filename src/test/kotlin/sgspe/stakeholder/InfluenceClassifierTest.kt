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

    // P17: cada valor guardado se reproduce desde los guardados un nivel más abajo
    @Test
    fun `el ejemplo oficial de api md se sostiene con el redondeo por nivel`() {
        val scores = InfluenceClassifier.scores(example)!!
        assertEquals(BigDecimal("3.00"), scores.components["interest_affectation"])
        assertEquals(BigDecimal("3.00"), scores.components["interest_opportunity"])
        assertEquals(BigDecimal("3.00"), scores.components["power_resource_control"])
        assertEquals(BigDecimal("2.67"), scores.components["power_action_capacity"])
        assertEquals(BigDecimal("2.33"), scores.components["power_network_mobilization"])
        assertEquals(BigDecimal("3.00"), scores.interestIndex)
        assertEquals(BigDecimal("2.67"), scores.powerIndex)
        assertEquals(BigDecimal("5.67"), scores.influenceScore)
    }

    @Test
    fun `la influencia es la suma de los indices redondeados`() {
        // (2,2,3) da 2,3333 en cada componente: interés 2,33 y poder 2,33; la suma sin redondear daría 4,67
        val indicators =
            InfluenceClassifier.INDICATORS.zip(listOf(2, 2, 3, 2, 2, 3, 2, 2, 3, 2, 2, 3, 2, 2, 3)).toMap()
        val scores = InfluenceClassifier.scores(indicators)!!
        assertEquals(BigDecimal("2.33"), scores.interestIndex)
        assertEquals(BigDecimal("2.33"), scores.powerIndex)
        assertEquals(BigDecimal("4.66"), scores.influenceScore)
        assertEquals(scores.interestIndex + scores.powerIndex, scores.influenceScore)
    }

    @Test
    fun `el indice es el promedio de los componentes redondeados`() {
        // poder: componentes 1,6667, 1,6667 y 1,00 se guardan como 1,67, 1,67 y 1,00; su promedio es 1,45 (desde sin redondear, 1,44)
        val indicators =
            mapOf("affect_environment" to 3) +
                listOf("power_legal_action", "power_credibility", "power_permit_control").zip(listOf(1, 2, 2)) +
                listOf("power_action_scope", "power_organization_level", "power_convening_capacity").zip(listOf(1, 2, 2)) +
                listOf("power_network_membership", "power_group_mobilization", "power_other_pressure").zip(listOf(1, 1, 1))
        val scores = InfluenceClassifier.scores(indicators)!!
        assertEquals(BigDecimal("1.67"), scores.components["power_resource_control"])
        assertEquals(BigDecimal("1.67"), scores.components["power_action_capacity"])
        assertEquals(BigDecimal("1.00"), scores.components["power_network_mobilization"])
        assertEquals(BigDecimal("1.45"), scores.powerIndex)
        assertEquals(BigDecimal("4.45"), scores.influenceScore)
    }

    // el empate del redondeo es HALF_UP: afectación (1,2,2) da 1,6667 y se guarda 1,67; con oportunidad 3,00 el promedio de los
    // guardados es 2,335, que sube a 2,34 (HALF_EVEN o HALF_DOWN darían 2,33)
    @Test
    fun `el empate en la tercera cifra del indice sube (HALF_UP)`() {
        val indicators =
            mapOf("affect_environment" to 1, "affect_livelihood" to 2, "affect_property_status" to 2) +
                mapOf("opportunity_work" to 3, "opportunity_development" to 3, "opportunity_benefits" to 3) +
                mapOf("power_legal_action" to 1)
        val scores = InfluenceClassifier.scores(indicators)!!
        assertEquals(BigDecimal("1.67"), scores.components["interest_affectation"])
        assertEquals(BigDecimal("3.00"), scores.components["interest_opportunity"])
        assertEquals(BigDecimal("2.34"), scores.interestIndex)
        assertEquals(BigDecimal("3.34"), scores.influenceScore)
    }

    @Test
    fun `sin indicadores o todos nulos no hay calculo`() {
        assertNull(InfluenceClassifier.scores(emptyMap()))
        assertNull(InfluenceClassifier.scores(InfluenceClassifier.INDICATORS.associateWith { null }))
    }

    @Test
    fun `un indicador ausente dentro de un componente se promedia con los informados`() {
        val indicators = mapOf("affect_environment" to 4, "affect_livelihood" to null, "affect_property_status" to 2, "power_credibility" to 5)
        val scores = InfluenceClassifier.scores(indicators)!!
        assertEquals(BigDecimal("3.00"), scores.components["interest_affectation"])
        assertEquals(BigDecimal("5.00"), scores.components["power_resource_control"])
        assertNull(scores.components["power_action_capacity"])
        assertEquals(BigDecimal("8.00"), scores.influenceScore)
    }

    @Test
    fun `INDICATORS son los 15 campos sin repetir`() {
        assertEquals(15, InfluenceClassifier.INDICATORS.size)
        assertEquals(15, InfluenceClassifier.INDICATORS.toSet().size)
    }

    @Test
    fun `el grupo no depende de la escala del umbral ni del indice`() {
        assertEquals("ESTRATEGICO", InfluenceClassifier.group(BigDecimal("3.00"), BigDecimal("3.00"), BigDecimal(3), BigDecimal(3)))
        assertEquals("ESTRATEGICO", InfluenceClassifier.group(BigDecimal(3), BigDecimal(3), BigDecimal("3.00"), BigDecimal("3.0")))
    }
}
