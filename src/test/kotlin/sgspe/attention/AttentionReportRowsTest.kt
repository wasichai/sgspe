package sgspe.attention

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import sgspe.common.ModelCatalog
import sgspe.common.Raised
import sgspe.common.RuleException
import sgspe.common.UnprocessableContentException

// el reporte unificado de QCS (api.md §4.6), sin base: conteos con AttentionCounting, tipos no legibles, filas sin texto libre,
// el plazo que corre en cada estado y los parámetros
class AttentionReportRowsTest {
    private val catalog = ModelCatalog.fromClasspath()

    private fun raised(
        type: String,
        id: String,
        state: String,
        attributes: Map<String, Any?> = emptyMap()
    ) = Raised(type, id, state, mapOf("code" to id.uppercase(), "received_at" to "2026-10-0${id.last()}T15:00:00Z") + attributes)

    @Suppress("UNCHECKED_CAST")
    private fun byType(
        report: Map<String, Any?>,
        type: String
    ): Map<String, Any?> = (report["byType"] as List<Map<String, Any?>>).first { it["type"] == type }

    @Test
    fun `cuenta cada planteamiento una vez y un tipo que no se lee sale no disponible`() {
        val read =
            mapOf(
                "grievance" to listOf(raised("grievance", "q1", "closed"), raised("grievance", "q2", "in_action", mapOf("deadline_status" to "VENCIDO"))),
                "inquiry" to listOf(raised("inquiry", "c3", "received", mapOf("reclassified_from_grievance" to "q1", "channel" to "CARTA"))),
                "stakeholder_request" to null
            )
        val report = AttentionReportRows.report(read, confidentialHidden = true, page = 0, size = 50)
        assertEquals(mapOf("received" to 2, "open" to 2, "closed" to 0, "rejected" to 0, "withdrawn" to 0, "absolved" to 0, "overdue" to 1), report["totals"])
        assertEquals(1, byType(report, "grievance")["received"])
        assertEquals(listOf(mapOf("key" to "CARTA", "value" to 1)), byType(report, "inquiry")["byChannel"])
        assertEquals(mapOf("type" to "stakeholder_request", "available" to false), byType(report, "stakeholder_request"))
        assertEquals(true, report["confidentialHidden"])
    }

    @Suppress("UNCHECKED_CAST")
    @Test
    fun `la pagina va de la mas reciente a la mas antigua y no lleva texto libre ni persona`() {
        val rows =
            listOf(
                raised("grievance", "q1", "received", mapOf("ack_due_date" to "2026-10-08", "description" to "texto", "person" to "p", "severity" to "ALTA")),
                raised("inquiry", "c2", "received", mapOf("response_due_date" to "2026-10-17", "response" to "texto"))
            )
        val report = AttentionReportRows.report(mapOf("grievance" to rows.take(1), "inquiry" to rows.drop(1)), false, 0, 1)
        val page = report["page"] as Map<String, Any?>
        assertEquals(2, page["totalElements"])
        val items = page["items"] as List<Map<String, Any?>>
        assertEquals(listOf("c2"), items.map { it["id"] })
        assertEquals(
            listOf(
                "type",
                "id",
                "code",
                "state",
                "channel",
                "category",
                "severity",
                "receivedAt",
                "deadlineStatus",
                "dueDate",
                "ownerEmail",
                "respondedAt",
                "anonymous"
            ),
            items.single().keys.toList()
        )
        assertEquals("2026-10-17", items.single()["dueDate"])
        val second = AttentionReportRows.report(mapOf("grievance" to rows.take(1), "inquiry" to rows.drop(1)), false, 1, 1)
        val item = ((second["page"] as Map<String, Any?>)["items"] as List<Map<String, Any?>>).single()
        assertEquals("ALTA", item["severity"])
        assertFalse("description" in item || "person" in item)
    }

    @Test
    fun `el plazo que corre depende del estado`() {
        val dates = mapOf("ack_due_date" to "2026-10-13", "resolution_due_date" to "2026-11-04", "response_due_date" to "2026-10-20")
        assertEquals("2026-10-13", AttentionReportRows.dueDate("grievance", "received", dates))
        assertEquals("2026-11-04", AttentionReportRows.dueDate("grievance", "in_investigation", dates))
        assertEquals(null, AttentionReportRows.dueDate("grievance", "resolved", dates))
        assertEquals(null, AttentionReportRows.dueDate("inquiry", "answered", dates))
        assertEquals(null, AttentionReportRows.dueDate("stakeholder_request", "received", dates))
    }

    @Test
    fun `los parametros se validan`() {
        val parsed = AttentionReportRows.parse(mapOf("type" to listOf("inquiry"), "channel" to listOf("CARTA"), "size" to listOf("10")), catalog)
        assertEquals(listOf("inquiry"), parsed.types)
        assertEquals(10, parsed.size)
        assertEquals(listOf("grievance", "inquiry", "stakeholder_request"), AttentionReportRows.parse(emptyMap(), catalog).types)
        assertThrows<UnprocessableContentException> { AttentionReportRows.parse(mapOf("territory" to listOf("x")), catalog) }
        for (bad in listOf("type" to "commitment", "channel" to "FAX", "periodStart" to "ayer", "size" to "500", "project" to "x")) {
            val e = assertThrows<RuleException> { AttentionReportRows.parse(mapOf(bad.first to listOf(bad.second)), catalog) }
            assertEquals(bad.first, e.violations.single().field)
            assertEquals("INVALID_FILTER", e.violations.single().message)
        }
    }
}
