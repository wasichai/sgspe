package sgspe.scope

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F5ProgramApiTest
import tools.jackson.databind.JsonNode

// el alcance de lectura por proyecto en las rutas de wasichai y de sgspe (ASM-106, ASM-108, security.md §9.7 y §14): con dos
// proyectos, «Proyecto Ejemplo A» y «Proyecto Ejemplo B», quien está asignado solo a A ve A y no B en listas, totales,
// detalle, relacionados, historial, auditoría, transiciones, features, archivos, documentos, indicadores, exportaciones y
// capas; no da de alta ni vincula hacia B; una asignación vencida deja de dar alcance pero se sigue leyendo; AUDITOR,
// PROJECT_ADMIN y ADMIN leen los dos; una cuenta de servicio asignada a A, solo A; y una QCS confidencial de A no la ve un gestor
// de quejas de B aunque tenga READ_CONFIDENTIAL. ConfidentialRelease dentro de A lo prueba StakeholderRequestWorkflowApiTest.
// desde F5 (especificación §3.2): las mediciones (nietas de social_program) y las entregas con dos padres (commitment o
// social_contribution) se leen por el padre que tienen, nunca «sin padre, todos»
class ProjectReadScopeApiTest : F5ProgramApiTest() {
    private val a by lazy {
        createWith(
            "project",
            mapOf("code" to uniqueName("PRY-A-"), "name" to "Proyecto Ejemplo A", "working_crs" to "EPSG 32717"),
            mapOf("location" to point(-78.2, -7.9))
        )
    }
    private val b by lazy {
        createWith(
            "project",
            mapOf("code" to uniqueName("PRY-B-"), "name" to "Proyecto Ejemplo B", "working_crs" to "EPSG 32717"),
            mapOf("location" to point(-78.3, -7.8))
        )
    }
    private val officer by lazy { member("COMMUNITY_RELATIONS_OFFICER", a) }

    private fun status(
        method: String,
        path: String,
        account: Account,
        body: Any? = null
    ): HttpStatus = exchange(method, path, body, account.token).status

    private fun ids(node: JsonNode): Set<String> = node["content"].values().map { it["id"].asString() }.toSet()

    private fun listed(
        objectName: String,
        account: Account,
        query: String = ""
    ): JsonNode = tree(send("GET", "/api/objects/$objectName/records?size=200$query", null, HttpStatus.OK, account.token))

    // una cuenta de servicio con `role`, asignada a `project` (como EarlyWarningWorkflowApiTest)
    private fun serviceAccount(
        role: String,
        project: String
    ): Account {
        val created = tree(send("POST", "/api/service-accounts", mapOf("name" to uniqueName("cuenta-"), "roles" to listOf(role)), HttpStatus.CREATED))
        val issued =
            tree(
                send(
                    "POST",
                    "/api/auth/token",
                    mapOf("clientId" to created["clientId"].asString(), "clientSecret" to created["clientSecret"].asString()),
                    HttpStatus.OK,
                    token = null
                )
            )
        val bearer = "Bearer ${issued["token"].asString()}"
        val me = me(bearer)
        val account = Account(bearer, me["email"].asString(), me["userId"].asString())
        assign(account, project, role)
        return account
    }

    // un rol de prueba creado por la API (no está en roles.json): sus permisos (objeto o null, acción)
    private fun roleWith(vararg grants: Pair<String?, String>): String {
        val name = "PRUEBA_" + uniqueName("").uppercase().filter { it.isLetterOrDigit() }
        send("POST", "/api/roles", mapOf("name" to name, "label" to "Rol de prueba"), HttpStatus.CREATED)
        send(
            "PUT",
            "/api/roles/$name/permissions",
            mapOf(
                "permissions" to
                    grants.map { (o, action) ->
                        mapOf("objectName" to o, "action" to action)
                    }
            ),
            HttpStatus.OK
        )
        return name
    }

