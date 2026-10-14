package sgspe.dashboard

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.DomainApiTest
import sgspe.stakeholder.InfluenceClassifier
import tools.jackson.databind.JsonNode
import java.util.UUID

// StakeholdersDashboardApiTest, EngagementDashboardApiTest y DashboardPermissionsApiTest de la matriz de trazabilidad,
// juntas porque comparten los datos: los tableros `stakeholders` y `engagement` (api.md §4.11), el catálogo (RQ-DSH-036)
// y el «no disponible» (RQ-DSH-032)
class DashboardsApiTest : DomainApiTest() {
    private class Data(
        val project: String,
        val manager: Account,
        val officer: Account
    )

    // los quince indicadores de una evaluación de interés y poder altos
    private val high: Map<String, Any?> = InfluenceClassifier.INDICATORS.associateWith { 4 }

    private fun fixture(): Data {
        val project = projectWithCrs(mapOf("meetings_per_month_target" to 3))
        val manager = member("STAKEHOLDER_MANAGER", project)
        val officer = member("COMMUNITY_RELATIONS_OFFICER", project)
        val profile = profileFields(manager, "AUTORIDAD LOCAL")
        for ((stance, date) in listOf("EN CONTRA" to "2026-04-10", "A FAVOR" to "2026-04-12")) {
            val stakeholder = stakeholder(project, profile)
            send(
                "POST",
                "/api/sgspe/stakeholders/$stakeholder/assessments",
                mapOf("attributes" to high + mapOf("assessment_date" to date, "basis" to "ENTREVISTA", "stance" to stance, "trust_score" to 3)),
                HttpStatus.CREATED,
                manager.token,
                mapOf(REASON to reason("evaluación"))
            )
        }
        val topic = createRecord("topic", mapOf("code" to uniqueName("T-"), "name" to "Agua", "active" to true))
        val meeting = engagement(project, "REUNION INFORMATIVA", officer.token, mapOf("held_at" to "2026-05-12T15:00:00Z"))
        participant(meeting, officer.token, mapOf("represented_people_count" to 10, "sex" to "FEMENINO", "participant_scope" to "AIDS"))
        participant(meeting, officer.token, mapOf("represented_people_count" to 10, "sex" to "MASCULINO", "participant_scope" to "AIIS"))
        link("engagement", meeting, "engagement_topic", topic, officer.token)
        createRecord(
            "engagement_question",
            mapOf("engagement" to meeting, "question_text" to "¿Y el agua?", "topic" to topic, "answered_on_site" to true),
            token = officer.token
        )
        createRecord("engagement_question", mapOf("engagement" to meeting, "question_text" to "¿Y el empleo?", "topic" to topic), token = officer.token)
        move("engagement", meeting, "register_held_unplanned", officer.token)
        engagement(project, "LLAMADA", officer.token, mapOf("held_at" to "2026-05-13T15:00:00Z")).also {
            participant(it, officer.token)
            move("engagement", it, "register_held_unplanned", officer.token)
        }
        return Data(project, manager, officer)
    }

    private fun indicator(
        body: JsonNode,
        code: String
    ): JsonNode = body["indicators"].values().first { it["code"].asString() == code }

