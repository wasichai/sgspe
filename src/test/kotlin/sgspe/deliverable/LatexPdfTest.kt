package sgspe.deliverable

import kotlinx.coroutines.runBlocking
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.XWPFParagraph
import org.apache.poi.xwpf.usermodel.XWPFTable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import sgspe.privacy.Disclosure
import sgspe.privacy.ReportDisclosure
import java.io.ByteArrayInputStream
import java.time.Duration

// ADR-0030, deliverables.md §13.3, fila «PDF» (etiqueta latex, en la CI): un T1 de prueba compuesto por sgspe-latex real. en la
// CI la URL es obligatoria (ci.yml la fija); fuera de ella, sin la URL la prueba se salta. una sola composición para todas
@Tag("latex")
class LatexPdfTest {
    // el texto de cada página sin su encabezado ni su pie (la primera y la última línea), salvo en la portada
    private fun body(pdf: ByteArray): List<String> =
        texts(pdf).mapIndexed { i, page ->
            val lines = page.lines().filter { it.isNotBlank() }
            (if (i == 0) lines else lines.drop(1).dropLast(1)).joinToString("\n")
        }

    private fun texts(pdf: ByteArray): List<String> =
        Loader.loadPDF(pdf).use { doc ->
            (1..doc.numberOfPages).map { page ->
                PDFTextStripper()
                    .apply {
                        startPage = page
                        endPage = page
                    }.getText(doc)
            }
        }

    // las letras de una página sin la revisión, que en el borrador es su letra y en la candidata la de emisión
    private fun letters(text: String): Map<Char, Int> =
        text
            .replace(Regex("Rev\\.\\s*\\S+"), "")
            .filterNot { it.isWhitespace() }
            .groupingBy { it }
            .eachCount()

    // los cortes de palabra con guion se unen y los espacios se normalizan
    private fun flat(text: String): String = text.replace(Regex("(\\p{Ll})-\\s+(\\p{Ll})"), "$1$2").replace(Regex("\\s+"), " ").trim()

    // la entrada `title` seguida de su página: un entero que es una página del documento
    private fun paged(
        part: String,
        title: String,
        pageCount: Int
    ): Boolean = Regex(Regex.escape(title) + "[ .]*(\\d+)(?![.\\d])").findAll(part).any { it.groupValues[1].toInt() in 1..pageCount }

    @Test
    fun `el indice y las listas del PDF traen cada titulo y cada leyenda con su pagina`() {
        val pages = body(rendered().candidate)
        val text = flat(pages.take(6).joinToString(" "))
        // el índice nombra también las dos listas: sus títulos son los que siguen a esas entradas
        val tables = text.indexOf("Lista de tablas", text.indexOf("Lista de figuras") + 1)
        val figures = text.indexOf("Lista de figuras", tables + 1)
        assertTrue(tables >= 0 && figures > tables, text)
        val toc = text.substring(0, tables)
        for (title in listOf("1. Capítulo de prueba 1", "2.1 Detalle del capítulo 2", "4. Riesgos sociales")) {
            assertTrue(paged(toc, title, pages.size), "$title sin página en el índice: $toc")
        }
        assertTrue(paged(text.substring(tables, figures), "2.1 Localidades del capítulo 2", pages.size), text)
        assertTrue(paged(text.substring(figures), "4.1 Matriz de probabilidad e impacto", pages.size), text)
        // el control de la regex: un título que no está en el documento no se halla
        assertFalse(paged(toc, "9. Capítulo que no existe", pages.size))
    }

    @Test
    fun `el borrador lleva BORRADOR en cada pagina y la candidata en ninguna, con las mismas paginas`() {
        val draft = texts(rendered().draft)
        val candidate = texts(rendered().candidate)
        assertTrue(draft.size > 2, "${draft.size} páginas")
        assertEquals(candidate.size, draft.size, "la marca no cambia la paginación")
        // la marca va en diagonal y la extracción puede partirla: lo que el borrador tiene de más en cada página son justo sus
        // letras
        val mark = letters(DocxWriter.WATERMARK)
        draft.indices.forEach { i ->
            val extra = letters(draft[i]).mapValues { (c, n) -> n - (letters(candidate[i])[c] ?: 0) }.filterValues { it != 0 }
            assertEquals(mark, extra, "página ${i + 1}")
        }
        candidate.forEachIndexed { i, page -> assertFalse(DocxWriter.WATERMARK in page, "página ${i + 1} con BORRADOR") }
    }

