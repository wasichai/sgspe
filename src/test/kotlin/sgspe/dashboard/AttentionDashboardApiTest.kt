package sgspe.dashboard

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F4ApiTest
import tools.jackson.databind.JsonNode

// el tablero attention (dashboards.md §5.7 y §8, api.md §4.11): los diez KPI-GRV en orden, cada planteamiento una vez, y las QCS
// confidenciales fuera para quien no las ve, con confidentialHidden en el tablero y en cada indicador; el gestor de quejas y el
// ADMIN las cuentan y no reciben la marca
class AttentionDashboardApiTest : F4ApiTest() {
    private fun dashboard(account: Account? = null): JsonNode =
        tree(
            send(
                "GET",
                "/api/sgspe/dashboards/attention?project=${testProject()}&periodStart=2026-10-01&periodEnd=2026-10-31",
                null,
                HttpStatus.OK,
                account?.token ?: token
            )
        )

    private fun received(body: JsonNode): Int = body["indicators"].values().first { it["code"].asString() == "KPI-GRV-01" }["value"].asInt()

    private fun hidden(node: JsonNode): Boolean = node["confidentialHidden"]?.asBoolean() == true

    @Test
    fun `el tablero trae los diez KPI-GRV en orden y cuenta una vez la QCS reclasificada`() {
        grievance(testProject())
        inquiry(testProject(), attributes = mapOf("reclassified_from_grievance" to grievance(testProject())))
        val body = dashboard()
        assertEquals((1..10).map { "KPI-GRV-%02d".format(it) }, body["indicators"].values().map { it["code"].asString() })
        assertEquals(2, received(body))
    }

    @Test
    fun `una QCS confidencial queda fuera para otro relacionista, con la marca, y el gestor y el ADMIN la cuentan`() {
        val receiver = member("COMMUNITY_RELATIONS_OFFICER")
        val colleague = member("COMMUNITY_RELATIONS_OFFICER")
        val manager = member("GRIEVANCE_MANAGER")
        grievance(testProject(), receiver)
        grievance(testProject(), receiver, mapOf("confidential" to true))
        val blind = dashboard(colleague)
        assertEquals(1, received(blind))
        assertTrue(hidden(blind))
        assertTrue(blind["indicators"].values().all { hidden(it) })
        val engagement =
            tree(send("GET", "/api/sgspe/dashboards/indicators/KPI-ENG-05?project=${testProject()}", null, HttpStatus.OK, colleague.token))
        assertTrue(hidden(engagement))
        for (account in listOf(manager, null)) {
            val full = dashboard(account)
            assertEquals(2, received(full))
            assertFalse(hidden(full))
            assertFalse(full["indicators"].values().any { hidden(it) })
        }
        val commitments = tree(send("GET", "/api/sgspe/dashboards/commitments?project=${testProject()}", null, HttpStatus.OK, colleague.token))
        assertFalse(commitments.has("confidentialHidden"))
    }
}
