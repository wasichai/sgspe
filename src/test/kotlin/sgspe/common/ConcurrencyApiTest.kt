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

    // el PATCH de wasichai 0.5.0 [WAS-ADR-0051] pasa por la misma precondición de sgspe, que responde el 412 con su valor
    // actual: la comparación propia de wasichai no ve el If-Match (IfMatchFilter lo quita)
    @Test
    fun `un PATCH tambien se condiciona con la precondicion de sgspe`() {
        val locality = createRecord("locality", mapOf("name" to "Caserío Ejemplo", "locality_type" to "CASERIO"))
        val read = record("locality", locality)["updatedAt"].asString()
        val path = "/api/objects/locality/records/$locality"
        val first = exchange("PATCH", path, mapOf("attributes" to mapOf("alternative_names" to "Ejemplo Alto")), headers = mapOf(IfMatch.HEADER to "\"$read\""))
        assertEquals(HttpStatus.OK, first.status, first.body)
        val stale = exchange("PATCH", path, mapOf("attributes" to mapOf("alternative_names" to "Ejemplo Bajo")), headers = mapOf(IfMatch.HEADER to "\"$read\""))
        assertEquals(HttpStatus.PRECONDITION_FAILED, stale.status, stale.body)
        val current = record("locality", locality)
        assertEquals("Ejemplo Alto", current["attributes"]["alternative_names"].asString())
        assertEquals("Caserío Ejemplo", current["attributes"]["name"].asString())
        assertEquals("valor actual ${current["updatedAt"].asString()}", tree(stale.body)["errors"][0]["message"].asString())
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

    @Test
    fun `una transicion con el updatedAt vigente pasa`() {
        val project = projectWithCrs()
        val visit = createRecord("field_visit", mapOf("project" to project, "title" to "Visita de prueba"))
        val current = record("field_visit", visit)["updatedAt"].asString()
        val moved = transitionWith("field_visit", visit, "start", headers = mapOf(IfMatch.HEADER to "\"$current\""))
        assertEquals(HttpStatus.OK, moved.status, moved.body)
    }

    // la versión que leyó A salió de un UPDATE, no del alta: la escritura de B, dentro de la ventana, sigue siendo un conflicto
    @Test
    fun `si la version leida salio de un update, la edicion de otro dentro de 2 segundos es 412`() {
        val locality = createRecord("locality", mapOf("name" to "Caserío Ejemplo", "locality_type" to "CASERIO"))
        assertEquals(HttpStatus.OK, edit("locality", locality, mapOf("alternative_names" to "Ejemplo Alto")).status)
        val read = record("locality", locality)["updatedAt"].asString()
        assertEquals(HttpStatus.OK, edit("locality", locality, mapOf("alternative_names" to "Ejemplo de B")).status)
        val stale = edit("locality", locality, mapOf("alternative_names" to "Ejemplo de A"), headers = mapOf(IfMatch.HEADER to "\"$read\""))
        assertEquals(HttpStatus.PRECONDITION_FAILED, stale.status, stale.body)
        assertEquals("Ejemplo de B", record("locality", locality)["attributes"]["alternative_names"].asString())
    }

    // el historial de atributos no ve una geometría: si el cambio de B no deja campos visibles, no puede pasar por inofensivo
    @Test
    fun `un cambio de solo la geometria de otro es 412`() {
        val concession = createWith("mining_concession", sample("mining_concession"), mapOf("boundary" to square(-78.6, -7.9, 0.01, "MultiPolygon")))
        val read = record("mining_concession", concession)["updatedAt"].asString()
        val moved = square(-78.5, -7.8, 0.01, "MultiPolygon")
        assertEquals(HttpStatus.OK, edit("mining_concession", concession, emptyMap(), geometries = mapOf("boundary" to moved)).status)
        val stale =
            edit(
                "mining_concession",
                concession,
                mapOf("name" to "Concesión de A"),
                headers = mapOf(IfMatch.HEADER to "\"$read\""),
                geometries =
                    mapOf("boundary" to square(-78.6, -7.9, 0.01, "MultiPolygon"))
            )
        assertEquals(HttpStatus.PRECONDITION_FAILED, stale.status, stale.body)
        assertEquals(-78.5, record("mining_concession", concession)["geometries"]["boundary"]["coordinates"][0][0][0][0].asDouble(), 1e-9)
    }
}
