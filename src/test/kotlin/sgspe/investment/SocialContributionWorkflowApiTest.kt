package sgspe.investment

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.ContributionApiTest

// WF-P (workflows.md, especificación de F5 §3.3 a §3.5, §5): las reglas de InvestmentWriteGuard por la API genérica, por la
// consulta en seco y por el comando de entrega. cada tarea del plan 5.2a suma sus casos al final
class SocialContributionWorkflowApiTest : ContributionApiTest() {
    private fun idOf(response: Response): String = tree(response.body)["id"].asString()

    // decisión 1: después de aprobar los ítems no cambian; un cambio es otro aporte
    @Test
    fun `un item nuevo, cambiado o borrado despues de aprobar es ITEMS_FROZEN`() {
        val id = requestedContribution()
        val item = idOf(itemFor(id, 120))
        move("social_contribution", id, "approve", member("APPROVER").token, "aprobación")
        val added = itemFor(id, 50)
        assertEquals(HttpStatus.CONFLICT, added.status, added.body)
        assertEquals("ITEMS_FROZEN", code(added))
        assertEquals(listOf("social_contribution"), problemFields(added.body))
        assertEquals("ITEMS_FROZEN", code(edit("contribution_item", item, mapOf("line_amount" to 500))))
        val deleted = exchange("DELETE", "/api/objects/contribution_item/records/$item", null, token)
        assertEquals(HttpStatus.CONFLICT, deleted.status, deleted.body)
        assertEquals("ITEMS_FROZEN", code(deleted))
    }

    @Test
    fun `un item no cambia de aporte, y en requested se cambia y se borra`() {
        val id = requestedContribution()
        val other = requestedContribution()
        val item = idOf(itemFor(id, 80))
        assertEquals("ITEMS_FROZEN", code(edit("contribution_item", item, mapOf("social_contribution" to other))))
        assertEquals(HttpStatus.OK, edit("contribution_item", item, mapOf("line_amount" to 90)).status)
        assertTrue(exchange("DELETE", "/api/objects/contribution_item/records/$item", null, token).status.is2xxSuccessful)
    }

    // Review Focus 3 y elección 7: el PUT del portal reenvía lo leído; solo cambiar un término juzgado es CONTRIBUTION_FROZEN
    @Test
    fun `cambiar la moneda o la campana despues de aprobar es CONTRIBUTION_FROZEN y reenviar lo leido pasa`() {
        val project = testProject()
        val id = approvedWithItems(project)
        val currency = edit("social_contribution", id, mapOf("currency" to "USD"), headers = withReason())
        assertEquals(HttpStatus.CONFLICT, currency.status, currency.body)
        assertEquals("CONTRIBUTION_FROZEN", code(currency))
        assertEquals(listOf("currency"), problemFields(currency.body))
        val campaign = edit("social_contribution", id, mapOf("exploration_campaign" to cappedCampaign(project, 1000)), headers = withReason())
        assertEquals("CONTRIBUTION_FROZEN", code(campaign))
        assertEquals(listOf("exploration_campaign"), problemFields(campaign.body))
        assertEquals(HttpStatus.OK, edit("social_contribution", id, emptyMap(), headers = withReason()).status)
        val other = edit("social_contribution", id, mapOf("occasion" to "Día de la comunidad", "beneficiaries_count" to 120), headers = withReason())
        assertEquals(HttpStatus.OK, other.status, other.body)
    }

    @Test
    fun `beneficiaries_count negativo es OUT_OF_RANGE`() {
        val id = requestedContribution()
        val refused = edit("social_contribution", id, mapOf("beneficiaries_count" to -3), headers = withReason())
        assertEquals(HttpStatus.BAD_REQUEST, refused.status, refused.body)
        assertEquals("OUT_OF_RANGE", code(refused))
        assertEquals(listOf("beneficiaries_count"), problemFields(refused.body))
    }
}