    @Test
    fun `el tablero de grupos de interes con sus cinco zonas`() {
        val f = fixture()
        val body =
            tree(
                send(
                    "GET",
                    "/api/sgspe/dashboards/stakeholders?project=${f.project}&periodStart=2026-05-01&periodEnd=2026-05-31",
                    null,
                    HttpStatus.OK,
                    f.manager.token
                )
            )
        assertEquals(listOf("KPI-STK-01", "KPI-STK-02", "KPI-STK-03", "KPI-STK-06", "KPI-STK-08"), body["indicators"].values().map { it["code"].asString() })
        assertEquals(listOf(f.project), body["filters"]["project"].values().map { it.asString() })
        assertEquals(2, indicator(body, "KPI-STK-01")["value"].asInt())
        val stances = indicator(body, "KPI-STK-02")["breakdowns"][0]["rows"].values().associate { it["key"].asString() to it["value"].asInt() }
        assertEquals(1, stances["EN CONTRA"])
        assertEquals(1, stances["A FAVOR"])
        val groups = indicator(body, "KPI-STK-01")["breakdowns"].values().first { it["by"].asString() == "influence_group" }
        assertEquals(2, groups["rows"].values().first { it["key"].asString() == "ESTRATEGICO" }["value"].asInt())
        val matrix = indicator(body, "KPI-STK-03")
        assertEquals(2, matrix["numerator"].asInt())
        assertEquals(2, matrix["denominator"].asInt())
        assertEquals("stakeholder", matrix["breakdowns"][0]["by"].asString())
        assertEquals(3.0, indicator(body, "KPI-STK-08")["value"].asDouble())
        // KPI-STK-06 sin oposición evaluada en el periodo: disponible y sin cifra, con value presente en null
        val changed = indicator(body, "KPI-STK-06")
        assertTrue(changed["available"].asBoolean())
        assertTrue(changed.has("value") && changed["value"].isNull)
        assertFalse(changed.has("ignoredFilters"))
        // P27: stakeholder no tiene comunidad; los KPI-STK ignoran community (y F2, explorationCampaign) y lo dicen, sin
        // quedar en cero
        val ignoring =
            tree(
                send(
                    "GET",
                    "/api/sgspe/dashboards/stakeholders?project=${f.project}&periodStart=2026-05-01&periodEnd=2026-05-31" +
                        "&community=${UUID.randomUUID()}&explorationCampaign=${UUID.randomUUID()}",
                    null,
                    HttpStatus.OK,
                    f.manager.token
                )
            )
        for (code in listOf("KPI-STK-01", "KPI-STK-02", "KPI-STK-03", "KPI-STK-06", "KPI-STK-08")) {
            assertEquals(listOf("community", "explorationCampaign"), indicator(ignoring, code)["ignoredFilters"].values().map { it.asString() }, code)
        }
        assertEquals(2, indicator(ignoring, "KPI-STK-01")["value"].asInt())
    }

    @Test
    fun `el tablero de relacionamiento cuenta realizadas, reuniones, asistentes, temas e inquietudes`() {
        val f = fixture()
        val body =
            tree(
                send(
                    "GET",
                    "/api/sgspe/dashboards/engagement?project=${f.project}&periodStart=2026-05-01&periodEnd=2026-05-31",
                    null,
                    HttpStatus.OK,
                    f.officer.token
                )
            )
        assertEquals(2, indicator(body, "KPI-ENG-01")["value"].asInt())
        val meetings = indicator(body, "KPI-ENG-02")
        assertEquals(1, meetings["value"].asInt())
        assertEquals(3, meetings["denominator"].asInt())
        val attendance = indicator(body, "KPI-ENG-03")
        assertEquals(20, attendance["numerator"].asInt())
        val meeting = attendance["breakdowns"].values().single { it["by"].asString() == "engagement" }["rows"][0]
        assertEquals(50.0, meeting["femalePercent"].asDouble())
        assertEquals(10, meeting["directArea"].asInt())
        assertEquals(1, indicator(body, "KPI-ENG-01")["breakdowns"].values().count { it["by"].asString() == "engagement_type" })
        assertEquals("Agua", indicator(body, "KPI-ENG-04")["breakdowns"][0]["rows"][0]["label"].asString())
        assertEquals(50.0, indicator(body, "KPI-ENG-05")["value"].asDouble())
        // en relacionamiento community sí filtra (la interacción tiene comunidad): otra comunidad deja 0 realizadas
        val elsewhere =
            tree(
                send(
                    "GET",
                    "/api/sgspe/dashboards/engagement?project=${f.project}&periodStart=2026-05-01&periodEnd=2026-05-31&community=${UUID.randomUUID()}",
                    null,
                    HttpStatus.OK,
                    f.officer.token
                )
            )
        assertEquals(0, indicator(elsewhere, "KPI-ENG-01")["value"].asInt())
        assertFalse(indicator(elsewhere, "KPI-ENG-01").has("ignoredFilters"))
    }

