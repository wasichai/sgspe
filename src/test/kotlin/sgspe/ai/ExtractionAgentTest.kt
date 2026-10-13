package sgspe.ai

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import wasichai.core.common.WasichaiException
import java.io.File
import java.math.BigDecimal
import java.util.UUID

// AI-01 con FakeModel y MockModel (ai-architecture.md §6.1 y §15): lo que sale hacia el modelo está seudonimizado; cada hallazgo
// válido es una EXTRACCION con la forma de su fila de §4.2 y marcadores estables; lo que no se valida se descarta
class ExtractionAgentTest {
    private val topic = UUID.fromString("00000000-0000-0000-0000-00000000f001")
    private val organization = UUID.fromString("00000000-0000-0000-0000-00000000f002")
    private val community = UUID.fromString("00000000-0000-0000-0000-00000000f003")
    private val lookups =
        FakeLookups(
            topics = listOf(TopicOption(topic, "Agua y riego")),
            places = mapOf(("community" to "Comunidad Ejemplo") to community),
            organizations = mapOf("Comunidad Ejemplo" to organization)
        )
    private val log = RecordedInteractions()

    private fun agent(model: StructuredModel) = ExtractionAgent(RunFixtures.calls(model, log), lookups, RunFixtures.CATALOG, RunFixtures.properties())

    private fun item(
        item: String,
        quote: String,
        confidence: Double = 0.8,
        kind: String? = null,
        name: String? = null,
        field: String? = null,
        value: String? = null,
        topic: String? = null,
        organizationType: String? = null,
        stakeholderType: String? = null,
        clauseNumber: String? = null,
        clauseText: String? = null
    ) = ExtractedItem(item, kind, name, organizationType, stakeholderType, topic, field, value, clauseNumber, clauseText, null, quote, confidence)

    private val found =
        listOf(
            item(
                "actor",
                "[PERSONA_1] (DNI [DOCUMENTO_1], celular [TELEFONO_1]) pidió reparar el canal",
                kind = "person",
                name = "[PERSONA_1]",
                stakeholderType = "LIDER LOCAL"
            ),
            item("actor", "[PERSONA] agradeció la reunión", kind = "person", name = "[PERSONA]"),
            item(
                "actor",
                "Acta de la asamblea de la Comunidad Ejemplo",
                kind = "organization",
                name = "Comunidad Ejemplo",
                organizationType = "COMUNIDAD CAMPESINA"
            ),
            item("topic", "pidió reparar el canal de riego del sector bajo", topic = topic.toString()),
            item("place", "Acta de la asamblea de la Comunidad Ejemplo", field = "community", name = "Comunidad Ejemplo"),
            // sin acuerdo en la fuente, una fecha no tiene destino
            item("date", "antes del 30 de noviembre de 2026", field = "expiry_date", value = "2026-11-30"),
            // cita inventada, catálogo inválido, tema fuera de la lista y confianza fuera de rango
            item("topic", "La empresa construirá un colegio nuevo", topic = topic.toString()),
            item("actor", "Acta de la asamblea de la Comunidad Ejemplo", kind = "organization", name = "Club Ejemplo", organizationType = "CLUB DE FUTBOL"),
            item("topic", "pidió reparar el canal de riego del sector bajo", topic = UUID.randomUUID().toString()),
            item("place", "Acta de la asamblea de la Comunidad Ejemplo", confidence = 1.5, field = "community", name = "Comunidad Ejemplo")
        )

    private fun run(
        source: RunSource = RunFixtures.minutes(),
        output: ExtractionOutput = ExtractionOutput(found)
    ) = runBlocking { agent(FakeModel { output }).run(source, RunFixtures.NAMES, RunBudget(8)) }

    @Test
    fun `lo que sale hacia el modelo esta seudonimizado y lleva la lista de temas`() {
        val mock = MockModel(FakeModel { ExtractionOutput() })
        runBlocking { agent(mock).run(RunFixtures.minutes(), RunFixtures.NAMES, RunBudget(8)) }
        val request = mock.sent.single()
        assertEquals("ai-01@1", request.prompt.reference)
        assertEquals(ExtractionOutput::class.java, request.outputClass)
        val text = request.prompt.text
        for (value in listOf("Ana", "40123456", "987654321", "Rosa", "Ccori")) assertFalse(value in text, "$value salió hacia el modelo")
        assertTrue("[PERSONA_1] (DNI [DOCUMENTO_1], celular [TELEFONO_1])" in text, text)
        assertTrue("[PERSONA] agradeció" in text, text)
        assertTrue("- $topic: Agua y riego" in text, text)
    }

