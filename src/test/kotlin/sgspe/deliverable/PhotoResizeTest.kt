package sgspe.deliverable

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.zip.CRC32
import javax.imageio.ImageIO

// revisión final del plan 8.2b, I3: las dimensiones se leen de la cabecera antes de decodificar; una imagen por encima del
// tope de megapíxeles no se decodifica, y una grande se decodifica submuestreada hacia max-side
class PhotoResizeTest {
    // un PNG con solo la cabecera IHDR (gris de 8 bits) y IEND: anuncia width × height píxeles sin traer ninguno
    private fun pngHeader(
        width: Int,
        height: Int
    ): ByteArray {
        val out = ByteArrayOutputStream()
        val data = DataOutputStream(out)
        data.write(byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10))

        fun chunk(
            type: String,
            body: ByteArray
        ) {
            data.writeInt(body.size)
            val bytes = type.toByteArray() + body
            data.write(bytes)
            data.writeInt(CRC32().also { it.update(bytes) }.value.toInt())
        }
        val ihdr = ByteArrayOutputStream()
        DataOutputStream(ihdr).apply {
            writeInt(width)
            writeInt(height)
            write(byteArrayOf(8, 0, 0, 0, 0))
        }
        chunk("IHDR", ihdr.toByteArray())
        chunk("IEND", ByteArray(0))
        return out.toByteArray()
    }

    @Test
    fun `una cabecera de 100 000 x 100 000 pixeles se rechaza sin decodificar`() {
        assertNull(PhotoResize.jpeg(pngHeader(100_000, 100_000), 2000, 85, 250))
        // también por debajo de Integer.MAX_VALUE bytes, donde decodificar entero agotaría la memoria
        assertNull(PhotoResize.jpeg(pngHeader(20_000, 20_000), 2000, 85, 250))
    }

    @Test
    fun `una foto grande se decodifica submuestreada y sale con el lado mayor de max-side`() {
        val image = BufferedImage(6000, 1500, BufferedImage.TYPE_INT_RGB)
        val png = ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray()
        val resized = PhotoResize.jpeg(png, 2000, 85, 250)!!
        assertEquals(2000, resized.width)
        assertEquals(500, resized.height)
        assertEquals(2000, ImageIO.read(resized.jpeg.inputStream()).width)
    }

    // menor del plan 8.2b: una imagen que ImageIO no termina de leer (cabecera sin datos, PNG cortado) bajo el tope de
    // megapíxeles sale «[Foto no disponible]» en lugar de tumbar el borrador con GENERATION_FAILED
    @Test
    fun `una imagen corrupta bajo el tope sale como no disponible`() {
        assertNull(PhotoResize.jpeg(pngHeader(100, 100), 2000, 85, 250))
        val png = ByteArrayOutputStream().also { ImageIO.write(BufferedImage(300, 200, BufferedImage.TYPE_INT_RGB), "png", it) }.toByteArray()
        assertNull(PhotoResize.jpeg(png.copyOf(png.size / 2), 2000, 85, 250))
    }
}
