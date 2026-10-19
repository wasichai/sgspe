package sgspe.deliverable

import org.apache.poi.xwpf.usermodel.ParagraphAlignment
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STTblLayoutType
import sgspe.privacy.Block
import sgspe.privacy.Cell
import sgspe.privacy.ColumnSpec
import sgspe.privacy.ReportMeta
import sgspe.privacy.ReportModel
import sgspe.privacy.ReportSection
import java.io.ByteArrayInputStream
import java.math.BigInteger

// ASM-141, deliverables.md §7.5: el DOCX con el diseño común. tablas al ancho de la caja con el reparto de ColumnWidths, sin
// filetes verticales, con relleno y alineación por columna; la plantilla base con márgenes, cuerpo justificado, guiones y
// títulos que no quedan solos al pie; el pie con el código a la izquierda y la página a la derecha
class DocxDesignTest {
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
        val bytes = DocxWriter(DeliverableProperties.Photos()).write(DocxWriter.Input(model, BaseTemplate.bytes, false, null, emptyMap()))
        return XWPFDocument(ByteArrayInputStream(bytes))
    }

    private val table =
        Block.Table(
            "Miembros del área de influencia",
            listOf("Localidad", "Justificación", "Población"),
            listOf(
                listOf(Cell("CC San Juan"), Cell("Colinda con el tajo y usa el camino de acceso para trasladar su ganado a la feria"), Cell("1 250")),
                listOf(Cell("Anexo Pucará"), Cell("Se ubica aguas abajo del depósito de relaves"), Cell("96"))
            )
        )

    // A4 menos dos márgenes de 2,5 cm
    private val textWidth = 11906L - 2 * ReportDesign.twips(ReportDesign.MARGIN_CM)

    @Test
    fun `la tabla ocupa la caja con un grid fijo repartido por el contenido`() {
        val t = write(table).tables.single()
        val pr = t.ctTbl.tblPr
        assertEquals(STTblLayoutType.FIXED, pr.tblLayout.type)
        assertEquals(BigInteger.valueOf(textWidth), pr.tblW.w)
        val grid =
            t.ctTbl.tblGrid.gridColList
                .map { (it.w as BigInteger).toLong() }
        assertEquals(3, grid.size)
        assertTrue(kotlin.math.abs(grid.sum() - textWidth) <= 3, grid.toString())
        val expected = ColumnWidths.of(table.header, table.rows).map { Math.round(it.fraction * textWidth) }
        assertEquals(expected, grid)
        assertTrue(grid[1] > grid[0] && grid[2] < grid[0], grid.toString())
    }

    @Test
    fun `las cifras van a la derecha, el encabezado en negrita sombreado y todo en Carlito de 9 pt sin espacio despues`() {
        val t = write(table).tables.single()
        val number =
            t
                .getRow(1)
                .getCell(2)
                .paragraphs
                .single()
        assertEquals(ParagraphAlignment.RIGHT, number.alignment)
        assertEquals(
            ParagraphAlignment.LEFT,
            t
                .getRow(1)
                .getCell(1)
                .paragraphs
                .single()
                .alignment
        )
        assertEquals(BigInteger.ZERO, number.ctp.pPr.spacing.after)
        val run = number.runs.single()
        assertEquals(ReportDesign.DOCX_SANS, run.fontFamily)
        assertEquals(ReportDesign.TABLE_PT, run.fontSizeAsDouble)
        val head = t.getRow(0).getCell(0)
        assertTrue(
            head.paragraphs
                .single()
                .runs
                .single()
                .isBold
        )
        assertEquals(ReportDesign.HEADER_SHADE, head.color)
        assertEquals(STBorder.SINGLE, head.ctTc.tcPr.tcBorders.bottom.`val`)
    }

    @Test
    fun `filetes arriba y abajo, linea fina entre filas, sin verticales y con relleno de celda`() {
        val pr =
            write(table)
                .tables
                .single()
                .ctTbl.tblPr
        val borders = pr.tblBorders
        assertEquals(STBorder.SINGLE, borders.top.`val`)
        assertEquals(STBorder.SINGLE, borders.bottom.`val`)
        assertEquals(BigInteger.valueOf(ReportDesign.RULE_HEAVY.toLong()), borders.top.sz)
        assertEquals(STBorder.NIL, borders.left.`val`)
        assertEquals(STBorder.NIL, borders.insideV.`val`)
        assertEquals(BigInteger.valueOf(ReportDesign.twips(ReportDesign.CELL_SIDE_CM)), pr.tblCellMar.left.w)
    }

    @Test
    fun `una tabla ancha se reparte sobre el ancho de la pagina horizontal y los pesos de la tabla mandan`() {
        val wide = table.copy(wide = true, columns = listOf(ColumnSpec("1"), ColumnSpec("2"), ColumnSpec("1", "CENTER")))
        val t = write(wide).tables.single()
        val landscapeWidth = 16838L - 2 * ReportDesign.twips(ReportDesign.MARGIN_CM)
        assertEquals(BigInteger.valueOf(landscapeWidth), t.ctTbl.tblPr.tblW.w)
        val grid =
            t.ctTbl.tblGrid.gridColList
                .map { (it.w as BigInteger).toLong() }
        assertEquals(Math.round(landscapeWidth * 0.5), grid[1])
        assertEquals(
            ParagraphAlignment.CENTER,
            t
                .getRow(1)
                .getCell(2)
                .paragraphs
                .single()
                .alignment
        )
    }

    @Test
    fun `la fuente y las tablas GFM de la narrativa siguen el mismo diseno`() {
        val doc = write(table, Block.Paragraph("| Código | Monto |\n|---|---|\n| GI-01 | S/ 1 200 |"))
        val source = doc.paragraphs.first { it.text.startsWith("Fuente:") }
        assertEquals(TemplateStyles.SOURCE, doc.styles.getStyle(source.style).name)
        val gfm = doc.tables.last()
        assertEquals(STTblLayoutType.FIXED, gfm.ctTbl.tblPr.tblLayout.type)
        assertEquals(
            ParagraphAlignment.RIGHT,
            gfm
                .getRow(1)
                .getCell(1)
                .paragraphs
                .single()
                .alignment
        )
    }

    @Test
    fun `las vinetas llevan un tabulador hacia la sangria francesa`() {
        val doc = write(Block.Paragraph("- primero\n- segundo"))
        val item = doc.paragraphs.first { "primero" in it.text }
        assertEquals("•\tprimero", item.text)
        assertEquals(TemplateStyles.LIST, doc.styles.getStyle(item.style).name)
    }

    @Test
    fun `el pie lleva el codigo a la izquierda y la pagina en un tabulador a la derecha`() {
        val footer = write(table).footerList.flatMap { it.paragraphs }.first { "Página" in it.text }
        assertTrue(footer.text.startsWith("ENT-002 · Rev. 0\tPágina"), footer.text)
        assertEquals(
            BigInteger.valueOf(textWidth),
            footer.ctp.pPr.tabs.tabList
                .single()
                .pos
        )
    }

    @Test
    fun `la base fija margenes, cuerpo justificado en Caladea, guiones en espanol y titulos que no quedan solos`() {
        val doc = write(Block.Heading(2, "Un título"))
        val margins = doc.document.body.sectPr.pgMar
        assertEquals(BigInteger.valueOf(ReportDesign.twips(ReportDesign.MARGIN_CM)), margins.left)
        assertEquals(BigInteger.valueOf(ReportDesign.twips(ReportDesign.BOTTOM_MARGIN_CM)), margins.bottom)
        assertNotNull(doc.settings.ctSettings.autoHyphenation)
        val normal = doc.styles.getStyle("Normal").ctStyle
        assertEquals(
            "both",
            normal.pPr.jc.`val`
                .toString()
        )
        assertEquals(
            ReportDesign.DOCX_SERIF,
            normal.rPr.rFontsArray
                .single()
                .ascii
        )
        assertEquals(
            "es-PE",
            doc.styles.ctStyles.docDefaults.rPrDefault.rPr.langArray
                .single()
                .`val`
        )
        val heading = doc.styles.getStyle("Heading2").ctStyle
        assertNotNull(heading.pPr.keepNext)
        assertEquals(
            ReportDesign.ACCENT,
            heading.rPr.colorArray
                .single()
                .xgetVal()
                .stringValue
        )
    }
}
