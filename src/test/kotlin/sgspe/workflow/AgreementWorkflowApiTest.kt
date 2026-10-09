package sgspe.workflow

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F3ApiTest

// WF-I (workflows.md): la negociación, la vigencia, la suspensión, la conclusión, las dos resoluciones y las dos
// anulaciones, con el motivo que exige `requiresReason`, y las partes, cláusulas y predios del acuerdo. cada acuerdo es un
// CONVENIO firmado y vigente, que AgreementWriteGuard (plan 3.2) deja llegar a `active`; las reglas de esa guarda
// (permiso de uso de terreno o servidumbre sin predios o sin comunidad titular, autorización de asamblea sin la interacción
// de asamblea, firma y vigencia) y `validity_status`, que mantiene DeadlineJob, los prueba el plan 3.2 en
// AgreementWorkflowGuardsApiTest y DeadlineJobApiTest
class AgreementWorkflowApiTest : F3ApiTest() {
    private val officer by lazy { member("COMMUNITY_RELATIONS_OFFICER") }
    private val social by lazy { member("SOCIAL_MANAGER") }
    private val manager by lazy { member("PROJECT_MANAGER") }

    @Test
    fun `un convenio se negocia, entra en vigencia, se suspende, se reanuda y se concluye`() {
        val id = agreement(testProject(), officer)
        assertEquals("draft", state("agreement", id))
        move("agreement", id, "start_negotiation", officer.token, "primera reunión con la junta")
        move("agreement", id, "activate", social.token, "convenio firmado")
        assertEquals("active", state("agreement", id))
        move("agreement", id, "suspend", manager.token, "observación de la asamblea")
        move("agreement", id, "resume", social.token, "observación levantada")
        move("agreement", id, "conclude", manager.token, "obligaciones cumplidas")
        assertEquals("concluded", state("agreement", id))
    }

    @Test
    fun `un acuerdo firmado se registra vigente desde el borrador y PROJECT_MANAGER lo resuelve`() {
        val active = agreement(testProject(), social)
        move("agreement", active, "activate_from_draft", social.token, "se registra firmado")
        move("agreement", active, "terminate", manager.token, "incumplimiento de la contraparte")
        assertEquals("terminated", state("agreement", active))
        val suspended = agreement(testProject(), social)
        move("agreement", suspended, "activate_from_draft", manager.token, "se registra firmado")
        move("agreement", suspended, "suspend", social.token, "conflicto en la zona")
        move("agreement", suspended, "terminate_suspended", manager.token, "no se pudo reanudar")
        assertEquals("terminated", state("agreement", suspended))
    }

    @Test
    fun `un borrador y uno en negociacion se anulan`() {
        val draft = agreement(testProject(), officer)
        move("agreement", draft, "cancel_draft", social.token, "registro duplicado")
        val negotiating = agreement(testProject(), officer)
        move("agreement", negotiating, "start_negotiation", officer.token, "inicio")
        move("agreement", negotiating, "cancel_in_negotiation", manager.token, "la comunidad no aceptó")
        assertEquals(listOf("cancelled", "cancelled"), listOf(state("agreement", draft), state("agreement", negotiating)))
    }

    @Test
    fun `resolver es de PROJECT_MANAGER, sin motivo se responde 400 y desde otro estado 409`() {
        val id = agreement(testProject(), social)
        move("agreement", id, "activate_from_draft", social.token, "firmado")
        assertEquals(HttpStatus.FORBIDDEN, transition("agreement", id, "terminate", social.token, "x").status)
        assertEquals(false to "requires role PROJECT_MANAGER", available("agreement", id, social.token)["terminate"])
        val withoutReason = transition("agreement", id, "suspend", manager.token)
        assertEquals(HttpStatus.BAD_REQUEST, withoutReason.status, withoutReason.body)
        assertEquals(listOf("reason"), problemFields(withoutReason.body))
        assertEquals(HttpStatus.CONFLICT, transition("agreement", id, "start_negotiation", officer.token, "x").status)
        // COMMITMENT_MANAGER solo lee los acuerdos
        assertEquals(HttpStatus.FORBIDDEN, transition("agreement", id, "suspend", member("COMMITMENT_MANAGER").token, "x").status)
        assertEquals("active", state("agreement", id))
    }

    @Test
    fun `el acuerdo lleva partes, clausulas unicas por numero y predios vinculados con motivo`() {
        val id = agreement(testProject(), officer)
        createRecord("agreement_party", mapOf("agreement" to id, "organization" to organization(), "signatory_role" to "Presidente"), token = officer.token)
        val clause = mapOf("agreement" to id, "clause_number" to "1", "clause_text" to "La empresa mejora el canal de riego.", "creates_commitment" to true)
        createRecord("agreement_clause", clause, token = social.token)
        val repeated = exchange("POST", "/api/objects/agreement_clause/records", mapOf("attributes" to clause), social.token)
        assertEquals(HttpStatus.CONFLICT, repeated.status, repeated.body)
        // agreement es requiresReason: el vínculo con un predio pide motivo; SOCIAL_MANAGER lo hace con su `u` sobre land_parcel
        val parcel = fixture("land_parcel").id
        val withoutReason = link("agreement", id, "agreement_land_parcel", parcel, social.token)
        assertEquals(HttpStatus.BAD_REQUEST, withoutReason.status, withoutReason.body)
        assertEquals(HttpStatus.NO_CONTENT, linkWithReason("agreement", id, "agreement_land_parcel", parcel, social.token).status)
        val related = tree(send("GET", "/api/objects/agreement/records/$id/related/agreement_land_parcel", null, HttpStatus.OK))
        assertEquals(listOf(parcel), related["content"].values().map { it["id"].asString() })
    }
}
