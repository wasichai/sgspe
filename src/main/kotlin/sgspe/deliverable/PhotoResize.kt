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
// headless). null si los bytes no son una imagen que ImageIO lea
object PhotoResize {
    data class Resized(
        val jpeg: ByteArray,
        val width: Int,
        val height: Int
    )

    fun jpeg(
        bytes: ByteArray,
        maxSide: Int,
        quality: Int
    ): Resized? {
        val source = ImageIO.read(ByteArrayInputStream(bytes)) ?: return null
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
}
