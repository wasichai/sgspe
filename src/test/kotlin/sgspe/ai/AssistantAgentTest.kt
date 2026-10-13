package sgspe.ai

import com.embabel.agent.core.AgentPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.support.StaticListableBeanFactory
import wasichai.agent.AgentAccessPolicy
import wasichai.agent.AgentAnswer
import wasichai.agent.AgentProperties
import wasichai.agent.AgentService
import wasichai.agent.AgentToolCatalog
import wasichai.agent.AgentToolResult
import wasichai.agent.AgentTools
import wasichai.agent.AgentUnavailableException
import wasichai.agent.WasichaiAgent
import wasichai.core.identity.CurrentUser
import wasichai.core.identity.RoleQueries
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

// AI-05 de punta a punta con el modelo guionado de Embabel (especificación de F7 §5, security.md §14 «Asistente AI-05»): el
// AgentService real de wasichai 1.2.0 sobre la plataforma de Embabel en memoria, con los filtros y el listener de SGSPE. el modelo
// recibe la pregunta y los resultados seudonimizados y sin campos restringidos, la respuesta vuelve restaurada y la ai_interaction
// queda con tokens, también si el proveedor falla. la corrida truncada y el fallo cerrado de un filtro van en AssistantRunApiTest:
// aquí Embabel reintentaría la acción durante minutos (decisión 18). sin base: las herramientas son un doble que devuelve una
// persona y una interacción con las formas de AgentTools
class AssistantAgentTest {
    private val ana = UUID.fromString("00000000-0000-0000-0000-0000000000a1")
    private val bruno = UUID.fromString("00000000-0000-0000-0000-0000000000b2")
    private val person = ana.toString()
    private val engagement = "00000000-0000-0000-0000-0000000000e1"
    private val personJson = """{"id":"$person","attributes":{"given_names":"Ana","surnames":"Ejemplo","id_document_number":"40123456"}}"""
    private val engagementJson =
        """{"id":"$engagement","attributes":{"title":"Reunión","description":"Ana Ejemplo pidió reparar el canal; su celular es 987654321."}}"""
    private val raw = listOf("Ana", "40123456", "987654321")

    private val tools: AgentTools =
        mock(AgentTools::class.java) { invocation ->
            if (invocation.method.name == "invoke") {
                val input = invocation.arguments[1] as Map<*, *>
                AgentToolResult(if (input["object"] == "person") personJson else engagementJson, "${input["object"]}: Ana Ejemplo", false)
            } else {
                null
            }
        }
    private val llm = RecordingLlm()
    private val log = RecordedInteractions()
    private val properties = AgentProperties(apiKey = "clave-de-prueba", model = RecordingLlm.MODEL)
    private val clock = Clock.fixed(Instant.parse("2026-10-13T15:00:02Z"), ZoneOffset.UTC)

    private fun <T : Any> providerOf(
        type: Class<T>,
        bean: T
    ): ObjectProvider<T> = StaticListableBeanFactory().apply { addBean("bean", bean) }.getBeanProvider(type)

    private fun service(model: RecordingLlm = llm): AgentService =
        AgentService(
            properties,
            CurrentUser(mock(RoleQueries::class.java)),
            providerOf(AgentPlatform::class.java, RecordingLlm.platform(model, WasichaiAgent(properties, tools))),
            AgentAccessPolicy.ALLOW_ALL,
            listOf(PseudonymizingResultFilter()),
            listOf(RestoringAnswerFilter()),
            listOf(InteractionRunListener(log, providerOf(AgentProperties::class.java, properties), clock))
        )

    private fun runOf(
        question: String,
        names: NameDictionary = NameDictionary(listOf(KnownPerson(ana, "Ana", "Ejemplo")))
    ) = AssistantRun(question, names, "ai-05@1", Instant.parse("2026-10-13T15:00:00Z"))

    // la pregunta como la manda AssistantQuestions: seudonimizada con la tabla de su corrida (sin el prompt de AI-05, que prueba
    // PromptsTest)
    private fun ask(
        run: AssistantRun,
        agent: AgentService = service()
    ): AgentAnswer = runBlocking(TestCallers.context()) { AssistantRun.bind(run) { agent.ask(Pseudonymizer.pseudonymize(run.question, run.names, run.table)) } }

    private fun scriptPersonAndEngagement(answer: String) {
        llm.script
            .callTool(AgentToolCatalog.GET_RECORD, """{"object": "person", "id": "$person"}""")
            .callTool(AgentToolCatalog.GET_RECORD, """{"object": "engagement", "id": "$engagement"}""")
            .returnObject(answer)
    }

    @Test
    fun `el modelo recibe la pregunta y los resultados seudonimizados, sin campos restringidos`() {
        scriptPersonAndEngagement("[PERSONA_1] pidió reparar el canal; su celular es [TELEFONO_1].")
        ask(runOf("¿Qué pidió Ana Ejemplo?"))
        val prompt = llm.script.promptsReceived.first()
        assertTrue("[PERSONA_1]" in prompt, prompt)
        val (personResult, engagementResult) = llm.script.toolCallsMade.map { it.result }
        assertTrue("\"given_names\":\"[PERSONA]\"" in personResult && "\"id_document_number\":\"[DOCUMENTO]\"" in personResult, personResult)
        assertTrue("[PERSONA_1] pidió reparar el canal; su celular es [TELEFONO_1]." in engagementResult, engagementResult)
        for (sent in llm.script.promptsReceived + llm.script.toolCallsMade.map { it.result }) {
            for (value in raw) assertFalse(value in sent, "$value salió hacia el modelo: $sent")
        }
    }

