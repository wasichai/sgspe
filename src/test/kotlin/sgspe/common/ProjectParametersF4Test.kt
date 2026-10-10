package sgspe.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

// los parámetros de F4 (database-model.md §6): su valor por defecto en un proyecto anterior a F4 y sus rangos
class ProjectParametersF4Test {
    private val empty = emptyMap<String, Any?>()
    private val catalog = ModelCatalog.fromClasspath()

    private fun codes(
        after: Map<String, Any?>,
        changed: Set<String> = after.keys
    ) = ProjectParameters.outOfRange(after, changed).map { it.field to it.code }

    @Test
    fun `los ocho de F4 tienen su valor por defecto`() {
        assertEquals(5L, ProjectParameters.integer(empty, "grievance_ack_days"))
        assertEquals(15L, ProjectParameters.integer(empty, "inquiry_response_days"))
        assertEquals(30L, ProjectParameters.integer(empty, "grievance_resolution_days"))
        assertEquals(7L, ProjectParameters.integer(empty, "risk_review_days"))
        assertEquals(listOf(5L, 10L, 15L), ProjectParameters.THRESHOLDS.map { ProjectParameters.integer(empty, it) })
        assertEquals(true, ProjectParameters.policy(empty, "policy_high_severity_requires_approver"))
    }

    // el valor por defecto de aquí es el de model.json (defaultValue), que wasichai guarda pero no aplica (UPS-15)
    @Test
    fun `los valores por defecto son los del modelo`() {
        val project = catalog.objectOrNull("project")!!.fields
        for (name in ProjectParameters.PERIODS + ProjectParameters.THRESHOLDS) assertEquals(true, name in project, name)
        assertEquals(true, "policy_high_severity_requires_approver" in project)
    }

    @Test
    fun `un plazo o una cadencia fuera de 1 a 365, o no entero, es OUT_OF_RANGE`() {
        assertEquals(emptyList<Pair<String?, String>>(), codes(mapOf("grievance_ack_days" to 1, "risk_review_days" to 365)))
        assertEquals(listOf("grievance_ack_days" to "OUT_OF_RANGE"), codes(mapOf("grievance_ack_days" to 0)))
        assertEquals(listOf("inquiry_response_days" to "OUT_OF_RANGE"), codes(mapOf("inquiry_response_days" to 366)))
        assertEquals(listOf("grievance_resolution_days" to "OUT_OF_RANGE"), codes(mapOf("grievance_resolution_days" to "2.5")))
        assertEquals(emptyList<Pair<String?, String>>(), codes(mapOf("grievance_resolution_days" to "30.00")))
        // vacío vale el valor por defecto
        assertEquals(emptyList<Pair<String?, String>>(), codes(mapOf("grievance_ack_days" to null)))
    }

    @Test
    fun `los umbrales van de 1 a 25 y crecen estrictamente`() {
        assertEquals(
            emptyList<Pair<String?, String>>(),
            codes(mapOf("risk_medium_min_score" to 4, "risk_high_min_score" to 9, "risk_critical_min_score" to 20))
        )
        assertEquals(listOf("risk_critical_min_score" to "OUT_OF_RANGE"), codes(mapOf("risk_critical_min_score" to 26)))
        // alto igual a crítico, con el medio por defecto
        assertEquals(listOf("risk_high_min_score" to "OUT_OF_RANGE"), codes(mapOf("risk_high_min_score" to 15)))
        // un umbral que no se cambia no se juzga: un valor viejo fuera de regla no bloquea otra escritura
        assertEquals(emptyList<Pair<String?, String>>(), codes(mapOf("risk_high_min_score" to 15, "name" to "x"), setOf("name")))
    }
}
