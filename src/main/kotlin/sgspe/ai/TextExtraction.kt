package sgspe.ai

import org.apache.pdfbox.Loader
import org.apache.pdfbox.io.IOUtils
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.XWPFParagraph
import org.apache.poi.xwpf.usermodel.XWPFTable
import org.springframework.stereotype.Component
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.Writer
import java.util.zip.ZipInputStream

// el texto de un archivo de evidencia (ai-architecture.md §6.1, ASM-132): un PDF con capa de texto (PDFBox) o un DOCX (POI), sin
// OCR. el formato se decide por los primeros bytes («%PDF-» o un ZIP de Word), no por el nombre ni por el tipo que dijo quien lo
// subió. un PDF escaneado, cifrado o roto, un ZIP que no es un DOCX, cualquier otro formato o un texto en blanco es 422 AI_NO_TEXT,
// antes de llamar al modelo. el texto sale normalizado: saltos de línea \n, sin caracteres de control y sin más de una línea en
// blanco seguida. nunca lanza con el contenido en el mensaje. es un bean: la usan las corridas (RunSources) y JOB-07 (plan 7.2c,
// que la llama con los bytes y el tipo del archivo)
@Component
class TextExtraction {
    /**
     * El texto del archivo, acotado: más de [maxChars] caracteres (el sgspe.ai.limits.input-chars configurado, que pasa siempre
     * quien llama), más de [MAX_PAGES] páginas o un DOCX que se infla más allá de
     * [MAX_INFLATED] es 422 AI_INPUT_TOO_LARGE sobre target (nunca se trunca). Con [truncate] (solo JOB-07, que indexa el comienzo
     * de un documento largo) el texto que pasa [maxChars] o [MAX_PAGES] páginas se corta en el tope, sin partir un par sustituto,
     * y se devuelve, sin lanzar; una bomba de inflado de un DOCX sigue siendo 422. Trabaja con CPU y con E/S bloqueante: quien
     * la llame desde código reactivo (Tareas 7 y 9, JOB-07) debe hacerlo en Dispatchers.IO.
     */
    fun extract(
        bytes: ByteArray,
        contentType: String,
        fileName: String? = null,
        maxChars: Int,
        truncate: Boolean = false
    ): String {
        val raw =
            when {
                bytes.startsWith(PDF_MAGIC) -> pdf(bytes, maxChars, truncate)
                bytes.startsWith(ZIP_MAGIC) && docx(contentType, fileName) -> word(bytes, maxChars, truncate)
                else -> null
            } ?: throw AiErrors.noText()
        val text = normalize(raw)
        if (text.isBlank()) throw AiErrors.noText()
        if (text.length > maxChars) {
            if (truncate) return Segmenter.prefix(text, maxChars)
            throw AiErrors.inputTooLarge(AiErrors.TARGET, maxChars)
        }
        return text
    }

    companion object {
        const val DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        const val MAX_PAGES = 500
        const val MAX_INFLATED = 50L * 1024 * 1024
        private const val MAX_ENTRIES = 1_000
        private const val MAX_RATIO = 200L
        private const val RATIO_FLOOR = 1L * 1024 * 1024
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

        private class TooLarge : IOException()

        // escribe hasta el tope y corta la extracción en cuanto lo pasa
        private class CappedWriter(
            private val max: Int
        ) : Writer() {
            private val out = StringBuilder()

            override fun write(
                cbuf: CharArray,
                off: Int,
                len: Int
            ) {
                if (out.length + len > max * 2) throw TooLarge()
                out.append(cbuf, off, len)
            }

            override fun flush() = Unit

            override fun close() = Unit

            override fun toString() = out.toString()
        }

        private fun pdf(
            bytes: ByteArray,
            maxChars: Int,
            truncate: Boolean
        ): String? {
            var tooLarge = false
            val writer = CappedWriter(maxChars)
            val text =
                try {
                    Loader.loadPDF(bytes, "", null, null, IOUtils.createTempFileOnlyStreamCache()).use { document ->
                        if (document.numberOfPages > MAX_PAGES && !truncate) {
                            tooLarge = true
                            null
                        } else {
                            PDFTextStripper().apply { if (truncate) endPage = MAX_PAGES }.writeText(document, writer)
                            writer.toString()
                        }
                    }
                } catch (_: TooLarge) {
                    if (truncate) {
                        writer.toString()
                    } else {
                        tooLarge = true
                        null
                    }
                } catch (_: Exception) {
                    null
                }
            if (tooLarge) throw AiErrors.inputTooLarge(AiErrors.TARGET, maxChars)
            return text
        }

        // recorre el ZIP sin POI: demasiadas partes, demasiados bytes inflados o una razón de inflado extrema es una bomba
        private fun inflatesSafely(bytes: ByteArray): Boolean {
            var total = 0L
            var entries = 0
            val buffer = ByteArray(8192)
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                while (zip.nextEntry != null) {
                    if (++entries > MAX_ENTRIES) return false
                    while (true) {
                        val read = zip.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > MAX_INFLATED || (total > RATIO_FLOOR && total > bytes.size.toLong() * MAX_RATIO)) return false
                    }
                }
            }
            return true
        }

        private fun word(
            bytes: ByteArray,
            maxChars: Int,
            truncate: Boolean
        ): String? {
            val safe =
                try {
                    inflatesSafely(bytes)
                } catch (_: Exception) {
                    return null
                }
            if (!safe) throw AiErrors.inputTooLarge(AiErrors.TARGET, maxChars)
            var tooLarge = false
            val partial = StringBuilder()
            val text =
                try {
                    XWPFDocument(ByteArrayInputStream(bytes)).use { document ->
                        val out = partial

                        fun add(line: String) {
                            if (out.length + line.length > maxChars * 2) {
                                if (truncate) throw TooLarge()
                                tooLarge = true
                                throw TooLarge()
                            }
                            out.append(line).append('\n')
                        }
                        document.bodyElements.forEach { element ->
                            when (element) {
                                is XWPFParagraph -> add(element.text)
                                is XWPFTable -> element.rows.forEach { row -> add(row.tableCells.joinToString("\t") { it.text }) }
                            }
                        }
                        out.toString()
                    }
                } catch (_: TooLarge) {
                    if (truncate) partial.toString() else null
                } catch (_: Exception) {
                    null
                }
            if (tooLarge) throw AiErrors.inputTooLarge(AiErrors.TARGET, maxChars)
            return text
        }

        private fun ByteArray.startsWith(prefix: ByteArray): Boolean = size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }
    }
}
