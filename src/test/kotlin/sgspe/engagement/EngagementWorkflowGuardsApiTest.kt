package sgspe.engagement

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.DomainApiTest

// los casos de WF-C que dependen de EngagementWriteGuard y TransitionReasonGuard (workflows.md §Pruebas,
// EngagementWorkflowApiTest)
class EngagementWorkflowGuardsApiTest : DomainApiTest() {
    @Test
    fun `programar exige scheduled_at y realizar exige held_at y un asistente`() {
        val project = projectWithCrs()
        val engagement = engagement(project)
        assertEquals(listOf("scheduled_at"), problemFields(transition("engagement", engagement, "schedule").body))
        edit("engagement", engagement, mapOf("held_at" to "2026-05-12T15:00:00Z"))
        participant(engagement, attributes = mapOf("attended" to false))
        val held = transition("engagement", engagement, "register_held_unplanned")
        assertEquals(HttpStatus.CONFLICT, held.status)
        assertEquals(listOf("attended"), problemFields(held.body))
        participant(engagement)
        move("engagement", engagement, "register_held_unplanned")
    }

    @Test
    fun `una REUNION no cierra sin acta con la politica activa, y un contacto si`() {
        val project = projectWithCrs()
        val meeting = engagement(project, "REUNION INFORMATIVA", attributes = mapOf("held_at" to "2026-05-12T15:00:00Z"))
        participant(meeting)
        move("engagement", meeting, "register_held_unplanned")
        assertEquals(listOf("engagement_type"), problemFields(transition("engagement", meeting, "close_light").body))
        val contact = engagement(project, "CONTACTO CARA A CARA", attributes = mapOf("held_at" to "2026-05-12T15:00:00Z"))
        participant(contact)
        move("engagement", contact, "register_held_unplanned")
        move("engagement", contact, "close_light")
    }

    @Test
    fun `cerrar con una pregunta pendiente se rechaza y respondida pasa`() {
        val project = projectWithCrs()
        val meeting = engagement(project, "REUNION", attributes = mapOf("held_at" to "2026-05-12T15:00:00Z", "minutes" to "Se presentó el proyecto."))
        participant(meeting)
        val question = createRecord("engagement_question", mapOf("engagement" to meeting, "question_text" to "¿Habrá empleo local?"))
        for (step in listOf("register_held_unplanned", "draft_minutes", "submit_minutes", "approve_minutes")) move("engagement", meeting, step)
        val dryRun =
            tree(send("POST", "/api/sgspe/transition-checks", mapOf("object" to "engagement", "recordId" to meeting, "transition" to "close"), HttpStatus.OK))
        assertEquals(listOf("PENDING_QUESTIONS"), dryRun["blocking"].values().map { it["code"].asString() })
        assertEquals(listOf("engagement_question"), problemFields(transition("engagement", meeting, "close").body))
        edit("engagement_question", question, mapOf("answer" to "Sí, según la política del proyecto", "answered_at" to "2026-05-13T15:00:00Z"))
        move("engagement", meeting, "close")
    }

    @Test
    fun `cancelar exige cancel_reason y el motivo de la transicion`() {
        val project = projectWithCrs()
        val engagement = engagement(project)
        assertEquals(listOf("reason"), problemFields(transition("engagement", engagement, "cancel_planned").body))
        assertEquals(listOf("cancel_reason"), problemFields(transition("engagement", engagement, "cancel_planned", reason = "lluvia").body))
        edit("engagement", engagement, mapOf("cancel_reason" to "Lluvias intensas"))
        move("engagement", engagement, "cancel_planned", reason = "lluvia")
    }

    @Test
    fun `represented_people_count menor que 1 es 400`() {
        val engagement = engagement(projectWithCrs())
        val response =
            exchange(
                "POST",
                "/api/objects/engagement_participant/records",
                mapOf("attributes" to mapOf("engagement" to engagement, "represented_people_count" to 0))
            )
        assertEquals(HttpStatus.BAD_REQUEST, response.status)
        assertEquals(listOf("represented_people_count"), problemFields(response.body))
    }
}
