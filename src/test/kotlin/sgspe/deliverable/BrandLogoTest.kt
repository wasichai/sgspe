package sgspe.deliverable

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

// ASM-140: el logo y el acento de la marca del PDF
class BrandLogoTest {
    private fun image(
        format: String,
        width: Int,
        height: Int
    ): ByteArray = ByteArrayOutputStream().also { ImageIO.write(BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), format, it) }.toByteArray()

    private fun codes(
        logo: ByteArray?,
        accent: String?
    ) = BrandLogo.problems(logo, accent).map { it.code }

    @Test
    fun `un PNG o un JPEG chico y un acento RRGGBB pasan`() {
        assertEquals(emptyList<String>(), codes(image("png", 400, 120), "1f4e79"))
        assertEquals(emptyList<String>(), codes(image("jpg", 400, 120), null))
        assertEquals(BrandLogo.PNG, BrandLogo.type(image("png", 2, 2)))
        assertEquals(BrandLogo.JPEG, BrandLogo.type(image("jpg", 2, 2)))
        assertEquals(emptyList<String>(), codes(null, null))
    }

    @Test
    fun `un SVG, un GIF, un archivo roto o enorme y un acento mal escrito no pasan`() {
        assertEquals(listOf(BrandLogo.LOGO_INVALID), codes("<svg xmlns=\"http://www.w3.org/2000/svg\"/>".toByteArray(), null))
        assertEquals(listOf(BrandLogo.LOGO_INVALID), codes(image("gif", 10, 10), null))
        assertEquals(listOf(BrandLogo.LOGO_INVALID), codes(image("png", 10, 10).copyOf(20), null))
        assertEquals(listOf(BrandLogo.LOGO_INVALID), codes(image("png", 4001, 10), null))
        assertEquals(listOf(BrandLogo.LOGO_INVALID), codes(ByteArray(BrandLogo.MAX_BYTES.toInt() + 1), null))
        assertEquals(listOf(BrandLogo.ACCENT_INVALID), codes(null, "#1F4E79"))
        assertEquals(listOf(BrandLogo.ACCENT_INVALID), codes(null, "1F4E79}\\x"))
    }
}
