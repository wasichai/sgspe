package sgspe.deliverable

import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import java.text.Normalizer

// la verificación del PDF de una presentación con PDFBox (deliverables.md §16.7): una página por lámina, todas de 1440 × 810
// pt (16:9), las fuentes incrustadas y de la lista de la clase (PdfChecks, sin PDF/A) y, en orden, el comienzo del título de
// cada lámina de contenido en su página. lo que falla son descripciones sin texto del documento; alguna deja la presentación
// FALLIDO con DECK_CHECK_FAILED
object DeckChecks {
    private const val WIDTH = 1440f
    private const val HEIGHT = 810f

    fun problems(
        pdf: ByteArray,
        deck: DeckSpec
    ): List<String> {
        val problems = PdfChecks.problems(pdf, false).toMutableList()
        try {
            Loader.loadPDF(pdf).use { doc ->
                if (doc.numberOfPages != deck.slides.size) problems += "${doc.numberOfPages} páginas para ${deck.slides.size} láminas"
                doc.pages.forEachIndexed { i, page ->
                    val box = page.mediaBox
                    if (kotlin.math.abs(box.width - WIDTH) > 1 || kotlin.math.abs(box.height - HEIGHT) > 1) problems += "la página ${i + 1} no es 16:9"
                }
                val stripper = PDFTextStripper()
                deck.slides.forEachIndexed { i, slide ->
                    if (i >= doc.numberOfPages) return@forEachIndexed
                    stripper.startPage = i + 1
                    stripper.endPage = i + 1
                    val text = squash(stripper.getText(doc))
                    // el comienzo del título: el texto extraído parte las líneas donde las partió el navegador
                    val start = squash(slide.title).take(TITLE_PREFIX)
                    if (start.isNotEmpty() && start !in text) problems += "la página ${i + 1} no lleva el título de su lámina"
                }
            }
        } catch (_: java.io.IOException) {
            problems += "no es un PDF legible"
        } catch (_: RuntimeException) {
            problems += "no es un PDF legible"
        }
        return problems.distinct()
    }

    // sin espacios, guiones de corte ni saltos, y con las ligaduras («fi») separadas: lo que PDFBox extrae y lo que dice la
    // especificación se comparan así
    private fun squash(text: String): String = text.replace(Regex("""[\s­  ]+"""), "")

    private const val TITLE_PREFIX = 24
}
