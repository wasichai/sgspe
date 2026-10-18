package sgspe.deliverable

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import sgspe.territory.MapExtent
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import javax.imageio.metadata.IIOMetadataNode
import kotlin.math.cos
import kotlin.math.sin

// especificación de F8 §3.7 paso 4 y §5 («Mapas»): el cuerpo en un lienzo con marco, grilla UTM rotulada, leyenda no vacía,
// barra y escala numérica, norte, CRS y fuente; PNG con pHYs de 300 dpi
class MapComposerTest {
    private val frame = MapFraming.frame(MapExtent(780_000.0, 9_240_000.0, 783_000.0, 9_242_000.0), 16.0, 12.0)
    private val body =
        BufferedImage(frame.widthPx, frame.heightPx, BufferedImage.TYPE_INT_RGB).apply {
            createGraphics().apply {
                color = java.awt.Color.WHITE
                fillRect(0, 0, width, height)
                dispose()
            }
        }
    private val legend =
        listOf(
            LegendEntry("Territorio de la comunidad campesina", ReportLayerStyles.PUBLISHED.getValue("sgspe-lyr-05")),
            LegendEntry("Centro poblado", ReportLayerStyles.PUBLISHED.getValue("sgspe-lyr-02-location")),
            LegendEntry("Ríos y quebradas", ReportLayerStyles.INLINE.getValue("LYR-08"))
        )

    private fun read(png: ByteArray): BufferedImage = ImageIO.read(ByteArrayInputStream(png))

    @Test
    fun `el lienzo es mas grande que el cuerpo, lleva pHYs de 300 dpi y la leyenda tiene pixeles`() {
        val png = MapComposer.compose(body, frame, 32717, legend, "Asociación Ejemplo", omitted = false)
        val image = read(png)
        assertTrue(image.width > body.width && image.height > body.height)
        val reader = ImageIO.getImageReadersByFormatName("png").next()
        reader.input = ImageIO.createImageInputStream(ByteArrayInputStream(png))
        val tree = reader.getImageMetadata(0).getAsTree("javax_imageio_png_1.0") as IIOMetadataNode
        val phys = tree.getElementsByTagName("pHYs").item(0) as IIOMetadataNode
        assertEquals("11811", phys.getAttribute("pixelsPerUnitXAxis"))
        assertEquals("meter", phys.getAttribute("unitSpecifier"))
        // debajo del cuerpo: la leyenda, la barra, el norte y los textos
        var dark = 0
        for (y in MapComposer.TOP + body.height + 1 until image.height) {
            for (x in 0 until image.width) if (image.getRGB(x, y) and 0xFFFFFF != 0xFFFFFF) dark++
        }
        assertTrue(dark > 2_000, "$dark píxeles no blancos bajo el mapa")
        // la grilla rotula el margen izquierdo
        var labels = 0
        for (y in MapComposer.TOP until MapComposer.TOP + body.height) {
            for (x in 0 until MapComposer.LEFT - 10) if (image.getRGB(x, y) and 0xFFFFFF != 0xFFFFFF) labels++
        }
        assertTrue(labels > 100, "$labels píxeles de rótulos")
    }

    // #83: la estrella de la leyenda (LYR-09) tiene cinco puntas, no es el triángulo: el brazo derecho está dentro y el hueco
    // entre las dos puntas de abajo, fuera; el triángulo es al revés
    @Test
    fun `la marca star es una estrella de cinco puntas y triangle un triangulo`() {
        val r = 12.0
        val star = MapComposer.mark("star", 50.0, 50.0, 2 * r)
        val triangle = MapComposer.mark("triangle", 50.0, 50.0, 2 * r)
        val arm = 50.0 + r * 0.85 * cos(Math.toRadians(18.0)) to 50.0 - r * 0.85 * sin(Math.toRadians(18.0))
        val notch = 50.0 to 50.0 + r * 0.6
        assertTrue(star.contains(arm.first, arm.second))
        assertFalse(star.contains(notch.first, notch.second))
        assertFalse(triangle.contains(arm.first, arm.second))
        assertTrue(triangle.contains(notch.first, notch.second))
        assertTrue(star.contains(50.0, 50.0 - r * 0.9), "la punta de arriba")
    }

    @Test
    fun `las capas omitidas suman una linea y los textos van en es-PE`() {
        val without = read(MapComposer.compose(body, frame, 32717, legend, null, omitted = false))
        val with = read(MapComposer.compose(body, frame, 32717, legend, null, omitted = true))
        assertEquals(without.height + MapComposer.LEGEND_ROW, with.height)
        assertEquals("9,245,000 N", MapComposer.gridLabel(9_245_000.0, 'N'))
        assertEquals("781,500 E", MapComposer.gridLabel(781_500.0, 'E'))
        assertEquals("Escala 1:25 000", MapComposer.scaleLabel(25_000))
        assertEquals("WGS 84 / UTM zona 17S", MapComposer.crsLabel(32717))
    }
}
