package sgspe.stakeholder

import java.math.BigDecimal
import java.math.RoundingMode

// los índices de una evaluación (RF-STK-006 a RF-STK-009, ASM-031, ASM-003). cada indicador va de 1 a 5; cada componente
// es el promedio de sus indicadores informados y cada índice, el promedio de sus componentes, con pesos iguales mientras
// GAP-002 no fije otros. influencia = interés + poder. el grupo sale de los umbrales del proyecto (3 por defecto): un
// índice es alto desde su umbral. todo se redondea a dos decimales, como se guarda, y el grupo se decide con lo guardado
object InfluenceClassifier {
    val INTEREST: Map<String, List<String>> =
        linkedMapOf(
            "interest_affectation" to listOf("affect_environment", "affect_livelihood", "affect_property_status"),
            "interest_opportunity" to listOf("opportunity_work", "opportunity_development", "opportunity_benefits")
        )
    val POWER: Map<String, List<String>> =
        linkedMapOf(
            "power_resource_control" to listOf("power_legal_action", "power_credibility", "power_permit_control"),
            "power_action_capacity" to listOf("power_action_scope", "power_organization_level", "power_convening_capacity"),
            "power_network_mobilization" to listOf("power_network_membership", "power_group_mobilization", "power_other_pressure")
        )
    val INDICATORS: List<String> = (INTEREST.values + POWER.values).flatten()

    const val STRATEGIC = "ESTRATEGICO"
    const val POLITICAL = "POLITICO"
    const val MONITORING = "PARA MONITOREO"
    const val VULNERABLE = "VULNERABLE"

    data class Scores(
        val components: Map<String, BigDecimal?>,
        val interestIndex: BigDecimal,
        val powerIndex: BigDecimal,
        val influenceScore: BigDecimal
    )

    // null si a uno de los dos índices no le llega ningún indicador
    fun scores(indicators: Map<String, Int?>): Scores? {
        val components = (INTEREST + POWER).mapValues { (_, names) -> average(names.mapNotNull { indicators[it] }.map { BigDecimal(it) }) }
        val interest = average(INTEREST.keys.mapNotNull { components[it] }) ?: return null
        val power = average(POWER.keys.mapNotNull { components[it] }) ?: return null
        return Scores(components.mapValues { it.value?.let(::round) }, round(interest), round(power), round(interest + power))
    }

    fun group(
        interestIndex: BigDecimal,
        powerIndex: BigDecimal,
        interestThreshold: BigDecimal,
        powerThreshold: BigDecimal
    ): String {
        val interestHigh = interestIndex >= interestThreshold
        val powerHigh = powerIndex >= powerThreshold
        return when {
            interestHigh && powerHigh -> STRATEGIC
            powerHigh -> POLITICAL
            interestHigh -> MONITORING
            else -> VULNERABLE
        }
    }

    private fun average(values: List<BigDecimal>): BigDecimal? =
        if (values.isEmpty()) null else values.reduce(BigDecimal::add).divide(BigDecimal(values.size), 10, RoundingMode.HALF_UP)

    private fun round(value: BigDecimal): BigDecimal = value.setScale(2, RoundingMode.HALF_UP)
}
