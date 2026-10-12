package sgspe.dashboard

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F6QueryApiTest
import tools.jackson.databind.JsonNode
import java.util.UUID

// el tablero territorial y la ficha contextual (dashboards.md §4.8 y §5.9; especificación de F6 §3.2 y §5): KPI-TER-01 con
// datos de cada tipo en una localidad y ceros en otra; la postura no disponible para quien no la lee; un proyecto no asignado
// da ceros; KPI-TER-04 con una plataforma sobre un predio PROPIEDAD y un acceso sobre territorio comunal; TERRITORY_REQUIRED
class TerritoryDashboardApiTest : F6QueryApiTest() {
    private val october = "periodStart=2026-10-01&periodEnd=2026-10-31"

    private fun summary(
        query: String,
        token: String = this.token
    ): Map<String, JsonNode> =
        get("/api/sgspe/dashboards/indicators/KPI-TER-01?$query", token)["breakdowns"]
            .values()
            .single { it["by"].asString() == "components" }["rows"]
            .values()
            .associateBy { it["key"].asString() }

    private fun indicator(
        body: JsonNode,
        code: String
    ): JsonNode = body["indicators"].values().first { it["code"].asString() == code }

    private class Place(
        val project: UUID,
        val place: UUID,
        val empty: UUID
    )

    // una localidad con un grupo de interés, una interacción realizada el 2026-10-06, un compromiso y una queja; y otra vacía
    private fun place(): Place {
        val (project, place, empty) =
            runBlocking { Triple(utmProject(), locality("Caserío Ejemplo", at(0.0, -120_000.0)), locality("Caserío Ejemplo Vacío", at(500.0, -120_000.0))) }
        val officer = member("COMMUNITY_RELATIONS_OFFICER", project.toString())
        stakeholder(project.toString(), mapOf("locality" to place.toString()))
        engagement(project.toString(), "REUNION", officer.token, mapOf("held_at" to "2026-10-06T15:00:00Z", "locality" to place.toString())).also {
            participant(it, officer.token)
            move("engagement", it, "register_held_unplanned", officer.token)
        }
        commitment(project.toString(), mapOf("locality" to place.toString()))
        grievance(project.toString(), attributes = mapOf("locality" to place.toString()))
        return Place(project, place, empty)
    }

    @Test
    fun `la ficha de una localidad con datos y la de otra sin datos`() {
        val p = place()
        val full = summary("project=${p.project}&locality=${p.place}&$october")
        assertEquals(
            listOf("KPI-STK-02", "KPI-ENG-01", "KPI-COM-02", "KPI-COM-03", "KPI-GRV-02", "KPI-RSK-01", "programs", "KPI-INV-03", "lastActivity"),
            full.keys.toList()
        )
        assertTrue(full.values.all { it["available"].asBoolean() }, "$full")
        assertEquals(1, full.getValue("KPI-STK-02")["value"].asInt())
        assertEquals(1, full.getValue("KPI-ENG-01")["value"].asInt())
        assertEquals(1, full.getValue("KPI-COM-02")["value"].asInt())
        assertEquals("2026-10-06", full.getValue("lastActivity")["value"].asString())
        val none = summary("project=${p.project}&locality=${p.empty}&$october")
        assertEquals(0, none.getValue("KPI-STK-02")["value"].asInt())
        assertEquals(0, none.getValue("KPI-ENG-01")["value"].asInt())
        assertEquals(0, none.getValue("KPI-COM-02")["value"].asInt())
        assertEquals(0, none.getValue("programs")["value"].asInt())
        assertTrue(absent(none.getValue("lastActivity")["value"]))
    }

    @Test
    fun `quien no lee la postura solo pierde KPI-STK-02 y un proyecto no asignado da ceros`() {
        val p = place()
        val analyst = member("ANALYST", p.project.toString())
        val rows = summary("project=${p.project}&locality=${p.place}&$october", analyst.token)
        val stance = rows.getValue("KPI-STK-02")
        assertFalse(stance["available"].asBoolean())
        assertEquals("FIELD_NOT_READABLE", stance["unavailableReason"].asString())
        assertEquals(listOf("stakeholder.stance"), stance["missingFields"].values().map { it.asString() })
        assertEquals(1, rows.getValue("KPI-ENG-01")["value"].asInt())
        val outsider = userWithRole("ANALYST")
        val foreign = summary("project=${p.project}&locality=${p.place}&$october", outsider.token)
        assertEquals(0, foreign.getValue("KPI-ENG-01")["value"].asInt())
        assertEquals(0, foreign.getValue("KPI-COM-02")["value"].asInt())
    }

