package sgspe.deliverable

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import sgspe.privacy.Disclosure
import sgspe.privacy.ReportDisclosure
import java.time.Duration

// ADR-0031, deliverables.md §16.7 (etiqueta slides, en la CI): presentaciones sintéticas de T1 y T2 compuestas por sgspe-slides
// real. en la CI la URL es obligatoria (ci.yml la fija); fuera de ella, sin la URL la prueba se salta
@Tag("slides")
class SlidesPdfTest {
    private val converter: SlidesConverter by lazy {
        val url = System.getenv("SGSPE_DELIVERABLE_SLIDES_URL")?.ifBlank { null }
        if (url == null) {
            check(System.getenv("CI") != "true") { "la CI debe fijar SGSPE_DELIVERABLE_SLIDES_URL" }
            assumeTrue(false, "sin sgspe-slides (SGSPE_DELIVERABLE_SLIDES_URL)")
        }
        SlidesConverter(DeliverableProperties(slides = DeliverableProperties.Slides(url, Duration.ofSeconds(170))))
    }

    private fun plan(
        level: Disclosure,
        audience: Audience
    ): DeckSpec = DeckPlanner.plan(DeckPlanner.Input(ReportDisclosure.apply(DeckDemo.model(), level), level, audience))

    @Test
    fun `T1 para la direccion y para la comunidad salen con una pagina 16 9 por lamina, sus titulos y las fuentes de la clase`() {
        for ((level, audience) in listOf(Disclosure.INTERNO to Audience.DIRECCION, Disclosure.PUBLICO to Audience.COMUNIDAD)) {
            val deck = plan(level, audience)
            val rendered = runBlocking { converter.render(deck.bytes(), "prueba-slides") }
            assertEquals(emptyList<String>(), DeckChecks.problems(rendered.pdf, deck), audience.name)
            assertEquals(deck.slides.size, rendered.slides)
            assertTrue(rendered.renderer.orEmpty().startsWith("remotion@4.0."), rendered.renderer)
            assertEquals(DeckDesign.VERSION, rendered.design)
        }
    }

    @Test
    fun `T2 con su foto y su mapa sale completo`() {
        val (model, photos) = DeckDemo.t2()
        val deck = DeckPlanner.plan(DeckPlanner.Input(ReportDisclosure.apply(model, Disclosure.EXTERNO), Disclosure.EXTERNO, Audience.INSTITUCIONES, photos))
        assertFalse(deck.assets.isEmpty())
        val rendered = runBlocking { converter.render(deck.bytes(), "prueba-slides-t2") }
        assertEquals(emptyList<String>(), DeckChecks.problems(rendered.pdf, deck))
    }

    @Test
    fun `una lamina que no cabe falla con su codigo y no devuelve un PDF parcial`() {
        val deck = plan(Disclosure.EXTERNO, Audience.DIRECCION)
        val broken = deck.copy(slides = deck.slides.take(2) + deck.slides[2].copy(title = "Un título larguísimo ".repeat(7).trim().take(160)))
        val failure = assertThrows<SlideFailed> { runBlocking { converter.render(broken.bytes(), "prueba-slides-desborde") } }
        assertEquals("SLIDE_OVERFLOW", failure.code)
        assertEquals(deck.slides[2].id, failure.slide)
    }
}
