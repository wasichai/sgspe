package sgspe.evidence

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F4ApiTest

// las evidencias de una QCS (api.md §4.9, especificación de F4 §3.1): el cargo y la carta firmados se suben como evidencia y
// se vinculan a la queja, la consulta o la solicitud; a una confidencial solo se vincula una evidencia CONFIDENCIAL o DATOS
// PERSONALES (409 EVIDENCE_NOT_RESTRICTED)
class EvidenceQcsLinksApiTest : F4ApiTest() {
    private fun linkTo(
        evidence: String,
        objectName: String,
        recordId: String,
        account: Account
    ): Response =
        exchange(
            "POST",
            "/api/sgspe/evidence/$evidence/links",
            mapOf("object" to objectName, "recordId" to recordId),
            account.token,
            withReason("cargo firmado")
        )

    @Test
    fun `una evidencia interna no se vincula a una queja confidencial y una de datos personales si`() {
        val officer = member("COMMUNITY_RELATIONS_OFFICER")
        val grievance = grievance(testProject(), officer, mapOf("confidential" to true))
        val internal = evidence(testProject(), "FORMULARIO", "INTERNA", officer)
        val refused = linkTo(internal, "grievance", grievance, officer)
        assertEquals(HttpStatus.CONFLICT, refused.status, refused.body)
        assertEquals("EVIDENCE_NOT_RESTRICTED", code(refused))
        assertEquals(listOf("classification"), problemFields(refused.body))
        val personal = evidence(testProject(), "FORMULARIO", "DATOS PERSONALES", officer)
        assertEquals(HttpStatus.NO_CONTENT, linkTo(personal, "grievance", grievance, officer).status)
    }

    @Test
    fun `una carta interna se vincula a una consulta y a una solicitud que no son confidenciales`() {
        val officer = member("COMMUNITY_RELATIONS_OFFICER")
        val letter = evidence(testProject(), "CARTA", "INTERNA", officer)
        assertEquals(HttpStatus.NO_CONTENT, linkTo(letter, "inquiry", inquiry(testProject(), officer), officer).status)
        assertEquals(HttpStatus.NO_CONTENT, linkTo(letter, "stakeholder_request", stakeholderRequest(testProject(), officer), officer).status)
    }
}
