package sgspe.ai

import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.poi.xwpf.extractor.XWPFWordExtractor
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.springframework.stereotype.Component
import java.io.ByteArrayInputStream

// el texto de un archivo de evidencia (ai-architecture.md §6.1, ASM-132): un PDF con capa de texto (PDFBox) o un DOCX (POI), sin
// OCR. el formato se decide por los primeros bytes («%PDF-» o un ZIP de Word), no por el nombre ni por el tipo que dijo quien lo
// subió. un PDF escaneado, cifrado o roto, un ZIP que no es un DOCX, cualquier otro formato o un texto en blanco es 422 AI_NO_TEXT,
// antes de llamar al modelo. el texto sale normalizado: saltos de línea \n, sin caracteres de control y sin más de una línea en
// blanco seguida. nunca lanza con el contenido en el mensaje. es un bean: la usan las corridas (RunSources) y JOB-07 (plan 7.2c,
// que la llama con los bytes y el tipo del archivo)
@Component
class TextExtraction {
    fun extract(
        bytes: ByteArray,
        contentType: String,
        fileName: String? = null
    ): String {
        val raw =
            when {
                bytes.startsWith(PDF_MAGIC) -> pdf(bytes)
                bytes.startsWith(ZIP_MAGIC) && docx(contentType, fileName) -> word(bytes)
                else -> null
            } ?: throw AiErrors.noText()
        val text = normalize(raw)
        if (text.isBlank()) throw AiErrors.noText()
        return text
    }

    companion object {
        const val DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        private val PDF_MAGIC = "%PDF-".toByteArray(Charsets.US_ASCII)
        private val ZIP_MAGIC = byteArrayOf(0x50, 0x4B, 0x03, 0x04)
        private val BLANK_LINES = Regex("""\n{3,}""")
        private val TRAILING = Regex("""[ \t]+\n""")

        // saltos de línea \n, sin caracteres de control (salvo \n y \t), sin espacios al final de una línea y a lo sumo una línea en
        // blanco seguida
        fun normalize(text: String): String {
            val unified = text.replace("\r\n", "\n").replace('\r', '\n')
            val clean = unified.filter { it == '\n' || it == '\t' || !it.isISOControl() }
            return BLANK_LINES.replace(TRAILING.replace(clean, "\n"), "\n\n").trim()
        }

        private fun docx(
            contentType: String,
            fileName: String?
        ): Boolean = contentType.substringBefore(';').trim() == DOCX || fileName?.lowercase()?.endsWith(".docx") == true

        private fun pdf(bytes: ByteArray): String? =
            try {
                Loader.loadPDF(bytes).use { document -> PDFTextStripper().getText(document) }
            } catch (_: Exception) {
                null
            }

        private fun word(bytes: ByteArray): String? =
            try {
                XWPFDocument(ByteArrayInputStream(bytes)).use { document -> XWPFWordExtractor(document).use { it.text } }
            } catch (_: Exception) {
                null
            }

        private fun ByteArray.startsWith(prefix: ByteArray): Boolean = size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }
    }
}
