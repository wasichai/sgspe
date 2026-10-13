package sgspe.ai

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpStatus
import sgspe.F7ApiTest
import wasichai.core.common.ConflictException
import wasichai.core.common.WasichaiException

// el modelo de F7 contra wasichai de verdad (plan 7.1; ai-architecture.md §15): los flags y el índice de ai_suggestion y
// ai_interaction, la API genérica cerrada sobre los tres objetos de la IA (403, también al admin), la interacción inmutable
// también como la plataforma (409), la lectura por proyecto de las salidas de la IA y la llamada a la IA de un candidato,
// que es de su proyecto o del tenant
class AiModelApiTest : F7ApiTest() {
    @Test
    fun `los flags y el indice de la sugerencia y de la interaccion`() {
        val suggestion = tree(send("GET", "/api/objects/ai_suggestion", null, HttpStatus.OK))
        assertEquals(true to false, suggestion["apiOnly"].asBoolean() to suggestion["appendOnly"].asBoolean())
        val interaction = tree(send("GET", "/api/objects/ai_interaction", null, HttpStatus.OK))
        assertEquals(true to true, interaction["apiOnly"].asBoolean() to interaction["appendOnly"].asBoolean())
        val fields = tree(send("GET", "/api/metadata/objects/ai_suggestion/fields", null, HttpStatus.OK)).values().associateBy { it["name"].asString() }
        assertEquals(true, fields.getValue("target_record_id")["indexed"].asBoolean())
    }

    // Review Focus 5 del plan 7.1: solo el código de sgspe.ai escribe las salidas de la IA
    @Test
    fun `la API generica no escribe ninguna salida de la IA, ni al admin`() {
        val project = testProject()
        val interaction = aiInteraction(project)
        val suggestion = aiSuggestion(project)
        val social = member("SOCIAL_MANAGER")
        val bodies =
            mapOf(
                "ai_interaction" to sample("ai_interaction"),
                "ai_suggestion" to sample("ai_suggestion"),
                "commitment_candidate" to mapOf("project" to project, "proposed_text" to "Texto", "ai_generated" to true)
            )
        for ((name, attributes) in bodies) {
            for (caller in listOf(token, social.token)) {
                val created = exchange("POST", "/api/objects/$name/records", mapOf("attributes" to attributes), caller)
                assertEquals(HttpStatus.FORBIDDEN, created.status, "$name: ${created.body}")
            }
        }
        for ((name, id) in listOf("ai_interaction" to interaction, "ai_suggestion" to suggestion)) {
            val changed = exchange("PUT", "/api/objects/$name/records/$id", mapOf("attributes" to mapOf("model" to "otro")))
            assertEquals(HttpStatus.FORBIDDEN, changed.status, "$name: ${changed.body}")
            assertEquals(HttpStatus.FORBIDDEN, exchange("DELETE", "/api/objects/$name/records/$id").status, name)
        }
        assertEquals(AI_MODEL, record("ai_interaction", interaction)["attributes"]["model"].asString())
    }

    @Test
    fun `una interaccion no cambia ni se borra, tampoco como la plataforma`() {
        val interaction = aiInteraction(testProject())
        assertThrows<ConflictException> { platformUpdate("ai_interaction", interaction, mapOf("output_summary" to "Otro resumen.")) }
        assertThrows<ConflictException> { platformDelete("ai_interaction", interaction) }
        assertEquals("Dos posibles compromisos con cita.", record("ai_interaction", interaction)["attributes"]["output_summary"].asString())
    }

    @Test
    fun `las salidas de la IA de otro proyecto no se leen y las del tenant si`() {
        val social = member("SOCIAL_MANAGER")
        val other = project()
        val path = "/api/objects/ai_interaction/records"
        assertEquals(HttpStatus.OK, exchange("GET", "$path/${aiInteraction(testProject())}", token = social.token).status)
        assertEquals(HttpStatus.OK, exchange("GET", "$path/${aiInteraction(null)}", token = social.token).status)
        assertEquals(HttpStatus.NOT_FOUND, exchange("GET", "$path/${aiInteraction(other)}", token = social.token).status)
        assertEquals(HttpStatus.NOT_FOUND, exchange("GET", "/api/objects/ai_suggestion/records/${aiSuggestion(other)}", token = social.token).status)
        // security.md §7.8: ANALYST registra sus preguntas al asistente, pero no lee sugerencias; READ_ONLY, ninguna de las dos
        val analyst = member("ANALYST")
        assertEquals(HttpStatus.OK, exchange("GET", "$path?size=1", token = analyst.token).status)
        assertEquals(HttpStatus.FORBIDDEN, exchange("GET", "/api/objects/ai_suggestion/records?size=1", token = analyst.token).status)
        assertEquals(HttpStatus.FORBIDDEN, exchange("GET", "$path?size=1", token = member("READ_ONLY").token).status)
    }

    // Review Focus 4 del plan 7.1 (Decisión 2): la llamada a la IA de un candidato es de su proyecto o del tenant
    @Test
    fun `un candidato de IA no cita la interaccion de otro proyecto`() {
        val project = testProject()
        val refused = assertThrows<WasichaiException> { aiCandidate(project, interaction = aiInteraction(project())) }
        assertEquals(listOf("ai_interaction" to "PROJECT_MISMATCH"), refused.violations.map { it.field to it.message }, refused.message)
        val fromTenant = aiCandidate(project, interaction = aiInteraction(null))
        val candidate = record("commitment_candidate", aiCandidate(project, engagement = engagement(project)))["attributes"]
        assertEquals(true, candidate["ai_generated"].asBoolean())
        assertEquals("proposed", state("commitment_candidate", fromTenant))
    }

    // el alta humana (RF-COM-010) no fija la llamada a la IA: la escribe solo SuggestionWriter (plan 7.2b)
    @Test
    fun `el alta humana de un candidato no fija su interaccion`() {
        val refused =
            exchange(
                "POST",
                "/api/sgspe/commitment-candidates",
                mapOf("attributes" to mapOf("project" to testProject(), "proposed_text" to "x", "ai_interaction" to aiInteraction(testProject())))
            )
        assertEquals(HttpStatus.BAD_REQUEST, refused.status, refused.body)
        assertEquals(listOf("attributes.ai_interaction"), problemFields(refused.body))
    }
}
