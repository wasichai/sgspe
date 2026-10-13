package sgspe.ai

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.test.context.TestPropertySource
import sgspe.F7ApiTest
import java.util.UUID

// AI-06 de punta a punta contra wasichai de verdad (ai-architecture.md §13.3, especificación de F7 §3.5 y §5), con la clave falsa
// y RecordingLlm delante del proveedor (el mismo contexto que AssistantRunApiTest): la persona pregunta por HTTP, el modelo recibe
// los fragmentos seudonimizados, la respuesta vuelve con citas y marcadores estables y la ai_interaction de AI-06 queda con sus
// tokens; sin fragmento que pase los filtros, la respuesta dice que no hay evidencia y el modelo no se llama
@TestPropertySource(
    properties = [
        "wasichai.agent.api-key=sk-ant-clave-de-prueba",
        "embabel.agent.platform.models.anthropic.api-key=sk-ant-clave-de-prueba",
        "spring.ai.anthropic.api-key=sk-ant-clave-de-prueba",
        "sgspe.ai.enabled-organizations=demo"
    ]
)
@Import(RecordingLlm.Config::class)
class KnowledgeRunApiTest : F7ApiTest() {
    @Autowired
    private lateinit var llm: RecordingLlm

    @Autowired
    private lateinit var chunks: KnowledgeChunks

    private val path = "/api/sgspe/ai/knowledge/questions"
    private val word =
        "reservorio" +
            UUID
                .randomUUID()
                .toString()
                .filter { it.isLetter() }
                .take(8)

    // un apellido ficticio que solo esta prueba escribe: el diccionario de nombres no tiene a otra «Ana» con él
    private val surname =
        UUID
            .randomUUID()
            .toString()
            .filter { it.isLetter() }
            .take(8)
            .replaceFirstChar { it.uppercase() }

    @Test
    fun `la persona pregunta, el modelo solo ve marcadores y la respuesta vuelve con citas`() {
        val person = createRecord("person", mapOf("given_names" to "Ana", "surnames" to surname, "sex" to "FEMENINO"))
        val evidence = evidence(testProject(), type = "ACTA")
        runBlocking {
            chunks.replace(
                organizationId(),
                UUID.fromString(evidence),
                UUID.fromString(testProject()),
                "INTERNA",
                listOf("En la asamblea, Ana $surname pidió un $word para el anexo norte.")
            )
        }
        val cro = member("COMMUNITY_RELATIONS_OFFICER")
        val script = llm.fresh()
        script.returnObject(KnowledgeAnswer("[PERSONA_1] pidió un $word.", listOf(KnowledgeQuote(1, "[PERSONA_1] pidió un $word para el anexo norte"))))
        val question = "¿Qué pidió la asamblea sobre el $word?"
        val response = exchange("POST", path, mapOf("question" to question, "project" to testProject()), cro.token)
        assertEquals(HttpStatus.OK, response.status, response.body)
        val body = tree(response.body)
        assertEquals("[PERSONA:$person] pidió un $word.", body["answer"].asString())
        val citation = body["citations"].values().single()
        assertEquals(evidence, citation["evidenceId"].asString())
        assertEquals(0, citation["chunkIndex"].asInt())
        assertEquals("[PERSONA:$person] pidió un $word para el anexo norte", citation["quote"].asString())
        for (sent in script.promptsReceived) assertFalse("Ana" in sent || surname in sent, "un nombre salió hacia el modelo: $sent")
        assertTrue(script.promptsReceived.single().contains("<fragmento n=\"1\">"))
        val interaction = tree(send("GET", "/api/objects/ai_interaction/records/${body["interaction"].asString()}", null, HttpStatus.OK))["attributes"]
        assertEquals("AI-06", interaction["use_case"].asString())
        assertEquals("ai-06@1", interaction["prompt_version"].asString())
        assertEquals("sha256:" + Hashes.sha256(question), interaction["input_reference"].asString())
        assertEquals(testProject(), interaction["project"].asString())
        assertEquals(cro.email, interaction["requested_by_user_email"].asString())
        assertEquals(RecordingLlm.INPUT, interaction["input_tokens"].asInt())
        runBlocking { chunks.delete(organizationId(), listOf(UUID.fromString(evidence))) }
    }

    @Test
    fun `sin fragmento que pase los filtros no hay evidencia y el modelo no se llama`() {
        llm.fresh()
        val cro = member("COMMUNITY_RELATIONS_OFFICER")
        val response = exchange("POST", path, mapOf("question" to "¿Qué se acordó sobre el $word?", "project" to testProject()), cro.token)
        assertEquals(HttpStatus.OK, response.status, response.body)
        val body = tree(response.body)
        assertEquals(KnowledgeAnswers.NO_EVIDENCE, body["answer"].asString())
        assertEquals(0, body["citations"].size())
        assertTrue(body["interaction"].isNull)
        assertEquals(0, llm.calls.get())
    }

    @Test
    fun `un proyecto que la persona no lee es 404, sin llamar al modelo`() {
        llm.fresh()
        val response =
            exchange("POST", path, mapOf("question" to "¿Qué pidió la asamblea?", "project" to project()), member("COMMUNITY_RELATIONS_OFFICER").token)
        assertEquals(HttpStatus.NOT_FOUND, response.status, response.body)
        assertEquals(0, llm.calls.get())
    }
}