    @Test
    fun `quien no lee la postura recibe no disponible, nunca cero`() {
        val f = fixture()
        val body = tree(send("GET", "/api/sgspe/dashboards/stakeholders?project=${f.project}", null, HttpStatus.OK, f.officer.token))
        val stance = indicator(body, "KPI-STK-02")
        assertFalse(stance["available"].asBoolean())
        assertEquals("FIELD_NOT_READABLE", stance["unavailableReason"].asString())
        assertEquals(listOf("stakeholder.stance"), stance["missingFields"].values().map { it.asString() })
        assertTrue(stance["value"] == null)
        val point = indicator(body, "KPI-STK-03")["breakdowns"][0]["rows"][0]
        assertFalse(point.has("stance"))
        val catalog =
            tree(send("GET", "/api/sgspe/dashboards/indicators", null, HttpStatus.OK, f.officer.token)).values().associate {
                it["code"].asString() to
                    it
            }
        assertFalse(catalog.getValue("KPI-STK-02")["available"].asBoolean())
        assertTrue(catalog.getValue("KPI-ENG-01")["available"].asBoolean())
        assertEquals("engagement", catalog.getValue("KPI-ENG-01")["dashboard"].asString())
        // desde F8 tienen zona en el tablero de monitoreo (dashboards.md §11)
        assertEquals("monitoring", catalog.getValue("KPI-STK-04")["dashboard"].asString())
        assertEquals("monitoring", catalog.getValue("KPI-STK-05")["dashboard"].asString())
        assertEquals("monitoring", catalog.getValue("KPI-STK-07")["dashboard"].asString())
    }

    // ANALYST y READ_ONLY leen grupos de interés, evaluaciones y cargos pero no los nombres de las personas ni la postura
    // (roles.json restrictedFields): la etiqueta del punto es «cargo, organización» del cargo vigente o, sin cargo,
    // «Persona»
    @Test
    fun `el nombre de una persona no llega por la matriz a quien no lo lee`() {
        val project = projectWithCrs()
        val manager = member("STAKEHOLDER_MANAGER", project)
        val withPosition = createRecord("person", mapOf("given_names" to "Persona Ejemplo Tres"))
        val withoutPosition = createRecord("person", mapOf("given_names" to "Persona Ejemplo Cuatro"))
        createRecord(
            "position",
            mapOf(
                "position_title" to "Teniente gobernador",
                "position_status" to "VIGENTE",
                "person" to withPosition,
                "organization" to organization("Asociación Ejemplo")
            )
        )
        val stakeholders =
            listOf(withPosition, withoutPosition).associateWith { person ->
                stakeholder(project, mapOf("person" to person) + profileFields(manager, "AUTORIDAD LOCAL")).also {
                    send(
                        "POST",
                        "/api/sgspe/stakeholders/$it/assessments",
                        mapOf("attributes" to high + mapOf("assessment_date" to "2026-05-02", "basis" to "ENTREVISTA", "stance" to "NEUTRAL")),
                        HttpStatus.CREATED,
                        manager.token,
                        mapOf(REASON to reason("evaluación"))
                    )
                }
            }

        fun rows(auth: String): Map<String, JsonNode> =
            tree(send("GET", "/api/sgspe/dashboards/indicators/KPI-STK-03?project=$project", null, HttpStatus.OK, auth))["breakdowns"][0]["rows"]
                .values()
                .associateBy { it["key"].asString() }

        val shown = rows(manager.token)
        assertEquals("Persona Ejemplo Tres", shown.getValue(stakeholders.getValue(withPosition))["label"].asString())
        assertEquals("Persona Ejemplo Cuatro", shown.getValue(stakeholders.getValue(withoutPosition))["label"].asString())
        assertEquals("NEUTRAL", shown.getValue(stakeholders.getValue(withPosition))["stance"].asString())
        for (role in listOf("ANALYST", "READ_ONLY")) {
            val hidden = rows(member(role, project).token)
            assertEquals("Teniente gobernador, Asociación Ejemplo", hidden.getValue(stakeholders.getValue(withPosition))["label"].asString(), role)
            assertEquals("Persona", hidden.getValue(stakeholders.getValue(withoutPosition))["label"].asString(), role)
            for (row in hidden.values) {
                assertFalse(row.toString().contains("Persona Ejemplo"), role)
                assertFalse(row.has("stance"), role)
            }
        }
    }

