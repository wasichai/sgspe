package sgspe.deliverable

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDMetadata
import org.apache.pdfbox.pdmodel.font.PDFont
import org.apache.pdfbox.pdmodel.font.PDType0Font
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream

// deliverables.md §7.1 y §13.3: fuentes incrustadas de la lista, páginas y PDF/A-2b en la candidata
class PdfChecksTest {
    @Test
    fun `un PDF con Liberation Sans incrustada pasa, y con la identificacion PDF-A pasa como candidata`() {
        assertEquals(emptyList<String>(), PdfChecks.problems(TestPdfs.liberation(pdfa = false), pdfa = false))
        assertEquals(emptyList<String>(), PdfChecks.problems(TestPdfs.liberation(pdfa = true), pdfa = true))
        assertTrue(PdfChecks.problems(TestPdfs.liberation(pdfa = false), pdfa = true).any { "PDF/A-2b" in it })
    }

    @Test
    fun `una fuente estandar sin incrustar o fuera de la lista falla, y lo que no es PDF tambien`() {
        val problems = PdfChecks.problems(TestPdfs.helvetica(), pdfa = false)
        assertTrue(problems.any { it.startsWith("fuente sin incrustar") }, "$problems")
        assertTrue(problems.any { it.startsWith("fuente fuera de la lista") }, "$problems")
        assertEquals(listOf("no es un PDF legible"), PdfChecks.problems("hola".toByteArray(), pdfa = false))
    }

    @Test
    fun `un PDF cortado o con basura tras la cabecera es un problema y no una excepcion`() {
        val valid = TestPdfs.liberation(pdfa = false)
        val truncated = valid.copyOf(valid.size / 2)
        val garbage = "%PDF-1.7\n\u0000\u0001 basura 1 0 obj << /Pages 9 0 R >> endobj".toByteArray()
        for (bytes in listOf(truncated, garbage)) {
            assertTrue(PdfChecks.problems(bytes, pdfa = false).isNotEmpty())
        }
    }
}

// PDF de prueba con PDFBox: Liberation Sans viene en el jar de PDFBox (su respaldo de fuentes), con licencia OFL
object TestPdfs {
    fun liberation(
        pdfa: Boolean,
        text: String = "Informe de campo y avance"
    ): ByteArray =
        build(
            pdfa,
            text
        ) { doc -> PDType0Font.load(doc, PdfChecks::class.java.getResourceAsStream("/org/apache/pdfbox/resources/ttf/LiberationSans-Regular.ttf")!!) }

    fun helvetica(): ByteArray = build(false, "texto") { PDType1Font(Standard14Fonts.FontName.HELVETICA) }

    private fun build(
        pdfa: Boolean,
        text: String,
        font: (PDDocument) -> PDFont
    ): ByteArray =
        PDDocument().use { doc ->
            val page = PDPage()
            doc.addPage(page)
            PDPageContentStream(doc, page).use { stream ->
                stream.beginText()
                stream.setFont(font(doc), 12f)
                stream.newLineAtOffset(72f, 700f)
                stream.showText(text)
                stream.endText()
            }
            if (pdfa) {
                val xmp =
                    """<?xpacket begin="" id="W5M0MpCehiHzreSzNTczkc9d"?><x:xmpmeta xmlns:x="adobe:ns:meta/">""" +
                        """<rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">""" +
                        """<rdf:Description rdf:about="" xmlns:pdfaid="http://www.aiim.org/pdfa/ns/id/" pdfaid:part="2" pdfaid:conformance="B"/></rdf:RDF></x:xmpmeta><?xpacket end="w"?>"""
                doc.documentCatalog.metadata = PDMetadata(doc).apply { importXMPMetadata(xmp.toByteArray()) }
            }
            ByteArrayOutputStream().also { doc.save(it) }.toByteArray()
        }
}
