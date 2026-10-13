package sgspe.ai

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import sgspe.F7ApiTest
import wasichai.core.common.WasichaiException
import java.math.BigDecimal
import java.util.UUID

// SuggestionWriter contra wasichai de verdad (ai-architecture.md §9.1, §6.18 del modelo de dominio): escribe como la persona; deja
// fuera la sugerencia cuyo destino no se lee o es de otro proyecto y la que repite una propuesta igual; y ProjectLinkGuard rechaza la
// que cuelga de una ai_interaction de otro proyecto
class SuggestionWriterApiTest : F7ApiTest() {
    @Autowired
    private lateinit var writer: SuggestionWriter

    private fun draft(
        target: Pair<String, String>,
        interaction: String,
        value: String = """{"field":"category","value":"EMPLEO"}"""
    ) = SuggestionDraft(
        "CLASIFICACION",
        target.first,
        UUID.fromString(target.second),
        value,
        "el agua del canal llega turbia",
        BigDecimal("0.80"),
        AI_MODEL,
        UUID.fromString(testProject()),
        UUID.fromString(interaction)
    )

    @Test
    fun `escribe como la persona y deja fuera lo de otro proyecto y lo repetido`() {
        val cro = member("COMMUNITY_RELATIONS_OFFICER")
        val own = grievance(testProject())
        val other = project()
        val foreign = grievance(other)
        val interaction = aiInteraction(testProject(), "AI-08", cro)
        val written =
            asUser(cro) {
                writer.write("AI-08", RunProposals(suggestions = listOf(draft("grievance" to own, interaction), draft("grievance" to foreign, interaction))))
            }
        assertEquals(listOf(own), written.suggestions.map { it.attributes["target_record_id"].toString() })
        assertEquals("proposed", written.suggestions.single().state)
        val again = asUser(cro) { writer.write("AI-08", RunProposals(suggestions = listOf(draft("grievance" to own, interaction)))) }
        assertTrue(again.suggestions.isEmpty())
    }

    @Test
    fun `una ai_interaction de otro proyecto es 409 PROJECT_MISMATCH`() {
        val cro = member("COMMUNITY_RELATIONS_OFFICER")
        val other = project()
        assign(cro, other, "COMMUNITY_RELATIONS_OFFICER")
        val elsewhere = aiInteraction(other, "AI-08", cro)
        val refused =
            assertThrows<WasichaiException> {
                asUser(cro) { writer.write("AI-08", RunProposals(suggestions = listOf(draft("grievance" to grievance(testProject()), elsewhere)))) }
            }
        assertEquals(409, refused.status.value())
        assertEquals("ai_interaction" to "PROJECT_MISMATCH", refused.violations.single().let { it.field to it.message })
    }

    // la escritura de a una (AI-07, plan 7.2c): rechaza lo que la corrida dejaría fuera y devuelve la propuesta que ya espera
    @Test
    fun `de a una, un destino que no se lee es 403 y la repetida vuelve tal como esta`() {
        val cro = member("COMMUNITY_RELATIONS_OFFICER")
        val own = grievance(testProject())
        val foreign = grievance(project())
        val interaction = aiInteraction(testProject(), "AI-08", cro)
        val refused = assertThrows<WasichaiException> { asUser(cro) { writer.suggestion(draft("grievance" to foreign, interaction)) } }
        assertEquals("target_record_id" to "OBJECT_NOT_READABLE", refused.violations.single().let { it.field to it.message })
        val first = asUser(cro) { writer.suggestion(draft("grievance" to own, interaction)) }
        val again = asUser(cro) { writer.suggestion(draft("grievance" to own, interaction)) }
        assertEquals(first.id, again.id)
    }
}
