package sgspe.dashboard

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F8IndicatorsApiTest
import tools.jackson.databind.JsonNode
import java.time.LocalDate
import java.util.UUID

// el tablero monitoring (dashboards.md §5.5 y §10, api.md §4.11): las 13 filas en su orden con su meta y su origen, la cifra
// oficial solo con el pedido de su medición (Review Focus 3), #2 y #3 «no disponible» sin stance también cerrados, y sin leer
// el catálogo el tablero sale sin metas ni cifras oficiales (Review Focus 5). las mediciones se crean como la plataforma: el
// endpoint de cierre llega en la Tarea 10 y tiene su propia prueba
class MonitoringDashboardApiTest : F8IndicatorsApiTest() {
    // wasichai aplica el defaultValue (2) a un alta que omite el campo (UPS-15 resuelta en 0.5.0): un proyecto «sin meta propia» la envía en null
    private fun catalogProject(): String = activeProject(attributes = mapOf("meetings_per_month_target" to null))

    private fun board(
        query: String,
        token: String = this.token
    ): Map<String, JsonNode> = get("/api/sgspe/dashboards/monitoring?$query", token)["rows"].values().associateBy { it["code"].asString() }

    private fun period(
        start: LocalDate,
        end: LocalDate
    ) = "periodStart=$start&periodEnd=$end"

    // quien cierra la medición: un usuario real del tenant (la regla de closed_by lo comprueba), uno por prueba
    private val closer by lazy { userWithRole("SOCIAL_MANAGER") }

    private fun measurement(
        code: String,
        project: String?,
        start: LocalDate,
        end: LocalDate,
        value: Int
    ): String =
        platformCreate(
            "indicator_measurement",
            mapOf(
                "indicator" to indicatorId(code),
                "project" to project,
                "period_start_date" to start.toString(),
                "period_end_date" to end.toString(),
                "measured_value" to value,
                "closed_by_user_id" to closer.id,
                "closed_by_user_email" to closer.email
            )
        )

    @Test
    fun `las 13 filas van en su orden con la meta del proyecto o la del catalogo`() {
        val (start, end) = lastMonth()
        val own = activeProject(attributes = mapOf("meetings_per_month_target" to 3))
        val plain = catalogProject()
        val body = get("/api/sgspe/dashboards/monitoring?project=$own&${period(start, end)}")
        assertEquals(MonitoringTable.CODES, body["indicators"].values().map { it["code"].asString() })
        assertEquals((1..13).toList(), body["rows"].values().map { it["number"].asInt() })
        assertEquals(MonitoringTable.CODES.map { indicatorId(it) }, body["rows"].values().map { it["indicatorId"].asString() })
        val meetings = body["rows"].values().single { it["code"].asString() == "KPI-ENG-02" }
        assertEquals(3 to "PROJECT", meetings["target"].asInt() to meetings["targetSource"].asString())
        val catalog = board("project=$plain&${period(start, end)}").getValue("KPI-ENG-02")
        assertEquals(2 to "CATALOG", catalog["target"].asInt() to catalog["targetSource"].asString())
        val first = board("project=$plain&${period(start, end)}").getValue("KPI-STK-04")
        assertEquals("NONE", first["targetSource"].asString())
        assertTrue(first["target"].isNull)
        assertEquals("Reporte de cobertura de grupos de interés contactados", first["verificationMeans"].asString())
    }

    // Review Focus 3
    @Test
    fun `la cifra oficial sale solo con el pedido de su medicion`() {
        val (start, end) = lastMonth()
        val project = activeProject()
        val other = activeProject()
        val id = measurement("KPI-COM-08", project, start, end, 7)
        val official = board("project=$project&${period(start, end)}").getValue("KPI-COM-08")["official"]
        assertEquals(id to 7, official["measurement"].asString() to official["value"].asInt())
        for (query in listOf(
            "project=$project&${period(start, end)}&locality=${UUID.randomUUID()}",
            "project=$project&project=$other&${period(start, end)}",
            "project=$project&${period(start.minusMonths(1), start.minusDays(1))}",
            "project=$other&${period(start, end)}"
        )) {
            assertTrue(board(query).getValue("KPI-COM-08")["official"].isNull, query)
        }
        // la del tenant, en un día propio (uniqueDay): sin project, solo para quien lee todos los proyectos
        val (oldStart, oldEnd) = uniqueDay()
        measurement("KPI-COM-08", null, oldStart, oldEnd, 9)
        assertEquals(9, board(period(oldStart, oldEnd)).getValue("KPI-COM-08")["official"]["value"].asInt())
        val social = member("SOCIAL_MANAGER", project)
        assertTrue(board(period(oldStart, oldEnd), social.token).getValue("KPI-COM-08")["official"].isNull)
    }

