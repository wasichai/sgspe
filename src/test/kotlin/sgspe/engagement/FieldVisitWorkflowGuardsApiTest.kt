package sgspe.engagement

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import sgspe.DomainApiTest

// el caso de WF-L que depende de TransitionReasonGuard (FieldVisitWorkflowApiTest): cancelar sin motivo es 400. el de
// FIELD_AGENT, que solo mueve sus visitas, está en EditAnyApiTest
class FieldVisitWorkflowGuardsApiTest : DomainApiTest() {
    @Test
    fun `cancelar sin motivo es 400`() {
        val visit = createRecord("field_visit", mapOf("project" to projectWithCrs(), "title" to "Visita"))
        assertEquals(listOf("reason"), problemFields(transition("field_visit", visit, "cancel").body))
        move("field_visit", visit, "cancel", reason = "huaico en la vía")
    }
}
