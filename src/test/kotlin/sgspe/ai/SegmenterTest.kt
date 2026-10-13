package sgspe.ai

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// la segmentación de JOB-07 (ai-architecture.md §13.2): fragmentos de unos 1 500 caracteres con solape, cortados en un párrafo,
// una oración o una palabra, del mismo modo siempre
class SegmenterTest {
    private val sentence = "La asamblea de la Comunidad Ejemplo pidió reparar el canal de riego antes de la siembra. "

    @Test
    fun `un texto corto es un solo fragmento, normalizado`() {
        assertEquals(
            listOf("Acta de la asamblea.\n\nAcuerdos: reparar el canal."),
            Segmenter.segments("  Acta   de la\tasamblea.\r\n\r\n\r\n Acuerdos: reparar el canal.  ")
        )
    }

    @Test
    fun `un texto vacio o en blanco no da fragmentos`() {
        assertEquals(emptyList<String>(), Segmenter.segments(""))
        assertEquals(emptyList<String>(), Segmenter.segments(" \n\t \r\n "))
    }

    @Test
    fun `un texto largo se corta en oraciones, con solape y sin pasar el tamano`() {
        val text = sentence.repeat(60)
        val segments = Segmenter.segments(text)
        assertTrue(segments.size > 3, "${segments.size}")
        for (segment in segments) {
            assertTrue(segment.length <= Segmenter.SIZE, "${segment.length}")
            assertTrue(segment.endsWith("siembra."), segment.takeLast(30))
            assertTrue(segment.startsWith("La ") || segment.first().isLetter(), segment.take(30))
        }
        // el solape: el comienzo de cada fragmento ya estaba al final del anterior
        for (i in 1 until segments.size) {
            assertTrue(segments[i - 1].contains(segments[i].take(40)), "sin solape entre ${i - 1} y $i")
        }
    }

    @Test
    fun `prefiere el fin de un parrafo`() {
        val paragraph = "a".repeat(900) + ".\n\n" + "b ".repeat(400)
        val first = Segmenter.segments(paragraph).first()
        assertEquals("a".repeat(900) + ".", first)
    }

    @Test
    fun `una palabra mas larga que el tamano se corta en el limite y el texto avanza`() {
        val word = "x".repeat(4_000)
        val segments = Segmenter.segments(word)
        assertEquals(Segmenter.SIZE, segments.first().length)
        assertTrue(segments.size >= 3)
        assertTrue(segments.all { it.isNotEmpty() })
    }

    @Test
    fun `el mismo texto da los mismos fragmentos y la forma compuesta no cambia el resultado`() {
        val decomposed = "Ana" + " pidio" + " la reunión. ".repeat(200)
        val composed = "Ana" + " pidio" + " la reunión. ".repeat(200)
        assertEquals(Segmenter.segments(composed), Segmenter.segments(decomposed))
        assertEquals(Segmenter.segments(composed), Segmenter.segments(composed))
    }

    @Test
    fun `cada parte del texto queda en algun fragmento`() {
        val words = (1..900).joinToString(" ") { "palabra$it" }
        val segments = Segmenter.segments(words)
        for (i in 1..900) assertTrue(segments.any { "palabra$i " in "$it " }, "falta palabra$i")
    }
}
