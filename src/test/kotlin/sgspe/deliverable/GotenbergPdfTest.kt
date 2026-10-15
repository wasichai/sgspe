package sgspe.deliverable

import kotlinx.coroutines.runBlocking
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import sgspe.privacy.Disclosure
import sgspe.privacy.ReportDisclosure
import java.time.Duration

// especificación de F8 §5, fila «PDF» (etiqueta gotenberg, en la CI; plan 8.2c, Tarea 4): un T1 de prueba convertido por
// Gotenberg real. en la CI la URL es obligatoria (ci.yml la fija); fuera de ella, sin la URL la prueba se salta
@Tag("gotenberg")
class GotenbergPdfTest {
    private val url: String? = System.getenv("SGSPE_DELIVERABLE_GOTENBERG_URL")?.ifBlank { null }
    private val writer = DocxWriter(DeliverableProperties.Photos())

    private fun converter(): GotenbergConverter {
        if (url == null) {
            check(System.getenv("CI") != "true") { "la CI debe fijar SGSPE_DELIVERABLE_GOTENBERG_URL" }
            assumeTrue(false, "sin Gotenberg (SGSPE_DELIVERABLE_GOTENBERG_URL)")
        }
        return GotenbergConverter(DeliverableProperties(gotenberg = DeliverableProperties.Gotenberg(url, Duration.ofSeconds(180))))
    }

    private val model = ReportDisclosure.apply(T1Fixtures.model(), Disclosure.INTERNO)

    private fun docx(draft: Boolean): ByteArray = writer.write(DocxWriter.Input(model, BaseTemplate.bytes, draft, "Asociación Ejemplo", emptyMap()))

    private fun pdf(
        draft: Boolean,
        pdfa: Boolean
    ): ByteArray = runBlocking { converter().convert(docx(draft), PdfRequest(pdfa, model.meta.title, model.meta.code, "A", "prueba-gotenberg")) }

    private fun pages(pdf: ByteArray): List<String> =
        Loader.loadPDF(pdf).use { doc ->
            (1..doc.numberOfPages).map { page ->
                PDFTextStripper()
                    .apply {
                        startPage = page
                        endPage = page
                    }.getText(doc)
            }
        }

    private fun flat(text: String): String = text.replace(Regex("\\s+"), " ")

    @Test
    fun `el indice y las listas del PDF traen cada titulo y cada leyenda con su pagina`() {
        val text = flat(pages(pdf(draft = false, pdfa = false)).take(3).joinToString(" "))
        for (title in listOf("1. Capítulo de prueba 1", "2.1 Detalle del capítulo 2", "4. Riesgos sociales")) {
            assertTrue(Regex(Regex.escape(title) + "[ .]*\\d+").containsMatchIn(text), "$title sin página en el índice: $text")
        }
        assertTrue(Regex("Tabla 2\\.1\\. Localidades del capítulo 2\\s*\\d+").containsMatchIn(text), text)
        assertTrue(Regex("Figura 4\\.1\\. Matriz de probabilidad e impacto\\s*\\d+").containsMatchIn(text), text)
        assertTrue(DocxWriter.TOC_PLACEHOLDER !in text, "el índice no se actualizó")
    }

    @Test
    fun `el borrador lleva BORRADOR en cada pagina`() {
        val pages = pages(pdf(draft = true, pdfa = false))
        assertTrue(pages.size > 2, "${pages.size} páginas")
        pages.forEachIndexed { i, page -> assertTrue(DocxWriter.WATERMARK in page, "página ${i + 1} sin BORRADOR") }
    }

    @Test
    fun `la candidata es PDF-A-2b con solo fuentes permitidas`() {
        assertEquals(emptyList<String>(), PdfChecks.problems(pdf(draft = false, pdfa = true), pdfa = true))
        assertEquals(emptyList<String>(), PdfChecks.problems(pdf(draft = true, pdfa = false), pdfa = false))
    }

    @Test
    fun `el texto del PDF es el del DOCX`() {
        val text = flat(pages(pdf(draft = false, pdfa = false)).joinToString(" "))
        for (chapter in 1..3) {
            for (n in 1..12) assertTrue(T1Fixtures.sentence(chapter, n) in text, "falta: ${T1Fixtures.sentence(chapter, n)}")
            assertTrue("Se conversó con Persona Ejemplo 1 sobre el capítulo $chapter." in text)
        }
    }
}
