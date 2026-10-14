package sgspe.dashboard

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F8IndicatorsApiTest
import tools.jackson.databind.JsonNode
import java.util.UUID

// el tablero executive (dashboards.md §5.1, api.md §4.11): las tiles en su orden con fixtures, la tendencia sin valor escalar,
// la tabla por proyecto con las mismas cifras que cada proyecto pedido solo, el tope de proyectos (Review Focus 5), el filtro
// de territorio de KPI-EXE-02 y un tablero vacío sin proyectos
class ExecutiveDashboardApiTest : F8IndicatorsApiTest() {
    private val september = "periodStart=2026-09-01&periodEnd=2026-09-30"

    private fun indicator(
        body: JsonNode,
        code: String
    ): JsonNode = body["indicators"].values().first { it["code"].asString() == code }

    // un proyecto activo con una interacción realizada en septiembre, un compromiso, una queja y una alerta detectada
    private fun busyProject(): String {
        val project = activeProject()
        val officer = member("COMMUNITY_RELATIONS_OFFICER", project)
        engagement(project, "REUNION", officer.token, mapOf("held_at" to "2026-09-10T15:00:00Z")).also {
            participant(it, officer.token)
            move("engagement", it, "register_held_unplanned", officer.token)
        }
        commitment(project)
        grievance(project)
        earlyWarning(project)
        return project
    }

    @Test
    fun `las tiles van en su orden con las cifras del proyecto y la tendencia no tiene valor`() {
        val project = busyProject()
        val body = get("/api/sgspe/dashboards/executive?project=$project&$september")
        assertEquals("executive", body["dashboard"].asString())
        assertEquals(ExecutiveKpis.TILES, body["indicators"].values().map { it["code"].asString() })
        assertEquals(1, indicator(body, "KPI-EXE-01")["value"].asInt())
        assertEquals(1, indicator(body, "KPI-EXE-02")["value"].asInt())
        assertEquals(1, indicator(body, "KPI-ENG-01")["value"].asInt())
        assertEquals(1, indicator(body, "KPI-GRV-02")["value"].asInt())
        val trend = indicator(body, "KPI-EXE-03")
        assertTrue(trend["available"].asBoolean())
        assertTrue(trend["value"].isNull)
        assertEquals(12, trend["series"].size())
        assertEquals(1, trend["series"].values().last()["engagements"].asInt())
    }

    @Test
    fun `byProject trae una fila por proyecto con las mismas cifras que cada uno pedido solo`() {
        val busy = busyProject()
        val quiet = activeProject()
        val body = get("/api/sgspe/dashboards/executive?project=$busy&project=$quiet&$september")
        val rows = body["byProject"].values().associateBy { it["project"].asString() }
        assertEquals(setOf(busy, quiet), rows.keys)
        for (project in listOf(busy, quiet)) {
            val alone = get("/api/sgspe/dashboards/executive?project=$project&$september")
            val row = rows.getValue(project)
            assertEquals(ExecutiveKpis.BY_PROJECT, row["indicators"].values().map { it["code"].asString() })
            for (code in ExecutiveKpis.BY_PROJECT) {
                assertEquals(
                    indicator(alone, code)["value"],
                    row["indicators"].values().first { it["code"].asString() == code }["value"],
                    "$project $code"
                )
            }
            assertTrue(row["indicators"].values().none { it.has("breakdowns") || it.has("series") })
        }
        assertEquals("Proyecto Ejemplo A", rows.getValue(busy)["name"].asString())
    }

    // KPI-EXE-02 con territorio: la alerta cuenta solo si su localidad cae en el filtro (la relación early_warning.locality)
    @Test
    fun `KPI-EXE-02 con locality cuenta solo las alertas de esa localidad`() {
        val project = activeProject()
        val place = kotlinx.coroutines.runBlocking { locality("Caserío Ejemplo Alerta", null) }.toString()
        val other = kotlinx.coroutines.runBlocking { locality("Caserío Ejemplo Otro", null) }.toString()
        earlyWarning(project, attributes = mapOf("locality" to place))
        earlyWarning(project, attributes = mapOf("locality" to other))
        earlyWarning(project)
        assertEquals(3, indicator(get("/api/sgspe/dashboards/executive?project=$project&$september"), "KPI-EXE-02")["value"].asInt())
        val here = get("/api/sgspe/dashboards/executive?project=$project&locality=$place&$september")
        assertEquals(1, indicator(here, "KPI-EXE-02")["value"].asInt())
        val none = get("/api/sgspe/dashboards/executive?project=$project&locality=${UUID.randomUUID()}&$september")
        assertEquals(0, indicator(none, "KPI-EXE-02")["value"].asInt())
    }

    // Review Focus 5 (decisión 24): pedir más proyectos que el tope es un 400; tenerlos por defecto no
    @Test
    fun `mas proyectos pedidos que el tope es 400 TOO_MANY_RECORDS sobre project`() {
        val query = (1..26).joinToString("&") { "project=${UUID.randomUUID()}" }
        val response = exchange("GET", "/api/sgspe/dashboards/executive?$query")
        assertEquals(HttpStatus.BAD_REQUEST, response.status, response.body)
        assertEquals("TOO_MANY_RECORDS", code(response))
        assertEquals(listOf("project"), problemFields(response.body))
    }

    @Test
    fun `con mas proyectos por defecto que el tope salen las tiles sin byProject`() {
        // el admin lee todos los proyectos del tenant de pruebas; se asegura creando más del tope
        repeat(26) { project() }
        val body = get("/api/sgspe/dashboards/executive?periodStart=2026-09-01&periodEnd=2026-09-30")
        assertEquals(ExecutiveKpis.TILES, body["indicators"].values().map { it["code"].asString() })
        assertEquals(true, body["byProjectOmitted"].asBoolean())
        assertEquals(false, body.has("byProject"))
    }

    @Test
    fun `sin proyectos las tiles salen en cero y byProject vacio`() {
        val loner = userWithRole("ANALYST")
        val body = get("/api/sgspe/dashboards/executive?$september", loner.token)
        assertEquals(0, indicator(body, "KPI-EXE-01")["value"].asInt())
        assertEquals(0, indicator(body, "KPI-COM-02")["value"].asInt())
        assertEquals(0, body["byProject"].size())
    }

    @Test
    fun `el catalogo informa los KPI-EXE disponibles en el tablero ejecutivo`() {
        val catalog = get("/api/sgspe/dashboards/indicators").values().associateBy { it["code"].asString() }
        for (code in listOf("KPI-EXE-01", "KPI-EXE-02", "KPI-EXE-03")) {
            assertTrue(catalog.getValue(code)["available"].asBoolean(), code)
            assertEquals("executive", catalog.getValue(code)["dashboard"].asString(), code)
        }
    }
}
