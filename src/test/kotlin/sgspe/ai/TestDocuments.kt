package sgspe.ai

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.io.ByteArrayOutputStream

// documentos ficticios para TextExtraction: un PDF con texto, un PDF sin capa de texto (como uno escaneado) y un DOCX
object TestDocuments {
    fun pdf(vararg lines: String): ByteArray =
        PDDocument().use { document ->
            val page = PDPage()
            document.addPage(page)
            PDPageContentStream(document, page).use { stream ->
                stream.beginText()
                stream.setFont(PDType1Font(Standard14Fonts.FontName.HELVETICA), 11f)
                stream.newLineAtOffset(72f, 720f)
                lines.forEach { line ->
                    stream.showText(line)
                    stream.newLineAtOffset(0f, -16f)
                }
                stream.endText()
            }
            ByteArrayOutputStream().also { document.save(it) }.toByteArray()
        }

    fun scannedPdf(): ByteArray =
        PDDocument().use { document ->
            document.addPage(PDPage())
            ByteArrayOutputStream().also { document.save(it) }.toByteArray()
        }

    fun docx(vararg paragraphs: String): ByteArray =
        XWPFDocument().use { document ->
            paragraphs.forEach { document.createParagraph().createRun().setText(it) }
            ByteArrayOutputStream().also { document.write(it) }.toByteArray()
        }
}
