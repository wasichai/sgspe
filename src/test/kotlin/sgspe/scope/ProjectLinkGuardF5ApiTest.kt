package sgspe.scope

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F5ApiTest

// «mismo proyecto» en BC-10 y en las tareas (elección 5, api.md §3.3): un aporte o una tarea solo referencian registros de su
// proyecto; del mismo proyecto, pasan
class ProjectLinkGuardF5ApiTest : F5ApiTest() {
    private fun contribution(extra: Map<String, Any?>): Response =
        exchange(
            "POST",
            "/api/objects/social_contribution/records",
            mapOf(
                "attributes" to
                    mapOf(
                        "project" to testProject(),
                        "code" to uniqueName("APO-"),
                        "declared_purpose" to "Útiles para la escuela.",
                        "conditions" to "Con acta de recepción.",
                        "currency" to "PEN",
                        "payer_organization" to organization()
                    ) + extra
            ),
            token,
            withReason("alta de prueba")
        )

    @Test
    fun `un aporte con la campana o el acuerdo de otro proyecto es PROJECT_MISMATCH`() {
        val other = project()
        val foreignCampaign = contribution(mapOf("exploration_campaign" to campaign(other)))
        assertEquals(HttpStatus.CONFLICT, foreignCampaign.status, foreignCampaign.body)
        assertEquals("PROJECT_MISMATCH", code(foreignCampaign))
        assertEquals(listOf("exploration_campaign"), problemFields(foreignCampaign.body))
        val foreignAgreement = contribution(mapOf("agreement" to agreement(other)))
        assertEquals("PROJECT_MISMATCH", code(foreignAgreement))
        assertEquals(listOf("agreement"), problemFields(foreignAgreement.body))
        assertEquals(HttpStatus.CREATED, contribution(mapOf("exploration_campaign" to campaign(testProject()), "agreement" to agreement(testProject()))).status)
    }

    @Test
    fun `una tarea con el origen de otro proyecto es PROJECT_MISMATCH`() {
        val response =
            exchange(
                "POST",
                "/api/objects/task/records",
                mapOf("attributes" to mapOf("project" to testProject(), "title" to "Coordinar la visita", "engagement" to engagement(project()))),
                token
            )
        assertEquals(HttpStatus.CONFLICT, response.status, response.body)
        assertEquals("PROJECT_MISMATCH", code(response))
        assertEquals(listOf("engagement"), problemFields(response.body))
        task(testProject())
    }
}
