package sgspe.workflow

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F2ApiTest

// WF-L (workflows.md): iniciar, concluir del todo o en parte y cancelar, por COMMUNITY_RELATIONS_OFFICER, FIELD_AGENT y
// SOCIAL_MANAGER. EditAnyGuard sobre visitas ajenas y el motivo obligatorio de `cancel` los agrega el plan 2.2
class FieldVisitWorkflowApiTest : F2ApiTest() {
    @Test
    fun `FIELD_AGENT inicia y concluye en parte la visita que creo`() {
        val agent = member("FIELD_AGENT")
        val visit = fixture("field_visit", mapOf("title" to "Visita a la quebrada"), agent).id
        assertEquals("planned", state("field_visit", visit))
        move("field_visit", visit, "start", agent.token)
        move("field_visit", visit, "complete_partial", agent.token)
        assertEquals("partial", state("field_visit", visit))
        assertEquals(emptyMap<String, Pair<Boolean, String?>>(), available("field_visit", visit, agent.token))
    }

    @Test
    fun `COMMUNITY_RELATIONS_OFFICER concluye y SOCIAL_MANAGER cancela con motivo`() {
        val officer = member("COMMUNITY_RELATIONS_OFFICER")
        val visit = fixture("field_visit", account = officer).id
        move("field_visit", visit, "start", officer.token)
        move("field_visit", visit, "complete", officer.token)
        assertEquals("completed", state("field_visit", visit))

        // SOCIAL_MANAGER no crea visitas (RU con EDIT_ANY): cancela la del relacionista
        val social = member("SOCIAL_MANAGER")
        val other = fixture("field_visit", account = officer).id
        move("field_visit", other, "cancel", social.token, "la carretera está cerrada")
        assertEquals("cancelled", state("field_visit", other))
    }

    @Test
    fun `quien solo lee recibe 403 y concluir antes de iniciar responde 409`() {
        val officer = member("COMMUNITY_RELATIONS_OFFICER")
        val visit = fixture("field_visit", account = officer).id
        assertEquals(HttpStatus.FORBIDDEN, transition("field_visit", visit, "start", member("STAKEHOLDER_MANAGER").token).status)
        assertEquals(HttpStatus.FORBIDDEN, transition("field_visit", visit, "start", member("GIS_ANALYST").token).status)
        assertEquals(HttpStatus.CONFLICT, transition("field_visit", visit, "complete", officer.token).status)
        assertEquals("planned", state("field_visit", visit))
    }
}
