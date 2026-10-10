package sgspe.scope

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.DomainApiTest

// los parámetros de F4 del proyecto (database-model.md §6, ASM-004, ASM-005): ProjectWriteGuard rechaza con 400 OUT_OF_RANGE
// un plazo o una cadencia fuera de 1 a 365 días y umbrales de riesgo fuera de 1 a 25 o que no crecen, en el alta y en el cambio
class ProjectParametersApiTest : DomainApiTest() {
    private fun createProject(attributes: Map<String, Any?>): Response =
        exchange(
            "POST",
            "/api/objects/project/records",
            mapOf("attributes" to mapOf("code" to uniqueName("PRY-"), "name" to "Proyecto de prueba") + attributes)
        )

    @Test
    fun `un plazo de 0 o de 366 dias no se guarda`() {
        val zero = createProject(mapOf("grievance_ack_days" to 0))
        assertEquals(HttpStatus.BAD_REQUEST, zero.status, zero.body)
        assertEquals("OUT_OF_RANGE", code(zero))
        assertEquals(listOf("grievance_ack_days"), problemFields(zero.body))
        val project = project(mapOf("inquiry_response_days" to 20))
        val long = edit("project", project, mapOf("risk_review_days" to 366))
        assertEquals(HttpStatus.BAD_REQUEST, long.status, long.body)
        assertEquals(listOf("risk_review_days"), problemFields(long.body))
    }

    @Test
    fun `los umbrales de riesgo crecen estrictamente`() {
        val project = project()
        val flat = edit("project", project, mapOf("risk_medium_min_score" to 10, "risk_high_min_score" to 10))
        assertEquals(HttpStatus.BAD_REQUEST, flat.status, flat.body)
        assertEquals("OUT_OF_RANGE", code(flat))
        val ok = edit("project", project, mapOf("risk_medium_min_score" to 4, "risk_high_min_score" to 12, "risk_critical_min_score" to 20))
        assertEquals(HttpStatus.OK, ok.status, ok.body)
    }
}
