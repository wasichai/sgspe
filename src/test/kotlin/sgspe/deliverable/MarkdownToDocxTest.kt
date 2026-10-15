package sgspe.deliverable

import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import sgspe.privacy.Block
import sgspe.privacy.Cell
import sgspe.privacy.ReportMeta
import sgspe.privacy.ReportModel
import sgspe.privacy.ReportSection
import java.io.ByteArrayInputStream

// deliverables.md §7.4 y §7.5: CommonMark con tablas GFM hacia los estilos de la plantilla, sin HTML; celdas combinadas y
// notas al pie; salto a una sección horizontal y vuelta
class MarkdownToDocxTest {
    private fun write(vararg blocks: Block): XWPFDocument {
        val meta =
            ReportMeta(
                "INFORME DE CAMPO Y AVANCE",
                1,
                "ENT-002",
                "Prueba",
                "A",
                "0",
                "Proyecto Ejemplo A",
                dateLabel = "Setiembre, 2026",
                templateSha256 = "0".repeat(64),
                level = "INTERNO"
            )
        val model = ReportModel(meta, listOf(ReportSection("conclusiones", "Conclusiones", "NARRATIVA", blocks.toList())))
        return XWPFDocument(
            ByteArrayInputStream(DocxWriter(DeliverableProperties.Photos()).write(DocxWriter.Input(model, BaseTemplate.bytes, true, null, emptyMap())))
        )
    }

    private fun styleName(
        doc: XWPFDocument,
        id: String?
    ): String? = id?.let { doc.styles.getStyle(it)?.name }

    @Test
    fun `titulos, listas, enfasis y tablas GFM con los estilos de la plantilla, y el HTML se ignora`() {
        val markdown =
            """
            ## Redes identificadas

            1. La red de la **Comunidad Ejemplo**.
            2. La red de *apoyo externo*.

            - una viñeta

            <script>alert(1)</script>

            | Red | Estado |
            |---|---|
            | Comunidad Ejemplo | PENETRADA |
            """.trimIndent()
        val doc = write(Block.Paragraph(markdown))
        val section = doc.paragraphs.first { it.text == "Conclusiones" }
        assertEquals("heading 1", styleName(doc, section.style))
        val sub = doc.paragraphs.first { it.text == "Redes identificadas" }
        assertEquals("heading 3", styleName(doc, sub.style))
        val first = doc.paragraphs.first { it.text.startsWith("1. ") }
        assertEquals("List Paragraph", styleName(doc, first.style))
        assertTrue(first.runs.any { it.isBold && it.text() == "Comunidad Ejemplo" })
        assertTrue(doc.paragraphs.any { it.text == "• una viñeta" })
        assertFalse(doc.paragraphs.any { "script" in it.text })
        assertEquals(
            "PENETRADA",
            doc.tables
                .single()
                .getRow(1)
                .getCell(1)
                .text
        )
    }

    @Test
    fun `celdas combinadas, nota al pie y seccion horizontal`() {
        val table =
            Block.Table(
                "Redes por caserío",
                listOf("Caserío", "Red", "Estado"),
                listOf(
                    listOf(Cell("Caserío Ejemplo", rowSpan = 2), Cell("Red A"), Cell("PENETRADA")),
                    listOf(Cell("Red B"), Cell("SIN CONTACTO")),
                    listOf(Cell("Total", colSpan = 2), Cell("2"))
                ),
                wide = true
            )
        val doc = write(Block.Paragraph("El cargo está vacante.", footnotes = listOf("Vacante desde agosto.")), table)
        val t = doc.tables.single()
        assertEquals(
            "RESTART",
            t
                .getRow(1)
                .getCell(0)
                .ctTc.tcPr.vMerge.`val`
                .toString()
                .uppercase()
        )
        assertEquals(2, t.getRow(3).tableCells.size)
        assertEquals(
            "2",
            t
                .getRow(3)
                .getCell(0)
                .ctTc.tcPr.gridSpan.`val`
                .toString()
        )
        assertEquals(
            "Vacante desde agosto.",
            doc.footnotes
                .last()
                .paragraphs
                .first()
                .text
        )
        val sections = doc.paragraphs.mapNotNull { it.ctp.pPr?.sectPr }
        assertEquals(listOf("portrait", "landscape"), sections.map { it.pgSz.orient.toString() })
    }
}
