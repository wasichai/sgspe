package sgspe.common

import java.math.BigDecimal

// los parámetros y políticas de `project` con su valor por defecto (database-model.md §6, ASM-019). wasichai guarda
// defaultValue pero no lo aplica (UPS-15): un parámetro vacío vale lo que dice la ficha, y todo el código de sgspe lo lee
// aquí para que el tablero, la guarda y el endpoint usen el mismo valor
object ProjectParameters {
    private val DECIMALS = mapOf("interest_threshold" to BigDecimal("3"), "power_threshold" to BigDecimal("3"))
    private val INTEGERS =
        mapOf(
            "stakeholder_review_days" to 30L,
            "inactivity_days" to 90L,
            "meetings_per_month_target" to 2L
        )
    private val POLICIES =
        mapOf(
            "policy_minutes_only_formal_types" to true,
            "policy_activation_preconditions" to true
        )

    fun decimal(
        project: Map<String, Any?>,
        name: String
    ): BigDecimal = Values.decimal(project[name]) ?: DECIMALS.getValue(name)

    fun integer(
        project: Map<String, Any?>,
        name: String
    ): Long = Values.decimal(project[name])?.toLong() ?: INTEGERS.getValue(name)

    fun policy(
        project: Map<String, Any?>,
        name: String
    ): Boolean = Values.bool(project[name]) ?: POLICIES.getValue(name)
}
