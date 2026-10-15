package sgspe.deliverable

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.test.context.TestPropertySource
import sgspe.F8ApiTest
import sgspe.ai.DraftText
import sgspe.ai.RecordingLlm
import tools.jackson.databind.JsonNode

// especificación de F8 §3.9 y §5 («IA», SectionDraftApiTest; plan 8.2c, Tareas 14 y 15; Review Focus 4): AI-04 de sección
// contra wasichai de verdad, con el modelo guionado (RecordingLlm) y una clave falsa que nunca se usa, como AiRunsApiTest. el
// único contexto de Spring nuevo del plan: el de F8 con la IA activada para la organización sembrada (slug demo)
@TestPropertySource(
    properties = [
        "wasichai.agent.api-key=sk-ant-clave-de-prueba",
        "embabel.agent.platform.models.anthropic.api-key=sk-ant-clave-de-prueba",
        "spring.ai.anthropic.api-key=sk-ant-clave-de-prueba",
        "sgspe.ai.enabled-organizations=demo"
    ]
)
@Import(RecordingLlm.Config::class)
class SectionDraftApiTest : F8ApiTest() {
    @Autowired
    private lateinit var llm: RecordingLlm

    private fun created(response: Response): JsonNode {
        assertEquals(HttpStatus.CREATED, response.status, response.body)
        return tree(response.body)
    }

    // una persona con un apellido único (solo letras): la base es compartida y el diccionario de nombres no debe ver homónimos
    private fun namedPerson(): Pair<String, String> {
        val surname = "Ejemplo" + (1..8).map { ('a'..'z').random() }.joinToString("").replaceFirstChar { it.uppercase() }
        return createRecord("person", mapOf("given_names" to "Persona", "surnames" to surname, "sex" to "FEMENINO")) to surname
    }

    @Test
    fun `el nombre de la seccion no sale hacia el modelo y la sugerencia lleva el marcador estable`() {
        val scenario = reportScenario()
        val manager = member("SOCIAL_MANAGER", scenario.project)
        val (person, surname) = namedPerson()
        val section = sectionId(scenario.fieldReport, "relaciones")
        assertEquals(HttpStatus.OK, edit("deliverable_section", section, mapOf("content" to "Se conversó con [[person:$person]] sobre la faena.")).status)
        val script = llm.fresh()
        script.returnObject(DraftText("## Relaciones\n\nSe acordó con [PERSONA_1] la próxima asamblea.", 0.7))
        val run = created(aiRun(manager, "AI-04", "deliverable_section", section))
        // el modelo recibió la sección, con la persona como marcador por llamada, y nunca su apellido
        assertTrue(script.promptsReceived.isNotEmpty(), "el modelo no recibió nada")
        assertTrue(script.promptsReceived.any { "[PERSONA_" in it }, "la persona no salió como marcador")
        for (sent in script.promptsReceived) assertFalse(surname in sent, "el apellido salió hacia el modelo")
        val suggestion = run["suggestions"].values().single()["attributes"]
        assertEquals("BORRADOR DE SECCION", suggestion["suggestion_kind"].asString())
        assertEquals("deliverable_section" to section, suggestion["target_object"].asString() to suggestion["target_record_id"].asString())
        assertEquals("## Relaciones\n\nSe acordó con [PERSONA:$person] la próxima asamblea.", suggestion["suggested_value"].asString())
        val interaction = record("ai_interaction", run["interaction"].asString())["attributes"]
        assertEquals("ai-04-seccion@1" to "AI-04", interaction["prompt_version"].asString() to interaction["use_case"].asString())
    }

    @Test
    fun `una seccion sin IA es 422 SECTION_NOT_ASSISTED y sin permiso 403, sin llamar al modelo`() {
        val scenario = reportScenario()
        val manager = member("SOCIAL_MANAGER", scenario.project)
        llm.fresh()
        val refused = aiRun(manager, "AI-04", "deliverable_section", sectionId(scenario.fieldReport, "objetivos"))
        assertEquals(HttpStatus.UNPROCESSABLE_CONTENT, refused.status, refused.body)
        assertEquals("SECTION_NOT_ASSISTED", code(refused))
        assertEquals(listOf("target.recordId"), problemFields(refused.body))
        val reader = member("READ_ONLY", scenario.project)
        assertEquals(HttpStatus.FORBIDDEN, aiRun(reader, "AI-04", "deliverable_section", sectionId(scenario.fieldReport, "conclusiones")).status)
        assertEquals(0, llm.calls.get())
    }

