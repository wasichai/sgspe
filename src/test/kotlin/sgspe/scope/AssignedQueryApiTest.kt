package sgspe.scope

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F3ApiTest
import tools.jackson.databind.JsonNode

// ?assigned=true en GET /api/objects/{object}/records (B-04, wasichai/sgspe#162, decisión (a)): la cola «Para tomar» de quien
// lee todos los proyectos se queda en sus proyectos asignados. con dos proyectos y una tarea sin responsable en cada uno, a una
// cuenta normal asignada a P el filtro no le cambia nada; AUDITOR (READ_ALL_PROJECTS) asignado a P recibe solo la de P; y un
// ADMIN sin asignaciones, una lista vacía
class AssignedQueryApiTest : F3ApiTest() {
    private val p by lazy { project() }
    private val q by lazy { project() }

    private fun listed(
        objectName: String,
        account: Account,
        query: String
    ): JsonNode = tree(send("GET", "/api/objects/$objectName/records?size=200&$query", null, HttpStatus.OK, account.token))

    private fun ids(node: JsonNode): Set<String> = node["content"].values().map { it["id"].asString() }.toSet()

    private fun projects(node: JsonNode): Set<String> = node["content"].values().map { it["attributes"]["project"].asString() }.toSet()

    @Test
    fun `a una cuenta normal el filtro no le cambia nada`() {
        val mine = task(p)
        task(q)
        val officer = member("COMMUNITY_RELATIONS_OFFICER", p)
        val all = listed("task", officer, "empty=assignee_user_id")
        val assigned = listed("task", officer, "empty=assignee_user_id&assigned=true")
        assertEquals(setOf(mine), ids(all))
        assertEquals(ids(all), ids(assigned))
        assertEquals(all["totalElements"].asInt(), assigned["totalElements"].asInt())
        assertEquals(ids(listed("project", officer, "")), ids(listed("project", officer, "assigned=true")))
    }

    @Test
    fun `quien tiene READ_ALL_PROJECTS recibe solo lo de sus asignaciones vigentes`() {
        val mine = task(p)
        val theirs = task(q)
        val auditor = member("AUDITOR", p)
        // sin el filtro lee la cola de todo el tenant, también la de Q
        assertEquals(setOf(theirs), ids(listed("task", auditor, "project=$q&empty=assignee_user_id")))
        val assigned = listed("task", auditor, "empty=assignee_user_id&assigned=true")
        assertTrue(mine in ids(assigned), assigned.toString())
        assertEquals(setOf(p), projects(assigned))
        assertEquals(0, listed("task", auditor, "project=$q&empty=assignee_user_id&assigned=true")["totalElements"].asInt())
        assertEquals(setOf(p), ids(listed("project", auditor, "assigned=true")))
    }

    @Test
    fun `un ADMIN sin asignaciones recibe una lista vacia`() {
        val theirs = task(q)
        val admin = userWithRole("ADMIN")
        assertEquals(setOf(theirs), ids(listed("task", admin, "project=$q&empty=assignee_user_id")))
        val assigned = listed("task", admin, "empty=assignee_user_id&assigned=true")
        assertEquals(0, assigned["totalElements"].asInt())
        assertEquals(emptySet<String>(), ids(assigned))
    }

    @Test
    fun `un valor que no es true ni false y un objeto sin proyecto son 400`() {
        assertEquals(HttpStatus.BAD_REQUEST, exchange("GET", "/api/objects/task/records?assigned=si").status)
        assertEquals(HttpStatus.BAD_REQUEST, exchange("GET", "/api/objects/topic/records?assigned=true").status)
        assertEquals(HttpStatus.OK, exchange("GET", "/api/objects/task/records?assigned=false").status)
    }
}
