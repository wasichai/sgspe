package sgspe.workflow

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F7ApiTest

// WF-K de las sugerencias de IA (workflows.md, «WF-K para sugerencias de IA»; RQ-AI-001, RQ-AI-003, RQ-AI-015): una
// sugerencia es apiOnly, nace en `proposed` y la decide una persona. aceptar es de comando (CommandTransitionGuard): por la
// API genérica, 403; rechazar y marcar duplicado piden motivo (TransitionReasonGuard) y no cambian el registro destino. desde 7.2b,
// una cuenta de servicio no decide (SuggestionDecisionGuard); la aceptación por su endpoint la prueba SuggestionAcceptanceApiTest
class AiSuggestionWorkflowApiTest : F7ApiTest() {
    private val social by lazy { member("SOCIAL_MANAGER") }
    private val risks by lazy { member("SOCIAL_RISK_MANAGER") }

    @Test
    fun `una sugerencia nace propuesta y la API generica no la da de alta, ni al admin`() {
        val id = aiSuggestion(testProject())
        assertEquals("proposed", state("ai_suggestion", id))
        val attributes = sample("ai_suggestion") + ("project" to testProject())
        for (caller in listOf(social.token, token)) {
            assertEquals(HttpStatus.FORBIDDEN, exchange("POST", "/api/objects/ai_suggestion/records", mapOf("attributes" to attributes), caller).status)
        }
    }

    // Review Focus 1 del plan 7.1
    @Test
    fun `aceptar por la API generica es 403 COMMAND_ONLY y la sugerencia sigue propuesta`() {
        val id = aiSuggestion(testProject())
        val refused = transition("ai_suggestion", id, "accept", social.token, "la acepto")
        assertEquals(HttpStatus.FORBIDDEN, refused.status, refused.body)
        assertEquals("COMMAND_ONLY", code(refused))
        assertEquals("proposed", state("ai_suggestion", id))
    }

    @Test
    fun `rechazar pide motivo y despues ya no se decide`() {
        val id = aiSuggestion(testProject())
        val without = transition("ai_suggestion", id, "reject", social.token)
        assertEquals(HttpStatus.BAD_REQUEST, without.status, without.body)
        assertEquals("REASON_REQUIRED", code(without))
        move("ai_suggestion", id, "reject", social.token, "la categoría no corresponde")
        assertEquals("rejected", state("ai_suggestion", id))
        assertEquals(HttpStatus.CONFLICT, transition("ai_suggestion", id, "reject", risks.token, "otra vez").status)
        assertTrue(available("ai_suggestion", id, risks.token).isEmpty(), "una sugerencia rechazada no ofrece transiciones")
    }

    // RQ-AI-015 (ASM-053): marcar duplicado es una decisión humana con motivo y nunca fusiona ni toca el registro destino
    @Test
    fun `marcar duplicado pide motivo y no cambia el registro destino`() {
        val engagement = engagement(testProject())
        val before = record("engagement", engagement)["attributes"]
        val id = aiSuggestion(testProject(), "BORRADOR DE ACTA", target = "engagement" to engagement)
        assertEquals(HttpStatus.BAD_REQUEST, transition("ai_suggestion", id, "mark_duplicate", risks.token).status)
        move("ai_suggestion", id, "mark_duplicate", risks.token, "repite el borrador de acta de otra sugerencia")
        assertEquals("duplicate", state("ai_suggestion", id))
        assertEquals(before, record("engagement", engagement)["attributes"])
    }

    @Test
    fun `quien solo lee no decide y el listado lo explica`() {
        val id = aiSuggestion(testProject())
        // AUDITOR lee todo el tenant sin UPDATE (security.md §7.1)
        val auditor = userWithRole("AUDITOR")
        assertEquals(HttpStatus.FORBIDDEN, transition("ai_suggestion", id, "reject", auditor.token, "x").status)
        val offered = available("ai_suggestion", id, auditor.token)
        assertEquals(setOf("accept", "reject", "mark_duplicate"), offered.keys)
        assertTrue(offered.values.all { it == (false to "requires permission UPDATE") }, "$offered")
        assertEquals("proposed", state("ai_suggestion", id))
    }

    // decisión 9 del plan 7.2b: rechazar y marcar duplicado tampoco los decide una cuenta de servicio, aunque tenga el rol
    @Test
    fun `una cuenta de servicio con el rol no rechaza ni marca duplicada`() {
        val service = serviceAccount("SOCIAL_MANAGER")
        val id = aiSuggestion(testProject())
        for (name in listOf("reject", "mark_duplicate")) {
            val refused = transition("ai_suggestion", id, name, service.token, "decisión automática")
            assertEquals(HttpStatus.FORBIDDEN, refused.status, refused.body)
            assertEquals("PERSON_REQUIRED", code(refused))
        }
        assertEquals("proposed", state("ai_suggestion", id))
    }
}