    @Test
    fun `adoptar con motivo deja ASISTIDO POR IA con el vinculo, convierte el marcador y la version emitida la lista`() {
        val scenario = reportScenario()
        val manager = member("SOCIAL_MANAGER", scenario.project)
        val (person, _) = namedPerson()
        val section = sectionId(scenario.fieldReport, "conclusiones")
        val suggestion =
            aiSuggestion(
                scenario.project,
                "BORRADOR DE SECCION",
                "deliverable_section" to section,
                mapOf("suggested_value" to "1. Se acordó con [PERSONA:$person] la asamblea.", "ai_interaction" to aiInteraction(scenario.project, "AI-04"))
            )
        val unresolved = acceptSuggestion(manager, suggestion, mapOf("content" to "1. Se acordó con [PERSONA] la asamblea."))
        assertEquals(HttpStatus.UNPROCESSABLE_CONTENT, unresolved.status, unresolved.body)
        assertEquals("UNRESOLVED_PSEUDONYM", code(unresolved))
        assertEquals(listOf("attributes.content"), problemFields(unresolved.body))
        assertEquals("proposed", state("ai_suggestion", suggestion))
        // otro marcador: 400 PSEUDONYM_NOT_ALLOWED, y nada se escribe
        val other = acceptSuggestion(manager, suggestion, mapOf("content" to "1. DNI [DOCUMENTO]."))
        assertEquals(HttpStatus.BAD_REQUEST, other.status, other.body)
        assertEquals("PSEUDONYM_NOT_ALLOWED", code(other))
        assertEquals(listOf("attributes.content"), problemFields(other.body))
        assertEquals("proposed", state("ai_suggestion", suggestion))
        assertFalse(record("deliverable_section", section)["attributes"]["content_origin"].asString() == "ASISTIDO POR IA")
        // las «» que SectionText pone alrededor de un nombre se quitan con el marcador (Tarea 14)
        val accepted = acceptSuggestion(manager, suggestion, mapOf("content" to "1. Se acordó con «[PERSONA:$person]» la asamblea."))
        assertEquals(HttpStatus.OK, accepted.status, accepted.body)
        val attributes = record("deliverable_section", section)["attributes"]
        assertEquals("1. Se acordó con [[person:$person]] la asamblea.", attributes["content"].asString())
        assertEquals("ASISTIDO POR IA", attributes["content_origin"].asString())
        assertEquals(suggestion, attributes["ai_suggestion"].asString())
        assertEquals("accepted", state("ai_suggestion", suggestion))
        // ASM-137, confirmada por el usuario: el origen no vuelve atrás en toda la vida del entregable
        val back = edit("deliverable_section", section, mapOf("content_origin" to "HUMANO"), headers = withReason("vuelta"))
        assertEquals(HttpStatus.UNPROCESSABLE_CONTENT, back.status, back.body)
        assertEquals("AI_ORIGIN_LOCKED", code(back))
        assertEquals(HttpStatus.ACCEPTED, requestDraft(scenario.fieldReport).status)
        generate()
        move("deliverable", scenario.fieldReport, "submit_for_review", manager.token, "envío de prueba")
        move("deliverable", scenario.fieldReport, "approve", manager.token, "aprobación de prueba")
        val issued = issue(scenario.fieldReport, manager)
        assertEquals(HttpStatus.CREATED, issued.status, issued.body)
        val versions =
            tree(
                send("GET", "/api/objects/deliverable_version/records?deliverable=${scenario.fieldReport}&size=10", null, HttpStatus.OK)
            )["content"].values()
        assertEquals(2, versions.size)
        assertTrue(versions.all { "conclusiones" in it["attributes"]["ai_assisted_sections"].asString() }, "$versions")
    }

    @Test
    fun `un resumen ejecutivo se copia a una seccion que se lee y no a la de otro proyecto`() {
        val scenario = reportScenario()
        val manager = member("SOCIAL_MANAGER", scenario.project)
        val summary =
            aiSuggestion(
                scenario.project,
                "RESUMEN EJECUTIVO",
                "project" to scenario.project,
                mapOf("suggested_value" to "## Situación general\n\nSin novedades.", "ai_interaction" to aiInteraction(scenario.project, "AI-04"))
            )
        val elsewhere = sectionId(deliverable(testProject()), "antecedentes")
        val refused = acceptSuggestion(manager, summary, mapOf("section" to elsewhere, "content" to "## Situación general"))
        assertEquals(HttpStatus.FORBIDDEN, refused.status, refused.body)
        assertEquals("OBJECT_NOT_READABLE", code(refused))
        // la lee, pero es de otro proyecto que la sugerencia: 409 PROJECT_MISMATCH, y nada se escribe
        assign(manager, testProject(), "SOCIAL_MANAGER")
        val mismatch = acceptSuggestion(manager, summary, mapOf("section" to elsewhere, "content" to "## Situación general"))
        assertEquals(HttpStatus.CONFLICT, mismatch.status, mismatch.body)
        assertEquals("PROJECT_MISMATCH", code(mismatch))
        assertEquals(listOf("attributes.section"), problemFields(mismatch.body))
        assertEquals("proposed", state("ai_suggestion", summary))
        assertFalse(record("deliverable_section", elsewhere)["attributes"]["content_origin"].asString() == "ASISTIDO POR IA")
        val section = sectionId(scenario.fieldReport, "antecedentes")
        val copied = acceptSuggestion(manager, summary, mapOf("section" to section, "content" to "## Situación general\n\nSin novedades."))
        assertEquals(HttpStatus.OK, copied.status, copied.body)
        val attributes = record("deliverable_section", section)["attributes"]
        assertEquals("ASISTIDO POR IA", attributes["content_origin"].asString())
        assertEquals(summary, attributes["ai_suggestion"].asString())
    }
}
