package sgspe.dashboard

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F4ApiTest
import tools.jackson.databind.JsonNode

// el tablero risks (dashboards.md §5.8 y §11, api.md §4.11): los cinco KPI-RSK en el orden de sus zonas, disponibles desde F4,
// sobre los riesgos activos y sin la marca de las QCS confidenciales
class RisksDashboardApiTest : F4ApiTest() {
    private fun dashboard(): JsonNode =
        tree(send("GET", "/api/sgspe/dashboards/risks?project=${testProject()}&periodStart=2026-10-01&periodEnd=2026-10-31", null, HttpStatus.OK))

    private fun indicator(
        body: JsonNode,
        code: String
    ): JsonNode = body["indicators"].values().first { it["code"].asString() == code }

    @Test
    fun `el tablero cuenta los riesgos activos con su ultima revision`() {
        val reviewer = member("SOCIAL_RISK_MANAGER")
        val high = socialRisk(testProject())
        val response =
            exchange(
                "POST",
                "/api/sgspe/social-risks/$high/assessments",
                mapOf("attributes" to mapOf("assessment_date" to "2026-10-05", "probability" to 3, "impact" to 4)),
                reviewer.token,
                withReason("revisión")
            )
        assertEquals(HttpStatus.CREATED, response.status, response.body)
        mitigationMeasure(high, reviewer)
        val closed = socialRisk(testProject())
        move("social_risk", closed, "close_identified", reviewer.token, "el riesgo dejó de existir")
        socialRisk(testProject())
        val body = dashboard()
        assertEquals(listOf("KPI-RSK-02", "KPI-RSK-01", "KPI-RSK-03", "KPI-RSK-04", "KPI-RSK-05"), body["indicators"].values().map { it["code"].asString() })
        assertEquals(1, indicator(body, "KPI-RSK-01")["value"].asInt())
        val heatmap = indicator(body, "KPI-RSK-02")
        assertEquals(1, heatmap["value"].asInt())
        val cell =
            heatmap["breakdowns"]
                .values()
                .first { it["by"].asString() == "matrix" }["rows"]
                .values()
                .first { it["key"].asString() == "3-4" }
        assertEquals(1, cell["value"].asInt())
        assertEquals("ALTO", cell["level"].asString())
        assertEquals(1, heatmap["breakdowns"].values().first { it["by"].asString() == "assessment" }["rows"][0]["value"].asInt())
        assertEquals(0.0, indicator(body, "KPI-RSK-05")["value"].asDouble())
        assertFalse(body.has("confidentialHidden"))
        assertFalse(body["indicators"].values().any { it.has("confidentialHidden") })
    }

    @Test
    fun `el catalogo informa los KPI-RSK disponibles desde F4`() {
        val catalog =
            tree(
                send("GET", "/api/sgspe/dashboards/indicators", null, HttpStatus.OK)
            ).values().filter { it["code"].asString().startsWith("KPI-RSK-") }
        assertEquals(5, catalog.size)
        assertTrue(catalog.all { it["available"].asBoolean() && it["dashboard"].asString() == "risks" })
    }
}