    // Review Focus 3: dashboards.md §8
    @Test
    fun `2 y 3 no disponibles sin stance, tambien cerrados`() {
        val (start, end) = lastMonth()
        val project = activeProject()
        measurement("KPI-STK-05", project, start, end, 50)
        val admin = board("project=$project&${period(start, end)}").getValue("KPI-STK-05")
        assertEquals(50, admin["official"]["value"].asInt())
        val analyst = board("project=$project&${period(start, end)}", member("ANALYST", project).token)
        for (code in listOf("KPI-STK-05", "KPI-STK-06")) {
            val row = analyst.getValue(code)
            assertFalse(row["available"].asBoolean(), code)
            assertTrue(row["value"].isNull, code)
            assertTrue(row["official"].isNull, code)
        }
    }

    // un rol de prueba creado por la API (no está en roles.json, donde todos leen el catálogo desde R1): lee lo que lee
    // KPI-ENG-02 pero no indicator ni indicator_measurement, y está asignado al proyecto
    private fun catalogBlind(project: String): Account {
        val name = "PRUEBA_" + uniqueName("").uppercase().filter { it.isLetterOrDigit() }
        send("POST", "/api/roles", mapOf("name" to name, "label" to "Rol de prueba"), HttpStatus.CREATED)
        val grants = listOf("project", "engagement", "engagement_plan").map { mapOf("objectName" to it, "action" to "READ") }
        send("PUT", "/api/roles/$name/permissions", mapOf("permissions" to grants), HttpStatus.OK)
        return userWithRole(name).also { assign(it, project, "READ_ONLY") }
    }

    // Review Focus 5: sin leer indicator ni indicator_measurement, ni metas ni cifras oficiales
    @Test
    fun `sin leer el catalogo el tablero sale sin metas ni cifras oficiales`() {
        val (start, end) = lastMonth()
        val project = activeProject()
        measurement("KPI-COM-08", project, start, end, 7)
        val rows = board("project=$project&${period(start, end)}", catalogBlind(project).token)
        assertEquals(13, rows.size)
        assertTrue(rows.values.all { it["targetSource"].asString() == "NONE" && it["official"].isNull && it["indicatorId"].isNull })
    }

    // FIELD_AGENT lee el catálogo pero no las mediciones (security.md §7.8): las metas sí, la cifra oficial no
    @Test
    fun `sin leer las mediciones el tablero sale con metas y sin cifras oficiales`() {
        val (start, end) = lastMonth()
        val project = catalogProject()
        measurement("KPI-COM-08", project, start, end, 7)
        val rows = board("project=$project&${period(start, end)}", member("FIELD_AGENT", project).token)
        assertTrue(rows.values.all { it["official"].isNull })
        val meetings = rows.getValue("KPI-ENG-02")
        assertTrue(meetings["available"].asBoolean())
        assertEquals("CATALOG", meetings["targetSource"].asString())
    }

    // R1: una sola fuente para la meta de KPI-ENG-02; el monitoreo y el tablero de relacionamiento la leen igual. edita el
    // catálogo compartido y lo deja como estaba aunque falle
    @Test
    fun `la meta de KPI-ENG-02 sin parametro del proyecto sigue al catalogo en los dos tableros`() {
        val (start, end) = lastMonth()
        val project = catalogProject()
        val id = indicatorId("KPI-ENG-02")
        val stored = attributesOf(record("indicator", id))
        send("PUT", "/api/objects/indicator/records/$id", mapOf("attributes" to stored + ("target_value" to 4)), HttpStatus.OK)
        try {
            val query = "project=$project&${period(start, end)}"
            assertEquals(4, board(query).getValue("KPI-ENG-02")["target"].asInt())
            val engagement = get("/api/sgspe/dashboards/indicators/KPI-ENG-02?$query")
            assertEquals(4, engagement["denominator"].asInt())
            // FIELD_AGENT lee el catálogo desde R1: la misma meta
            val agent = get("/api/sgspe/dashboards/indicators/KPI-ENG-02?$query", member("FIELD_AGENT", project).token)
            assertEquals(4, agent["denominator"].asInt())
            // quien no lee indicator no recibe otra meta: KPI-ENG-02 no disponible, por el objeto que falta, también en el catálogo
            val blind = catalogBlind(project)
            val hidden = get("/api/sgspe/dashboards/indicators/KPI-ENG-02?$query", blind.token)
            assertFalse(hidden["available"].asBoolean())
            assertEquals("OBJECT_NOT_READABLE", hidden["unavailableReason"].asString())
            assertEquals(listOf("indicator.target_value"), hidden["missingFields"].values().map { it.asString() })
            val listed = get("/api/sgspe/dashboards/indicators", blind.token).values().single { it["code"].asString() == "KPI-ENG-02" }
            assertFalse(listed["available"].asBoolean())
        } finally {
            send("PUT", "/api/objects/indicator/records/$id", mapOf("attributes" to stored), HttpStatus.OK)
        }
    }

    @Test
    fun `el catalogo ubica los KPI sin zona propia en monitoring`() {
        val catalog = get("/api/sgspe/dashboards/indicators").values().associateBy { it["code"].asString() }
        for (code in listOf("KPI-STK-04", "KPI-STK-05", "KPI-STK-07", "KPI-ENG-06", "KPI-ENG-07", "KPI-ENG-08")) {
            assertEquals("monitoring", catalog.getValue(code)["dashboard"].asString(), code)
        }
    }
}
