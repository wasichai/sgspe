package sgspe.ai

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.encryption.AccessPermission
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import wasichai.core.common.WasichaiException
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

// el texto de una evidencia con PDFBox y POI, sin OCR (ai-architecture.md §15, ASM-132): un PDF con texto y un DOCX ficticios se
// leen; un PDF sin capa de texto, un archivo roto o de otro formato es 422 AI_NO_TEXT sobre target
class TextExtractionTest {
    private val extraction = TextExtraction()

    // el tope de application.yml (ASM-131): quien llama pasa siempre el configurado
    private val limit = AiLimits().inputChars

    private fun noText(block: () -> Unit) {
        val refused = assertThrows<WasichaiException> { block() }
        assertEquals(422, refused.status.value())
        assertEquals("target" to "AI_NO_TEXT", refused.violations.single().let { it.field to it.message })
    }

    @Test
    fun `un PDF con texto se lee con sus tildes`() {
        val text = extraction.extract(TestDocuments.pdf("Acta de la asamblea", "Se acordó reparar el canal."), "application/pdf", "acta.pdf", maxChars = limit)
        assertTrue("Acta de la asamblea" in text, text)
        assertTrue("Se acordó reparar el canal." in text, text)
    }

    @Test
    fun `un DOCX se lee por sus parrafos`() {
        val text =
            extraction.extract(
                TestDocuments.docx("Convenio marco", "Cláusula 5: el proyecto contratará un vigía."),
                TextExtraction.DOCX,
                "convenio.docx",
                maxChars = limit
            )
        assertEquals("Convenio marco\nCláusula 5: el proyecto contratará un vigía.", text)
    }

    @Test
    fun `el formato lo deciden los bytes, no el nombre`() {
        val text = extraction.extract(TestDocuments.pdf("Informe mensual"), "application/octet-stream", "informe.docx", maxChars = limit)
        assertTrue("Informe mensual" in text, text)
        noText { extraction.extract("no soy un PDF".toByteArray(), "application/pdf", "acta.pdf", maxChars = limit) }
    }

    // JOB-07 (plan 7.2c) la llama solo con los bytes y el tipo del archivo
    @Test
    fun `sin nombre de archivo, un DOCX se reconoce por su tipo`() {
        assertEquals("Informe mensual", extraction.extract(TestDocuments.docx("Informe mensual"), TextExtraction.DOCX, maxChars = limit))
    }

    @Test
    fun `un PDF sin capa de texto es AI_NO_TEXT`() {
        noText { extraction.extract(TestDocuments.scannedPdf(), "application/pdf", "escaneado.pdf", maxChars = limit) }
    }

