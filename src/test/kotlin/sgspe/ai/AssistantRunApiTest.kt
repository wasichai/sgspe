package sgspe.ai

import com.embabel.agent.spi.loop.MaxIterationsExceededException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.test.context.TestPropertySource
import sgspe.F7ApiTest
import wasichai.agent.AgentFilterException
import wasichai.agent.AgentService
import java.net.URLEncoder

// AI-05 contra wasichai de verdad (especificación de F7 §5, security.md §14 «Asistente AI-05»): con una clave falsa que nunca se usa
// (RecordingLlm, el modelo guionado, está delante del proveedor) y la IA activada para la organización sembrada (slug demo). una
// persona pregunta por HTTP, las herramientas leen como ella, el modelo solo ve marcadores, la respuesta vuelve restaurada y la
// ai_interaction queda con tokens, también truncada o fallida; sin pregunta abierta el filtro falla cerrado; /api/agent/status
// sigue a AiAgentAccess y POST /api/agent/ask sigue cerrado
@TestPropertySource(
    properties = [
        "wasichai.agent.api-key=sk-ant-clave-de-prueba",
        "embabel.agent.platform.models.anthropic.api-key=sk-ant-clave-de-prueba",
        "spring.ai.anthropic.api-key=sk-ant-clave-de-prueba",
        "sgspe.ai.enabled-organizations=demo"
    ]
)
@Import(RecordingLlm.Config::class)
class AssistantRunApiTest : F7ApiTest() {
    @Autowired
    private lateinit var llm: RecordingLlm

    @Autowired
    private lateinit var agent: AgentService

    private val path = "/api/sgspe/ai/assistant/questions"

    // una persona registrada y una interacción del proyecto de la prueba que la nombra, con su DNI y su celular
    private fun scene(): Pair<String, String> {
        val person = createRecord("person", mapOf("given_names" to "Ana", "surnames" to "Ejemplo", "sex" to "FEMENINO", "id_document_number" to "40123456"))
        val engagement =
            engagement(
                testProject(),
                attributes =
                    mapOf("description" to "Ana Ejemplo (DNI 40123456) pidió reparar el canal; su celular es 987654321.")
            )
        return person to engagement
    }

    private fun ask(
        account: Account,
        question: String
    ): Response = exchange("POST", path, mapOf("question" to question), account.token)

    @Test
    fun `la persona pregunta, el modelo solo ve marcadores y la respuesta vuelve restaurada`() {
        val (person, engagement) = scene()
        val cro = member("COMMUNITY_RELATIONS_OFFICER")
        val script = llm.fresh()
        script
            .callTool("get_record", """{"object": "person", "id": "$person"}""")
            .callTool("get_record", """{"object": "engagement", "id": "$engagement"}""")
            .returnObject("[PERSONA_1] pidió reparar el canal; su celular es [TELEFONO_1] y su documento [DOCUMENTO].")
        val question = "¿Qué pidió Ana Ejemplo en la reunión?"
        val response = ask(cro, question)
        assertEquals(HttpStatus.OK, response.status, response.body)
        val body = tree(response.body)
        assertEquals("Ana Ejemplo pidió reparar el canal; su celular es 987654321 y su documento [DOCUMENTO].", body["answer"].asString())
        assertEquals(listOf("get_record", "get_record"), body["steps"].values().map { it["tool"].asString() })
        assertFalse(body["truncated"].asBoolean())
        for (sent in script.promptsReceived + script.toolCallsMade.map { it.result }) {
            for (value in listOf("Ana", "40123456", "987654321")) assertFalse(value in sent, "$value salió hacia el modelo: $sent")
        }
        assertTrue("\"id_document_number\":\"[DOCUMENTO]\"" in script.toolCallsMade[0].result, script.toolCallsMade[0].result)
        assertTrue("[TELEFONO_1]" in script.toolCallsMade[1].result, script.toolCallsMade[1].result)
        val interaction = tree(send("GET", "/api/objects/ai_interaction/records/${body["interaction"].asString()}", null, HttpStatus.OK))["attributes"]
        assertEquals("AI-05", interaction["use_case"].asString())
        assertEquals("ai-05@1", interaction["prompt_version"].asString())
        assertEquals(RecordingLlm.MODEL, interaction["model"].asString())
        assertEquals(RecordingLlm.INPUT * llm.calls.get(), interaction["input_tokens"].asInt())
        assertEquals(RecordingLlm.OUTPUT * llm.calls.get(), interaction["output_tokens"].asInt())
        assertEquals("sha256:" + Hashes.sha256(question), interaction["input_reference"].asString())
        assertEquals(cro.email, interaction["requested_by_user_email"].asString())
        assertFalse(interaction["run_by_platform"].asBoolean())
    }