    @Test
    fun `la candidata es PDF-A-2b con solo las fuentes de la clase y sin cajas desbordadas`() {
        assertEquals(emptyList<String>(), PdfChecks.problems(rendered().candidate, pdfa = true))
        assertEquals(emptyList<String>(), PdfChecks.problems(rendered().draft, pdfa = false))
        assertEquals(0, rendered().overfull)
    }

    @Test
    fun `el PDF tiene el contenido del DOCX en el mismo orden`() {
        val text = flat(body(rendered().candidate).joinToString(" "))
        // títulos, leyendas y celdas de las tablas del DOCX, en su orden
        val doc = docx()
        val items = mutableListOf<String>()
        for (element in doc.bodyElements) {
            when (element) {
                is XWPFParagraph -> {
                    val style =
                        element.style
                            ?.let {
                                doc.styles
                                    .getStyle(it)
                                    ?.name
                                    ?.lowercase()
                            }.orEmpty()
                    if ((style.startsWith("heading") || style == "caption") && element.text.isNotBlank()) items += element.text.trim()
                }
                is XWPFTable ->
                    element.rows.forEach { row ->
                        row.tableCells.forEach { cell ->
                            cell.text
                                .trim()
                                .takeIf { it.isNotEmpty() }
                                ?.let { items += it }
                        }
                    }
            }
        }
        assertTrue(items.size > 30, "${items.size} elementos")
        // después del índice y de las listas, que repiten títulos y leyendas
        var at = text.indexOf(T1Fixtures.sentence(1, 1)).also { assertTrue(it > 0, text) }.let { text.lastIndexOf("1. Capítulo de prueba 1", it) }
        for (item in items.dropWhile { it != "1. Capítulo de prueba 1" }) {
            val found = text.indexOf(flat(item), at)
            assertTrue(found >= 0, "falta o fuera de orden: «$item» después de «${text.substring(maxOf(0, at - 80), at)}»")
            at = found
        }
        for (chapter in 1..3) {
            for (n in 1..12) assertTrue(T1Fixtures.sentence(chapter, n) in text, "falta: ${T1Fixtures.sentence(chapter, n)}")
            assertTrue("Se conversó con Persona Ejemplo 1 sobre el capítulo $chapter." in text)
        }
    }

    private companion object {
        val model = ReportDisclosure.apply(T1Fixtures.model(), Disclosure.INTERNO)

        private var cached: RenderedPdfs? = null

        // una sola composición para la clase: tarda decenas de segundos
        @Synchronized
        fun rendered(): RenderedPdfs {
            cached?.let { return it }
            val url = System.getenv("SGSPE_DELIVERABLE_LATEX_URL")?.ifBlank { null }
            if (url == null) {
                check(System.getenv("CI") != "true") { "la CI debe fijar SGSPE_DELIVERABLE_LATEX_URL" }
                assumeTrue(false, "sin sgspe-latex (SGSPE_DELIVERABLE_LATEX_URL)")
            }
            val converter = LatexConverter(DeliverableProperties(latex = DeliverableProperties.Latex(url, Duration.ofSeconds(180))))
            val bundle = LatexWriter(DeliverableProperties.Photos()).write(LatexWriter.Input(model, "Asociación Ejemplo", emptyMap()))
            return runBlocking { converter.render(bundle, PdfRequest("prueba-latex")) }.also { cached = it }
        }

        fun docx(): XWPFDocument =
            XWPFDocument(
                ByteArrayInputStream(
                    DocxWriter(DeliverableProperties.Photos()).write(DocxWriter.Input(model, BaseTemplate.bytes, false, "Asociación Ejemplo", emptyMap()))
                )
            )
    }
}
