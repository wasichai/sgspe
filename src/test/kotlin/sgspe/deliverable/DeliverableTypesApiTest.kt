package sgspe.deliverable

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F8ApiTest

// GET /api/sgspe/deliverable-types (api.md §4.12): los dos tipos de F8 con sus secciones a quien lee deliverable, como la
// persona; 403 a quien no (FIELD_AGENT no tiene READ sobre deliverable en roles.json)
class DeliverableTypesApiTest : F8ApiTest() {
    @Test
    fun `quien lee deliverable recibe T1 y T2 con sus secciones por orden y las de IA marcadas`() {
        val project = testProject()
        val reader = member("PROJECT_MANAGER", project)
        val response = exchange("GET", "/api/sgspe/deliverable-types", token = reader.token)
        assertEquals(HttpStatus.OK, response.status, response.body)
        val types = tree(response.body).values().toList()
        assertEquals(listOf(SocialDiagnosisType.NAME, FieldReportType.NAME), types.map { it["type"].asString() })
        val t1 = types.first()
        assertEquals(2, t1["version"].asInt())
        assertEquals(2, types[1]["version"].asInt())
        val sections = t1["sections"].values().toList()
        val count = SocialDiagnosisType().sections.size
        assertEquals(count, sections.size)
        assertEquals((1..count).toList(), sections.map { it["order"].asInt() })
        assertEquals(
            SocialDiagnosisType().sections.filter { it.assisted }.map { it.key },
            sections.filter { it["assisted"].asBoolean() }.map { it["key"].asString() }
        )
        val first = sections.first()
        assertTrue(listOf("key", "title", "order", "kind", "required", "assisted", "guide").all { first.has(it) }, first.toString())
        assertEquals(FieldReportType().sections.size, types[1]["sections"].size())
    }

    @Test
    fun `sin READ sobre deliverable es 403, y un parametro desconocido 422`() {
        val project = testProject()
        val agent = member("FIELD_AGENT", project)
        val denied = exchange("GET", "/api/sgspe/deliverable-types", token = agent.token)
        assertEquals(HttpStatus.FORBIDDEN, denied.status, denied.body)
        assertTrue("deliverable" in denied.body, denied.body)
        assertEquals(HttpStatus.UNPROCESSABLE_CONTENT, exchange("GET", "/api/sgspe/deliverable-types?type=T1").status)
    }
}
