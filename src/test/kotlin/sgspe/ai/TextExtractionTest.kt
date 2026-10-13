package sgspe.ai

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import wasichai.core.common.WasichaiException

// el texto de una evidencia con PDFBox y POI, sin OCR (ai-architecture.md §15, ASM-132): un PDF con texto y un DOCX ficticios se
// leen; un PDF sin capa de texto, un archivo roto o de otro formato es 422 AI_NO_TEXT sobre target
class TextExtractionTest {
    private val extraction = TextExtraction()

    private fun noText(block: () -> Unit) {
        val refused = assertThrows<WasichaiException> { block() }
        assertEquals(422, refused.status.value())
        assertEquals("target" to "AI_NO_TEXT", refused.violations.single().let { it.field to it.message })
    }

    @Test
    fun `un PDF con texto se lee con sus tildes`() {
        val text = extraction.extract(TestDocuments.pdf("Acta de la asamblea", "Se acordó reparar el canal."), "application/pdf", "acta.pdf")
        assertTrue("Acta de la asamblea" in text, text)
        assertTrue("Se acordó reparar el canal." in text, text)
    }

    @Test
    fun `un DOCX se lee por sus parrafos`() {
        val text =
            extraction.extract(
                TestDocuments.docx("Convenio marco", "Cláusula 5: el proyecto contratará un vigía."),
                TextExtraction.DOCX,
                "convenio.docx"
            )
        assertEquals("Convenio marco\nCláusula 5: el proyecto contratará un vigía.", text)
    }

    @Test
    fun `el formato lo deciden los bytes, no el nombre`() {
        val text = extraction.extract(TestDocuments.pdf("Informe mensual"), "application/octet-stream", "informe.docx")
        assertTrue("Informe mensual" in text, text)
        noText { extraction.extract("no soy un PDF".toByteArray(), "application/pdf", "acta.pdf") }
    }

    // JOB-07 (plan 7.2c) la llama solo con los bytes y el tipo del archivo
    @Test
    fun `sin nombre de archivo, un DOCX se reconoce por su tipo`() {
        assertEquals("Informe mensual", extraction.extract(TestDocuments.docx("Informe mensual"), TextExtraction.DOCX))
    }

    @Test
    fun `un PDF sin capa de texto es AI_NO_TEXT`() {
        noText { extraction.extract(TestDocuments.scannedPdf(), "application/pdf", "escaneado.pdf") }
    }

    @Test
    fun `un PDF roto, un ZIP que no es Word y una imagen son AI_NO_TEXT`() {
        noText { extraction.extract("%PDF-1.4\n%archivo de prueba de sgspe\n".toByteArray(), "application/pdf", "prueba.pdf") }
        noText { extraction.extract(byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x00), TextExtraction.DOCX, "roto.docx") }
        noText { extraction.extract(byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x00), "application/zip", "fotos.zip") }
        noText { extraction.extract(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()), "image/jpeg", "foto.jpg") }
    }

    @Test
    fun `el texto sale normalizado`() {
        assertEquals("uno\n\ndos\ttres", TextExtraction.normalize("uno  \r\n\r\n\r\n\u0000dos\ttres\u0007  "))
    }
}
