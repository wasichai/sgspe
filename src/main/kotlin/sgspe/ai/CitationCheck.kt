package sgspe.ai

import java.text.Normalizer

// la cita de una propuesta tiene que estar, literal, en el texto seudonimizado que recibió el modelo (ai-architecture.md §6.1,
// especificación de F7 §2.2.8): así no hay evidencia inventada. se compara con los marcadores de la llamada ([PERSONA_1]),
// normalizando espacios y saltos de línea, la forma compuesta (NFC) y las comillas tipográficas; una cita de menos de MIN_CHARS
// caracteres no respalda nada y se descarta. `source` es la variable de la fuente, nunca el prompt entero: una cita de las
// instrucciones no vale
object CitationCheck {
    const val MIN_CHARS = 12
    private val SPACES = Regex("""\s+""")
    private const val QUOTES = "\"'«»“”‘’"

    fun found(
        quote: String?,
        source: String
    ): Boolean {
        val wanted = normalize(quote ?: return false).trim { it in QUOTES || it.isWhitespace() }
        if (wanted.length < MIN_CHARS) return false
        return wanted in normalize(source)
    }

    fun normalize(text: String): String =
        SPACES
            .replace(Normalizer.normalize(text, Normalizer.Form.NFC), " ")
            .map {
                when (it) {
                    '“', '”', '«', '»' -> '"'
                    '‘', '’' -> '\''
                    else -> it
                }
            }.joinToString("")
            .trim()
}