    @Test
    fun `quien esta asignado a A no ve B en listas, totales, detalle, historial, transiciones ni relacionados`() {
        val organization = organization("Asociación Ejemplo")
        val mine = stakeholder(a, mapOf("organization" to organization))
        val theirs = stakeholder(b, mapOf("organization" to organization))
        val list = listed("stakeholder", officer)
        assertEquals(setOf(mine), ids(list) intersect setOf(mine, theirs))
        assertEquals(1, listed("stakeholder", officer, "&project=$a")["totalElements"].asInt())
        assertEquals(0, listed("stakeholder", officer, "&project=$b")["totalElements"].asInt())
        assertEquals(setOf(a), ids(listed("project", officer)) intersect setOf(a, b))
        for (path in listOf(
            "/api/objects/stakeholder/records/$theirs",
            "/api/objects/stakeholder/records/$theirs/history",
            "/api/objects/stakeholder/records/$theirs/transitions",
            "/api/objects/project/records/$b",
            "/api/objects/project/records/$b/related/stakeholder_project"
        )) {
            assertEquals(HttpStatus.NOT_FOUND, status("GET", path, officer), path)
        }
        assertEquals(HttpStatus.OK, status("GET", "/api/objects/stakeholder/records/$mine", officer))
        // desde un maestro del tenant, la organización, el otro lado se omite
        val related =
            tree(send("GET", "/api/objects/organization/records/$organization/related/stakeholder_organization?size=200", null, HttpStatus.OK, officer.token))
        assertEquals(setOf(mine), ids(related))
        // cambiar, borrar o transicionar un registro de B: 404, y nada cambia
        assertEquals(
            HttpStatus.NOT_FOUND,
            status(
                "PUT",
                "/api/objects/stakeholder/records/$theirs",
                officer,
                mapOf("attributes" to mapOf("profile_summary" to "x"))
            )
        )
        assertEquals(HttpStatus.NOT_FOUND, transition("stakeholder", theirs, "profile", officer.token, "perfil").status)
        assertEquals("identified", state("stakeholder", theirs))
    }

    @Test
    fun `la auditoria de todo el tenant solo trae lo de los proyectos de quien la lee`() {
        val mine = stakeholder(a)
        val theirs = stakeholder(b)
        // un rol con READ de todo el tenant y sin READ_ALL_PROJECTS: /api/audit pide READ de todo el tenant
        val reader = userWithRole(roleWith(null to "READ")).also { assign(it, a, "READ_ONLY") }
        for ((id, expected) in listOf(mine to true, theirs to false)) {
            val audit = tree(send("GET", "/api/audit?recordId=$id&limit=500", null, HttpStatus.OK, reader.token))
            assertEquals(expected, audit.values().any { it["recordId"]?.asString() == id }, id)
        }
    }

    @Test
    fun `el feature GIS, el archivo de una evidencia y los documentos de B responden 404`() {
        assertEquals(HttpStatus.OK, status("GET", "/api/gis/objects/project/features/$a", officer))
        assertEquals(HttpStatus.NOT_FOUND, status("GET", "/api/gis/objects/project/features/$b", officer))
        val file = evidence(b)
        assertEquals(HttpStatus.NOT_FOUND, status("GET", "/api/objects/evidence/records/$file/files/file", officer))
        assertEquals(HttpStatus.NOT_FOUND, status("GET", "/api/sgspe/evidence/$file/content", officer))
        val meeting = engagement(b)
        val issued = tree(send("POST", "/api/objects/engagement/records/$meeting/documents/meeting-minutes", null, HttpStatus.CREATED))
        val document = issued["id"].asString()
        assertEquals(HttpStatus.NOT_FOUND, status("GET", "/api/objects/engagement/records/$meeting/documents", officer))
        assertEquals(HttpStatus.NOT_FOUND, status("GET", "/api/documents/$document", officer))
        assertEquals(HttpStatus.NOT_FOUND, status("POST", "/api/objects/engagement/records/$meeting/documents/meeting-minutes", officer))
        // quien es de B sí los lee
        val other = member("COMMUNITY_RELATIONS_OFFICER", b)
        assertEquals(HttpStatus.OK, status("GET", "/api/objects/engagement/records/$meeting/documents", other))
        assertEquals(HttpStatus.OK, status("GET", "/api/documents/$document", other))
    }

    @Test
    fun `el alta con project B y el vinculo hacia un registro de B no pasan`() {
        // RELATION: el 400 de wasichai sobre el campo, como un registro que no existe (D30)
        val created =
            exchange(
                "POST",
                "/api/objects/engagement/records",
                mapOf("attributes" to mapOf("project" to b, "engagement_type" to "REUNION", "title" to "Reunión")),
                officer.token
            )
        assertEquals(HttpStatus.BAD_REQUEST, created.status, created.body)
        assertEquals(listOf("project"), problemFields(created.body))
        assertEquals("no record with this id", tree(created.body)["errors"][0]["message"].asString())
        // M2M: POST .../related responde 404 si un extremo no se lee (RelatedRecordService)
        val own = engagement(a, token = officer.token)
        val foreign = stakeholder(b)
        assertEquals(HttpStatus.NOT_FOUND, link("engagement", own, "engagement_stakeholder", foreign, officer.token).status)
    }