    // FIELD_AGENT no lee stakeholder_assessment (roles.json): KPI-STK-08 no está disponible por el objeto, sin cifra
    @Test
    fun `sin READ sobre un objeto del indicador es OBJECT_NOT_READABLE`() {
        val project = projectWithCrs()
        val agent = member("FIELD_AGENT", project)
        val trust = tree(send("GET", "/api/sgspe/dashboards/indicators/KPI-STK-08?project=$project", null, HttpStatus.OK, agent.token))
        assertFalse(trust["available"].asBoolean())
        assertEquals("OBJECT_NOT_READABLE", trust["unavailableReason"].asString())
        assertTrue("stakeholder_assessment.trust_score" in trust["missingFields"].values().map { it.asString() })
        assertFalse(trust.has("value"))
    }

    // sin project: el ADMIN y quien lee todos los proyectos ven todos; quien no tiene asignaciones, ninguno; quien las tiene,
    // solo las suyas vigentes (dashboards.md §3.1)
    @Test
    fun `los proyectos por defecto del ADMIN, de quien no tiene asignaciones y de quien las tiene`() {
        val mine = projectWithCrs()
        val other = projectWithCrs()
        val path = "/api/sgspe/dashboards/stakeholders?periodStart=2026-05-01&periodEnd=2026-05-31"

        fun projects(auth: String): List<String> = tree(send("GET", path, null, HttpStatus.OK, auth))["filters"]["project"].values().map { it.asString() }

        assertTrue(projects(token).containsAll(listOf(mine, other)))
        // desde 4.4 (ProjectReach): quien lee todos los proyectos (AUDITOR) los recibe todos; sin asignaciones, ninguno
        assertTrue(projects(userWithRole("AUDITOR").token).containsAll(listOf(mine, other)))
        assertEquals(emptyList<String>(), projects(userWithRole("ANALYST").token))
        assertEquals(listOf(mine), projects(member("ANALYST", mine).token))
    }

    @Test
    fun `KPI-STK-03 exige un solo proyecto, un tablero desconocido es 404 y un parametro desconocido 422`() {
        val f = fixture()
        val other = projectWithCrs()
        val body = tree(send("GET", "/api/sgspe/dashboards/stakeholders?project=${f.project}&project=$other", null, HttpStatus.OK, f.manager.token))
        assertEquals("SINGLE_PROJECT_REQUIRED", indicator(body, "KPI-STK-03")["unavailableReason"].asString())
        val unknown = exchange("GET", "/api/sgspe/dashboards/no-existe", token = f.manager.token)
        assertEquals(HttpStatus.NOT_FOUND, unknown.status)
        assertEquals("DASHBOARD_NOT_FOUND", tree(unknown.body)["errors"][0]["message"].asString())
        assertEquals(
            HttpStatus.UNPROCESSABLE_CONTENT,
            exchange("GET", "/api/sgspe/dashboards/engagement?projectId=${f.project}", token = f.manager.token).status
        )
        val detail =
            tree(
                send(
                    "GET",
                    "/api/sgspe/dashboards/indicators/KPI-STK-04?project=${f.project}&periodStart=2026-05-01&periodEnd=2026-05-31&breakdown=sex",
                    null,
                    HttpStatus.OK,
                    f.manager.token
                )
            )
        assertEquals(listOf("sex"), detail["breakdowns"].values().map { it["by"].asString() })
        val color = exchange("GET", "/api/sgspe/dashboards/indicators/KPI-STK-04?breakdown=color", token = f.manager.token)
        assertEquals(HttpStatus.BAD_REQUEST, color.status)
        assertEquals("INVALID_BREAKDOWN", tree(color.body)["errors"][0]["message"].asString())
    }
}
