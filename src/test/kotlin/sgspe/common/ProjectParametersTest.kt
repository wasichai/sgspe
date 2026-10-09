package sgspe.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import java.io.File
import java.math.BigDecimal

// los parámetros y políticas de project con el valor por defecto de la ficha (database-model.md §6), sin servidor
class ProjectParametersTest {
    @Test
    fun `un parametro vacio vale lo que dice la ficha`() {
        val empty = emptyMap<String, Any?>()
        assertEquals(0, BigDecimal("3").compareTo(ProjectParameters.decimal(empty, "interest_threshold")))
        assertEquals(0, BigDecimal("3").compareTo(ProjectParameters.decimal(empty, "power_threshold")))
        assertEquals(30L, ProjectParameters.integer(empty, "stakeholder_review_days"))
        assertEquals(90L, ProjectParameters.integer(empty, "inactivity_days"))
        assertEquals(2L, ProjectParameters.integer(empty, "meetings_per_month_target"))
        assertTrue(ProjectParameters.policy(empty, "policy_activation_preconditions"))
        assertTrue(ProjectParameters.policy(empty, "policy_minutes_only_formal_types"))
    }

    @Test
    fun `un valor guardado manda, venga como numero, texto o booleano`() {
        val project = mapOf("interest_threshold" to "3.5", "inactivity_days" to 45, "policy_activation_preconditions" to false)
        assertEquals(0, BigDecimal("3.5").compareTo(ProjectParameters.decimal(project, "interest_threshold")))
        assertEquals(45L, ProjectParameters.integer(project, "inactivity_days"))
        assertFalse(ProjectParameters.policy(project, "policy_activation_preconditions"))
        assertFalse(ProjectParameters.policy(mapOf("policy_activation_preconditions" to "false"), "policy_activation_preconditions"))
    }

    // la ficha manda: cada valor por defecto del código es el defaultValue del campo en model/model.json
    @Test
    fun `los valores por defecto son los defaultValue de model json`() {
        val project =
            JsonMapper
                .builder()
                .build()
                .readTree(File("model/model.json"))
                .path("objects")
                .values()
                .first { it.path("name").asString() == "project" }
        val defaults =
            project.path("fields").values().filter { it.has("defaultValue") }.associate {
                it.path("name").asString() to
                    it.path("defaultValue").asString()
            }
        val empty = emptyMap<String, Any?>()
        listOf("interest_threshold", "power_threshold").forEach {
            assertEquals(0, BigDecimal(defaults.getValue(it)).compareTo(ProjectParameters.decimal(empty, it)), it)
        }
        // desde F3, los plazos y las políticas que leen las guardas, JOB-01 y los tableros (el modelo del plan 3.1)
        listOf(
            "stakeholder_review_days",
            "inactivity_days",
            "meetings_per_month_target",
            "due_soon_days",
            "escalation_first_days",
            "escalation_second_days"
        ).forEach {
            assertEquals(defaults.getValue(it).toLong(), ProjectParameters.integer(empty, it), it)
        }
        listOf(
            "policy_minutes_only_formal_types",
            "policy_activation_preconditions",
            "policy_workshop_commitments_blocked",
            "policy_senior_approval_for_dialogue",
            "policy_internal_approval",
            "policy_cap_blocks_approval"
        ).forEach {
            assertEquals(defaults.getValue(it).toBooleanStrict(), ProjectParameters.policy(empty, it), it)
        }
    }
}