    @Test
    fun `cada hallazgo valido es una EXTRACCION con la forma de su fila y marcadores estables`() {
        val result = run()
        val byValue = result.suggestions.associateBy { it.suggestedValue }
        assertEquals(5, result.suggestions.size, byValue.keys.toString())
        assertEquals(5, result.discarded)
        val ana =
            byValue.getValue(
                """{"item":"actor","kind":"person","name":"[PERSONA:${RunFixtures.ANA}]","stakeholder":{"stakeholder_type":"LIDER LOCAL"}}"""
            )
        assertEquals("person" to RunFixtures.ANA, ana.targetObject to ana.targetRecordId)
        assertEquals("[PERSONA:${RunFixtures.ANA}] (DNI [DOCUMENTO], celular [TELEFONO]) pidió reparar el canal", ana.evidenceQuote)
        val unregistered = byValue.getValue("""{"item":"actor","kind":"person","name":"[PERSONA]"}""")
        assertEquals("person" to null, unregistered.targetObject to unregistered.targetRecordId)
        val group = byValue.getValue("""{"item":"actor","kind":"organization","name":"Comunidad Ejemplo","organization_type":"COMUNIDAD CAMPESINA"}""")
        assertEquals("organization" to organization, group.targetObject to group.targetRecordId)
        val theme = byValue.getValue("""{"item":"topic","topic":"$topic"}""")
        assertEquals("engagement" to RunFixtures.ENGAGEMENT, theme.targetObject to theme.targetRecordId)
        val place = byValue.getValue("""{"item":"place","field":"community","value":"$community"}""")
        assertEquals("engagement" to RunFixtures.ENGAGEMENT, place.targetObject to place.targetRecordId)
        for (suggestion in result.suggestions) {
            assertEquals("EXTRACCION", suggestion.kind)
            assertEquals(BigDecimal("0.80"), suggestion.confidence)
            assertEquals(RunFixtures.PROJECT, suggestion.project)
            assertEquals("modelo-de-prueba", suggestion.model)
            assertEquals(result.interactions.single(), suggestion.interaction)
        }
    }

    @Test
    fun `con un acuerdo vinculado, las fechas y las clausulas apuntan a el`() {
        val output =
            ExtractionOutput(
                listOf(
                    item("date", "antes del 30 de noviembre de 2026", field = "expiry_date", value = "2026-11-30"),
                    item("date", "antes del 30 de noviembre de 2026", field = "held_at", value = "2026-11-30"),
                    item("date", "antes del 30 de noviembre de 2026", field = "signed_date", value = "30 de noviembre"),
                    item(
                        "clause",
                        "La empresa se compromete a reparar el canal de riego",
                        clauseNumber = "5",
                        clauseText = "La empresa reparará el canal de riego de [PERSONA_1]"
                    )
                )
            )
        val result = run(RunFixtures.minutes(agreement = RunFixtures.AGREEMENT), output)
        assertEquals(
            listOf(
                """{"item":"date","field":"expiry_date","value":"2026-11-30"}""",
                """{"item":"clause","clause_number":"5","clause_text":"La empresa reparará el canal de riego de [PERSONA:${RunFixtures.ANA}]","creates_commitment":false}"""
            ),
            result.suggestions.map { it.suggestedValue }
        )
        assertTrue(result.suggestions.all { it.targetObject == "agreement" && it.targetRecordId == RunFixtures.AGREEMENT })
    }

    @Test
    fun `sin margen no llama al modelo ni deja interaccion`() {
        val mock = MockModel(FakeModel { ExtractionOutput(found) })
        val refused = assertThrows<WasichaiException> { runBlocking { agent(mock).run(RunFixtures.minutes(), RunFixtures.NAMES, RunBudget(0)) } }
        assertEquals("AI_RUN_TRUNCATED", refused.violations.single().message)
        assertTrue(mock.sent.isEmpty())
        assertTrue(log.entries.isEmpty())
    }

    @Test
    fun `la llamada queda en su ai_interaction con el resumen sin datos personales`() {
        run()
        val entry = log.entries.single()
        assertEquals("AI-01", entry.useCase)
        assertEquals("ai-01@1", entry.promptVersion)
        assertEquals("engagement:${RunFixtures.ENGAGEMENT} updated_at:2026-10-07T12:00:00Z", entry.inputReference)
        assertEquals("10 hallazgos propuestos", entry.outputSummary)
        assertEquals(RunFixtures.PROJECT, entry.project)
    }

    @Test
    fun `un valor fuera del catalogo o un destino que no se lee descarta la propuesta`() {
        val output =
            ExtractionOutput(
                listOf(
                    item("actor", "pidió reparar el canal de riego", kind = "person", name = "[PERSONA]", stakeholderType = "REY DEL BARRIO"),
                    item("actor", "Acta de la asamblea de la Comunidad Ejemplo", kind = "organization", name = "Club", organizationType = "CLUB DE FUTBOL"),
                    item("place", "Acta de la asamblea de la Comunidad Ejemplo", field = "community", name = "Comunidad Inexistente"),
                    item("place", "Acta de la asamblea de la Comunidad Ejemplo", field = "district", name = "Comunidad Ejemplo")
                )
            )
        val result = run(output = output)
        assertTrue(result.suggestions.isEmpty(), result.suggestions.toString())
        assertEquals(4, result.discarded)
    }

    // el prompt nombra los catálogos de §10 tal como están en el modelo: un valor nuevo pide una versión nueva del prompt
    @Test
    fun `el prompt de AI-01 nombra los tipos de organizacion y de grupo de interes del catalogo`() {
        val prompt = File("src/main/resources/sgspe/ai/prompts/ai-01/1.md").readText().replace(Regex("""\s+"""), " ")
        for (value in RunFixtures.CATALOG.enumOf("organization", "organization_type") + RunFixtures.CATALOG.enumOf("stakeholder", "stakeholder_type")) {
            assertTrue(value in prompt, value)
        }
    }
}
