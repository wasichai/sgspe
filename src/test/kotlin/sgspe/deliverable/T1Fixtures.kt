package sgspe.deliverable

import sgspe.privacy.Block
import sgspe.privacy.Cell
import sgspe.privacy.ChartSeries
import sgspe.privacy.MentionTarget
import sgspe.privacy.ReportFixtures
import sgspe.privacy.ReportModel
import sgspe.privacy.ReportSection

// un T1 de prueba sin base (plan 8.2c, Tareas 4 y 16): portada, índice con sus listas y `chapters` capítulos con
// `paragraphs` párrafos, una tabla por capítulo, la matriz 5 × 5 y la tendencia. todo ficticio (RQ-SEC-018)
object T1Fixtures {
    const val PERSON = "person:11111111-1111-1111-1111-111111111111"

    // una frase distinta por capítulo y párrafo: la prueba de texto busca cada una en el PDF
    fun sentence(
        chapter: Int,
        n: Int
    ): String = "En el capítulo $chapter, párrafo $n, el equipo social registró la visita a la Comunidad Ejemplo."

    fun model(
        chapters: Int = 3,
        paragraphs: Int = 12
    ): ReportModel {
        val body =
            (1..chapters).map { c ->
                ReportSection(
                    "capitulo-$c",
                    "$c. Capítulo de prueba $c",
                    "MIXTA",
                    listOf(Block.Heading(2, "$c.1 Detalle del capítulo $c")) +
                        (1..paragraphs).map { Block.Paragraph(sentence(c, it)) } +
                        Block.Table(
                            "Localidades del capítulo $c",
                            listOf("Localidad", "Familias"),
                            (1..8).map { listOf(Cell("Caserío Ejemplo $it"), Cell("${it * 10}")) }
                        ) + Block.Paragraph("Se conversó con [[$PERSON]] sobre el capítulo $c."),
                    chapter = c
                )
            }
        val risks =
            ReportSection(
                "riesgos",
                "${chapters + 1}. Riesgos sociales",
                "CALCULADA",
                listOf(
                    Block.Table(
                        "Matriz de probabilidad e impacto",
                        listOf("Impacto \\ Probabilidad", "1", "2", "3", "4", "5"),
                        (5 downTo 1).map { i ->
                            listOf(Cell("$i")) +
                                (1..5).map { p -> Cell(if (p * i >= 15) "1" else "0", shade = if (p * i >= 15) "FF7C80" else "C6E0B4") }
                        },
                        figure = true
                    ),
                    Block.LineChart(
                        "Confianza promedio",
                        listOf("2026-07", "2026-08", "2026-09"),
                        listOf(ChartSeries("Confianza", listOf("3.00", "3.50", "4.00")))
                    )
                ),
                chapter = chapters + 1
            )
        return ReportModel(
            meta = ReportFixtures.model().meta.copy(type = "DIAGNOSTICO SOCIAL", title = "Diagnóstico social del Proyecto Ejemplo A"),
            sections =
                listOf(
                    ReportSection("portada", "Portada", "CALCULADA", listOf(Block.Paragraph("Diagnóstico social del Proyecto Ejemplo A"))),
                    ReportSection(
                        "indice",
                        "Índice",
                        "CALCULADA",
                        listOf(
                            Block.Toc("Índice"),
                            Block.CaptionList("Lista de tablas", CaptionNumbering.TABLE),
                            Block.CaptionList("Lista de figuras", CaptionNumbering.FIGURE)
                        )
                    )
                ) + body + risks,
            people = mapOf(PERSON to MentionTarget("Persona Ejemplo 1", "Presidente", "Comunidad Ejemplo", "GI-111111111111"))
        )
    }
}
