package sgspe.deliverable

import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDResources
import org.apache.pdfbox.pdmodel.font.PDFont
import org.apache.pdfbox.pdmodel.font.PDType0Font
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject

// lo que se pide al convertir un DOCX: PDF/A-2b en las candidatas (las que se emiten) y PDF común en los borradores, con
// título, código y revisión en los metadatos y el id de correlación de la corrida. nada de esto viene del cliente
data class PdfRequest(
    val pdfa: Boolean,
    val title: String,
    val code: String,
    val revision: String,
    val correlation: String?
)

// el conversor no respondió o no está configurado: el borrador queda FALLIDO con PDF_CONVERTER_UNAVAILABLE
class PdfConverterUnavailable(
    message: String
) : RuntimeException(message)

// el PDF que devolvió el conversor pasa de sgspe.deliverable.max-file-bytes: el borrador queda FALLIDO con FILE_TOO_LARGE (#83)
class PdfTooLarge : RuntimeException("El PDF pasa del tope de los archivos de entregables")

// el puerto hacia Gotenberg (especificación de F8 §2.2.17, deliverables.md §7.7): solo JOB-05 lo llama, nunca una petición.
// GotenbergConverter es el de producción (plan 8.2c); las pruebas de integración usan un doble
fun interface PdfConverter {
    suspend fun convert(
        docx: ByteArray,
        request: PdfRequest
    ): ByteArray
}

// la verificación de un PDF con PDFBox (deliverables.md §7.1, §13.3): al menos una página, todas las fuentes (de las páginas y
// de sus formularios) incrustadas y de la lista (Carlito, Caladea, Liberation Sans, Liberation Serif, sin el prefijo de subconjunto ni el estilo) y, en una
// candidata, la identificación PDF/A-2b en los metadatos XMP. lo que falla son descripciones, sin texto del documento;
// alguna hace que el borrador quede FALLIDO con PDF_CHECK_FAILED
object PdfChecks {
    private val ALLOWED = TemplateStyles.FONTS.map { it.replace(" ", "") }
    private val PART = Regex("""pdfaid:part(?:="2"|>2<)""")
    private val CONFORMANCE = Regex("""pdfaid:conformance(?:="B"|>B<)""")

    fun problems(
        pdf: ByteArray,
        pdfa: Boolean
    ): List<String> {
        val problems = mutableListOf<String>()
        try {
            Loader.loadPDF(pdf).use { doc ->
                if (doc.numberOfPages < 1) problems += "sin páginas"
                val fonts = mutableSetOf<PDFont>()
                for (page in doc.pages) page.resources?.let { collect(it, fonts, mutableSetOf()) }
                for (font in fonts) {
                    val base =
                        font.name
                            .orEmpty()
                            .substringAfter('+')
                            .substringBefore('-')
                            .substringBefore(',')
                    val embedded = if (font is PDType0Font) font.descendantFont.isEmbedded else font.isEmbedded
                    if (!embedded) problems += "fuente sin incrustar: $base"
                    if (ALLOWED.none { base.startsWith(it) }) problems += "fuente fuera de la lista: $base"
                }
                if (pdfa) {
                    val xmp =
                        doc.documentCatalog.metadata
                            ?.exportXMPMetadata()
                            ?.readAllBytes()
                            ?.toString(Charsets.UTF_8)
                            .orEmpty()
                    if (!PART.containsMatchIn(xmp) || !CONFORMANCE.containsMatchIn(xmp)) problems += "sin identificación PDF/A-2b"
                }
            }
        } catch (_: java.io.IOException) {
            problems += "no es un PDF legible"
        } catch (_: RuntimeException) {
            problems += "no es un PDF legible"
        } catch (_: StackOverflowError) {
            problems += "no es un PDF legible"
        }
        return problems.distinct()
    }

    // las fuentes de unos recursos y de sus formularios (XObject de forma), sin repetir uno ya visto
    private fun collect(
        resources: PDResources,
        fonts: MutableSet<PDFont>,
        seen: MutableSet<PDResources>
    ) {
        if (!seen.add(resources)) return
        for (name in resources.fontNames) resources.getFont(name)?.let { fonts += it }
        for (name in resources.xObjectNames) {
            val form = resources.getXObject(name) as? PDFormXObject ?: continue
            form.resources?.let { collect(it, fonts, seen) }
        }
    }
}
