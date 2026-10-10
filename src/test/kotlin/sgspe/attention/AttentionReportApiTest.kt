package sgspe.attention

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F4ApiTest
import tools.jackson.databind.JsonNode

// GET /api/sgspe/attention/report (api.md §4.6): cuenta cada planteamiento una vez, lee como quien llama (una QCS confidencial
// que no ve no cuenta y confidentialHidden lo dice), no trae texto libre y rechaza un parámetro desconocido o inválido
class AttentionReportApiTest : F4ApiTest() {
    private fun report(
        query: String,
        account: Account? = null
    ): JsonNode = tree(send("GET", "/api/sgspe/attention/report?project=${testProject()}$query", null, HttpStatus.OK, account?.token ?: token))

    private fun byType(
        report: JsonNode,
        type: String
    ): JsonNode = report["byType"].values().first { it["type"].asString() == type }

    @Test
    fun `una queja reclasificada cuenta una vez, como su consulta`() {
        val manager = member("GRIEVANCE_MANAGER")
        val id = grievance(testProject())
        val reclassified =
            exchange(
                "POST",
                "/api/sgspe/grievances/$id/reclassify",
                mapOf("targetType" to "inquiry", "attributes" to mapOf("code" to uniqueName("CO-"))),
                manager.token,
                ifMatch("grievance", id, manager.token) + withReason("era una consulta")
            )
        assertEquals(HttpStatus.CREATED, reclassified.status, reclassified.body)
        stakeholderRequest(testProject())
        val body = report("")
        assertEquals(2, body["totals"]["received"].asInt())
        assertEquals(0, byType(body, "grievance")["received"].asInt())
        assertEquals(1, byType(body, "inquiry")["received"].asInt())
        assertFalse(body["confidentialHidden"].asBoolean())
        val item = body["page"]["items"].values().first()
        assertFalse(item.has("description"))
        assertFalse(item.has("person"))
    }

    @Test
    fun `una QCS confidencial que quien llama no ve no cuenta y confidentialHidden lo dice`() {
        val receiver = member("COMMUNITY_RELATIONS_OFFICER")
        val colleague = member("COMMUNITY_RELATIONS_OFFICER")
        grievance(testProject(), receiver, mapOf("confidential" to true))
        grievance(testProject(), receiver)
        val seen = report("&type=grievance", colleague)
        assertEquals(1, seen["totals"]["received"].asInt())
        assertTrue(seen["confidentialHidden"].asBoolean())
        assertEquals(2, report("&type=grievance", receiver)["totals"]["received"].asInt())
        assertEquals(2, report("&type=grievance", member("GRIEVANCE_MANAGER"))["totals"]["received"].asInt())
    }

    @Test
    fun `un parametro desconocido es 422 y uno invalido 400 INVALID_FILTER`() {
        assertEquals(422, exchange("GET", "/api/sgspe/attention/report?territory=x").status.value())
        val invalid = exchange("GET", "/api/sgspe/attention/report?type=commitment")
        assertEquals(HttpStatus.BAD_REQUEST, invalid.status, invalid.body)
        assertEquals("INVALID_FILTER", code(invalid))
        assertEquals(listOf("type"), problemFields(invalid.body))
    }
}
