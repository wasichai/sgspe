package sgspe.attention

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F4ApiTest

// WF-E2 con AttentionWriteGuard (workflows.md, especificación de F4 §3.2): responder exige la respuesta y su fecha, derivar
// exige la respuesta, cerrar una derivada exige la fecha de respuesta; una consulta anónima no lleva grupo de interés
class InquiryWorkflowApiTest : F4ApiTest() {
    private val manager by lazy { member("GRIEVANCE_MANAGER") }

    @Test
    fun `responder exige response y responded_at`() {
        val id = inquiry(testProject(), attributes = mapOf("response" to null, "responded_at" to null))
        val response = transition("inquiry", id, "answer", manager.token, "respuesta")
        assertEquals(HttpStatus.BAD_REQUEST, response.status, response.body)
        assertEquals("FIELD_REQUIRED", code(response))
        assertEquals(listOf("response"), problemFields(response.body))
    }

    @Test
    fun `derivar exige la respuesta y cerrar la derivada su fecha`() {
        val id = inquiry(testProject(), attributes = mapOf("responded_at" to null))
        move("inquiry", id, "refer", manager.token, "derivada al área de proyectos")
        val response = transition("inquiry", id, "close_referred", manager.token, "cierre")
        assertEquals(HttpStatus.BAD_REQUEST, response.status, response.body)
        assertEquals(listOf("responded_at"), problemFields(response.body))
    }

    @Test
    fun `una consulta anonima no lleva grupo de interes`() {
        val response =
            exchange(
                "POST",
                "/api/objects/inquiry/records",
                mapOf(
                    "attributes" to attentionAttributes(testProject(), null, "CO-") + mapOf("anonymous" to true, "stakeholder" to stakeholder(testProject()))
                ),
                token,
                withReason("alta de prueba")
            )
        assertEquals(HttpStatus.BAD_REQUEST, response.status, response.body)
        assertEquals("ANONYMOUS_WITH_IDENTITY", code(response))
        assertEquals(listOf("stakeholder"), problemFields(response.body))
    }
}
