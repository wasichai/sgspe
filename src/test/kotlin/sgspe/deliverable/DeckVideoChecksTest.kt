package sgspe.deliverable

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// la verificación del MP4 de una presentación (deliverables.md §16.2): que sea un MP4 y que dure 8 s por lámina
class DeckVideoChecksTest {
    @Test
    fun `un MP4 con 8 s por lamina pasa`() {
        assertEquals(emptyList<String>(), DeckVideoChecks.problems(TestVideos.mp4(14), 14))
    }

    @Test
    fun `una duracion distinta, otro archivo o un MP4 cortado fallan`() {
        assertTrue(DeckVideoChecks.problems(TestVideos.mp4(14, seconds = 42), 14).any { "dura" in it })
        assertTrue(DeckVideoChecks.problems(TestVideos.mp4(13), 14).any { "dura" in it })
        assertEquals(listOf("no es un MP4"), DeckVideoChecks.problems("%PDF-1.7".toByteArray(), 14))
        assertEquals(listOf("no es un MP4"), DeckVideoChecks.problems(TestVideos.mp4(14, brand = "qt  "), 14))
        assertEquals(listOf("no trae su duración"), DeckVideoChecks.problems(TestVideos.mp4(14).copyOf(40), 14))
    }

    @Test
    fun `los fotogramas salen de la escala y la duracion de mvhd`() {
        assertEquals(240, DeckVideoChecks.frames(TestVideos.mp4(1)))
        assertEquals(null, DeckVideoChecks.frames(ByteArray(3)))
    }
}
