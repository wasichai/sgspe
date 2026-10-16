package sgspe.dashboard

import kotlinx.coroutines.reactor.asCoroutineContext
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.util.LinkedMultiValueMap
import sgspe.F6QueryApiTest
import sgspe.common.LIMA
import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.ObjectNode
import java.time.YearMonth
import java.util.UUID

// plan 8.2a-bis: los KPI del tablero executive contados en la base, en las tiles y en byProject, como el ADMIN, como un relacionista asignado a un solo proyecto (alcance por proyecto y QCS confidencial
// oculta), como un rol de prueba sin campos de territorio de la interacción ni closed_date de la queja (columnas NULL), como
// la plataforma, con locality, con los ámbitos AIDS y AIIS (donde las tiles y la fila de cada proyecto usan territorios
// distintos), en el detalle con desglose y en el cierre sin las confidenciales (R4) como el ADMIN y como la plataforma.
// también en los tableros project, attention, engagement, commitments, risks y territory (la ficha KPI-TER-01): las cifras de
// los fixtures (Review Focus 1, 3, 4 y 5). hasta el cierre de F8 la prueba comparaba además con la lectura de filas con
// Records, que se quitó (decisión 8)
class ExecutiveAggregatesApiTest : F6QueryApiTest() {
    @Autowired
    private lateinit var dashboards: Dashboards

    @Autowired
    private lateinit var readers: KpiReaders

    @Autowired
    private lateinit var jwtDecoder: ReactiveJwtDecoder

    private val month: YearMonth = YearMonth.now(LIMA).minusMonths(1)
    private val start = month.atDay(1)
    private val end = month.atEndOfMonth()

    // un servicio como la persona del token, como lo llama un controlador (F7ApiTest.asUser)
    private fun <T> asToken(
        token: String,
        block: suspend () -> T
    ): T {
        val jwt = jwtDecoder.decode(token.removePrefix("Bearer ")).block()!!
        return runBlocking(ReactiveSecurityContextHolder.withAuthentication(JwtAuthenticationToken(jwt)).asCoroutineContext()) { block() }
    }

    private fun json(value: Any): JsonNode = (tree(JSON.writeValueAsString(value)) as ObjectNode).also { it.remove("computedAt") }

    private fun params(query: Map<String, List<String>>) = LinkedMultiValueMap<String, String>().apply { query.forEach { (k, v) -> addAll(k, v) } }

    // como la persona del token o, sin token, como la plataforma (fuera de una petición)
    private fun both(
        token: String?,
        call: suspend (KpiReader) -> Any
    ): JsonNode =
        if (token == null) {
            json(runBlocking { call(readers.platform(organizationId())) })
        } else {
            json(asToken(token) { call(readers.person()) })
        }

    // el tablero por SpatialGate; donde la prueba no mira cifras, que responda sin error para ese lector y esos filtros
    private fun board(
        name: String,
        query: Map<String, List<String>>,
        token: String?
    ): JsonNode = both(token) { reader -> dashboards.dashboard(name, params(query), reader) }

    private fun closing(
        code: String,
        projects: List<UUID>,
        token: String?
    ): JsonNode = both(token) { reader -> dashboards.value(code, projects, start, end, reader, true) }

    private fun indicator(
        body: JsonNode,
        code: String
    ): JsonNode = body["indicators"].values().first { it["code"].asString() == code }

    private fun row(
        body: JsonNode,
        project: UUID,
        code: String
    ): JsonNode =
        body["byProject"].values().first { it["project"].asString() == project.toString() }["indicators"].values().first {
            it["code"].asString() ==
                code
        }

    // «Proyecto Ejemplo A» en UTM 17S, sin aprobación interna (los programas pasan a planned con plan_direct) y en active
    private fun activeUtmProject(attributes: Map<String, Any?> = emptyMap()): UUID {
        val project =
            project(
                mapOf("code" to uniqueName("PRY-UTM-"), "name" to "Proyecto Ejemplo A", "working_crs" to "EPSG 32717", "policy_internal_approval" to false) +
                    attributes
            )
        createRecord("project_party", mapOf("project" to project, "organization" to organization(), "party_role" to "CLIENTE"))
        val admin = member("PROJECT_ADMIN", project)
        move("project", project, "submit_for_review", admin.token)
        move("project", project, "approve", admin.token)
        return UUID.fromString(project)
    }

