package sgspe.commitment

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F3DomainApiTest

// P54: un compromiso solo referencia registros de su proyecto (PROJECT_MISMATCH, 409, en el campo vinculado)
class ProjectLinkApiTest : F3DomainApiTest() {
    private fun create(
        project: String,
        links: Map<String, Any?>
    ): Response {
        val owner = userWithRole("COMMITMENT_MANAGER")
        val verifier = userWithRole("COMMITMENT_MANAGER")
        return exchange(
            "POST",
            "/api/objects/commitment/records",
            mapOf("attributes" to commitmentAttributes(project, owner, verifier) + links),
            token,
            withReason()
        )
    }

    private fun campaign(project: String) =
        createRecord(
            "exploration_campaign",
            mapOf(
                "project" to project,
                "name" to "Campaña",
                "social_support_cap" to 1000,
                "currency" to "PEN"
            )
        )

    @Test
    fun `un alta con la campana o el acuerdo de otro proyecto es PROJECT_MISMATCH en ese campo`() {
        val mine = testProject()
        val other = project()
        assertEquals(HttpStatus.CREATED, create(mine, mapOf("exploration_campaign" to campaign(mine), "agreement" to agreement(mine))).status)
        val campaignRefused = create(mine, mapOf("exploration_campaign" to campaign(other)))
        assertEquals(HttpStatus.CONFLICT, campaignRefused.status, campaignRefused.body)
        assertEquals("PROJECT_MISMATCH", code(campaignRefused))
        assertEquals(listOf("exploration_campaign"), problemFields(campaignRefused.body))
        val agreementRefused = create(mine, mapOf("agreement" to agreement(other)))
        assertEquals("PROJECT_MISMATCH", code(agreementRefused))
        assertEquals(listOf("agreement"), problemFields(agreementRefused.body))
    }

    @Test
    fun `cambiar el proyecto del compromiso revisa otra vez cada vinculo, y cambiar solo un vinculo revisa ese`() {
        val mine = testProject()
        val other = project()
        val id = commitment(mine, attributes = mapOf("exploration_campaign" to campaign(mine)))
        val moved = edit("commitment", id, mapOf("project" to other), headers = withReason())
        assertEquals(HttpStatus.CONFLICT, moved.status, moved.body)
        assertEquals("PROJECT_MISMATCH", code(moved))
        assertEquals(listOf("exploration_campaign"), problemFields(moved.body))
        val relinked = edit("commitment", id, mapOf("exploration_campaign" to campaign(other)), headers = withReason())
        assertEquals("PROJECT_MISMATCH", code(relinked))
        assertEquals(HttpStatus.OK, edit("commitment", id, mapOf("exploration_campaign" to campaign(mine)), headers = withReason()).status)
    }
}
