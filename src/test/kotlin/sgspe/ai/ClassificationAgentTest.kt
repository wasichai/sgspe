package sgspe.ai

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

// AI-08 (ai-architecture.md §4.2): un valor de catálogo, o un tema de la lista, solo para los campos permitidos del objeto; nunca la
// postura, un derivado ni workflow_state; nada que el registro ya tenga
class ClassificationAgentTest {
    private val topic = UUID.fromString("00000000-0000-0000-0000-00000000f001")
    private val grievance = UUID.fromString("00000000-0000-0000-0000-00000000a501")
    private val log = RecordedInteractions()

    private fun agent(model: StructuredModel) =
        ClassificationAgent(
            RunFixtures.calls(model, log),
            FakeLookups(topics = listOf(TopicOption(topic, "Agua y riego"))),
            RunFixtures.CATALOG,
            RunFixtures.properties()
        )

    private val source =
        RunSource(
            objectName = "grievance",
            id = grievance,
            project = RunFixtures.PROJECT,
            text = "La junta de usuarios denuncia que el agua del canal llega turbia desde que empezó la perforación.",
            inputReference = "grievance:$grievance updated_at:2026-10-07T12:00:00Z",
            current = mapOf("priority" to "ALTA", "category" to null)
        )

    private val proposals =
        ClassificationOutput(
            listOf(
                ProposedClassification("category", "AGUA Y MEDIO AMBIENTE", "el agua del canal llega turbia", 0.9),
                ProposedClassification("priority", "ALTA", "el agua del canal llega turbia", 0.6),
                ProposedClassification("category", "EMPLEO", "desde que empezó la perforación", 0.4),
                ProposedClassification("stance", "EN CONTRA", "La junta de usuarios denuncia", 0.8),
                ProposedClassification("workflow_state", "closed", "La junta de usuarios denuncia", 0.8),
                ProposedClassification("tone", "NEGATIVO", "La junta de usuarios denuncia", 0.8),
                ProposedClassification("topic", topic.toString(), "el agua del canal llega turbia", 0.75)
            )
        )

    @Test
    fun `solo los campos permitidos, con un valor del catalogo y distinto del vigente`() {
        val result = runBlocking { agent(FakeModel { proposals }).run(source, RunFixtures.NAMES, RunBudget(8)) }
        assertEquals(
            listOf("""{"field":"category","value":"AGUA Y MEDIO AMBIENTE"}""", """{"field":"topic","value":"$topic"}"""),
            result.suggestions.map { it.suggestedValue }
        )
        assertEquals(5, result.discarded)
        assertTrue(result.suggestions.all { it.kind == "CLASIFICACION" && it.targetObject == "grievance" && it.targetRecordId == grievance })
        assertEquals("el agua del canal llega turbia", result.suggestions.first().evidenceQuote)
    }

    @Test
    fun `el modelo recibe los campos del objeto con su catalogo`() {
        val mock = MockModel(FakeModel { ClassificationOutput() })
        runBlocking { agent(mock).run(source, RunFixtures.NAMES, RunBudget(8)) }
        val text =
            mock.sent
                .single()
                .prompt.text
        assertTrue("- category: AGUA Y MEDIO AMBIENTE | EMPLEO | TIERRAS" in text, text)
        assertTrue("- priority: BAJA | MEDIA | ALTA | MUY ALTA" in text, text)
        assertTrue("- $topic: Agua y riego" in text, text)
        assertTrue("tone" !in text.substringAfter("Campos:").substringBefore("Temas:"), text)
    }

    @Test
    fun `una mencion solo recibe tono y tema`() {
        val mention = source.copy(objectName = "media_mention", text = "Radio Ejemplo: la junta de usuarios pide diálogo con la empresa.", current = emptyMap())
        val output =
            ClassificationOutput(
                listOf(
                    ProposedClassification("tone", "NEGATIVO", "la junta de usuarios pide diálogo", 0.7),
                    ProposedClassification("category", "OTRO", "pide diálogo con la empresa", 0.7)
                )
            )
        val result = runBlocking { agent(FakeModel { output }).run(mention, RunFixtures.NAMES, RunBudget(8)) }
        assertEquals(listOf("""{"field":"tone","value":"NEGATIVO"}"""), result.suggestions.map { it.suggestedValue })
    }

    @Test
    fun `un valor fuera del catalogo y un tema que no se lee se descartan`() {
        val output =
            ClassificationOutput(
                listOf(
                    ProposedClassification("category", "NO EXISTE", "el agua del canal llega turbia", 0.9),
                    ProposedClassification("priority", "MAXIMA", "el agua del canal llega turbia", 0.9),
                    ProposedClassification("topic", "00000000-0000-0000-0000-00000000f999", "el agua del canal llega turbia", 0.9)
                )
            )
        val result = runBlocking { agent(FakeModel { output }).run(source, RunFixtures.NAMES, RunBudget(8)) }
        assertTrue(result.suggestions.isEmpty())
        assertEquals(3, result.discarded)
    }
}
