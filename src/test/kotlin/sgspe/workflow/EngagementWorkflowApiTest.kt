package sgspe.workflow

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F2ApiTest

// WF-C (workflows.md): el ciclo con acta y el de postergar y cancelar, por los roles del equipo de relacionamiento; el
// acta la devuelve y la aprueba solo SOCIAL_MANAGER. las guardas de EngagementWriteGuard (asistente, tipos formales,
// preguntas pendientes, motivo de cancelación, close_light de una REUNION), EditAnyGuard sobre interacciones y
// participantes ajenos y los conteos de asistentes los prueba el plan 2.2 en EngagementWorkflowGuardsApiTest y
// EditAnyApiTest, no aquí. cada interacción de esta clase ya trae lo que esas guardas piden
class EngagementWorkflowApiTest : F2ApiTest() {
    private fun engagement(
        account: Account,
        attributes: Map<String, Any?> = emptyMap()
    ): String =
        fixture(
            "engagement",
            mapOf("engagement_type" to "REUNION", "title" to "Reunión con la junta", "scheduled_at" to "2026-10-08T15:00:00Z") + attributes,
            account
        ).id

    @Test
    fun `una reunion se programa, se realiza, lleva acta revisada y aprobada y se cierra`() {
        val officer = member("COMMUNITY_RELATIONS_OFFICER")
        val social = member("SOCIAL_MANAGER")
        val engagement = engagement(officer, mapOf("held_at" to "2026-10-08T16:00:00Z", "minutes" to "Acuerdos de la reunión."))
        createRecord(
            "engagement_participant",
            mapOf("engagement" to engagement, "represented_group" to "Junta directiva", "represented_people_count" to 3, "attended" to true),
            token = officer.token
        )
        assertEquals("planned", state("engagement", engagement))
        for (step in listOf("schedule", "register_held", "draft_minutes", "submit_minutes")) {
            move("engagement", engagement, step, officer.token)
        }
        assertEquals(HttpStatus.FORBIDDEN, transition("engagement", engagement, "approve_minutes", officer.token).status)
        assertEquals(HttpStatus.FORBIDDEN, transition("engagement", engagement, "return_minutes", officer.token, "falta la lista").status)
        move("engagement", engagement, "return_minutes", social.token, "falta un acuerdo")
        move("engagement", engagement, "submit_minutes", officer.token)
        move("engagement", engagement, "approve_minutes", social.token)
        move("engagement", engagement, "close", officer.token)
        assertEquals("closed", state("engagement", engagement))
    }

    @Test
    fun `una reunion se posterga, se reprograma y se cancela`() {
        val manager = member("STAKEHOLDER_MANAGER")
        val engagement = engagement(manager, mapOf("cancel_reason" to "Lluvias intensas"))
        move("engagement", engagement, "schedule", manager.token)
        move("engagement", engagement, "postpone", manager.token, "la junta pidió otra fecha")
        assertEquals("postponed", state("engagement", engagement))
        move("engagement", engagement, "reschedule", manager.token)
        move("engagement", engagement, "cancel_scheduled", manager.token, "lluvias intensas")
        assertEquals("cancelled", state("engagement", engagement))
    }

    @Test
    fun `FIELD_AGENT mueve su propia interaccion`() {
        val agent = member("FIELD_AGENT")
        val engagement = engagement(agent)
        move("engagement", engagement, "schedule", agent.token)
        assertEquals("scheduled", state("engagement", engagement))
    }

    @Test
    fun `quien solo lee recibe 403 y close desde planned responde 409`() {
        val officer = member("COMMUNITY_RELATIONS_OFFICER")
        val engagement = engagement(officer)
        assertEquals(HttpStatus.FORBIDDEN, transition("engagement", engagement, "schedule", member("ANALYST").token).status)
        assertEquals(HttpStatus.FORBIDDEN, transition("engagement", engagement, "schedule", member("PROJECT_MANAGER").token).status)
        assertEquals(HttpStatus.CONFLICT, transition("engagement", engagement, "close", officer.token).status)
        assertEquals("planned", state("engagement", engagement))
    }
}
