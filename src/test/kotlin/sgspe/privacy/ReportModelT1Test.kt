package sgspe.privacy

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// especificación de F8 §3.2 y §3.8 (plan 8.2c, Tarea 1): lo que T1 suma al modelo del informe viaja en el JSON canónico y
// pasa por la divulgación sin perder nada que no sea sensible. todo ficticio (RQ-SEC-018)
class ReportModelT1Test {
    private val person = "person:11111111-1111-1111-1111-111111111111"

    private fun model(): ReportModel =
        ReportModel(
            meta = ReportFixtures.model().meta.copy(type = "DIAGNOSTICO SOCIAL", title = "Diagnóstico social"),
            sections =
                listOf(
                    ReportSection(
                        "indice",
                        "Índice",
                        "CALCULADA",
                        listOf(Block.Toc("Índice"), Block.CaptionList("Lista de tablas", "Tabla"), Block.CaptionList("Lista de figuras", "Figura"))
                    ),
                    ReportSection(
                        "riesgos",
                        "9.3.2 Riesgos sociales",
                        "CALCULADA",
                        listOf(
                            Block.Table(
                                "Matriz de probabilidad e impacto",
                                listOf("Impacto", "1"),
                                listOf(listOf(Cell("5"), Cell("2", shade = "C00000"))),
                                figure = true
                            ),
                            Block.LineChart(
                                "Confianza de [[$person]]",
                                listOf("2026-08", "2026-09"),
                                listOf(ChartSeries("Confianza de [[$person]]", listOf("3.50", null)))
                            ),
                            Block.ScatterChart(
                                "Influencia según [[$person]]",
                                "Interés",
                                "Poder",
                                listOf(ChartPoint("GI-222222222222", "4.2", "3.8"), ChartPoint("[[$person]]", "2", "4")),
                                "1",
                                "5",
                                "3.5",
                                "3",
                                "Evaluaciones de [[$person]]"
                            )
                        ),
                        level = 3,
                        chapter = 9
                    )
                ),
            people = mapOf(person to MentionTarget("Persona Ejemplo 1", "Presidente", "Comunidad Ejemplo", "GI-111111111111"))
        )

    @Test
    fun `los bloques de T1 vuelven iguales del JSON canonico`() {
        val model = model()
        assertEquals(model, CanonicalJson.read(CanonicalJson.bytes(model)))
    }

    @Test
    fun `una seccion sin capitulo no lleva chapter y si su nivel`() {
        val text = String(CanonicalJson.bytes(ReportFixtures.model()), Charsets.UTF_8)
        assertFalse("\"chapter\"" in text, text)
        assertTrue("\"level\":1" in text, text)
    }

    @Test
    fun `EXTERNO cita por cargo en el grafico y deja el indice, las listas y las celdas sombreadas`() {
        val external = ReportDisclosure.apply(model(), Disclosure.EXTERNO)
        assertEquals(3, external.sections[0].blocks.size)
        val blocks = external.sections[1].blocks
        val matrix = blocks[0] as Block.Table
        assertEquals("C00000", matrix.rows[0][1].shade)
        assertTrue(matrix.figure)
        val chart = blocks[1] as Block.LineChart
        assertEquals("Confianza de Presidente, Comunidad Ejemplo", chart.title)
        assertEquals("Confianza de Presidente, Comunidad Ejemplo", chart.series.single().name)
        assertEquals(listOf("3.50", null), chart.series.single().values)
        assertEquals(9 to 3, external.sections[1].chapter to external.sections[1].level)
    }

    // #108: la dispersión resuelve sus menciones como el gráfico de líneas; los códigos, las coordenadas y los umbrales quedan
    @Test
    fun `EXTERNO deja la dispersion con sus codigos, coordenadas y umbrales y cita por cargo`() {
        val scatter = ReportDisclosure.apply(model(), Disclosure.EXTERNO).sections[1].blocks[2] as Block.ScatterChart
        assertEquals("Influencia según Presidente, Comunidad Ejemplo", scatter.title)
        assertEquals("Evaluaciones de Presidente, Comunidad Ejemplo", scatter.source)
        assertEquals(listOf(ChartPoint("GI-222222222222", "4.2", "3.8"), ChartPoint("Presidente, Comunidad Ejemplo", "2", "4")), scatter.points)
        assertEquals(listOf("1", "5", "3.5", "3"), listOf(scatter.minimum, scatter.maximum, scatter.xThreshold, scatter.yThreshold))
        val text = String(CanonicalJson.bytes(model()), Charsets.UTF_8)
        assertTrue("\"block\":\"scatter-chart\"" in text, text)
    }
}