    @Test
    fun `una asignacion vencida deja de dar alcance pero la persona la sigue leyendo`() {
        val expired = userWithRole("COMMUNITY_RELATIONS_OFFICER")
        val assignment = assignWith(expired, a, "COMMUNITY_RELATIONS_OFFICER", mapOf("start_date" to "2020-01-01", "end_date" to "2020-12-31"))
        assertEquals(HttpStatus.NOT_FOUND, status("GET", "/api/objects/project/records/$a", expired))
        assertEquals(setOf(assignment), ids(listed("project_assignment", expired, "&member_user_id=${expired.id}")))
        // y ya no ve las asignaciones del equipo de A: solo la suya
        assertEquals(setOf(assignment), ids(listed("project_assignment", expired, "&project=$a")))
    }

    @Test
    fun `AUDITOR, PROJECT_ADMIN y ADMIN leen los dos proyectos sin asignacion, y me permissions lo dice`() {
        val theirs = stakeholder(b)
        val auditor = userWithRole("AUDITOR")
        assertEquals(HttpStatus.OK, status("GET", "/api/objects/stakeholder/records/$theirs", auditor))
        // por el detalle, no por la lista: en un tenant con cientos de proyectos a y b pueden quedar fuera de la primera página
        val admin = userWithRole("PROJECT_ADMIN")
        for (project in listOf(a, b)) {
            assertEquals(HttpStatus.OK, status("GET", "/api/objects/project/records/$project", auditor), project)
            assertEquals(HttpStatus.OK, status("GET", "/api/objects/project/records/$project", admin), project)
        }
        assertEquals(HttpStatus.OK, exchange("GET", "/api/objects/stakeholder/records/$theirs").status)
        // GET /api/auth/me/permissions lista la acción declarada en objects.project (WAS-ADR-0042): el portal la usa (4.4b)
        for ((role, expected) in listOf("AUDITOR" to true, "PROJECT_ADMIN" to true, "ANALYST" to false, "SOCIAL_MANAGER" to false)) {
            val permissions = tree(send("GET", "/api/auth/me/permissions", null, HttpStatus.OK, userWithRole(role).token))
            val actions = permissions["objects"]["project"].values().map { it.asString() }
            assertEquals(expected, "READ_ALL_PROJECTS" in actions, "$role: $actions")
        }
    }

    @Test
    fun `PROJECT_ADMIN lee un proyecto ajeno pero no escribe en el sin asignacion`() {
        val admin = userWithRole("PROJECT_ADMIN")
        val party = mapOf("project" to b, "organization" to organization(), "party_role" to "CLIENTE")
        val refused = exchange("POST", "/api/objects/project_party/records", mapOf("attributes" to party), admin.token)
        assertEquals(HttpStatus.FORBIDDEN, refused.status, refused.body)
        assertEquals("NOT_ASSIGNED", code(refused))
    }

    @Test
    fun `una cuenta de servicio asignada a A solo lee A`() {
        val mine = stakeholder(a)
        val theirs = stakeholder(b)
        val service = serviceAccount("STAKEHOLDER_MANAGER", a)
        assertEquals(HttpStatus.OK, status("GET", "/api/objects/stakeholder/records/$mine", service))
        assertEquals(HttpStatus.NOT_FOUND, status("GET", "/api/objects/stakeholder/records/$theirs", service))
    }

    @Test
    fun `una QCS confidencial de A no la ve un gestor de quejas de B aunque tenga READ_CONFIDENTIAL`() {
        val receiver = member("COMMUNITY_RELATIONS_OFFICER", a)
        val confidential = grievance(a, receiver, mapOf("confidential" to true))
        assertEquals(HttpStatus.OK, status("GET", "/api/objects/grievance/records/$confidential", member("GRIEVANCE_MANAGER", a)))
        assertEquals(HttpStatus.NOT_FOUND, status("GET", "/api/objects/grievance/records/$confidential", member("GRIEVANCE_MANAGER", b)))
    }

    @Test
    fun `el indicador, la exportacion y la capa de B no traen datos para quien es de A`() {
        stakeholder(a)
        stakeholder(b)
        val analyst = member("ANALYST", a)

        fun value(project: String): Int =
            tree(send("GET", "/api/sgspe/dashboards/indicators/KPI-STK-01?project=$project", null, HttpStatus.OK, analyst.token))["value"].asInt()
        assertEquals(1, value(a))
        assertEquals(0, value(b))
        // dos proyectos pedidos no exigen asignación, pero las filas de B no salen
        val export =
            exchange(
                "POST",
                "/api/sgspe/exports/stakeholder-register",
                mapOf("filters" to mapOf("project" to listOf(a, b)), "disclosureLevel" to "INTERNO"),
                analyst.token
            )
        assertEquals(HttpStatus.OK, export.status)
        val log = tree(send("GET", "/api/objects/export_log/records?exporter_user_id=${analyst.id}&sort=exported_at&dir=desc", null, HttpStatus.OK))["content"]
        assertEquals(1, log[0]["attributes"]["row_count"].asInt())
        val layer = tree(send("GET", "/api/sgspe/territory/layers/LYR-09?project=$b", null, HttpStatus.OK, analyst.token))
        assertEquals(0, layer["features"].size())
    }

