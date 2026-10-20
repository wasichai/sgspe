package sgspe.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F3DomainApiTest

// ?state= y ?state_not= en GET /api/objects/{object}/records (D10 de la revisión de UX): una vista por estado se pagina y se
// cuenta en el servidor, con los demás filtros; un estado desconocido o un objeto sin workflow es 400
class StateQueryApiTest : F3DomainApiTest() {
    private fun ids(query: String): Set<String> =
        tree(send("GET", "/api/objects/commitment/records?$query", null, HttpStatus.OK))["content"].values().map { it["id"].asString() }.toSet()

    @Test
    fun `filtra la lista por estado, dentro y fuera, con el proyecto y el conteo`() {
        val project = project()
        val draft = commitment(project)
        val running = approvedCommitment(project)
        val cancelled = commitment(project)
        move("commitment", cancelled, "cancel_draft", reason = "duplicado")
        val elsewhere = commitment(project())

        assertEquals(setOf(draft, running), ids("project=$project&state_not=closed,cancelled"))
        assertEquals(setOf(running), ids("project=$project&state=in_progress,blocked"))
        assertEquals(setOf(cancelled), ids("project=$project&state=cancelled"))
        assertEquals(setOf(draft, running, cancelled), ids("project=$project&state="))
        assertEquals(true, elsewhere in ids("state=draft"))
        val page = tree(send("GET", "/api/objects/commitment/records?project=$project&state_not=cancelled&size=1", null, HttpStatus.OK))
        assertEquals(2, page["totalElements"].asInt())
    }

    @Test
    fun `un estado que el workflow no tiene, o un objeto sin workflow, es 400`() {
        assertEquals(HttpStatus.BAD_REQUEST, exchange("GET", "/api/objects/commitment/records?state=abierto").status)
        assertEquals(HttpStatus.BAD_REQUEST, exchange("GET", "/api/objects/locality/records?state_not=draft").status)
    }
}
