package sgspe.deliverable

import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDResources
import org.apache.pdfbox.pdmodel.font.PDFont
import org.apache.pdfbox.pdmodel.font.PDType0Font
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject

// lo que acompaña al paquete de LaTeX: el id de correlación de la corrida. nada de esto viene del cliente
data class PdfRequest(
    val correlation: String?
)

// lo que devuelve sgspe-latex por un nivel: la candidata (PDF/A-2b, la que se emite) y el borrador, con la marca BORRADOR, y
// cuántas cajas desbordadas tuvo la composición (si el compositor lo dice)
class RenderedPdfs(
    val candidate: ByteArray,
    val draft: ByteArray,
    val overfull: Int? = null
)

// el compositor no respondió o no está configurado: el borrador queda FALLIDO con PDF_CONVERTER_UNAVAILABLE
class PdfConverterUnavailable(
    message: String
) : RuntimeException(message)

// sgspe-latex respondió, pero el paquete no compuso (422 LATEX_ERROR): un error de SGSPE, no del servicio. el borrador queda
// FALLIDO con GENERATION_FAILED, y el log lleva la línea de main.tex
class PdfCompositionFailed : RuntimeException("sgspe-latex no pudo componer el paquete")

// el PDF que devolvió el compositor pasa de sgspe.deliverable.max-file-bytes: el borrador queda FALLIDO con FILE_TOO_LARGE (#83)
class PdfTooLarge : RuntimeException("El PDF pasa del tope de los archivos de entregables")

// el puerto hacia sgspe-latex (ADR-0030, deliverables.md §7.7): solo JOB-05 lo llama, nunca una petición. LatexConverter es el
// de producción; las pruebas de integración usan un doble
fun interface PdfRenderer {
    suspend fun render(
        bundle: LatexWriter.Bundle,
        request: PdfRequest
    ): RenderedPdfs
}

// la verificación de un PDF con PDFBox (deliverables.md §7.1, §13.3): al menos una página, todas las fuentes (de las páginas y
// de sus formularios) incrustadas y de la lista de la clase (Source Serif 4 y Source Sans 3, ASM-141; sin el prefijo de
// subconjunto ni el estilo) y, en una candidata, la identificación PDF/A-2b en los metadatos XMP. lo que falla son descripciones, sin texto del documento;
// alguna hace que el borrador quede FALLIDO con PDF_CHECK_FAILED
object PdfChecks {
    val FONTS: Set<String> = setOf("SourceSerif4", "SourceSans3")
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
                    if (FONTS.none { base.startsWith(it) }) problems += "fuente fuera de la lista: $base"
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
