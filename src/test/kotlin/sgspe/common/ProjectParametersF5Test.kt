package sgspe.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

// los valores por defecto de las políticas de aportes cuando el proyecto trae el parámetro vacío (ASM-019,
// database-model.md §6): existen desde F3 y las lee InvestmentWriteGuard desde F5
class ProjectParametersF5Test {
    private val empty = emptyMap<String, Any?>()
    private val names = listOf("policy_no_paid_labor", "policy_non_monetary_donations", "policy_synergy_requires_agreement", "policy_priority_warning")

    @Test
    fun `las politicas de aportes valen true por defecto y un valor del proyecto manda`() {
        for (name in names) {
            assertEquals(true, ProjectParameters.policy(empty, name), name)
            assertEquals(false, ProjectParameters.policy(mapOf(name to false), name), name)
        }
    }

    @Test
    fun `cada politica de aportes existe en project`() {
        val fields = ModelCatalog.fromClasspath().objectOrNull("project")!!.fields
        for (name in names) assertEquals("BOOLEAN", fields[name]?.type, name)
    }
}
