package sgspe.workflow

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F3ApiTest

// WF-K (workflows.md): un candidato es apiOnly, nace en `proposed` y una persona lo rechaza, con motivo obligatorio
// (TransitionReasonGuard). Aquí se crean como la plataforma; el alta humana, `accept` y `mark_duplicate` los prueba el
// plan 3.2 (CandidateApiTest)
class CommitmentCandidateWorkflowApiTest : F3ApiTest() {
    private val manager by lazy { member("COMMITMENT_MANAGER") }
    private val social by lazy { member("SOCIAL_MANAGER") }

    // un candidato en `proposed` de una interacción del proyecto
    private fun candidate(): String =
        platformCreate(
            "commitment_candidate",
            mapOf(
                "project" to testProject(),
                "proposed_text" to "Arreglar el canal de riego antes de la siembra",
                "ai_generated" to false,
                "engagement" to engagement(testProject())
            )
        )

    @Test
    fun `la API generica no da de alta un candidato, ni al admin`() {
        val attributes = mapOf("project" to testProject(), "proposed_text" to "Texto", "ai_generated" to false)
        for (caller in listOf(manager.token, token)) {
            assertEquals(HttpStatus.FORBIDDEN, exchange("POST", "/api/objects/commitment_candidate/records", mapOf("attributes" to attributes), caller).status)
        }
    }

    @Test
    fun `un candidato nace propuesto, una persona lo rechaza con motivo y despues ya no se decide`() {
        val id = candidate()
        assertEquals("proposed", state("commitment_candidate", id))
        assertEquals(HttpStatus.BAD_REQUEST, transition("commitment_candidate", id, "reject", social.token).status)
        assertEquals("proposed", state("commitment_candidate", id))
        move("commitment_candidate", id, "reject", social.token, "no es un compromiso")
        assertEquals("rejected", state("commitment_candidate", id))
        assertEquals(HttpStatus.CONFLICT, transition("commitment_candidate", id, "reject", manager.token, "otra vez").status)
        assertTrue(available("commitment_candidate", id, manager.token).isEmpty(), "un candidato rechazado no ofrece transiciones")
    }

    @Test
    fun `quien solo lee no decide y el listado lo explica`() {
        val id = candidate()
        // APPROVER y STAKEHOLDER_MANAGER leen los candidatos, sin UPDATE
        for (role in listOf("APPROVER", "STAKEHOLDER_MANAGER")) {
            val reader = member(role)
            assertEquals(HttpStatus.FORBIDDEN, transition("commitment_candidate", id, "reject", reader.token, "x").status, role)
            val offered = available("commitment_candidate", id, reader.token)
            assertEquals(setOf("accept", "reject", "mark_duplicate"), offered.keys, role)
            assertTrue(offered.values.all { it == (false to "requires permission UPDATE") }, role)
        }
        val forOfficer = available("commitment_candidate", id, member("COMMUNITY_RELATIONS_OFFICER").token)
        assertEquals(setOf("accept", "reject", "mark_duplicate"), forOfficer.keys)
        assertEquals("proposed", state("commitment_candidate", id))
    }
}
