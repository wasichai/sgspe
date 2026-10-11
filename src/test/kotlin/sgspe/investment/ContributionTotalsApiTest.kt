package sgspe.investment

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import sgspe.ContributionApiTest
import sgspe.common.DerivedFieldsJob
import java.math.BigDecimal

// total_amount del aporte (elección 12): lo mantiene ContributionTotalsListener con cada ítem y en la aprobación, y JOB-02 lo
// corrige mientras el aporte está en requested
class ContributionTotalsApiTest : ContributionApiTest() {
    @Autowired
    private lateinit var job: DerivedFieldsJob

    private fun total(id: String): BigDecimal = record("social_contribution", id)["attributes"]["total_amount"].decimalValue()

    @Test
    fun `cada item mueve el total del aporte y la aprobacion lo deja escrito`() {
        val id = requestedContribution(items = listOf(300 to "BIEN", 150 to "SERVICIO"))
        assertEquals(0, BigDecimal("450").compareTo(total(id)))
        val item = tree(itemFor(id, "49.50").body)["id"].asString()
        assertEquals(0, BigDecimal("499.5").compareTo(total(id)))
        assertTrue(exchange("DELETE", "/api/objects/contribution_item/records/$item", null, token).status.is2xxSuccessful)
        assertEquals(0, BigDecimal("450").compareTo(total(id)))
        move("social_contribution", id, "approve", member("APPROVER").token, "aprobación")
        assertEquals(0, BigDecimal("450").compareTo(total(id)))
    }

    @Test
    fun `JOB-02 corrige el total de un aporte en requested con el motivo del trabajo`() {
        val id = requestedContribution(items = listOf(300 to "BIEN"))
        platformPatch("social_contribution", id, mapOf("total_amount" to 1))
        assertNotNull(runBlocking { job.runOnce(full = true) }, "otro nodo o la programación tenía el candado")
        assertEquals(0, BigDecimal("300").compareTo(total(id)))
        val history = tree(send("GET", "/api/objects/social_contribution/records/$id/history", null, HttpStatus.OK))
        assertEquals(ContributionTotalsRecalculator.REASON, history.values().first()["reason"].asString())
    }
}
