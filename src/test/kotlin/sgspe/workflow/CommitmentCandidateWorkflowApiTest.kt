package sgspe.workflow

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F3ApiTest

// WF-K para candidatos a compromiso (workflows.md): un candidato es apiOnly, nace en `proposed` y solo una persona lo
// rechaza, con motivo (TransitionReasonGuard). aquí los candidatos se crean como la plataforma: el alta humana
// (POST /api/sgspe/commitment-candidates), `accept` por su endpoint y por la API genérica (CommandTransitionGuard),
// `mark_duplicate` con y sin el `duplicate_of` que escribe POST .../duplicate (CommitmentWriteGuard) y las cuentas de
// servicio los prueba el plan 3.2 en CandidateApiTest
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
        for (token in listOf(manager.token, token)) {
            assertEquals(HttpStatus.FORBIDDEN, exchange("POST", "/api/objects/commitment_candidate/records", mapOf("attributes" to attributes), token).status)
        }
    }

    @Test
    fun `un candidato nace propuesto, una persona lo rechaza con motivo y despues ya no se decide`() {
        val id = candidate()
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
            assertTrue(available("commitment_candidate", id, reader.token).values.all { it == (false to "requires permission UPDATE") }, role)
        }
        val forOfficer = available("commitment_candidate", id, member("COMMUNITY_RELATIONS_OFFICER").token)
        assertEquals(setOf("accept", "reject", "mark_duplicate"), forOfficer.keys)
        assertEquals("proposed", state("commitment_candidate", id))
    }
}
