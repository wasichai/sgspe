package sgspe.deliverable

import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam

// una foto reducida antes de entrar al DOCX (deliverables.md §7.5, RQ-REP-010): lado mayor de `maxSide` píxeles como máximo
// y JPEG de calidad `quality` (sgspe.deliverable.photos), sobre fondo blanco si traía transparencia. sin pantalla (Java 2D
// headless). las dimensiones se leen de la cabecera antes de decodificar: por encima de `maxMegapixels` no se decodifica (una
// foto de 200 MP o una bomba PNG agotarían la memoria de JOB-05), y por debajo se decodifica submuestreada hacia `maxSide`.
// null si los bytes no son una imagen que ImageIO lea o pasan el tope (la foto sale como no disponible)
object PhotoResize {
    data class Resized(
        val jpeg: ByteArray,
        val width: Int,
        val height: Int
    )

    fun jpeg(
        bytes: ByteArray,
        maxSide: Int,
        quality: Int,
        maxMegapixels: Int
    ): Resized? {
        val source = decode(bytes, maxSide, maxMegapixels) ?: return null
        val scale = minOf(1.0, maxSide.toDouble() / maxOf(source.width, source.height))
        val width = maxOf(1, (source.width * scale).toInt())
        val height = maxOf(1, (source.height * scale).toInt())
        val target = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g = target.createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            g.color = Color.WHITE
            g.fillRect(0, 0, width, height)
            g.drawImage(source, 0, 0, width, height, null)
        } finally {
            g.dispose()
        }
        val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
        val out = ByteArrayOutputStream()
        try {
            ImageIO.createImageOutputStream(out).use { stream ->
                writer.output = stream
                val param =
                    writer.defaultWriteParam.apply {
                        compressionMode = ImageWriteParam.MODE_EXPLICIT
                        compressionQuality = quality / 100f
                    }
                writer.write(null, IIOImage(target, null, null), param)
            }
        } finally {
            writer.dispose()
        }
        return Resized(out.toByteArray(), width, height)
    }

    // la imagen submuestreada por un factor entero que deja el lado mayor en maxSide o algo más (el escalado bicúbico termina
    // el trabajo); null si no hay lector, si la cabecera pasa el tope o si no trae imagen
    private fun decode(
        bytes: ByteArray,
        maxSide: Int,
        maxMegapixels: Int
    ): BufferedImage? {
        val stream = ImageIO.createImageInputStream(ByteArrayInputStream(bytes)) ?: return null
        stream.use {
            val readers = ImageIO.getImageReaders(stream)
            if (!readers.hasNext()) return null
            val reader = readers.next()
            try {
                reader.input = stream
                val width = reader.getWidth(0).toLong()
                val height = reader.getHeight(0).toLong()
                if (width <= 0 || height <= 0 || width * height > maxMegapixels * 1_000_000L) return null
                val factor = maxOf(1L, maxOf(width, height) / maxSide).toInt()
                val param = reader.defaultReadParam.apply { setSourceSubsampling(factor, factor, 0, 0) }
                return reader.read(0, param)
            } finally {
                reader.dispose()
            }
        }
    }
}