    @Test
    fun `con una comunidad la postura dice que ignora el filtro y sin territorio es TERRITORY_REQUIRED`() {
        val project = runBlocking { utmProject() }
        val community = runBlocking { communityWithTerritory("Comunidad Ejemplo", rect(0.0, -121_000.0, 500.0, -120_500.0, multi = true)) }
        val rows = summary("project=$project&community=$community&$october")
        assertEquals(listOf("community"), rows.getValue("KPI-STK-02")["ignoredFilters"].values().map { it.asString() })
        val bare = get("/api/sgspe/dashboards/indicators/KPI-TER-01?project=$project")
        assertFalse(bare["available"].asBoolean())
        assertEquals("TERRITORY_REQUIRED", bare["unavailableReason"].asString())
    }

    @Test
    fun `el tablero exige el proyecto, trae KPI-TER-02 a 04 y con territorio la ficha primero`() {
        val y = -130_000.0
        val project = runBlocking { utmProject() }
        val area = area(project, boundary = rect(0.0, y, 1000.0, y + 1000, multi = true))
        val place = localityWith("Caserío Ejemplo", location = at(100.0, y + 100))
        val community = runBlocking { communityWithTerritory("Comunidad Ejemplo", rect(3000.0, y, 4000.0, y + 1000, multi = true)) }
        areaMember(area, "locality", place)
        areaMember(area, "community", community)
        approveArea(area, project)
        runBlocking {
            parcel(uniqueName("PR-"), "PROPIEDAD", rect(1500.0, y, 2500.0, y + 1000))
            component(project, uniqueName("PLT-"), "PLATAFORMA", rect(1900.0, y + 400, 2100.0, y + 600))
            component(project, uniqueName("ACC-"), "ACCESO", path(3200.0 to y + 500, 3800.0 to y + 500))
        }
        assertEquals("PROJECT_REQUIRED", failing("/api/sgspe/dashboards/territory", HttpStatus.BAD_REQUEST))
        val board = get("/api/sgspe/dashboards/territory?project=$project")
        assertEquals(listOf("KPI-TER-02", "KPI-TER-03", "KPI-TER-04"), board["indicators"].values().map { it["code"].asString() })
        val surface = indicator(board, "KPI-TER-02")
        assertEquals(record("area_of_influence", area.toString())["attributes"]["calculated_area_ha"].asDouble(), surface["value"].asDouble())
        val members = indicator(board, "KPI-TER-03")
        assertEquals(2, members["value"].asInt())
        val land = indicator(board, "KPI-TER-04")
        assertEquals(2, land["value"].asInt())
        val tenure =
            land["breakdowns"].values().single { it["by"].asString() == "tenureType" }["rows"].values().associate {
                it["key"].asString() to
                    it["value"].asInt()
            }
        assertEquals(1, tenure["PROPIEDAD"])
        assertEquals(1, tenure["TERRITORIO COMUNAL"])
        val withPlace = get("/api/sgspe/dashboards/territory?project=$project&locality=$place")
        assertEquals(listOf("KPI-TER-01", "KPI-TER-02", "KPI-TER-03", "KPI-TER-04"), withPlace["indicators"].values().map { it["code"].asString() })
        assertEquals(listOf("locality"), indicator(withPlace, "KPI-TER-04")["ignoredFilters"].values().map { it.asString() })
        // H5: el periodo pedido no cambia KPI-TER-02 a 04 y lo declaran
        val withPeriod = get("/api/sgspe/dashboards/territory?project=$project&periodStart=2026-10-01&periodEnd=2026-10-31")
        for (code in listOf("KPI-TER-02", "KPI-TER-03", "KPI-TER-04")) {
            assertEquals(listOf("periodStart", "periodEnd"), indicator(withPeriod, code)["ignoredFilters"].values().map { it.asString() }, code)
        }
        assertEquals(land["value"].asInt(), indicator(withPeriod, "KPI-TER-04")["value"].asInt())
    }
}
