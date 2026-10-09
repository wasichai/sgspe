package sgspe.commitment

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// D1, D2 y la regla de nivel superior de WF-D (RF-COM-001, RN-ENG-002), sin base
class ApprovalDataTest {
    private val complete =
        ApprovalData.MATRIX.associateWith { "x" } + ("owner_user_id" to "u") + ("owner_user_email" to "u@sgspe.test")

    @Test
    fun `falta el primero de la matriz o del responsable, en orden, execution_time es opcional`() {
        assertNull(ApprovalData.firstMissing(complete))
        assertNull(ApprovalData.firstMissing(complete + ("execution_time" to null)))
        assertEquals("objective", ApprovalData.firstMissing(complete + ("objective" to "") + ("unit" to null)))
        assertEquals("owner_user_email", ApprovalData.firstMissing(complete + ("owner_user_email" to null)))
    }

    @Test
    fun `una mesa de dialogo o un solicitante politico exigen nivel superior, solo con la politica activa`() {
        assertTrue(SeniorApproval.required(true, "MESA DE DIALOGO", emptyList()))
        assertTrue(SeniorApproval.required(true, "REUNION", listOf("ESTRATEGICO", "POLITICO")))
        assertFalse(SeniorApproval.required(true, "REUNION", listOf("ESTRATEGICO", null)))
        assertFalse(SeniorApproval.required(false, "MESA DE DIALOGO", listOf("POLITICO")))
    }

    @Test
    fun `la causa del nivel superior es el origen antes que los solicitantes`() {
        assertEquals(SeniorApproval.Cause.ORIGIN, SeniorApproval.cause(true, "MESA DE DIALOGO", listOf("POLITICO")))
        assertEquals(SeniorApproval.Cause.STAKEHOLDER, SeniorApproval.cause(true, "REUNION", listOf("POLITICO")))
        assertNull(SeniorApproval.cause(true, null, emptyList()))
        assertNull(SeniorApproval.cause(false, "MESA DE DIALOGO", listOf("POLITICO")))
    }
}