    @Test
    fun `quien no lee nombres pregunta igual y los formatos no salen`() {
        val (_, engagement) = scene()
        val analyst = member("ANALYST")
        val script = llm.fresh()
        script.callTool("get_record", """{"object": "engagement", "id": "$engagement"}""").returnObject("Se pidió reparar el canal.")
        val response = ask(analyst, "¿Qué se pidió en la reunión?")
        assertEquals(HttpStatus.OK, response.status, response.body)
        val result = script.toolCallsMade.single().result
        for (value in listOf("40123456", "987654321")) assertFalse(value in result, "$value salió hacia el modelo: $result")
    }

    @Test
    fun `el estado del asistente de wasichai sigue a AiAgentAccess`() {
        val cro = member("COMMUNITY_RELATIONS_OFFICER")
        val reader = member("READ_ONLY")
        val other = provisionTenant()

        fun enabled(token: String) = tree(send("GET", "/api/agent/status", null, HttpStatus.OK, token))["enabled"].asBoolean()
        assertTrue(enabled(cro.token))
        assertFalse(enabled(reader.token))
        assertFalse(enabled(other.token))
        assertTrue(tree(send("GET", "/api/sgspe/ai/status", null, HttpStatus.OK, cro.token))["providerAvailable"].asBoolean())
    }

    @Test
    fun `con proveedor, POST a api agent ask sigue cerrado y el modelo no oye nada`() {
        val cro = member("COMMUNITY_RELATIONS_OFFICER")
        val script = llm.fresh()
        script.returnObject("no debería decirse")
        assertEquals(HttpStatus.FORBIDDEN, exchange("POST", "/api/agent/ask", mapOf("question" to "¿Hola?"), cro.token).status)
        assertTrue(script.promptsReceived.isEmpty())
        assertEquals(0, llm.calls.get())
    }

    @Test
    fun `un proveedor que falla responde 503 y deja su ai_interaction`() {
        val cro = member("COMMUNITY_RELATIONS_OFFICER")
        llm.fresh().returnObject("no llega")
        llm.failure = IllegalStateException("proveedor caído")
        val response = ask(cro, "¿Cuántas reuniones hubo?")
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.status, response.body)
        assertEquals("AI_UNAVAILABLE", code(response))
        assertEquals(listOf("provider"), problemFields(response.body))
        val email = URLEncoder.encode(cro.email, Charsets.UTF_8)
        val logged = tree(send("GET", "/api/objects/ai_interaction/records?requested_by_user_email=$email", null, HttpStatus.OK))["content"].values()
        assertEquals(1, logged.size)
        assertTrue(logged.single()["attributes"]["output_summary"].asString().startsWith("Corrida fallida"))
    }

    // Embabel reintenta una acción fallida hasta 5 veces con esperas de 10 s a 60 s; con
    // embabel.agent.platform.action-qos.default.max-attempts=1 (application.yml, decisión 18) responde en el acto
    @Test
    @Timeout(60)
    fun `una corrida sin margen responde enseguida, truncada y en espanol, y queda registrada`() {
        val cro = member("COMMUNITY_RELATIONS_OFFICER")
        llm.fresh()
        llm.failure = MaxIterationsExceededException(20)
        val response = ask(cro, "¿Cuántas reuniones hubo este año?")
        assertEquals(HttpStatus.OK, response.status, response.body)
        val body = tree(response.body)
        assertTrue(body["truncated"].asBoolean())
        assertEquals(AssistantQuestions.TRUNCATED, body["answer"].asString())
        val interaction = tree(send("GET", "/api/objects/ai_interaction/records/${body["interaction"].asString()}", null, HttpStatus.OK))["attributes"]
        assertTrue(interaction["output_summary"].asString().startsWith("Corrida truncada"), interaction["output_summary"].asString())
        assertEquals(RecordingLlm.INPUT * llm.calls.get(), interaction["input_tokens"].asInt())
    }

    // sin pregunta abierta (AgentService.ask llamado en proceso, sin AssistantQuestions), el filtro falla cerrado: wasichai cierra
    // la corrida y ningún resultado de herramienta sale hacia el modelo
    @Test
    @Timeout(60)
    fun `sin pregunta abierta el filtro falla cerrado y nada sale hacia el modelo`() {
        val (person, _) = scene()
        val cro = member("COMMUNITY_RELATIONS_OFFICER")
        val script = llm.fresh()
        script.callTool("get_record", """{"object": "person", "id": "$person"}""").returnObject("no debería decirse")
        assertThrows<AgentFilterException> { asUser(cro) { agent.ask("¿Quién es [PERSONA_1]?") } }
        for (call in script.toolCallsMade) {
            for (value in listOf("Ana", "40123456")) assertFalse(value in call.result, "$value salió hacia el modelo: ${call.result}")
        }
        assertEquals(1, llm.calls.get())
    }
}
