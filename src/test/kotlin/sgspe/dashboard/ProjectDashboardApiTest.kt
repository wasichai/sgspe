package sgspe.dashboard

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F8IndicatorsApiTest

// el tablero project (dashboards.md §5.2): exige un solo project (400 PROJECT_REQUIRED, también sin project y con «mis
// proyectos»), trae las zonas de §5.2 en su orden y la postura «no disponible» a quien no la lee; el mapa lo arma el portal
class ProjectDashboardApiTest : F8IndicatorsApiTest() {
    private val zones =
        listOf(
            "KPI-STK-01",
            "KPI-STK-02",
            "KPI-STK-03",
            "KPI-ENG-01",
            "KPI-ENG-02",
            "KPI-COM-02",
            "KPI-COM-03",
            "KPI-COM-04",
            "KPI-COM-06",
            "KPI-GRV-01",
            "KPI-GRV-02",
            "KPI-GRV-06",
            "KPI-RSK-01",
            "KPI-RSK-02",
            "KPI-INV-02",
            "KPI-INV-03",
            "KPI-INV-04"
        )

    @Test
    fun `sin un solo project es 400 PROJECT_REQUIRED`() {
        val a = activeProject()
        val b = activeProject()
        for (query in listOf("", "?project=$a&project=$b")) {
            val response = exchange("GET", "/api/sgspe/dashboards/project$query")
            assertEquals(HttpStatus.BAD_REQUEST, response.status, response.body)
            assertEquals("PROJECT_REQUIRED", code(response))
            assertEquals(listOf("project"), problemFields(response.body))
        }
    }

    @Test
    fun `trae las zonas en su orden y la postura no disponible a quien no la lee`() {
        val project = activeProject()
        commitment(project)
        val body = get("/api/sgspe/dashboards/project?project=$project")
        assertEquals(zones, body["indicators"].values().map { it["code"].asString() })
        assertEquals(1, body["indicators"].values().first { it["code"].asString() == "KPI-COM-02" }["value"].asInt())
        val analyst = member("ANALYST", project)
        val stance = get("/api/sgspe/dashboards/project?project=$project", analyst.token)["indicators"].values().first { it["code"].asString() == "KPI-STK-02" }
        assertFalse(stance["available"].asBoolean())
        assertEquals("FIELD_NOT_READABLE", stance["unavailableReason"].asString())
    }
}
