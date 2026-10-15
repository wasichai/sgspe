package sgspe.deliverable

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import sgspe.privacy.Block
import sgspe.privacy.ReportModel
import java.util.UUID

// especificación de F8 §3.8 y §5 («Tipos», SocialDiagnosisTypeTest; plan 8.2c, Tareas 9 a 13): T1 ensamblado con una fuente
// falsa, sin base. todo ficticio (RQ-SEC-018)
class SocialDiagnosisTypeTest {
    private val type = SocialDiagnosisType()

    private fun assemble(
        source: FakeReportSource = T1Source.source(),
        content: Map<String, String> = emptyMap()
    ): Assembled = runBlocking { type.assemble(T1Source.input(content), source) }

    private fun ReportModel.blocks(key: String): List<Block> = sections.single { it.key == key }.blocks

    private fun ReportModel.tables(key: String): List<Block.Table> = blocks(key).filterIsInstance<Block.Table>()

    @Test
    fun `declara las 46 secciones en orden con sus capitulos y las asistidas por IA de la especificacion`() {
        val keys = type.sections.map { it.key }
        assertEquals(46, keys.size)
        assertEquals(keys.size, keys.toSet().size)
        assertEquals(listOf("portada", "indice", "introduccion"), keys.take(3))
        assertEquals(
            setOf(
                "introduccion",
                "areas",
                "contexto",
                "percepciones",
                "grupos_estrategicos",
                "grupos_politicos",
                "grupos_monitoreo",
                "estrategias",
                "comunicacion",
                "necesidades"
            ),
            type.sections
                .filter { it.assisted }
                .map { it.key }
                .toSet()
        )
        assertEquals((1..46).toList(), type.sections.map { it.order })
        assertTrue(type.requiresApprovedArea)
    }

    @Test
    fun `exige estudio, AIDS, periodo, corte y CRS de trabajo`() {
        val missing = runBlocking { type.missingParameters(emptyMap(), emptyMap()) }
        assertEquals(listOf("study", "area_of_influence", "period_start_date", "period_end_date", "cutoff_date", "project.working_crs"), missing)
    }

    @Test
    fun `los capitulos 1 a 4 salen del estudio, las areas y el trabajo de campo, con el mapa de areas`() {
        val assembled = assemble(content = mapOf("introduccion" to "Se conversó con [[person:${T1Source.PERSON}]]."))
        val model = assembled.model
        assertEquals(listOf(Block.Toc::class, Block.CaptionList::class, Block.CaptionList::class), model.blocks("indice").map { it::class })
        val intro = (model.blocks("introduccion").first() as Block.Paragraph).markdown
        assertTrue("Campaña 2026" in intro && "Asociación Ejemplo" in intro && "Consultora Ejemplo" in intro, intro)
        assertEquals("Persona Ejemplo 1", model.people.getValue("person:${T1Source.PERSON}").name)
        val fieldwork = model.tables("trabajo_campo").single()
        assertEquals(listOf("Ámbito", "Localidad", "Entrevistas", "Fichas de observación"), fieldwork.header)
        assertEquals(listOf("AIDS", "Caserío Ejemplo Alto", "1", "0"), fieldwork.rows[0].map { it.text })
        assertEquals(listOf("AIIS", "Caserío Ejemplo Bajo", "0", "1"), fieldwork.rows[1].map { it.text })
        val dates = (model.blocks("trabajo_campo").first() as Block.Paragraph).markdown
        assertTrue(dates.endsWith("Visitas de campo en esas fechas:** 1"), dates)
        val aids = model.tables("aids").single()
        assertTrue(aids.rows.any { it[0].text == "Caserío Ejemplo Alto" && it[1].text == "INCLUIDO" }, "${aids.rows}")
        assertTrue(aids.rows.any { it[0].text == "Comunidad Ejemplo" })
        assertTrue(
            model
                .tables("aiis")
                .single()
                .rows
                .any { it[0].text == "Caserío Ejemplo Bajo (EN SEPARACION)" }
        )
        assertEquals(Block.MapFigure(DiagnosisStudy.AREAS_MAP, "Áreas de influencia social del proyecto"), model.blocks("aiis").last())
        val map = assembled.maps.single { it.key == DiagnosisStudy.AREAS_MAP }
        assertEquals(MapFrameKind.AREAS, map.frame)
        assertEquals(listOf(T1Source.AIDS, T1Source.AIIS).map(UUID::fromString), map.areas)
        assertEquals("Asociación Ejemplo", map.author)
        val scope = model.sections.single { it.key == "alcance" }
        assertEquals(2 to 3, scope.level to scope.chapter)
        assertEquals(null, model.meta.coverMap)
    }

    @Test
    fun `un AIDS que ya no esta aprobado deja FALLIDO con DELIVERABLE_PARAMETERS_MISSING`() {
        val source = T1Source.source()
        source.rows.getValue("area_of_influence").replaceAll { if (it.id == T1Source.AIDS) it.copy(state = "superseded") else it }
        val failure = assertThrows<DraftFailure> { assemble(source) }
        assertEquals(DeliverableDrafts.PARAMETERS_MISSING to "area_of_influence", failure.code to failure.section)
    }

    @Test
    fun `un periodo que falta o no se lee deja FALLIDO con DELIVERABLE_PARAMETERS_MISSING sobre ese campo`() {
        fun failure(
            field: String,
            value: String?
        ): DraftFailure {
            val input = T1Source.input()
            val d = input.deliverable
            val changed = AssemblyInput(d.copy(attributes = d.attributes + (field to value)), input.sections, null, input.templateSha256, "A", "0", input.today)
            return assertThrows<DraftFailure> { runBlocking { type.assemble(changed, T1Source.source()) } }
        }
        assertEquals(DeliverableDrafts.PARAMETERS_MISSING to "period_end_date", failure("period_end_date", null).let { it.code to it.section })
        assertEquals(DeliverableDrafts.PARAMETERS_MISSING to "period_start_date", failure("period_start_date", "no es fecha").let { it.code to it.section })
    }

    @Test
    fun `la fuente falsa rechaza un objeto no declarado y quita los campos no declarados`() {
        val source = FakeReportSource(mapOf("project" to setOf("name")))
        T1Source.source(source)
        val row = runBlocking { source.one("project", UUID.fromString(T1Source.PROJECT)) }
        assertEquals(setOf("name"), row!!.attributes.keys)
        assertThrows<IllegalStateException> { runBlocking { source.all("person") } }
    }
}
