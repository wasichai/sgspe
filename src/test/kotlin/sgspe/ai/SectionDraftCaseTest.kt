package sgspe.ai

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import sgspe.deliverable.SectionSource
import sgspe.deliverable.SectionText
import sgspe.privacy.Block
import sgspe.privacy.Cell
import sgspe.privacy.MentionTarget
import java.time.LocalDate
import java.util.UUID

// AI-04 de sección (plan 8.2c, decisión 16): ningún nombre de persona llega al proveedor. una mención sale por nombre solo si el
// diccionario de la corrida la reemplaza entera (y vuelve como [PERSONA:<id>]); si no, con la cita de EXTERNO
class SectionDraftCaseTest {
    private val rosa = UUID.fromString("00000000-0000-0000-0000-0000000000b1")
    private val wayra = UUID.fromString("00000000-0000-0000-0000-0000000000b2")
    private val position = UUID.fromString("00000000-0000-0000-0000-0000000000b3")
    private val names =
        NameDictionary(
            listOf(
                KnownPerson(RunFixtures.ANA, "Ana", "Ejemplo"),
                KnownPerson(rosa, "Rosa María", null),
                KnownPerson(wayra, "Wayra", null)
            )
        )
    private val people =
        mapOf(
            "person:${RunFixtures.ANA}" to MentionTarget("Ana Ejemplo", "Presidenta", "Comunidad Ejemplo"),
            "person:$rosa" to MentionTarget("Rosa María", null, null, "GI-000000000001"),
            "person:$wayra" to MentionTarget("Wayra", null, null, null, "Localidad Ejemplo"),
            "position:$position" to MentionTarget("Rosa María", "Tesorera", "Asociación Ejemplo")
        )

    private fun section(text: String) =
        SectionSource(
            section = RunFixtures.PROJECT,
            deliverable = RunFixtures.ENGAGEMENT,
            project = RunFixtures.PROJECT,
            key = "conclusiones",
            title = "Conclusiones",
            guide = "Lo central del periodo.",
            periodStart = LocalDate.parse("2026-08-01"),
            periodEnd = null,
            text = text,
            updatedAt = null
        )

    @Test
    fun `ninguna persona llega por nombre al proveedor, y la del diccionario vuelve como su id`() {
        val blocks =
            listOf(
                Block.Paragraph("Se conversó con [[person:${RunFixtures.ANA}]], [[person:$rosa]] y [[person:$wayra]]."),
                Block.Table("Cargos", listOf("Cargo"), listOf(listOf(Cell("[[position:$position]]")))),
                Block.Heading(3, "Con [[person:$wayra]]")
            )
        val text = SectionText.render(blocks, people, SectionDraftCase.nameable(names))
        assertTrue("Ana Ejemplo" in text, text)
        assertTrue("GI-000000000001" in text && "una persona de Localidad Ejemplo" in text && "Tesorera, Asociación Ejemplo" in text, text)
        val model = MockModel(FakeModel { DraftText("[PERSONA_1] presidió la reunión.", 0.5) })
        val source =
            RunSource(
                objectName = DraftAgents.SECTION_TARGET,
                id = RunFixtures.PROJECT,
                project = RunFixtures.PROJECT,
                text = SectionDraftCase.text(section(text), emptyList()),
                inputReference = "deliverable_section:${RunFixtures.PROJECT}"
            )
        val result = runBlocking { DraftAgents(RunFixtures.calls(model)).section(source, names, RunBudget(8)) }
        val sent =
            model.sent
                .single()
                .prompt.text
        for (name in listOf("Ana", "Ejemplo, Ana", "Rosa", "María", "Wayra")) assertFalse(name in sent, "$name salió: $sent")
        assertEquals("[PERSONA:${RunFixtures.ANA}] presidió la reunión.", result.suggestions.single().suggestedValue)
    }

    @Test
    fun `el texto nombra un periodo sin fin como sin dato`() {
        val text = SectionDraftCase.text(section("Sin novedades."), listOf("- KPI-ENG-01 Reuniones: 3"))
        assertTrue("Periodo: 2026-08-01 a sin dato" in text, text)
        assertTrue("Indicadores del periodo:\n- KPI-ENG-01 Reuniones: 3" in text, text)
        assertFalse("null" in text, text)
        assertFalse("Periodo" in SectionDraftCase.text(section("x").copy(periodStart = null), emptyList()))
    }

    @Test
    fun `nombrable solo si el diccionario reemplaza el nombre entero`() {
        val nameable = SectionDraftCase.nameable(names)
        assertTrue(nameable("Ana Ejemplo"))
        assertFalse(nameable("Rosa María"))
        assertFalse(nameable("Wayra"))
        assertFalse(nameable("Ana Ejemplo Ccori"))
        assertFalse(nameable("Sin nombre"))
    }
}
