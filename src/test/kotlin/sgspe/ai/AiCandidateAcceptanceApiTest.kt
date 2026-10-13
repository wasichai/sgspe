package sgspe.ai

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F7ApiTest

// POST /api/sgspe/commitment-candidates/{id}/accept (F3) acepta también los candidatos de la IA (especificación de F7 §3.4,
// ai-architecture.md §4.2): el candidato no trae responsable, lo elige quien acepta; el compromiso nace en draft con los valores
// revisados, sin marcadores, y la interacción de origen del candidato
class AiCandidateAcceptanceApiTest : F7ApiTest() {
    @Test
    fun `quien acepta un candidato de la IA elige al responsable`() {
        val engagement = engagement(testProject())
        val candidate = aiCandidate(testProject(), engagement = engagement)
        val manager = member("COMMITMENT_MANAGER")
        val owner = userWithRole("COMMITMENT_MANAGER")
        val attributes =
            mapOf(
                "code" to uniqueName("C-"),
                "title" to "Reparar el canal de riego de la Comunidad Ejemplo",
                "owner_user_id" to owner.id,
                "owner_user_email" to owner.email
            )
        val accepted =
            exchange(
                "POST",
                "/api/sgspe/commitment-candidates/$candidate/accept",
                mapOf("attributes" to attributes),
                manager.token,
                ifMatch("commitment_candidate", candidate, manager.token) + withReason("revisado en la bandeja")
            )
        assertEquals(HttpStatus.CREATED, accepted.status, accepted.body)
        val commitment = tree(accepted.body)["commitment"]
        assertEquals("draft", commitment["state"].asString())
        assertEquals(owner.email, commitment["attributes"]["owner_user_email"].asString())
        assertEquals(engagement, commitment["attributes"]["origin_engagement"].asString())
        assertEquals("accepted", state("commitment_candidate", candidate))
    }
}
