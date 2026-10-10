package sgspe.scope

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F4ApiTest

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

    @Test
    fun `una solicitud reclasificada de una queja de otro proyecto es PROJECT_MISMATCH`() {
        val foreign = grievance(project())
        val response =
            create("stakeholder_request", attentionAttributes(testProject(), null, "SO-") + mapOf("reclassified_from_grievance" to foreign))
        assertEquals("PROJECT_MISMATCH", code(response))
        assertEquals(listOf("reclassified_from_grievance"), problemFields(response.body))
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

    @Test
    fun `una alerta de una regla del tenant pasa y una de una regla de otro proyecto no`() {
        earlyWarning(testProject(), attributes = mapOf("early_warning_rule" to warningRule(null)))
        val response =
            exchange(
                "POST",
                "/api/objects/early_warning/records",
                mapOf(
                    "attributes" to
                        mapOf(
                            "project" to testProject(),
                            "title" to "Alerta",
                            "detected_at" to "2026-10-07T15:00:00Z",
                            "early_warning_rule" to warningRule(project())
                        )
                ),
                token
            )
        assertEquals(HttpStatus.CONFLICT, response.status, response.body)
        assertEquals(listOf("early_warning_rule"), problemFields(response.body))
    }

    @Test
    fun `quien no esta asignado al proyecto no registra una queja`() {
        val outsider = userWithRole("COMMUNITY_RELATIONS_OFFICER")
        val response = create("grievance", attentionAttributes(testProject(), outsider, "QJ-"), outsider.token)
        assertEquals(HttpStatus.FORBIDDEN, response.status, response.body)
        assertEquals("NOT_ASSIGNED", code(response))
    }
}