    private fun held(
        project: UUID,
        type: String,
        day: Int,
        attributes: Map<String, Any?> = emptyMap(),
        monthOf: YearMonth = month
    ) {
        val officer = member("COMMUNITY_RELATIONS_OFFICER", project.toString())
        engagement(project.toString(), type, officer.token, mapOf("held_at" to "${monthOf.atDay(day)}T15:00:00Z") + attributes).also {
            participant(it, officer.token)
            move("engagement", it, "register_held_unplanned", officer.token)
        }
    }

    // un área aprobada de `type` del proyecto con `place` como miembro
    private fun approvedArea(
        project: UUID,
        type: String,
        y: Double,
        place: UUID
    ) {
        val area = area(project, type, boundary = rect(0.0, y, 1000.0, y + 1000, multi = true))
        areaMember(area, "locality", place)
        approveArea(area, project)
    }

    // una queja cerrada por WF-E1 (como GrievanceWorkflowApiTest) con closed_date dentro del periodo, como la plataforma
    private fun closedGrievance(
        project: UUID,
        receivedAt: String
    ): String {
        val manager = member("GRIEVANCE_MANAGER", project.toString())
        val id = grievance(project.toString(), attributes = mapOf("received_at" to receivedAt))
        for (step in listOf("acknowledge", "assess", "start_action", "resolve", "close_resolved")) move("grievance", id, step, manager.token, step)
        platformPatch("grievance", id, mapOf("closed_date" to start.plusDays(20).toString()))
        return id
    }

    // un compromiso en ejecución, vencido (deadline_status lo escribe JOB-01; aquí, como la plataforma) con plazo en el periodo
    private fun overdueCommitment(project: UUID): String =
        approvedCommitment(project.toString(), attributes = mapOf("agreed_date" to start.toString(), "due_date" to start.plusDays(9).toString())).also {
            platformPatch("commitment", it, mapOf("deadline_status" to "VENCIDO"))
        }

    // un rol de prueba que lee los objetos del tablero pero no la localidad ni la comunidad de la interacción ni el closed_date
    // de la queja: en el SQL esas columnas salen NULL, como el atributo ausente en Records
    private fun fieldBlind(vararg projects: UUID): Account {
        val name = "PRUEBA_" + uniqueName("").uppercase().filter { it.isLetterOrDigit() }
        send("POST", "/api/roles", mapOf("name" to name, "label" to "Rol de prueba"), HttpStatus.CREATED)
        val objects =
            listOf(
                "project",
                "engagement",
                "grievance",
                "inquiry",
                "stakeholder_request",
                "commitment",
                "commitment_reschedule",
                "social_risk",
                "early_warning",
                "area_of_influence",
                "area_of_influence_member",
                "locality",
                "community",
                "administrative_unit",
                "delivery",
                "social_contribution",
                "exploration_campaign",
                "social_program",
                "program_budget"
            )
        send("PUT", "/api/roles/$name/permissions", mapOf("permissions" to objects.map { mapOf("objectName" to it, "action" to "READ") }), HttpStatus.OK)
        val blind = listOf("engagement" to "locality", "engagement" to "community", "grievance" to "closed_date")
        send(
            "PUT",
            "/api/roles/$name/field-permissions",
            mapOf("fields" to blind.map { (o, f) -> mapOf("objectName" to o, "fieldName" to f, "read" to false, "write" to false) }),
            HttpStatus.OK
        )
        return userWithRole(name).also { account -> projects.forEach { assign(account, it.toString(), "READ_ONLY") } }
    }

