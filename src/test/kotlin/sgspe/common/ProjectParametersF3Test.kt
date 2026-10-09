package sgspe.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

// los valores por defecto de F3 cuando el proyecto trae el parámetro vacío (ASM-019)
class ProjectParametersF3Test {
    private val empty = emptyMap<String, Any?>()

    @Test
    fun `los enteros de plazos y escalamiento tienen su valor por defecto`() {
        assertEquals(7L, ProjectParameters.integer(empty, "due_soon_days"))
        assertEquals(7L, ProjectParameters.integer(empty, "escalation_first_days"))
        assertEquals(15L, ProjectParameters.integer(empty, "escalation_second_days"))
    }

    @Test
    fun `las politicas de compromisos tienen su valor por defecto`() {
        assertEquals(false, ProjectParameters.policy(empty, "policy_workshop_commitments_blocked"))
        assertEquals(true, ProjectParameters.policy(empty, "policy_senior_approval_for_dialogue"))
        assertEquals(true, ProjectParameters.policy(empty, "policy_internal_approval"))
        assertEquals(true, ProjectParameters.policy(empty, "policy_cap_blocks_approval"))
    }

    @Test
    fun `un valor del proyecto manda sobre el por defecto`() {
        assertEquals(3L, ProjectParameters.integer(mapOf("due_soon_days" to 3), "due_soon_days"))
        assertEquals(true, ProjectParameters.policy(mapOf("policy_workshop_commitments_blocked" to true), "policy_workshop_commitments_blocked"))
    }
}
