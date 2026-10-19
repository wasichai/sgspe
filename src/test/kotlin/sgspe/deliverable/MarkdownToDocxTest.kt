package sgspe.deliverable

import org.apache.poi.openxml4j.opc.OPCPackage
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.XWPFRelation
import org.apache.poi.xwpf.usermodel.XWPFStyle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTStyle
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STStyleType
import sgspe.privacy.Block
import sgspe.privacy.Cell
import sgspe.privacy.ReportMeta
import sgspe.privacy.ReportModel
import sgspe.privacy.ReportSection
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

// deliverables.md §7.4 y §7.5: CommonMark con tablas GFM hacia los estilos de la plantilla, sin HTML; celdas combinadas y
// notas al pie; salto a una sección horizontal y vuelta
class MarkdownToDocxTest {
    private fun write(vararg blocks: Block): XWPFDocument = XWPFDocument(ByteArrayInputStream(bytes(*blocks)))

    private fun bytes(vararg blocks: Block): ByteArray {
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
        return DocxWriter(DeliverableProperties.Photos()).write(DocxWriter.Input(model, BaseTemplate.bytes, true, null, emptyMap()))
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
        val first = doc.paragraphs.first { it.text.startsWith("1.\t") }
        assertEquals("List Paragraph", styleName(doc, first.style))
        assertTrue(first.runs.any { it.isBold && it.text() == "Comunidad Ejemplo" })
        assertTrue(doc.paragraphs.any { it.text == "•\tuna viñeta" })
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

    private val meta =
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

    // una plantilla guardada en español o por LibreOffice: ids distintos y nombres con otras mayúsculas
    private fun foreignTemplate(): ByteArray {
        val docx =
            org.apache.poi.xwpf.usermodel.XWPFDocument().use { doc ->
                doc.createStyles()
                listOf("Normal" to "Normal", "Ttulo1" to "Heading 1", "Leyenda" to "Caption", "Prrafodelista" to "list paragraph").forEach { (id, name) ->
                    val ct = CTStyle.Factory.newInstance()
                    ct.styleId = id
                    ct.type = STStyleType.PARAGRAPH
                    ct.addNewName().setVal(name)
                    doc.styles.addStyle(XWPFStyle(ct))
                }
                ByteArrayOutputStream().also { doc.write(it) }.toByteArray()
            }
        return OPCPackage.open(ByteArrayInputStream(docx)).use { pkg ->
            pkg.replaceContentType(XWPFRelation.DOCUMENT.contentType, XWPFRelation.TEMPLATE.contentType)
            ByteArrayOutputStream().also { pkg.save(it) }.toByteArray()
        }
    }

    private fun foreignModel() =
        ReportModel(
            meta,
            listOf(
                ReportSection(
                    "conclusiones",
                    "Conclusiones",
                    "NARRATIVA",
                    listOf(Block.Paragraph("1. uno"), Block.Table("Redes", listOf("A"), listOf(listOf(Cell("x")))))
                )
            )
        )

    @Test
    fun `los estilos se buscan por nombre sin distinguir mayusculas aunque el id sea otro`() {
        val bytes = DocxWriter(DeliverableProperties.Photos()).write(DocxWriter.Input(foreignModel(), foreignTemplate(), true, null, emptyMap()))
        val doc = XWPFDocument(ByteArrayInputStream(bytes))
        assertEquals("Ttulo1", doc.paragraphs.first { it.text == "Conclusiones" }.style)
        assertEquals("Prrafodelista", doc.paragraphs.first { it.text.startsWith("1.\t") }.style)
        assertEquals("Leyenda", doc.paragraphs.first { it.text.startsWith("Tabla 1. Redes") }.style)
    }

    @Test
    fun `el mismo escritor da los mismos ids de marcador en cada corrida`() {
        val writer = DocxWriter(DeliverableProperties.Photos())

        fun ids(): List<String> =
            XWPFDocument(ByteArrayInputStream(writer.write(DocxWriter.Input(foreignModel(), BaseTemplate.bytes, true, null, emptyMap()))))
                .paragraphs
                .flatMap { it.ctp.bookmarkStartList }
                .map { it.id.toString() }
        val first = ids()
        assertTrue(first.isNotEmpty())
        assertEquals(first, ids())
        assertEquals(first.toSet().size, first.size)
    }

    // revisión final del plan 8.2b, I3: un anidado muy profundo (citas, listas, énfasis) no desborda la pila de JOB-05; más
    // allá del tope el contenido sale como texto plano
    @Test
    fun `un anidado muy profundo no desborda la pila y conserva el texto`() {
        val depth = 50_000
        val quotes = "> ".repeat(depth) + "cita del fondo"
        val lists = "- ".repeat(depth) + "lista del fondo"
        val emphasis = (1..depth).joinToString(" ") { "*x" } + " énfasis del fondo " + (1..depth).joinToString(" ") { "z*" }
        val docx = bytes(Block.Paragraph(quotes), Block.Paragraph(lists), Block.Paragraph(emphasis))
        // el cuerpo, sin POI: tanto texto repetido pasa su tope de compresión (ZipSecureFile), que es de la lectura y no del escritor
        val text =
            ZipInputStream(ByteArrayInputStream(docx)).use { zip ->
                generateSequence { zip.nextEntry }.first { it.name == "word/document.xml" }
                zip.readAllBytes().toString(Charsets.UTF_8)
            }
        assertTrue("cita del fondo" in text)
        assertTrue("lista del fondo" in text)
        assertTrue("énfasis del fondo" in text)
    }
}