    @Test
    fun `los conteos en la base dan las cifras de los fixtures`() {
        val a = activeUtmProject()
        val b = activeUtmProject(mapOf("escalation_first_days" to 3, "escalation_second_days" to 30))
        val place = localityWith("Caserío Ejemplo", location = at(100.0, -150_000.0 + 100))
        val other = localityWith("Caserío Ejemplo Dos", location = at(100.0, -152_000.0 + 100))
        approvedArea(a, "AIDS", -150_000.0, place)
        approvedArea(b, "AIIS", -152_000.0, other)
        val receivedAt = "${start.plusDays(3)}T15:00:00Z"
        // interacciones: A, dos en el periodo (una en place) y una de hace tres meses; B, una en place y una en other
        held(a, "REUNION", 5, mapOf("locality" to place.toString()))
        held(a, "TALLER", 6)
        held(a, "REUNION", 7, monthOf = month.minusMonths(3))
        held(b, "REUNION", 8, mapOf("locality" to place.toString()))
        held(b, "TALLER", 9, mapOf("locality" to other.toString()))
        // QCS de A: una abierta en place, una confidencial, una consulta que reclasificó una queja de A (el original no cuenta),
        // una cerrada en el periodo y una que B intenta reclasificar; de B, una queja y una consulta
        val receiver = member("COMMUNITY_RELATIONS_OFFICER", a.toString())
        grievance(a.toString(), receiver, mapOf("received_at" to receivedAt, "locality" to place.toString()))
        grievance(a.toString(), receiver, mapOf("received_at" to receivedAt, "confidential" to true))
        val original = grievance(a.toString(), receiver, mapOf("received_at" to receivedAt))
        platformPatch("inquiry", inquiry(a.toString(), receiver, mapOf("received_at" to receivedAt)), mapOf("reclassified_from_grievance" to original))
        closedGrievance(a, receivedAt)
        val crossed = grievance(a.toString(), receiver, mapOf("received_at" to receivedAt))
        grievance(b.toString(), attributes = mapOf("received_at" to receivedAt, "locality" to place.toString()))
        val successor = inquiry(b.toString(), attributes = mapOf("received_at" to receivedAt))
        // la regla «mismo proyecto» (ProjectLinkGuard) rechaza el vínculo entre proyectos (409 PROJECT_MISMATCH): si algún día
        // pasara, gone (las tiles) y gone_own (la fila de cada proyecto) lo cuentan distinto, y las cifras de abajo lo dicen
        val crossLinked = runCatching { platformPatch("inquiry", successor, mapOf("reclassified_from_grievance" to crossed)) }.isSuccess
        // compromisos: uno abierto en cada proyecto, uno vencido en cada uno (umbrales 7/15 en A y 3/30 en B) y, en A, uno con
        // una entrega de 700 en el periodo (KPI-INV-03)
        commitment(a.toString())
        commitment(b.toString())
        overdueCommitment(a)
        overdueCommitment(b)
        val delivered = approvedCommitment(a.toString(), attributes = mapOf("amount" to 3000))
        val delivery =
            exchange(
                "POST",
                "/api/sgspe/commitments/$delivered/deliveries",
                mapOf(
                    "attributes" to mapOf("delivery_date" to start.plusDays(4).toString(), "conformity_given" to true, "delivered_amount" to 700),
                    "evidence" to emptyList<String>()
                ),
                token,
                withReason("entrega de prueba")
            )
        assertEquals(HttpStatus.CREATED, delivery.status, delivery.body)
        // programas planificados con lo ejecutado del año: 250 en A y 100 en B
        for ((project, executed) in listOf(a to 250, b to 100)) {
            val investor = member("SOCIAL_INVESTMENT_MANAGER", project.toString())
            val program =
                socialProgram(project.toString(), investor, mapOf("start_date" to "${start.year}-01-01", "end_date" to "${start.year + 1}-12-31"))
            programBudget(program, start.year, 1000, executed = executed, creator = investor)
            move("social_program", program, "plan_direct", investor.token)
        }
        // riesgos de A: ALTO, CRITICO y BAJO (risk_level lo escribe la plataforma)
        for (level in listOf("ALTO", "CRITICO", "BAJO")) platformPatch("social_risk", socialRisk(a.toString()), mapOf("risk_level" to level))
        earlyWarning(a.toString())
        earlyWarning(b.toString())

        val both = mapOf("project" to listOf(a.toString(), b.toString()), "periodStart" to listOf(start.toString()), "periodEnd" to listOf(end.toString()))
        val viewer = member("COMMUNITY_RELATIONS_OFFICER", a.toString())
        val blind = fieldBlind(a, b)

        // ADMIN: las tiles y las filas
        val admin = board(Kpis.EXECUTIVE, both, token)
        assertEquals(if (crossLinked) 5 else 6, indicator(admin, "KPI-GRV-02")["value"].asInt())
        assertEquals(4, row(admin, a, "KPI-GRV-02")["value"].asInt())
        assertEquals(2, row(admin, b, "KPI-GRV-02")["value"].asInt())
        assertEquals(4, indicator(admin, "KPI-ENG-01")["value"].asInt())
        assertEquals(2, indicator(admin, "KPI-RSK-01")["value"].asInt())
        assertEquals(0, row(admin, b, "KPI-RSK-01")["value"].asInt())
        assertEquals(3, row(admin, a, "KPI-COM-02")["value"].asInt())
        assertEquals(2, row(admin, b, "KPI-COM-02")["value"].asInt())
        assertEquals(2, indicator(admin, "KPI-COM-03")["value"].asInt())
        assertEquals(listOf(1, 1), listOf(a, b).map { row(admin, it, "KPI-COM-03")["value"].asInt() })
        assertEquals(2, indicator(admin, "KPI-EXE-02")["value"].asInt())
        assertEquals(2, indicator(admin, "KPI-EXE-01")["value"].asInt())
        assertEquals(1050.0, indicator(admin, "KPI-INV-03")["value"].asDouble())
        assertEquals(950.0, row(admin, a, "KPI-INV-03")["value"].asDouble())
        assertEquals(100.0, row(admin, b, "KPI-INV-03")["value"].asDouble())
        val trend = indicator(admin, ExecutiveKpis.TREND)["series"].values().last()
        assertEquals(4, trend["engagements"].asInt())
        assertEquals(1, trend["grievancesClosed"].asInt())

        // el relacionista de A con B pedido: sin la confidencial y sin nada de B
        val officer = board(Kpis.EXECUTIVE, both, viewer.token)
        assertEquals(3, indicator(officer, "KPI-GRV-02")["value"].asInt())
        assertEquals(3, row(officer, a, "KPI-GRV-02")["value"].asInt())
        assertEquals(0, row(officer, b, "KPI-GRV-02")["value"].asInt())

        // el rol sin la localidad de la interacción: con locality=place no casa ninguna; sin closed_date, KPI-EXE-03 no disponible
        val withPlace = both + ("locality" to listOf(place.toString()))
        assertEquals(2, indicator(board(Kpis.EXECUTIVE, withPlace, token), "KPI-ENG-01")["value"].asInt())
        val blindBoard = board(Kpis.EXECUTIVE, withPlace, blind.token)
        assertEquals(0, indicator(blindBoard, "KPI-ENG-01")["value"].asInt())
        assertFalse(indicator(blindBoard, ExecutiveKpis.TREND)["available"].asBoolean())
        board(Kpis.EXECUTIVE, both, blind.token)

        // la plataforma
        board(Kpis.EXECUTIVE, both, null)
        board(Kpis.EXECUTIVE, withPlace, null)

        // los ámbitos: las tiles miran el territorio de todos los proyectos y cada fila el de su proyecto
        val aids = board(Kpis.EXECUTIVE, both + ("scope" to listOf("AIDS")), token)
        assertEquals(2, indicator(aids, "KPI-ENG-01")["value"].asInt())
        assertEquals(listOf(1, 0), listOf(a, b).map { row(aids, it, "KPI-ENG-01")["value"].asInt() })
        val aiis = board(Kpis.EXECUTIVE, both + ("scope" to listOf("AIIS")), token)
        assertEquals(1, indicator(aiis, "KPI-ENG-01")["value"].asInt())
        assertEquals(listOf(0, 1), listOf(a, b).map { row(aiis, it, "KPI-ENG-01")["value"].asInt() })

        // los tableros que comparten los KPI, con un proyecto
        val one = mapOf("project" to listOf(a.toString()), "periodStart" to listOf(start.toString()), "periodEnd" to listOf(end.toString()))
        for (name in listOf(Kpis.PROJECT, Kpis.ATTENTION, Kpis.ENGAGEMENT, Kpis.COMMITMENTS, Kpis.RISKS)) {
            board(name, one, token)
            board(name, one, viewer.token)
        }
        // la ficha de la localidad (KPI-TER-01) reúne KPI-ENG-01, KPI-COM-02, KPI-COM-03 y KPI-GRV-02
        board(Kpis.TERRITORY, one + ("locality" to listOf(place.toString())), token)
        // el detalle con desglose
        for ((code, by) in listOf("KPI-GRV-02" to "workflow_state", "KPI-ENG-01" to "engagement_type", "KPI-RSK-01" to "category")) {
            both(token) { reader -> dashboards.indicator(code, params(both + ("breakdown" to listOf(by))), reader) }
        }

        // el cierre (R4): sin las confidenciales, el ADMIN (la persona) y la plataforma dan la misma cifra
        for (code in listOf("KPI-GRV-02", ExecutiveKpis.TREND, "KPI-ENG-01", "KPI-COM-02", "KPI-COM-03", "KPI-RSK-01", ExecutiveKpis.ACTIVE_WARNINGS)) {
            assertEquals(closing(code, listOf(a, b), token)["value"], closing(code, listOf(a, b), null)["value"], code)
        }
        val closed = closing("KPI-GRV-02", listOf(a, b), token)["value"].asInt()
        assertEquals(indicator(admin, "KPI-GRV-02")["value"].asInt() - 1, closed)
    }

