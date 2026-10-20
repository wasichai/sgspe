package sgspe.deliverable

import org.apache.pdfbox.pdmodel.font.PDType0Font
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import sgspe.privacy.Disclosure
import sgspe.privacy.ReportDisclosure

// la verificación del PDF de una presentación (deliverables.md §16.7): páginas, tamaño, fuentes y títulos
class DeckChecksTest {
    private val deck =
        DeckPlanner.plan(DeckPlanner.Input(ReportDisclosure.apply(DeckDemo.model(), Disclosure.EXTERNO), Disclosure.EXTERNO, Audience.DIRECCION))
    private val spec = deck.bytes()

    @Test
    fun `un PDF con una pagina 16 9 por lamina, sus titulos y las fuentes de la clase pasa`() {
        assertEquals(emptyList<String>(), DeckChecks.problems(TestDecks.pdf(spec), deck))
    }

    @Test
    fun `una lamina de menos, otro tamano, un titulo que falta o una fuente ajena fallan`() {
        assertTrue(DeckChecks.problems(TestDecks.pdf(spec, pages = deck.slides.size - 1), deck).any { "páginas" in it })
        assertTrue(DeckChecks.problems(TestDecks.pdf(spec, width = 1224f, height = 792f), deck).any { "16:9" in it })
        assertTrue(DeckChecks.problems(TestDecks.pdf(spec, titles = false), deck).any { "título" in it })
        val liberation =
            TestDecks.pdf(
                spec
            ) { doc -> PDType0Font.load(doc, PdfChecks::class.java.getResourceAsStream("/org/apache/pdfbox/resources/ttf/LiberationSans-Regular.ttf")!!) }
        assertTrue(DeckChecks.problems(liberation, deck).any { "fuera de la lista" in it })
        assertEquals(listOf("no es un PDF legible"), DeckChecks.problems("no".toByteArray(), deck).filter { "legible" in it }.distinct())
    }
}
