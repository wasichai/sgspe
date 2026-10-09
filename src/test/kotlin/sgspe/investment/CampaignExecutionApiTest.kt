package sgspe.investment

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import sgspe.F3DomainApiTest
import sgspe.common.DerivedFieldsJob
import sgspe.common.SgspeWrite
import wasichai.core.data.RecordRequest
import wasichai.core.data.RecordService
import java.util.UUID

// CampaignExecutionRecalculator (JOB-02): corrige el ejecutado dañado de la campaña con el motivo que nombra al trabajo, y una
// vuelta sin cambios no escribe
class CampaignExecutionApiTest : F3DomainApiTest() {
    @Autowired
    private lateinit var job: DerivedFieldsJob

    @Autowired
    private lateinit var service: RecordService

    private fun damage(campaign: String) {
        runBlocking {
            service.asPlatform(organizationId()) {
                val id = UUID.fromString(campaign)
                val stored = service.get("exploration_campaign", id).attributes
                withContext(SgspeWrite) {
                    service.update(
                        "exploration_campaign",
                        id,
                        RecordRequest(stored + mapOf("executed_social_amount" to 999, "other_currency_items_count" to 7)),
                        "prueba: ejecutado dañado"
                    )
                }
            }
        }
    }

    @Test
    fun `JOB-02 corrige el ejecutado de la campana y una vuelta sin cambios no escribe`() {
        val project = testProject()
        val campaign = createRecord("exploration_campaign", mapOf("project" to project, "name" to "Campaña", "social_support_cap" to 1000, "currency" to "PEN"))
        approvedCommitment(project, attributes = mapOf("exploration_campaign" to campaign, "amount" to 400))
        damage(campaign)
        assertNotNull(runBlocking { job.runOnce(full = true) }, "otro nodo o la programación tenía el candado")
        val fixed = record("exploration_campaign", campaign)
        assertEquals(0, fixed["attributes"]["executed_social_amount"].asDouble().compareTo(0.0))
        assertEquals(0, fixed["attributes"]["other_currency_items_count"].asInt())
        val history = tree(send("GET", "/api/objects/exploration_campaign/records/$campaign/history", null, org.springframework.http.HttpStatus.OK))
        assertEquals(CampaignExecutionRecalculator.REASON, history.values().first()["reason"].asString())
        assertEquals(
            "derivado: ejecutado de la campaña (JOB-02 DerivedFieldsJob)",
            CampaignExecutionRecalculator.REASON.let {
                DerivedFieldsJob.reason("ejecutado de la campaña")
            }
        )
        val updatedAt = fixed["updatedAt"].asString()
        assertNotNull(runBlocking { job.runOnce(full = true) })
        assertEquals(updatedAt, record("exploration_campaign", campaign)["updatedAt"].asString())
    }
}
