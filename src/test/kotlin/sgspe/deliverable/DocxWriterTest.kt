package sgspe.deliverable

import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import sgspe.privacy.Block
import sgspe.privacy.Cell
import sgspe.privacy.Disclosure
import sgspe.privacy.ReportDisclosure
import sgspe.privacy.ReportFixtures
import sgspe.privacy.ReportSection
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

// especificación de F8 §3.3 y deliverables.md §7.5 y §13.3 («Lectura del DOCX»): el DOCX de un nivel sobre la plantilla base,
// leído de vuelta con POI
class DocxWriterTest {
    private val writer = DocxWriter(DeliverableProperties.Photos())

    private fun write(
        level: Disclosure,
        draft: Boolean
    ): XWPFDocument {
        val model = ReportDisclosure.apply(ReportFixtures.model(), level)
        val photos = model.photos.keys.associateWith { png(1200, 800) }
        val bytes = writer.write(DocxWriter.Input(model, BaseTemplate.bytes, draft, "Asociación Ejemplo", photos))
        return XWPFDocument(ByteArrayInputStream(bytes))
    }

    private fun body(doc: XWPFDocument): String =
        (doc.paragraphs.map { it.text } + doc.tables.flatMap { t -> t.rows.flatMap { r -> r.tableCells.map { it.text } } }).joinToString("\n")

    @Test
    fun `el borrador lleva BORRADOR en el encabezado y la letra, la candidata no lleva marca y si la revision de emision`() {
        val draft = write(Disclosure.INTERNO, draft = true)
        assertTrue(draft.headerList.any { DocxWriter.WATERMARK in it.text })
        assertTrue(draft.footerList.any { "ENT-001 · Rev. A\tPágina" in it.text }, draft.footerList.joinToString { it.text })
        val candidate = write(Disclosure.INTERNO, draft = false)
        assertFalse(candidate.headerList.any { DocxWriter.WATERMARK in it.text })
        assertTrue(candidate.footerList.any { "Rev. 0\tPágina" in it.text })
    }

    @Test
    fun `los marcadores de la plantilla se rellenan y el nivel sale fuera de INTERNO`() {
        val doc = write(Disclosure.EXTERNO, draft = true)
        val text = body(doc)
        assertTrue("Informe de campo y avance" in text)
        assertTrue("Proyecto Ejemplo A" in text)
        assertTrue("Setiembre, 2026" in text)
        assertFalse("{{" in text, text)
        assertTrue(doc.footerList.any { "· EXTERNO\tPágina" in it.text })
    }

    @Test
    fun `tablas con titulo numerado arriba y Fuente abajo, fotos de a dos con texto alternativo`() {
        val doc = write(Disclosure.INTERNO, draft = true)
        val paragraphs = doc.paragraphs.map { it.text }
        val caption = paragraphs.indexOfFirst { it.startsWith("Tabla 1. Comprobantes del apoyo") }
        assertTrue(caption >= 0, "$paragraphs")
        assertTrue(paragraphs.drop(caption).any { it == "Fuente: Elaboración Asociación Ejemplo" })
        assertTrue(paragraphs.any { it.startsWith("Figura 1. Entrega del apoyo") })
        assertTrue(
            doc.tables
                .first()
                .getRow(0)
                .isRepeatHeader
        )
        // ninguna fila se parte entre páginas (#83)
        assertTrue(
            doc.tables
                .first()
                .rows
                .all { it.isCantSplitRow }
        )
        val photoTable =
            doc.tables.first { t ->
                t.rows.any { r -> r.tableCells.any { c -> c.paragraphs.any { p -> p.runs.any { it.embeddedPictures.isNotEmpty() } } } }
            }
        assertEquals(2, photoTable.getRow(0).tableCells.count { c -> c.paragraphs.any { p -> p.runs.any { it.embeddedPictures.isNotEmpty() } } })
        val descr =
            photoTable
                .getRow(0)
                .getCell(0)
                .paragraphs
                .first()
                .runs
                .first {
                    it.embeddedPictures.isNotEmpty()
                }.ctr.drawingList
                .first()
                .inlineList
                .first()
                .docPr.descr
        assertEquals("Entrega", descr)
    }

    // #83: un mismo bloque repetido (la misma instancia en dos secciones) lleva una leyenda por aparición, no la última de las dos
    @Test
    fun `un mismo bloque repetido lleva una leyenda por aparicion`() {
        val shared = Block.Table("Localidades", listOf("Localidad"), listOf(listOf(Cell("Caserío Ejemplo"))))
        val model =
            ReportFixtures.model().copy(
                sections =
                    listOf(
                        ReportSection("uno", "Uno", "CALCULADA", listOf(shared)),
                        ReportSection("dos", "Dos", "CALCULADA", listOf(shared))
                    ),
                photos = emptyMap()
            )
        val bytes = writer.write(DocxWriter.Input(model, BaseTemplate.bytes, false, "Asociación Ejemplo", emptyMap()))
        val captions = XWPFDocument(ByteArrayInputStream(bytes)).paragraphs.map { it.text }.filter { it.startsWith("Tabla ") }
        assertEquals(listOf("Tabla 1. Localidades", "Tabla 2. Localidades"), captions)
    }

    @Test
    fun `EXTERNO no lleva el nombre de la persona ni las fotos sin consentimiento`() {
        val doc = write(Disclosure.EXTERNO, draft = true)
        assertFalse("Persona Ejemplo 1" in body(doc))
        assertTrue("Presidente, Comunidad Ejemplo" in body(doc))
        assertEquals(1, doc.allPictures.count { it.pictureType == org.apache.poi.xwpf.usermodel.Document.PICTURE_TYPE_JPEG })
    }

    private fun png(
        width: Int,
        height: Int
    ): ByteArray = ByteArrayOutputStream().also { ImageIO.write(BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", it) }.toByteArray()
}
