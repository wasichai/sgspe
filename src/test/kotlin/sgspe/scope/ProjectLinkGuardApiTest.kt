package sgspe.scope

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpStatus
import sgspe.F4ApiTest
import wasichai.core.common.WasichaiException

// el alcance de los objetos de F4 y la regla «mismo proyecto» (ProjectLinkGuard, api.md §3.3): un registro de F4 solo
// referencia registros de su proyecto, también por un vínculo M2M y desde un compromiso; una regla del tenant no discrepa
class ProjectLinkGuardApiTest : F4ApiTest() {
    private fun create(
        objectName: String,
        attributes: Map<String, Any?>,
        token: String = this.token
    ): Response = exchange("POST", "/api/objects/$objectName/records", mapOf("attributes" to attributes), token, withReason("alta de prueba"))

    @Test
    fun `una queja con el grupo de interes de otro proyecto es PROJECT_MISMATCH`() {
        val other = project()
        val response = create("grievance", attentionAttributes(testProject(), null, "QJ-") + mapOf("stakeholder" to stakeholder(other)))
        assertEquals(HttpStatus.CONFLICT, response.status, response.body)
        assertEquals("PROJECT_MISMATCH", code(response))
        assertEquals(listOf("stakeholder"), problemFields(response.body))
        // del mismo proyecto, pasa
        grievance(testProject(), attributes = mapOf("stakeholder" to stakeholder(testProject())))
    }

    // reclassified_from_* solo lo escribe su comando (403 COMMAND_ONLY por la API genérica), y el comando fija el proyecto del
    // original; la regla «mismo proyecto» vale igual para quien sí lo escribe: aquí, la plataforma
    @Test
    fun `una solicitud reclasificada de una queja de otro proyecto es PROJECT_MISMATCH`() {
        val foreign = grievance(project())
        val generic =
            create("stakeholder_request", attentionAttributes(testProject(), null, "SO-") + mapOf("reclassified_from_grievance" to foreign))
        assertEquals("COMMAND_ONLY", code(generic))
        val request = stakeholderRequest(testProject())
        val refused = assertThrows<WasichaiException> { platformPatch("stakeholder_request", request, mapOf("reclassified_from_grievance" to foreign)) }
        assertEquals(HttpStatus.CONFLICT, refused.status)
        assertEquals("PROJECT_MISMATCH", refused.violations.single().message)
        assertEquals("reclassified_from_grievance", refused.violations.single().field)
    }

    @Test
    fun `un compromiso que nace de una queja de otro proyecto es PROJECT_MISMATCH`() {
        val foreign = grievance(project())
        val response =
            exchange(
                "POST",
                "/api/objects/commitment/records",
                mapOf(
                    "attributes" to
                        commitmentAttributes(testProject(), userWithRole("COMMITMENT_MANAGER"), userWithRole("COMMITMENT_MANAGER")) +
                        ("origin_grievance" to foreign)
                ),
                token,
                withReason("alta de prueba")
            )
        assertEquals(HttpStatus.CONFLICT, response.status, response.body)
        assertEquals(listOf("origin_grievance"), problemFields(response.body))
    }

    @Test
    fun `un riesgo vinculado a un grupo de interes de otro proyecto es PROJECT_MISMATCH`() {
        val risk = socialRisk(testProject())
        val response = linkWithReason("social_risk", risk, "social_risk_stakeholder", stakeholder(project()))
        assertEquals(HttpStatus.CONFLICT, response.status, response.body)
        assertEquals("PROJECT_MISMATCH", code(response))
        assertEquals(listOf("social_risk_stakeholder"), problemFields(response.body))
        assertEquals(HttpStatus.NO_CONTENT, linkWithReason("social_risk", risk, "social_risk_stakeholder", stakeholder(testProject())).status)
    }

    // el vínculo con la regla solo lo escribe la plataforma (JOB-04; ronda branchfix de 4.2b, R-I3), y la regla «mismo
    // proyecto» la juzga también entonces
    @Test
    fun `una alerta de una regla del tenant pasa y una de una regla de otro proyecto no`() {
        ruleWarning(testProject(), warningRule(null))
        val refused = assertThrows<WasichaiException> { ruleWarning(testProject(), warningRule(project())) }
        assertEquals(HttpStatus.CONFLICT, refused.status)
        assertEquals(listOf("early_warning_rule" to "PROJECT_MISMATCH"), refused.violations.map { it.field to it.message })
    }

    @Test
    fun `quien no esta asignado al proyecto no registra una queja`() {
        // desde 4.4 el proyecto no existe para quien no lo lee: el 400 de wasichai sobre la relación (ASM-108)
        val outsider = userWithRole("COMMUNITY_RELATIONS_OFFICER")
        val response = create("grievance", attentionAttributes(testProject(), outsider, "QJ-"), outsider.token)
        assertEquals(HttpStatus.BAD_REQUEST, response.status, response.body)
        assertTrue("project" in problemFields(response.body), response.body)
    }
}
