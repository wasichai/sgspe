package sgspe.ai

import java.text.Normalizer

// el texto de una evidencia en fragmentos para el RAG (ai-architecture.md §13.2, especificación de F7 §3.5): de unos `size`
// caracteres con `overlap` de solape, cortados donde termina un párrafo, una oración o al menos una palabra, nunca en medio de
// una salvo que no haya otro lugar. el texto se normaliza antes (NFC, sin espacios repetidos ni más de una línea en blanco), así
// que el mismo documento da siempre los mismos fragmentos. Kotlin puro
object Segmenter {
    const val SIZE = 1_500
    const val OVERLAP = 200

    private val SPACES = Regex("""[ \t\u00a0\f\x0B]+""")
    private val BLANK_LINES = Regex("""\n[ \t]*(\n[ \t]*)+""")
    private val SENTENCE_END = Regex("""[.!?;:][)"'»]*\s""")

    fun normalize(text: String): String =
        Normalizer
            .normalize(text, Normalizer.Form.NFC)
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .replace(SPACES, " ")
            .replace(BLANK_LINES, "\n\n")
            .lines()
            .joinToString("\n") { it.trim() }
            .trim()

    fun segments(
        text: String,
        size: Int = SIZE,
        overlap: Int = OVERLAP
    ): List<String> {
        require(size > 0 && overlap in 0 until size / 2) { "segmentos de $size caracteres con $overlap de solape" }
        val clean = normalize(text)
        if (clean.isEmpty()) return emptyList()
        if (clean.length <= size) return listOf(clean)
        val out = mutableListOf<String>()
        var start = 0
        while (start < clean.length) {
            val limit = minOf(start + size, clean.length)
            val end = if (limit == clean.length) limit else cut(clean, start, limit, size)
            clean
                .substring(start, end)
                .trim()
                .takeIf { it.isNotEmpty() }
                ?.let { out += it }
            if (end >= clean.length) break
            start = next(clean, maxOf(end - overlap, start + 1), end)
        }
        return out
    }

    // el mejor corte en la segunda mitad del tramo: fin de párrafo, fin de oración, un espacio o, sin ninguno, el límite
    private fun cut(
        text: String,
        start: Int,
        limit: Int,
        size: Int
    ): Int {
        val from = start + size / 2
        val window = text.substring(from, limit)
        window.lastIndexOf("\n\n").takeIf { it >= 0 }?.let { return from + it + 2 }
        SENTENCE_END.findAll(window).lastOrNull()?.let { return from + it.range.last + 1 }
        window.indexOfLast { it.isWhitespace() }.takeIf { it >= 0 }?.let { return from + it + 1 }
        return limit
    }

    // el comienzo del fragmento siguiente: el solape empieza en una palabra entera, sin pasar el corte anterior
    private fun next(
        text: String,
        candidate: Int,
        end: Int
    ): Int {
        if (candidate == 0 || text[candidate - 1].isWhitespace()) return candidate
        val space = (candidate until end).firstOrNull { text[it].isWhitespace() } ?: return candidate
        return space + 1
    }
}
