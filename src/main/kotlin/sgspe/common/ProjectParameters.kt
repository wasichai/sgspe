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
            "meetings_per_month_target" to 2L,
            // F3: plazos y escalamiento (ASM-006, ASM-030)
            "due_soon_days" to 7L,
            "escalation_first_days" to 7L,
            "escalation_second_days" to 15L
        )
    private val POLICIES =
        mapOf(
            "policy_minutes_only_formal_types" to true,
            "policy_activation_preconditions" to true,
            // F3: compromisos (RN-ENG-001, RN-ENG-002, ASM-008) y tope de apoyo social (RN-INV-003)
            "policy_workshop_commitments_blocked" to false,
            "policy_senior_approval_for_dialogue" to true,
            "policy_internal_approval" to true,
            "policy_cap_blocks_approval" to true
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