    @Test
    fun `quien lee todo y exporta un proyecto ajeno lo exporta y la fila de export_log no lleva el proyecto`() {
        stakeholder(b)
        val role = roleWith("stakeholder" to "READ", "stakeholder" to "EXPORT", "export_log" to "CREATE", "project" to "READ", "project" to "READ_ALL_PROJECTS")
        val exporter = userWithRole(role)
        val export =
            exchange(
                "POST",
                "/api/sgspe/exports/stakeholder-register",
                mapOf("filters" to mapOf("project" to listOf(b)), "disclosureLevel" to "INTERNO"),
                exporter.token
            )
        assertEquals(HttpStatus.OK, export.status, export.body)
        val log = tree(send("GET", "/api/objects/export_log/records?exporter_user_id=${exporter.id}", null, HttpStatus.OK))["content"][0]["attributes"]
        assertEquals(1, log["row_count"].asInt())
        assertTrue(log["project"] == null || log["project"].isNull)
        assertTrue(log["filters_description"].asString().contains(b), log.toString())
    }

    // una entrega como la plataforma (delivery es apiOnly; ExactlyOneGuard pide uno de sus dos padres)
    private fun deliveryOf(
        field: String,
        parent: String
    ): String = platformCreate("delivery", mapOf(field to parent, "delivery_date" to "2026-10-10", "conformity_given" to true))

    @Test
    fun `quien es de A lee las entregas de compromisos y de aportes de A y no las de B, y una huerfana solo quien lee todo`() {
        val investorA = member("SOCIAL_INVESTMENT_MANAGER", a)
        val investorB = member("SOCIAL_INVESTMENT_MANAGER", b)
        val mine = setOf(deliveryOf("commitment", commitment(a)), deliveryOf("social_contribution", registerContribution(a, investorA)))
        val theirs = setOf(deliveryOf("commitment", commitment(b)), deliveryOf("social_contribution", registerContribution(b, investorB)))
        // la huérfana: su compromiso en borrador se borra y wasichai deja la relación vacía (decisión 15 del plan 5.2b)
        val doomed = commitment(a)
        val orphan = deliveryOf("commitment", doomed)
        assertEquals(HttpStatus.NO_CONTENT, exchange("DELETE", "/api/objects/commitment/records/$doomed", null, token, withReason("borrado de prueba")).status)
        val everything = mine + theirs + orphan
        assertEquals(mine, ids(listed("delivery", officer)) intersect everything)
        for (id in theirs + orphan) {
            assertEquals(HttpStatus.NOT_FOUND, status("GET", "/api/objects/delivery/records/$id", officer), id)
        }
        for (id in mine) assertEquals(HttpStatus.OK, status("GET", "/api/objects/delivery/records/$id", officer), id)
        // decisión 10 del plan 5.2b: AUDITOR lee todo (`*` READ y READ_ALL_PROJECTS), también la huérfana. por el detalle, no
        // por la lista: con todo el tenant, las entregas de otras clases pueden dejar estas fuera de la primera página
        val auditor = userWithRole("AUDITOR")
        for (id in everything) assertEquals(HttpStatus.OK, status("GET", "/api/objects/delivery/records/$id", auditor), id)
    }

    @Test
    fun `quien es de A lee las mediciones de A y no las de B`() {
        val investorA = member("SOCIAL_INVESTMENT_MANAGER", a)
        val investorB = member("SOCIAL_INVESTMENT_MANAGER", b)
        val mine = programMeasurement(programIndicator(socialProgram(a, investorA), investorA), creator = investorA)
        val theirs = programMeasurement(programIndicator(socialProgram(b, investorB), investorB), creator = investorB)
        assertEquals(setOf(mine), ids(listed("program_measurement", officer)) intersect setOf(mine, theirs))
        assertEquals(HttpStatus.NOT_FOUND, status("GET", "/api/objects/program_measurement/records/$theirs", officer))
        assertEquals(HttpStatus.OK, status("GET", "/api/objects/program_measurement/records/$mine", officer))
        // AUDITOR por el detalle, como en las entregas
        val auditor = userWithRole("AUDITOR")
        for (id in listOf(mine, theirs)) assertEquals(HttpStatus.OK, status("GET", "/api/objects/program_measurement/records/$id", auditor), id)
    }
}
