package sgspe.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.DomainApiTest

// la precondición sobre updatedAt por la API genérica (api.md §3.6, ADR-0025, RNF-INT-003): If-Match viejo, 412 con el
// valor actual; el que se leyó, pasa; sin cabecera gana la última escritura, como en /admin (UPS-04)
class ConcurrencyApiTest : DomainApiTest() {
    @Test
    fun `un PUT con el updatedAt que se leyo pasa y uno viejo es 412 con el valor actual`() {
        val locality = createRecord("locality", mapOf("name" to "Caserío Ejemplo", "locality_type" to "CASERIO"))
        val read = record("locality", locality)["updatedAt"].asString()
        assertEquals(
            HttpStatus.OK,
            edit("locality", locality, mapOf("alternative_names" to "Ejemplo Alto"), headers = mapOf(IfMatch.HEADER to "\"$read\"")).status
        )
        val stale = edit("locality", locality, mapOf("alternative_names" to "Ejemplo Bajo"), headers = mapOf(IfMatch.HEADER to "\"$read\""))
        assertEquals(HttpStatus.PRECONDITION_FAILED, stale.status, stale.body)
        val current = record("locality", locality)
        assertEquals("Ejemplo Alto", current["attributes"]["alternative_names"].asString())
        assertEquals("valor actual ${current["updatedAt"].asString()}", tree(stale.body)["errors"][0]["message"].asString())
        assertEquals(HttpStatus.OK, edit("locality", locality, mapOf("alternative_names" to "Sin precondición")).status)
    }

    @Test
    fun `una transicion y un borrado tambien se condicionan, y un If-Match que no es un instante es 400`() {
        val project = projectWithCrs()
        val visit = createRecord("field_visit", mapOf("project" to project, "title" to "Visita de prueba"))
        val read = record("field_visit", visit)["updatedAt"].asString()
        edit("field_visit", visit, mapOf("itinerary" to "Caserío Ejemplo"))
        assertEquals(HttpStatus.PRECONDITION_FAILED, transitionWith("field_visit", visit, "start", headers = mapOf(IfMatch.HEADER to "\"$read\"")).status)
        assertEquals(HttpStatus.BAD_REQUEST, transitionWith("field_visit", visit, "start", headers = mapOf(IfMatch.HEADER to "\"ayer\"")).status)
        val node = createRecord("topic", mapOf("code" to uniqueName("T-"), "name" to "Agua", "active" to true))
        val nodeRead = record("topic", node)["updatedAt"].asString()
        edit("topic", node, mapOf("description" to "Calidad y cantidad"))
        assertEquals(
            HttpStatus.PRECONDITION_FAILED,
            exchange(
                "DELETE",
                "/api/objects/topic/records/$node",
                null,
                headers =
                    mapOf(IfMatch.HEADER to "\"$nodeRead\"")
            ).status
        )
    }
}
