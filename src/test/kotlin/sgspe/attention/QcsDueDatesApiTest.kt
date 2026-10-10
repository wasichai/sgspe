package sgspe.attention

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import sgspe.F4ApiTest
import sgspe.common.DerivedFieldsJob
import sgspe.common.LIMA
import tools.jackson.databind.JsonNode
import java.time.LocalDate

// los plazos y las fechas de transición de las QCS (ASM-096, ASM-100, dashboards.md §12): QcsDueDatesListener al recibir, con
// el calendario de application.yml (el 8 de octubre de 2026 es feriado), QcsTransitionDatesListener al resolver, reabrir y
// cerrar, y JOB-02 completa un plazo que falte sin pisar los guardados
class QcsDueDatesApiTest : F4ApiTest() {
    @Autowired
    private lateinit var derived: DerivedFieldsJob

    private val manager by lazy { member("GRIEVANCE_MANAGER") }

    private fun attributes(
        objectName: String,
        id: String
    ): JsonNode = record(objectName, id)["attributes"]

    private fun blank(node: JsonNode?): Boolean = node == null || node.isNull || node.isMissingNode

    private fun today(): String = LocalDate.now(LIMA).toString()

    @Test
    fun `una queja recibida un lunes vence su acuse en el quinto dia habil, sin contar el feriado`() {
        val id = grievance(testProject(), member("COMMUNITY_RELATIONS_OFFICER"), mapOf("received_at" to "2026-10-05T15:00:00Z"))
        val dates = attributes("grievance", id)
        assertEquals("2026-10-13", dates["ack_due_date"].asString())
        assertEquals("2026-11-04", dates["resolution_due_date"].asString())
        assertTrue(blank(dates["response_due_date"]))
    }

    @Test
    fun `una consulta mide su respuesta y una solicitud no tiene plazo`() {
        val inquiry = inquiry(testProject(), attributes = mapOf("received_at" to "2026-10-05T15:00:00Z"))
        assertEquals("2026-10-20", attributes("inquiry", inquiry)["response_due_date"].asString())
        val request = stakeholderRequest(testProject())
        assertTrue(blank(attributes("stakeholder_request", request)["response_due_date"]))
    }

    @Test
    fun `resolver, reabrir y rechazar escriben sus fechas`() {
        val id = grievance(testProject())
        for (step in listOf("acknowledge", "assess", "start_action", "resolve")) move("grievance", id, step, manager.token, step)
        assertEquals(today(), attributes("grievance", id)["resolved_date"].asString())
        move("grievance", id, "reopen", manager.token, "reabrir")
        assertTrue(blank(attributes("grievance", id)["resolved_date"]))
        val rejected = grievance(testProject())
        move("grievance", rejected, "reject_received", manager.token, "rechazo")
        assertEquals(today(), attributes("grievance", rejected)["closed_date"].asString())
    }

    @Test
    fun `JOB-02 completa un plazo que falta y no pisa los guardados`() {
        val id = grievance(testProject(), attributes = mapOf("received_at" to "2026-10-05T15:00:00Z"))
        platformPatch("grievance", id, mapOf("ack_due_date" to null, "resolution_due_date" to "2026-12-01"))
        runBlocking { derived.runOnce(full = true) }
        val dates = attributes("grievance", id)
        assertEquals("2026-10-13", dates["ack_due_date"].asString())
        assertEquals("2026-12-01", dates["resolution_due_date"].asString())
    }
}
