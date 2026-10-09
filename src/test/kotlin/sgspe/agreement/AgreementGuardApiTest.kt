package sgspe.agreement

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F3DomainApiTest

// AgreementWriteGuard (WF-I, RF-AGR-002, RF-AGR-003): los casos que AgreementWorkflowApiTest (3.1) deja a 3.2; y la cláusula
// que nombra un compromiso, que DependentsDeleteGuard no deja borrar
class AgreementGuardApiTest : F3DomainApiTest() {
    private fun agreement(
        type: String,
        extra: Map<String, Any?> = emptyMap()
    ): String =
        createRecord(
            "agreement",
            mapOf(
                "project" to testProject(),
                "code" to uniqueName("A-"),
                "title" to "Acuerdo de prueba",
                "agreement_type" to type,
                "signed_date" to "2026-09-01",
                "effective_date" to "2026-09-15"
            ) + extra,
            withReason("alta de prueba")
        )

    @Test
    fun `una servidumbre sin predios no se activa, con predios y comunidad si`() {
        val community = createRecord("community", mapOf("name" to "Comunidad Ejemplo"))
        val id = agreement("SERVIDUMBRE", mapOf("community" to community))
        val refused = transition("agreement", id, "activate_from_draft", reason = "firmado")
        assertEquals(HttpStatus.CONFLICT, refused.status, refused.body)
        assertEquals("LAND_PARCEL_REQUIRED", code(refused))
        val parcel = createRecord("land_parcel", mapOf("code" to uniqueName("P-")))
        val linked = exchange("POST", "/api/objects/agreement/records/$id/related/agreement_land_parcel", mapOf("otherId" to parcel), token, withReason())
        assertEquals(true, linked.status.is2xxSuccessful, linked.body)
        assertEquals(HttpStatus.OK, transition("agreement", id, "activate_from_draft", reason = "firmado").status)
    }

    @Test
    fun `una autorizacion de asamblea exige la interaccion de tipo ASAMBLEA`() {
        val project = testProject()
        val meeting = agreement("AUTORIZACION DE ASAMBLEA", mapOf("assembly_engagement" to engagement(project, "REUNION")))
        assertEquals("ASSEMBLY_REQUIRED", code(transition("agreement", meeting, "activate_from_draft", reason = "firmado")))
        val assembly = agreement("AUTORIZACION DE ASAMBLEA", mapOf("assembly_engagement" to engagement(project, "ASAMBLEA")))
        assertEquals(HttpStatus.OK, transition("agreement", assembly, "activate_from_draft", reason = "firmado").status)
    }

    @Test
    fun `una clausula que nombra un compromiso no se borra y una libre si`() {
        val project = testProject()
        val id = agreement("CONVENIO")
        val social = member("SOCIAL_MANAGER", project)
        val cited = createRecord("agreement_clause", mapOf("agreement" to id, "clause_number" to "1", "clause_text" to "Mejorar el canal"))
        val free = createRecord("agreement_clause", mapOf("agreement" to id, "clause_number" to "2", "clause_text" to "Capacitar a la junta"))
        commitment(project, mapOf("agreement" to id, "agreement_clause" to cited))
        // ON DELETE SET NULL vaciaría commitment.agreement_clause sin motivo ni auditoría (commitment es requiresReason)
        val refused = exchange("DELETE", "/api/objects/agreement_clause/records/$cited", null, social.token)
        assertEquals(HttpStatus.CONFLICT, refused.status, refused.body)
        assertEquals("HAS_DEPENDENTS", code(refused))
        assertEquals("commitment.agreement_clause", problemFields(refused.body).single())
        assertEquals(HttpStatus.NO_CONTENT, exchange("DELETE", "/api/objects/agreement_clause/records/$free", null, social.token).status)
    }
}