    @Test
    fun `un PDF roto, un ZIP que no es Word y una imagen son AI_NO_TEXT`() {
        noText { extraction.extract("%PDF-1.4\n%archivo de prueba de sgspe\n".toByteArray(), "application/pdf", "prueba.pdf", maxChars = limit) }
        noText { extraction.extract(byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x00), TextExtraction.DOCX, "roto.docx", maxChars = limit) }
        noText { extraction.extract(byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x00), "application/zip", "fotos.zip", maxChars = limit) }
        noText { extraction.extract(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()), "image/jpeg", "foto.jpg", maxChars = limit) }
    }

    @Test
    fun `el texto sale normalizado`() {
        assertEquals("uno\n\ndos\ttres", TextExtraction.normalize("uno  \r\n\r\n\r\n\u0000dos\ttres\u0007  "))
    }

    private fun tooLarge(block: () -> Unit) {
        val refused = assertThrows<WasichaiException> { block() }
        assertEquals(422, refused.status.value())
        assertEquals("target" to "AI_INPUT_TOO_LARGE", refused.violations.single().let { it.field to it.message })
    }

    // reescribe una parte del DOCX con otro contenido
    private fun rewrite(
        docx: ByteArray,
        part: String,
        content: ByteArray
    ): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            ZipInputStream(ByteArrayInputStream(docx)).use { input ->
                generateSequence { input.nextEntry }.forEach { entry ->
                    zip.putNextEntry(ZipEntry(entry.name))
                    zip.write(if (entry.name == part) content else input.readBytes())
                    zip.closeEntry()
                }
            }
        }
        return out.toByteArray()
    }

    @Test
    fun `un PDF cifrado con clave es AI_NO_TEXT`() {
        val plain = TestDocuments.pdf("Acta reservada")
        val encrypted =
            org.apache.pdfbox.Loader.loadPDF(plain).use { document ->
                val policy = StandardProtectionPolicy("duena", "clave", AccessPermission())
                policy.encryptionKeyLength = 128
                document.protect(policy)
                ByteArrayOutputStream().also { document.save(it) }.toByteArray()
            }
        noText { extraction.extract(encrypted, "application/pdf", "reservada.pdf", maxChars = limit) }
    }

    @Test
    fun `un DOCX con una entidad externa no la expande`() {
        val secret = Files.createTempFile("sgspe-xxe", ".txt").also { Files.writeString(it, "SECRETO-XXE") }
        try {
            val xml =
                """<?xml version="1.0" encoding="UTF-8"?><!DOCTYPE d [<!ENTITY x SYSTEM "${secret.toUri()}">]>
                |<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body><w:p><w:r><w:t>hola &x;</w:t></w:r></w:p></w:body></w:document>
                """.trimMargin()
            val bytes = rewrite(TestDocuments.docx("base"), "word/document.xml", xml.toByteArray())
            val result = runCatching { extraction.extract(bytes, TextExtraction.DOCX, "xxe.docx", maxChars = limit) }
            assertFalse(result.getOrDefault("").contains("SECRETO-XXE"), result.toString())
        } finally {
            Files.deleteIfExists(secret)
        }
    }

    @Test
    fun `una bomba de compresion en un DOCX se rechaza`() {
        val bomb = rewrite(TestDocuments.docx("base"), "word/document.xml", ByteArray(60 * 1024 * 1024))
        assertTrue(bomb.size < 200_000, "la bomba pesa ${bomb.size}")
        tooLarge { extraction.extract(bomb, TextExtraction.DOCX, "bomba.docx", maxChars = limit) }
    }

    @Test
    fun `un PDF o un DOCX con mas texto que el tope es AI_INPUT_TOO_LARGE`() {
        val longLines = Array(40) { "x".repeat(90) }
        tooLarge { extraction.extract(TestDocuments.pdf(*longLines), "application/pdf", "largo.pdf", maxChars = 500) }
        tooLarge { extraction.extract(TestDocuments.docx(*longLines), TextExtraction.DOCX, "largo.docx", maxChars = 500) }
        assertTrue(extraction.extract(TestDocuments.pdf("corto"), "application/pdf", maxChars = 500).isNotEmpty())
    }

    @Test
    fun `un PDF con demasiadas paginas es AI_INPUT_TOO_LARGE`() {
        val many =
            PDDocument().use { document ->
                repeat(TextExtraction.MAX_PAGES + 1) {
                    document.addPage(
                        org.apache.pdfbox.pdmodel
                            .PDPage()
                    )
                }
                ByteArrayOutputStream().also { document.save(it) }.toByteArray()
            }
        tooLarge { extraction.extract(many, "application/pdf", "paginas.pdf", maxChars = limit) }
    }

    @Test
    fun `con truncate un texto largo se corta en el tope y sin truncate sigue lanzando`() {
        val longLines = Array(40) { "x".repeat(90) }
        for ((bytes, type) in listOf(TestDocuments.pdf(*longLines) to "application/pdf", TestDocuments.docx(*longLines) to TextExtraction.DOCX)) {
            val text = extraction.extract(bytes, type, maxChars = 500, truncate = true)
            assertEquals(500, text.length)
            assertTrue(text.startsWith("xxxx"), text)
            tooLarge { extraction.extract(bytes, type, maxChars = 500) }
        }
    }
}
