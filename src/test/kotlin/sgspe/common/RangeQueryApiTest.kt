package sgspe.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F3DomainApiTest

// ?range_field=&range_from=&range_to= y ?empty= en GET /api/objects/{object}/records (D10 de la revisión de UX): el rango es
// [desde, hasta) y se combina con los demás filtros y el conteo; un campo que no es fecha es 400
class RangeQueryApiTest : F3DomainApiTest() {
    private fun ids(query: String): Set<String> =
        tree(send("GET", "/api/objects/commitment/records?$query", null, HttpStatus.OK))["content"].values().map { it["id"].asString() }.toSet()

    @Test
    fun `filtra por un rango de fechas y por un campo vacio, dentro del proyecto`() {
        val project = project()
        val noviembre = commitment(project, mapOf("due_date" to "2026-11-01"))
        val diciembre = commitment(project, mapOf("due_date" to "2026-12-15", "beneficiaries_count" to null))
        val enero = commitment(project, mapOf("due_date" to "2027-01-10"))

        assertEquals(setOf(noviembre), ids("project=$project&range_field=due_date&range_from=2026-11-01&range_to=2026-12-15"))
        assertEquals(setOf(diciembre, enero), ids("project=$project&range_field=due_date&range_from=2026-12-01"))
        assertEquals(setOf(noviembre, diciembre), ids("project=$project&range_field=due_date&range_to=2027-01-01"))
        assertEquals(setOf(diciembre), ids("project=$project&empty=beneficiaries_count"))
        val page = tree(send("GET", "/api/objects/commitment/records?project=$project&range_field=due_date&range_from=2026-12-01&size=1", null, HttpStatus.OK))
        assertEquals(2, page["totalElements"].asInt())
    }

    @Test
    fun `un rango sobre un campo que no es fecha, sin campo o al reves, y un campo vacio desconocido, es 400`() {
        assertEquals(HttpStatus.BAD_REQUEST, exchange("GET", "/api/objects/commitment/records?range_field=title&range_from=2026-01-01").status)
        assertEquals(HttpStatus.BAD_REQUEST, exchange("GET", "/api/objects/commitment/records?range_from=2026-01-01").status)
        assertEquals(
            HttpStatus.BAD_REQUEST,
            exchange("GET", "/api/objects/commitment/records?range_field=due_date&range_from=2026-02-01&range_to=2026-01-01").status
        )
        assertEquals(HttpStatus.BAD_REQUEST, exchange("GET", "/api/objects/commitment/records?empty=no_existe").status)
    }
}
