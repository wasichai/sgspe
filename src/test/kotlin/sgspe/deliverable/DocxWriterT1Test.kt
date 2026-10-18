package sgspe.deliverable

import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import sgspe.privacy.Block
import sgspe.privacy.Cell
import sgspe.privacy.ChartSeries
import sgspe.privacy.ReportFixtures
import sgspe.privacy.ReportModel
import sgspe.privacy.ReportSection
import java.io.ByteArrayInputStream

// especificación de F8 §3.3 y §3.8, deliverables.md §7.5 y §7.9 (plan 8.2c, Tarea 2): el DOCX de T1 leído de vuelta con POI
class DocxWriterT1Test {
    private val writer = DocxWriter(DeliverableProperties.Photos())

    private fun model(): ReportModel =
        ReportModel(
            meta = ReportFixtures.model().meta.copy(type = "DIAGNOSTICO SOCIAL", title = "Diagnóstico social"),
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
                    ),
                    ReportSection(
                        "poblacion",
                        "5.2 Población y migración",
                        "MIXTA",
                        listOf(
                            Block.Heading(4, "Caserío Ejemplo Alto"),
                            Block.Table(
                                "Familias y población",
                                listOf("Localidad", "Familias"),
                                listOf(listOf(Cell("Caserío Ejemplo"), Cell("120\nsegunda línea")))
                            )
                        ),
                        level = 2,
                        chapter = 5
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
                            Block.LineChart("Confianza promedio", listOf("2026-08", "2026-09"), listOf(ChartSeries("Confianza", listOf("3.50", "4.00"))))
                        ),
                        level = 3,
                        chapter = 9
                    ),
                    ReportSection("anexo", "10.1 Anexo 1", "ANEXO", listOf(Block.SectionBreak(true), Block.Paragraph("Mapa.")), level = 2, chapter = 10)
                )
        )

    private fun doc(): XWPFDocument =
        XWPFDocument(ByteArrayInputStream(writer.write(DocxWriter.Input(model(), BaseTemplate.bytes, false, "Asociación Ejemplo", emptyMap()))))

    @Test
    fun `una celda con dos lineas lleva un salto w br entre sus textos`() {
        val cell =
            doc()
                .tables
                .first { t -> t.rows.any { r -> r.tableCells.any { "segunda" in it.text } } }
                .rows[1]
                .getCell(1)
        val xml = cell.ctTc.xmlText()
        assertTrue("<w:br/>" in xml, xml)
        assertEquals(2, Regex("<w:t[ >]").findAll(xml).count(), xml)
        assertEquals(
            listOf("120", "segunda línea"),
            cell.paragraphs
                .single()
                .runs
                .single()
                .ctr.tList
                .map { it.stringValue }
        )
    }

    @Test
    fun `el indice es el campo TOC de cinco niveles y las listas citan cada leyenda con PAGEREF`() {
        val doc = doc()
        val xml = doc.document.xmlText()
        assertTrue("TOC \\o \"1-5\" \\h \\z \\u" in xml, "sin el campo TOC")
        assertTrue("PAGEREF _Tabla_5_1 \\h" in xml, "sin PAGEREF de la tabla")
        assertTrue("PAGEREF _Figura_9_1 \\h" in xml && "PAGEREF _Figura_9_2 \\h" in xml, "sin PAGEREF de las figuras")
        val texts = doc.paragraphs.map { it.text }
        // una vez en la lista de tablas y otra como leyenda
        assertEquals(2, texts.count { it.startsWith("Tabla 5.1. Familias y población") }, "$texts")
        assertEquals(2, texts.count { it.startsWith("Figura 9.1. Matriz de probabilidad e impacto") }, "$texts")
        assertEquals(2, texts.count { it.startsWith("Figura 9.2. Confianza promedio") }, "$texts")
        assertTrue(texts.any { DocxWriter.TOC_PLACEHOLDER in it })
    }

    @Test
    fun `los titulos usan el nivel de la seccion y la celda sombreada lleva su color`() {
        val doc = doc()
        assertEquals("Heading2", doc.paragraphs.first { it.text == "5.2 Población y migración" }.style)
        assertEquals("Heading3", doc.paragraphs.first { it.text == "9.3.2 Riesgos sociales" }.style)
        val shaded = doc.tables.flatMap { t -> t.rows.flatMap { it.tableCells } }.single { it.text == "2" }
        assertEquals("C00000", shaded.color)
    }

    @Test
    fun `la portada y el indice no llevan titulo propio y el nivel 4 usa Heading4 de la plantilla base`() {
        val doc = doc()
        val texts = doc.paragraphs.map { it.text }
        assertTrue("Portada" !in texts, "$texts")
        assertEquals(1, texts.count { it == "Índice" }, "$texts")
        assertTrue(texts.any { it == "Diagnóstico social del Proyecto Ejemplo A" })
        assertEquals("Heading4", doc.paragraphs.first { it.text == "Caserío Ejemplo Alto" }.style)
    }

    @Test
    fun `el grafico de tendencia es nativo y las propiedades llevan titulo y codigo`() {
        val doc = doc()
        assertEquals(1, doc.charts.size)
        assertEquals("Diagnóstico social", doc.properties.coreProperties.title)
        assertTrue(
            "ENT-001" in
                doc.properties.coreProperties.subject
                    .orEmpty()
        )
    }

    @Test
    fun `un salto al inicio de una seccion va antes de su titulo`() {
        val doc = doc()
        val paragraphs = doc.paragraphs
        val title = paragraphs.indexOfFirst { it.text == "10.1 Anexo 1" }
        // el párrafo que cierra la sección vertical (lleva sectPr) está antes del título del anexo
        val closing = paragraphs.indexOfLast { it.ctp.pPr?.isSetSectPr == true }
        assertTrue(closing in 0 until title, "cierre $closing, título $title")
    }
}