    @Test
    fun `la respuesta vuelve restaurada, salvo los campos restringidos, y los pasos seudonimizados`() {
        scriptPersonAndEngagement("[PERSONA_1] pidió el canal; documento [DOCUMENTO]; celular [TELEFONO_1].")
        val answer = ask(runOf("¿Qué pidió Ana Ejemplo?"))
        assertEquals("Ana Ejemplo pidió el canal; documento [DOCUMENTO]; celular 987654321.", answer.answer)
        assertEquals(listOf("person: [PERSONA_1]", "engagement: [PERSONA_1]"), answer.steps.map { it.summary })
    }

    @Test
    fun `la interaccion queda con modelo, tokens, latencia y la huella de la pregunta`() {
        scriptPersonAndEngagement("listo")
        val run = runOf("¿Qué pidió Ana Ejemplo?")
        ask(run)
        val entry = log.entries.single()
        assertEquals("AI-05", entry.useCase)
        assertEquals(RecordingLlm.MODEL, entry.model)
        assertEquals("ai-05@1", entry.promptVersion)
        assertEquals(RecordingLlm.INPUT * llm.calls.get(), entry.inputTokens)
        assertEquals(RecordingLlm.OUTPUT * llm.calls.get(), entry.outputTokens)
        assertEquals(2_000L, entry.latencyMs)
        assertEquals("sha256:" + Hashes.sha256("¿Qué pidió Ana Ejemplo?"), entry.inputReference)
        assertEquals("Respuesta con 2 pasos; herramientas: get_record", entry.outputSummary)
        assertNotNull(run.interaction)
    }

    @Test
    fun `un proveedor que falla queda registrado como fallido`() {
        llm.failure = IllegalStateException("proveedor caído")
        assertThrows<AgentUnavailableException> { ask(runOf("¿Cuántas reuniones hubo?")) }
        assertTrue(
            log.entries
                .single()
                .outputSummary
                .startsWith("Corrida fallida"),
            log.entries.single().outputSummary
        )
    }

    @Test
    fun `dos preguntas a la vez no comparten tabla`() {
        val other = RecordingLlm()
        for (model in listOf(llm, other)) {
            model.script.callTool(AgentToolCatalog.GET_RECORD, """{"object": "engagement", "id": "$engagement"}""").returnObject("[PERSONA_1]")
        }
        val first = runOf("¿Y Ana Ejemplo?")
        val second = runOf("¿Y Bruno Ejemplo?", NameDictionary(listOf(KnownPerson(bruno, "Bruno", "Ejemplo"))))
        val answers =
            runBlocking(TestCallers.context()) {
                listOf(
                    async(
                        Dispatchers.Default
                    ) { AssistantRun.bind(first) { service(llm).ask(Pseudonymizer.pseudonymize(first.question, first.names, first.table)) } },
                    async(
                        Dispatchers.Default
                    ) { AssistantRun.bind(second) { service(other).ask(Pseudonymizer.pseudonymize(second.question, second.names, second.table)) } }
                ).awaitAll()
            }
        assertEquals(listOf("Ana Ejemplo", "Bruno Ejemplo"), answers.map { it.answer })
    }

    private val prompts = Prompts(AiProperties(prompts = mapOf("ai-05" to "1")))

    // el camino de producción: AssistantQuestions seudonimiza la pregunta antes de AgentService.ask, que nunca la recibe en claro
    // (revisión de la Tarea 11). una persona registrada y un DNI escritos en la pregunta no llegan al modelo
    @Test
    fun `AgentService nunca recibe la pregunta en claro`() {
        llm.script.returnObject("[PERSONA_1] tiene el documento [DOCUMENTO_1].")
        val answer =
            runBlocking(TestCallers.context()) {
                AssistantQuestions.answer(service(), prompts, "¿Qué pidió Ana Ejemplo, DNI 40123456?", runOf("").names, clock.instant())
            }
        val sent = llm.script.promptsReceived
        assertTrue(sent.any { "<pregunta>\n¿Qué pidió [PERSONA_1], DNI [DOCUMENTO_1]?\n</pregunta>" in it }, "$sent")
        for (prompt in sent) {
            for (value in listOf("Ana", "Ejemplo", "40123456")) assertFalse(value in prompt, "$value salió hacia el modelo: $prompt")
        }
        assertEquals("Ana Ejemplo tiene el documento 40123456.", answer.answer)
        assertEquals("sha256:" + Hashes.sha256("¿Qué pidió Ana Ejemplo, DNI 40123456?"), log.entries.single().inputReference)
        assertEquals("ai-05@1", log.entries.single().promptVersion)
        assertNotNull(answer.interaction)
    }
}
