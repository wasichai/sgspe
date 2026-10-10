package sgspe.attention

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F4ApiTest

// la derivación de una pregunta de reunión (RF-ENG-006, api.md §4.4): crea la QCS con la pregunta, la interacción, el proyecto,
// el canal REUNION y la recepción de la reunión, marca referred_at y con eso la interacción cierra (RN-ENG-005); una pregunta
// no se deriva dos veces, ni la de una interacción sin realizar
class ReferralApiTest : F4ApiTest() {
    private val officer by lazy { member("COMMUNITY_RELATIONS_OFFICER") }

    // una reunión realizada con acta aprobada y una pregunta pendiente: su id y el de la pregunta
    private fun meeting(held: Boolean = true): Pair<String, String> {
        val meeting = engagement(testProject(), attributes = mapOf("held_at" to "2026-10-05T15:00:00Z", "minutes" to "Se presentó el proyecto."))
        participant(meeting)
        val question = createRecord("engagement_question", mapOf("engagement" to meeting, "question_text" to "¿Quién arregla el canal?"))
        if (held) for (step in listOf("register_held_unplanned", "draft_minutes", "submit_minutes", "approve_minutes")) move("engagement", meeting, step)
        return meeting to question
    }

    private fun refer(
        question: String,
        target: String = "inquiry",
        account: Account = officer,
        attributes: Map<String, Any?> = mapOf("code" to uniqueName("CO-"), "description" to "Pregunta derivada de la reunión.")
    ): Response =
        exchange(
            "POST",
            "/api/sgspe/engagement-questions/$question/referral",
            mapOf("target" to target, "attributes" to attributes),
            account.token,
            withReason("derivada")
        )

    @Test
    fun `derivar crea la consulta de la reunion y deja cerrar la interaccion`() {
        val (meeting, question) = meeting()
        val response = refer(question)
        assertEquals(HttpStatus.CREATED, response.status, response.body)
        val created = tree(response.body)["created"]["attributes"]
        assertEquals(question, created["origin_question"].asString())
        assertEquals(meeting, created["origin_engagement"].asString())
        assertEquals(testProject(), created["project"].asString())
        assertEquals("REUNION", created["channel"].asString())
        assertTrue(created["received_at"].asString().startsWith("2026-10-05T15:00"))
        assertTrue(tree(response.body)["question"]["attributes"]["referred_at"].asString().isNotBlank())
        move("engagement", meeting, "close")
    }

    @Test
    fun `una pregunta no se deriva dos veces`() {
        val (_, question) = meeting()
        assertEquals(HttpStatus.CREATED, refer(question).status)
        val again = refer(question, "grievance")
        assertEquals(HttpStatus.CONFLICT, again.status, again.body)
        assertEquals("ALREADY_REFERRED", code(again))
    }

    @Test
    fun `la pregunta de una interaccion sin realizar no se deriva y un destino invalido es INVALID_TARGET`() {
        val (_, question) = meeting(held = false)
        val planned = refer(question)
        assertEquals(HttpStatus.CONFLICT, planned.status, planned.body)
        assertEquals("STATE_MISMATCH", code(planned))
        assertEquals(listOf("engagement"), problemFields(planned.body))
        val invalid = refer(question, "commitment")
        assertEquals(HttpStatus.BAD_REQUEST, invalid.status, invalid.body)
        assertEquals("INVALID_TARGET", code(invalid))
    }

    // decisión 3 del plan 4.2a: el gestor de quejas deriva con su `u` sobre engagement_question, que solo le deja escribir
    // referred_at (derivado, lo pone el comando con la marca): editar la pregunta es un 400 que nombra el campo
    @Test
    fun `el gestor de quejas deriva, pero no edita la pregunta`() {
        val (_, question) = meeting()
        val manager = member("GRIEVANCE_MANAGER")
        val response = refer(question, account = manager)
        assertEquals(HttpStatus.CREATED, response.status, response.body)
        assertTrue(tree(response.body)["question"]["attributes"]["referred_at"].asString().isNotBlank())
        val edit =
            exchange(
                "PUT",
                "/api/objects/engagement_question/records/$question",
                mapOf("attributes" to mapOf("question_text" to "Otra pregunta.")),
                manager.token
            )
        assertEquals(HttpStatus.BAD_REQUEST, edit.status, edit.body)
        assertEquals(listOf("question_text"), problemFields(edit.body))
    }

    @Test
    fun `una derivacion confidencial la recibe quien deriva`() {
        val (_, question) = meeting()
        val other = member("COMMUNITY_RELATIONS_OFFICER")
        val foreign =
            refer(
                question,
                "grievance",
                attributes =
                    mapOf(
                        "code" to uniqueName("QJ-"),
                        "description" to "Queja planteada en la reunión.",
                        "confidential" to true,
                        "received_by_user_id" to other.id,
                        "received_by_user_email" to other.email
                    )
            )
        assertEquals(HttpStatus.BAD_REQUEST, foreign.status, foreign.body)
        assertEquals("CONFIDENTIAL_RECEIVER_REQUIRED", code(foreign))
    }
}
