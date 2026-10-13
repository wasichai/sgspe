package sgspe.ai

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

// AI-07 por POST /api/sgspe/ai/runs contra wasichai de verdad (ai-architecture.md §4.1 y §4.2, especificación de F7 §3.5), con la
// clave falsa y RecordingLlm delante del proveedor (el contexto de AiRunsApiTest): las reglas arman el par, el modelo lo compara
// sin ver nombres y queda una ai_suggestion DUPLICADO en proposed con {other_record_id, reasons[]}; una segunda corrida no vuelve a
// proponer el par ni llama al modelo; quien no lee los nombres de person recibe 403
@TestPropertySource(
    properties = [
        "wasichai.agent.api-key=sk-ant-clave-de-prueba",
        "embabel.agent.platform.models.anthropic.api-key=sk-ant-clave-de-prueba",
        "spring.ai.anthropic.api-key=sk-ant-clave-de-prueba",
        "sgspe.ai.enabled-organizations=demo",
        // la misma lista que AiRunsApiTest, para compartir un solo contexto de Spring (AI-07 no lee archivos)
        "sgspe.ai.limits.file-bytes=65536"
    ]
)
@Import(RecordingLlm.Config::class)
class DuplicateRunApiTest : F7ApiTest() {
    @Autowired
    private lateinit var llm: RecordingLlm

    private fun run(
        account: Account,
        project: String
    ) = aiRun(account, DuplicateDetection.USE_CASE, "project", project, UUID.randomUUID().toString())

    // dos quejas del mismo pedido en un proyecto propio, recibidas con dos días de diferencia; la primera es la más antigua
    private fun grievances(project: String): Pair<String, String> {
        val first =
            grievance(
                project,
                attributes =
                    mapOf(
                        "description" to "El agua del canal de riego del anexo norte llega turbia desde la perforación del sondaje.",
                        "received_at" to "2026-10-01T15:00:00Z"
                    )
            )
        val second =
            grievance(
                project,
                attributes =
                    mapOf(
                        "description" to "Desde la perforación del sondaje, el agua del canal de riego del anexo norte llega turbia.",
                        "received_at" to "2026-10-03T15:00:00Z"
                    )
            )
        return first to second
    }

    @Test
    fun `una corrida sobre el proyecto deja una sugerencia DUPLICADO por par y la segunda no repite`() {
        val project = project()
        val (first, second) = grievances(project)
        val social = member("SOCIAL_MANAGER", project)
        val script = llm.fresh()
        script.returnObject(DuplicateVerdicts(listOf(DuplicateVerdict(1, true, 0.8, listOf("Mismo pedido sobre el canal")))))
        val response = run(social, project)
        assertEquals(HttpStatus.CREATED, response.status, response.body)
        val body = tree(response.body)
        val suggestion = body["suggestions"].values().single()["attributes"]
        assertEquals("DUPLICADO", suggestion["suggestion_kind"].asString())
        assertEquals("grievance", suggestion["target_object"].asString())
        assertEquals(second, suggestion["target_record_id"].asString())
        assertEquals(project, suggestion["project"].asString())
        val value = tree(suggestion["suggested_value"].asString())
        assertEquals(first, value["other_record_id"].asString())
        assertEquals(
            listOf("Textos parecidos", "Mismo proyecto", "Fechas cercanas", "Mismo pedido sobre el canal"),
            value["reasons"].values().map { it.asString() }
        )
        assertEquals(body["interaction"].asString(), suggestion["ai_interaction"].asString())
        val interaction = tree(send("GET", "/api/objects/ai_interaction/records/${body["interaction"].asString()}", null, HttpStatus.OK))["attributes"]
        assertEquals("AI-07" to "ai-07@1", interaction["use_case"].asString() to interaction["prompt_version"].asString())
        assertTrue(script.promptsReceived.single().contains("<par n=\"1\" objeto=\"grievance\">"))
        llm.fresh()
        val again = run(social, project)
        assertEquals(HttpStatus.CREATED, again.status, again.body)
        assertEquals(0, tree(again.body)["suggestions"].size())
        assertTrue(tree(again.body)["interaction"].isNull)
        assertEquals(0, llm.calls.get())
    }

    @Test
    fun `los nombres de dos personas parecidas no salen hacia el modelo`() {
        val project = project()
        val surname =
            UUID
                .randomUUID()
                .toString()
                .filter { it.isLetter() }
                .take(8)
                .replaceFirstChar { it.uppercase() }
        for (written in listOf("Ana $surname", "ANA ${surname.uppercase()}")) {
            val person =
                createRecord(
                    "person",
                    mapOf(
                        "given_names" to written.substringBefore(' '),
                        "surnames" to written.substringAfter(' '),
                        "sex" to "FEMENINO"
                    )
                )
            stakeholder(project, mapOf("person" to person))
        }
        val social = member("SOCIAL_MANAGER", project)
        val script = llm.fresh()
        script.returnObject(DuplicateVerdicts(listOf(DuplicateVerdict(1, true, 0.7), DuplicateVerdict(2, true, 0.7))))
        val response = run(social, project)
        assertEquals(HttpStatus.CREATED, response.status, response.body)
        val prompt = script.promptsReceived.single()
        assertFalse(surname in prompt || surname.uppercase() in prompt, prompt)
        assertEquals(
            setOf("person", "stakeholder"),
            tree(response.body)["suggestions"]
                .values()
                .map { it["attributes"]["target_object"].asString() }
                .toSet()
        )
    }

    @Test
    fun `quien no lee los nombres de person recibe 403 sin llamar al modelo`() {
        val project = project()
        grievances(project)
        llm.fresh()
        val response = run(member("ANALYST", project), project)
        assertEquals(HttpStatus.FORBIDDEN, response.status, response.body)
        assertEquals(0, llm.calls.get())
    }
}