    // los filtros que el escenario principal no ejerce (campaña, responsable, comunidad, unidad administrativa) y los bordes del
    // periodo en Lima (UTC-5): 04:59:59Z del día 1 es del mes anterior y 05:00:00Z ya es del periodo; lo mismo al final. en un
    // proyecto aparte para no mover las cifras del escenario principal
    @Test
    fun `los conteos en la base con los filtros de campana, responsable y comunidad y en los bordes del dia de Lima`() {
        val c = activeUtmProject()
        val place = localityWith("Caserío Ejemplo Tres", location = at(100.0, -154_000.0 + 100))
        approvedArea(c, "AIDS", -154_000.0, place)
        val community = UUID.fromString(createRecord("community", mapOf("name" to "Comunidad Ejemplo")))
        val district = unit("DISTRITO", rect(-500.0, -154_500.0, 1500.0, -152_500.0, multi = true))
        val edges =
            listOf(
                "${start}T04:59:59Z",
                "${start}T05:00:00Z",
                "${end.plusDays(1)}T04:59:59Z",
                "${end.plusDays(1)}T05:00:00Z"
            )
        // interacciones y quejas justo en los bordes (las del primer y último instante cuentan o no según Lima, no según UTC)
        edges.forEachIndexed { i, at ->
            held(
                c,
                "REUNION",
                1,
                mapOf("held_at" to at) + if (i % 2 == 0) mapOf("community" to community.toString()) else emptyMap()
            )
        }
        val receiver = member("COMMUNITY_RELATIONS_OFFICER", c.toString())
        edges.forEach { grievance(c.toString(), receiver, mapOf("received_at" to it)) }
        // compromisos con campaña y responsable (los dos filtros de la base de KPI-COM-02 y KPI-COM-03)
        val campaign = campaign(c.toString(), cap = 100_000)
        val owner = userWithRole("COMMITMENT_MANAGER")
        approvedCommitment(c.toString(), owner, mapOf("exploration_campaign" to campaign))
        approvedCommitment(
            c.toString(),
            owner,
            mapOf("exploration_campaign" to campaign, "agreed_date" to start.toString(), "due_date" to start.plusDays(9).toString())
        ).also { platformPatch("commitment", it, mapOf("deadline_status" to "VENCIDO")) }
        approvedCommitment(c.toString())

        val base = mapOf("project" to listOf(c.toString()), "periodStart" to listOf(start.toString()), "periodEnd" to listOf(end.toString()))
        val viewer = member("COMMUNITY_RELATIONS_OFFICER", c.toString())
        for (caller in listOf(token, viewer.token, null)) {
            board(Kpis.EXECUTIVE, base, caller)
            board(Kpis.EXECUTIVE, base + ("owner" to listOf(owner.id)), caller)
            board(Kpis.EXECUTIVE, base + ("explorationCampaign" to listOf(campaign)), caller)
            board(Kpis.EXECUTIVE, base + ("owner" to listOf(owner.id)) + ("explorationCampaign" to listOf(campaign)), caller)
            board(Kpis.EXECUTIVE, base + ("community" to listOf(community.toString())), caller)
            board(Kpis.EXECUTIVE, base + ("administrativeUnit" to listOf(district.toString())), caller)
            board(Kpis.COMMITMENTS, base + ("owner" to listOf(owner.id)) + ("explorationCampaign" to listOf(campaign)), caller)
        }
        // el borde: las dos de dentro (05:00:00Z del día 1 y 04:59:59Z del día siguiente al fin) y no las de fuera
        val edge = board(Kpis.EXECUTIVE, base, token)
        assertEquals(2, indicator(edge, "KPI-ENG-01")["value"].asInt())
        // los tres compromisos abiertos; dos con el responsable y dos con la campaña
        assertEquals(3, indicator(edge, "KPI-COM-02")["value"].asInt())
        assertEquals(2, indicator(board(Kpis.EXECUTIVE, base + ("owner" to listOf(owner.id)), token), "KPI-COM-02")["value"].asInt())
        assertEquals(2, indicator(board(Kpis.EXECUTIVE, base + ("explorationCampaign" to listOf(campaign)), token), "KPI-COM-02")["value"].asInt())
        for (caller in listOf(token, null)) closing("KPI-GRV-02", listOf(c), caller)
    }
}
