package sgspe.deliverable

import java.nio.ByteBuffer

// la verificación del MP4 de una presentación (ASM-148, deliverables.md §16.2): que sea un MP4 (la caja `ftyp` al principio,
// con una marca ISO) y que dure lo que dicen sus láminas, 3 s de animación y 5 s quieta cada una a 30 fotogramas por segundo,
// los mismos números de slides/src/timing.ts. la duración sale de `mvhd`, dentro de `moov`. lo que falla son descripciones
// sin texto del documento; alguna deja el video FALLIDO con DECK_CHECK_FAILED
object DeckVideoChecks {
    const val FPS = 30
    const val FRAMES_PER_SLIDE = 240

    private val BRANDS = setOf("isom", "iso2", "mp41", "mp42", "avc1")

    fun problems(
        mp4: ByteArray,
        slides: Int
    ): List<String> {
        if (mp4.size < 12 || type(mp4, 4) != "ftyp" || type(mp4, 8) !in BRANDS) return listOf("no es un MP4")
        val frames = frames(mp4) ?: return listOf("no trae su duración")
        val expected = slides * FRAMES_PER_SLIDE
        return if (frames == expected) emptyList() else listOf("dura $frames fotogramas para $slides láminas ($expected)")
    }

    // los fotogramas del video: la duración de `mvhd` en su escala, por FPS; null si no se puede leer
    fun frames(mp4: ByteArray): Int? {
        val buffer = ByteBuffer.wrap(mp4)
        val moov = box(buffer, 0, mp4.size, "moov") ?: return null
        val mvhd = box(buffer, moov + 8, moov + buffer.getInt(moov), "mvhd") ?: return null
        val version = mp4[mvhd + 8].toInt()
        val end = mvhd + if (version == 1) 40 else 28
        if (end > mp4.size) return null
        val timescale = Integer.toUnsignedLong(buffer.getInt(mvhd + if (version == 1) 28 else 20))
        val duration = if (version == 1) buffer.getLong(mvhd + 32) else Integer.toUnsignedLong(buffer.getInt(mvhd + 24))
        if (timescale == 0L || duration < 0) return null
        return Math.round(duration.toDouble() * FPS / timescale).toInt()
    }

    // la posición de la primera caja `type` entre `from` y `to`; null si no está o si una caja no cabe
    private fun box(
        buffer: ByteBuffer,
        from: Int,
        to: Int,
        type: String
    ): Int? {
        var at = from
        while (at + 8 <= to) {
            val size = buffer.getInt(at)
            if (size < 8 || size > to - at) return null
            if (type(buffer.array(), at + 4) == type) return at
            at += size
        }
        return null
    }

    private fun type(
        bytes: ByteArray,
        at: Int
    ): String = String(bytes, at, 4, Charsets.ISO_8859_1)
}
