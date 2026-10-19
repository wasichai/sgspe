package sgspe.deliverable

import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

// la marca del PDF de una organización (ASM-140, deliverables.md §7.6): el logo, PNG o JPEG por sus bytes mágicos, de hasta
// 2 MB y 4000 px por lado (las dimensiones se leen de la cabecera, sin decodificar la imagen; nunca SVG), y el color de acento
// RRGGBB. lo que no cumple es una violación de la plantilla (LOGO_INVALID, ACCENT_INVALID). puro
object BrandLogo {
    const val LOGO_INVALID = "LOGO_INVALID"
    const val ACCENT_INVALID = "ACCENT_INVALID"
    const val MAX_BYTES = 2L * 1024 * 1024
    const val MAX_SIDE = 4000
    const val PNG = "image/png"
    const val JPEG = "image/jpeg"

    private val ACCENT = Regex("^[0-9A-Fa-f]{6}$")
    private val PNG_MAGIC = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte())

    // el tipo del logo por sus bytes mágicos, o null si no es PNG ni JPEG
    fun type(bytes: ByteArray): String? =
        when {
            bytes.size > 8 && bytes.copyOfRange(0, 4).contentEquals(PNG_MAGIC) -> PNG
            bytes.size > 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte() -> JPEG
            else -> null
        }

    fun problems(
        logo: ByteArray?,
        accent: String?
    ): List<TemplateFinding> {
        val out = mutableListOf<TemplateFinding>()
        if (logo != null) {
            val type = type(logo)
            when {
                logo.size > MAX_BYTES -> out += TemplateFinding(LOGO_INVALID, "el logo pasa de 2 MB")
                type == null -> out += TemplateFinding(LOGO_INVALID, "el logo no es PNG ni JPEG")
                else -> {
                    val size = dimensions(logo)
                    if (size == null) {
                        out += TemplateFinding(LOGO_INVALID, "el logo no se puede leer")
                    } else if (size.first > MAX_SIDE || size.second > MAX_SIDE || size.first < 1 || size.second < 1) {
                        out += TemplateFinding(LOGO_INVALID, "el logo pasa de $MAX_SIDE px por lado")
                    }
                }
            }
        }
        if (accent != null && !ACCENT.matches(accent)) out += TemplateFinding(ACCENT_INVALID, "el acento debe ser RRGGBB en hexadecimal")
        return out
    }

    // ancho y alto de la cabecera, sin decodificar los píxeles
    private fun dimensions(bytes: ByteArray): Pair<Int, Int>? =
        runCatching {
            ImageIO.createImageInputStream(ByteArrayInputStream(bytes)).use { stream ->
                val reader = ImageIO.getImageReaders(stream).asSequence().firstOrNull() ?: return@use null
                try {
                    reader.input = stream
                    reader.getWidth(0) to reader.getHeight(0)
                } finally {
                    reader.dispose()
                }
            }
        }.getOrNull()
}
