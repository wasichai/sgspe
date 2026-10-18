package sgspe.deliverable

import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.openxmlformats.schemas.drawingml.x2006.chart.CTAxDataSource
import org.openxmlformats.schemas.drawingml.x2006.chart.CTNumDataSource
import sgspe.privacy.Block
import sgspe.privacy.Cell
import sgspe.privacy.ChartPoint
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
                    ReportSection("portada", "Portada", "CALCULADA", listOf(Block.Paragraph("Diagnóstico social del Proyecto Ejemplo A")), showTitle = false),
                    ReportSection(
                        "indice",
                        "Índice",
                        "CALCULADA",
                        listOf(
                            Block.Toc("Índice"),
                            Block.CaptionList("Lista de tablas", CaptionNumbering.TABLE),
                            Block.CaptionList("Lista de figuras", CaptionNumbering.FIGURE)
                        ),
                        showTitle = false
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
                            Block.LineChart("Confianza promedio", listOf("2026-08", "2026-09"), listOf(ChartSeries("Confianza", listOf("3.50", "4.00")))),
                            Block.ScatterChart(
                                "Gráfico de influencia",
                                "Interés",
                                "Poder",
                                listOf(ChartPoint("GI-111111111111", "4.2", "3.8"), ChartPoint("GI-222222222222", "2", "4")),
                                "1",
                                "5",
                                "3.5",
                                "3"
                            )
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
        // el orden de los hijos de w:r es el que Word muestra: texto, salto, texto
        assertEquals(listOf("t", "br", "t"), Regex("<w:(t|br)[ />]").findAll(xml).map { it.groupValues[1] }.toList(), xml)
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
        assertEquals(2, texts.count { it.startsWith("Figura 9.3. Gráfico de influencia") }, "$texts")
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

    // M1 de la revisión de #105 a #111: el título se quita por el modelo, no por la clave. una sección `portada` sin showTitle
    // (la de T2, o la de un modelo congelado antes del campo) conserva su título
    @Test
    fun `una seccion sin showTitle conserva su titulo aunque su clave sea portada`() {
        val base = model()
        val titled = base.copy(sections = base.sections.map { it.copy(showTitle = null) })
        val bytes = writer.write(DocxWriter.Input(titled, BaseTemplate.bytes, false, "Asociación Ejemplo", emptyMap()))
        val texts = XWPFDocument(ByteArrayInputStream(bytes)).use { doc -> doc.paragraphs.map { it.text } }
        assertTrue("Portada" in texts, "$texts")
        assertEquals(2, texts.count { it == "Índice" }, "$texts")
    }

    @Test
    fun `el grafico de tendencia es nativo y las propiedades llevan titulo y codigo`() {
        val doc = doc()
        assertEquals(2, doc.charts.size)
        assertEquals("Diagnóstico social", doc.properties.coreProperties.title)
        assertTrue(
            "ENT-001" in
                doc.properties.coreProperties.subject
                    .orEmpty()
        )
    }

    // #83: la tendencia es una línea nativa con su leyenda numerada, sus periodos como categorías, sus valores, el nombre de
    // la serie, marcadores y la leyenda abajo (no basta con contar los gráficos)
    @Test
    fun `el grafico de tendencia lleva sus periodos, sus valores y la serie con nombre`() {
        val doc = doc()
        assertTrue(doc.paragraphs.any { it.text == "Figura 9.2. Confianza promedio" }, doc.paragraphs.joinToString { it.text })
        val chart =
            doc.charts.single {
                it.ctChart.plotArea.lineChartList
                    .isNotEmpty()
            }
        val series =
            chart.ctChart.plotArea
                .getLineChartArray(0)
                .serList
                .single()
        assertEquals(
            "Confianza",
            series.tx.strRef
                ?.strCache
                ?.getPtArray(0)
                ?.v ?: series.tx.v
        )
        assertEquals(
            listOf("2026-08", "2026-09"),
            (
                series.cat.strRef
                    ?.strCache
                    ?.ptList ?: series.cat.strLit.ptList
            ).map { it.v }
        )
        assertEquals(listOf("3.5", "4.0"), numbers(series.`val`))
        assertEquals(
            "circle",
            series.marker.symbol.`val`
                .toString()
        )
        assertEquals(
            "b",
            chart.ctChart.legend.legendPos.`val`
                .toString()
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

    // #108: el gráfico de influencia es una dispersión nativa con UNA serie (LibreOffice no dibuja los marcadores de decenas de
    // series de un punto), los ejes de 1 a 5 cruzados en los umbrales del proyecto (los cuadrantes, sin series de línea: una
    // línea como serie sale torcida en LibreOffice), sin leyenda ni etiquetas
    // por punto: con decenas de grupos se pisan, y la tabla de cuadrantes lista los códigos
    @Test
    fun `el grafico de influencia es una dispersion nativa de una serie con los ejes cruzados en los umbrales`() {
        val chart =
            doc().charts.single {
                it.ctChart.plotArea.scatterChartList
                    .isNotEmpty()
            }
        val series =
            chart.ctChart.plotArea
                .getScatterChartArray(0)
                .serList
        assertEquals(1, series.size)
        val points = series.single()
        assertEquals(listOf("4.2", "2.0"), numbers(points.xVal).map { it.toDouble().toString() })
        assertEquals(listOf("3.8", "4.0"), numbers(points.yVal).map { it.toDouble().toString() })
        assertEquals(
            "circle",
            points.marker.symbol.`val`
                .toString()
        )
        assertFalse(points.isSetDLbls)
        val axes = chart.ctChart.plotArea.valAxList
        assertEquals(2, axes.size)
        assertTrue(axes.all { it.scaling.min.`val` == 1.0 && it.scaling.max.`val` == 5.0 })
        // el eje vertical cruza el horizontal en el umbral de interés, y el horizontal al vertical en el de poder
        val bottom = axes.single { it.axPos.`val`.toString() == "b" }
        val left = axes.single { it.axPos.`val`.toString() == "l" }
        assertEquals(3.0, bottom.crossesAt.`val`)
        assertEquals(3.5, left.crossesAt.`val`)
        assertTrue(axes.all { it.tickLblPos.`val`.toString() == "low" })
        assertTrue(chart.ctChart.legend == null)
        // la leyenda no queda sola al pie de la página anterior
        val caption = doc().paragraphs.single { it.text.startsWith("Figura 9.3. Gráfico de influencia") && it.ctp.bookmarkStartList.isNotEmpty() }
        assertTrue(caption.ctp.pPr.isSetKeepNext)
    }

    // los valores de una serie: XDDF los escribe como literales (numLit) cuando vienen de un arreglo
    private fun numbers(source: CTNumDataSource): List<String> = (source.numRef?.numCache?.ptList ?: source.numLit.ptList).map { it.v }

    private fun numbers(source: CTAxDataSource): List<String> = (source.numRef?.numCache?.ptList ?: source.numLit.ptList).map { it.v }
}
