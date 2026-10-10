package sgspe.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.DomainApiTest
import java.util.UUID

// POST /api/sgspe/transition-checks (api.md §4.1): lo que bloquearía una transición y sus advertencias, sin escribir
class TransitionChecksApiTest : DomainApiTest() {
    private fun check(
        objectName: String,
        id: String,
        transition: String,
        token: String = this.token
    ) = tree(
        send("POST", "/api/sgspe/transition-checks", mapOf("object" to objectName, "recordId" to id, "transition" to transition), HttpStatus.OK, token)
    )

    @Test
    fun `una transicion que pide motivo trae la advertencia, y una de comando se bloquea`() {
        val project = projectWithCrs()
        val visit = createRecord("field_visit", mapOf("project" to project, "title" to "Visita de prueba"))
        val cancel = check("field_visit", visit, "cancel")
        assertTrue(cancel["allowed"].asBoolean())
        assertEquals("REASON_REQUIRED", cancel["warnings"][0]["code"].asString())
        // la forma de cada ítem es la que fija 2.3: code, message y field (null si no apunta a un campo)
        assertEquals(setOf("code", "message", "field"), cancel["warnings"][0].propertyNames().toSet())
        assertEquals("reason", cancel["warnings"][0]["field"].asString())
        val stakeholder = stakeholder(project)
        val assess = check("stakeholder", stakeholder, "assess")
        assertEquals(setOf("COMMAND_ONLY", "STATE_MISMATCH"), assess["blocking"].values().map { it["code"].asString() }.toSet())
        val stateMismatch = assess["blocking"].values().first { it["code"].asString() == "STATE_MISMATCH" }
        assertTrue(stateMismatch["field"].isNull)
    }

    @Test
    fun `en seco no escribe nada y lo bloqueado por datos trae su campo`() {
        val project = projectWithCrs(mapOf("working_crs" to null))
        move("project", project, "submit_for_review")
        val before = tree(send("GET", "/api/objects/project/records/$project", null, HttpStatus.OK))["updatedAt"].asString()
        val approve = check("project", project, "approve")
        assertEquals(false, approve["allowed"].asBoolean())
        val byCode = approve["blocking"].values().associate { it["code"].asString() to it["field"].asString() }
        assertEquals(mapOf("WORKING_CRS_REQUIRED" to "working_crs", "PARTY_REQUIRED" to "project_party"), byCode)
        assertEquals("in_review", state("project", project))
        assertEquals(before, tree(send("GET", "/api/objects/project/records/$project", null, HttpStatus.OK))["updatedAt"].asString())
    }

    @Test
    fun `un cuerpo con una propiedad desconocida es 400 y un registro de otro es 404`() {
        val response =
            exchange(
                "POST",
                "/api/sgspe/transition-checks",
                mapOf(
                    "object" to "engagement",
                    "recordId" to UUID.randomUUID().toString(),
                    "transition" to "close",
                    "extra" to 1
                )
            )
        assertEquals(HttpStatus.BAD_REQUEST, response.status)
        assertEquals(listOf("extra"), problemFields(response.body))
        val missing =
            exchange(
                "POST",
                "/api/sgspe/transition-checks",
                mapOf("object" to "engagement", "recordId" to UUID.randomUUID().toString(), "transition" to "close")
            )
        assertEquals(HttpStatus.NOT_FOUND, missing.status)
    }

    @Test
    fun `un parametro de consulta desconocido es 422`() {
        val response =
            exchange(
                "POST",
                "/api/sgspe/transition-checks?proyecto=1",
                mapOf("object" to "engagement", "recordId" to UUID.randomUUID().toString(), "transition" to "close")
            )
        assertEquals(HttpStatus.valueOf(422), response.status)
        assertEquals(listOf("proyecto"), problemFields(response.body))
    }

    @Test
    fun `sin token es 401`() {
        val response =
            exchange(
                "POST",
                "/api/sgspe/transition-checks",
                mapOf("object" to "project", "recordId" to UUID.randomUUID().toString(), "transition" to "approve"),
                token = null
            )
        assertEquals(HttpStatus.UNAUTHORIZED, response.status)
    }

    @Test
    fun `quien no lee el objeto no recibe ni la consulta ni el estado del registro`() {
        val project = projectWithCrs()
        // ningún rol de roles.json queda sin READ sobre project en todas las fases (AUDITOR lee todo el tenant con `*`), así que
        // el ajeno es un usuario sin roles: no depende de la fase
        val email = "${uniqueName("sin-roles")}@sgspe.test"
        send(
            "POST",
            "/api/users",
            mapOf("email" to email, "displayName" to "Usuario sin roles", "password" to PASSWORD, "roles" to emptyList<String>()),
            HttpStatus.CREATED
        )
        val outsider = Account(bearer(email, PASSWORD), email)
        val response =
            exchange(
                "POST",
                "/api/sgspe/transition-checks",
                mapOf("object" to "project", "recordId" to project, "transition" to "submit_for_review"),
                outsider.token
            )
        assertTrue(response.status == HttpStatus.FORBIDDEN || response.status == HttpStatus.NOT_FOUND, "${response.status} ${response.body}")
        assertTrue("draft" !in response.body, response.body)
    }

    @Test
    fun `quien no esta asignado al proyecto recibe NOT_ASSIGNED entre los bloqueos`() {
        // desde 4.4 quien no lee el proyecto recibe 404 (ASM-108); NOT_ASSIGNED queda para quien lo lee sin estar asignado:
        // PROJECT_ADMIN, que tiene READ_ALL_PROJECTS
        val project = projectWithCrs()
        val unreadable =
            exchange(
                "POST",
                "/api/sgspe/transition-checks",
                mapOf("object" to "project", "recordId" to project, "transition" to "submit_for_review"),
                userWithRole("PROJECT_MANAGER").token
            )
        assertEquals(HttpStatus.NOT_FOUND, unreadable.status, unreadable.body)
        val stranger = userWithRole("PROJECT_ADMIN")
        val check =
            tree(
                send(
                    "POST",
                    "/api/sgspe/transition-checks",
                    mapOf("object" to "project", "recordId" to project, "transition" to "submit_for_review"),
                    HttpStatus.OK,
                    stranger.token
                )
            )
        assertEquals(false, check["allowed"].asBoolean())
        assertEquals(listOf("NOT_ASSIGNED"), check["blocking"].values().map { it["code"].asString() })
    }

    @Test
    fun `quien no lee las partes recibe OBJECT_NOT_READABLE, y quien las lee no`() {
        val project = projectWithCrs()
        party(project, "CLIENTE")
        move("project", project, "submit_for_review")
        // FIELD_AGENT asignado lee project pero no project_party, y no tiene APPROVE
        val agent = member("FIELD_AGENT", project)
        val blocked = check("project", project, "approve", agent.token)
        assertEquals(false, blocked["allowed"].asBoolean())
        val byCode = blocked["blocking"].values().associate { it["code"].asString() to it["field"].asString() }
        assertEquals(mapOf("OBJECT_NOT_READABLE" to "project_party"), byCode - "ACTION_REQUIRED")
        assertTrue("ACTION_REQUIRED" in byCode)
        assertTrue("PARTY_REQUIRED" !in byCode)
        // quien las lee ve la parte CLIENTE y no recibe ni PARTY_REQUIRED ni OBJECT_NOT_READABLE
        val reader = check("project", project, "approve", member("PROJECT_ADMIN", project).token)
        assertEquals(true, reader["allowed"].asBoolean())
    }

    private fun party(
        project: String,
        role: String
    ) = createRecord("project_party", mapOf("project" to project, "organization" to organization(), "party_role" to role))
}
