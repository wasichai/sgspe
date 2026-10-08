package sgspe.engagement

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.DomainApiTest
import sgspe.common.IfMatch

// S24 del portal (Fase 2.3, Tarea 21) registra una interacción como escrituras sucesivas por la API genérica, sin
// transacción: el alta, los vínculos con grupos de interés y temas, los participantes, las preguntas y al final la
// transición, con If-Match del updatedAt que devolvió el alta. las guardas y los listeners tienen que dejar pasar esa
// secuencia: los conteos que escribe AttendanceCountsListener no invalidan ese If-Match (Versions)
class EngagementRegistrationApiTest : DomainApiTest() {
    @Test
    fun `FIELD_AGENT registra una interaccion realizada como la registra el portal`() {
        val project = projectWithCrs()
        val agent = member("FIELD_AGENT", project)
        val stakeholder = stakeholder(project)
        val topic = createRecord("topic", mapOf("code" to uniqueName("T-"), "name" to "Agua", "active" to true))
        val created =
            tree(
                send(
                    "POST",
                    "/api/objects/engagement/records",
                    mapOf(
                        "attributes" to
                            mapOf(
                                "project" to project,
                                "engagement_type" to "REUNION INFORMATIVA",
                                "title" to "Asamblea de octubre",
                                "held_at" to "2026-10-07T15:00:00Z"
                            )
                    ),
                    HttpStatus.CREATED,
                    agent.token
                )
            )
        val engagement = created["id"].asString()
        assertEquals(HttpStatus.NO_CONTENT, link("engagement", engagement, "engagement_stakeholder", stakeholder, agent.token).status)
        assertEquals(HttpStatus.NO_CONTENT, link("engagement", engagement, "engagement_topic", topic, agent.token).status)
        participant(engagement, agent.token, mapOf("represented_people_count" to 12, "sex" to "FEMENINO", "participant_scope" to "AIDS"))
        participant(engagement, agent.token, mapOf("represented_people_count" to 8, "sex" to "MASCULINO", "participant_scope" to "AIDS"))
        createRecord("engagement_question", mapOf("engagement" to engagement, "question_text" to "¿Y el agua?", "topic" to topic), token = agent.token)
        val held =
            transitionWith(
                "engagement",
                engagement,
                "register_held_unplanned",
                agent.token,
                headers = mapOf(IfMatch.HEADER to "\"${created["updatedAt"].asString()}\"")
            )
        assertEquals(HttpStatus.OK, held.status, held.body)
        val stored = record("engagement", engagement)
        assertEquals("held", stored["state"].asString())
        assertEquals(20, stored["attributes"]["attendees_total_count"].asInt())
        assertEquals(12, stored["attributes"]["attendees_female_count"].asInt())
    }
}
