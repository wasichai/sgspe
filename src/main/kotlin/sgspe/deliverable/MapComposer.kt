package sgspe.deliverable

import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.Polygon
import java.awt.RenderingHints
import java.awt.geom.Ellipse2D
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageTypeSpecifier
import javax.imageio.metadata.IIOMetadataNode
import kotlin.math.ceil
import kotlin.math.roundToInt

// una entrada de la leyenda: el texto de la capa y su estilo
data class LegendEntry(
    val label: String,
    val style: LayerStyle
)

// las decoraciones del mapa de informe (especificación de F8 §2.2.11 y §3.7 paso 4, ASM-134): el cuerpo que devuelve
// GeoServer en un lienzo con marco, grilla UTM con rótulos en el borde (formato es-PE, «9,245,000 N»), leyenda de las capas
// presentes (con «Capas omitidas por nivel de divulgación» si EXTERNO quitó alguna), barra y escala numérica, norte de
// cuadrícula, el CRS y «Fuente: Elaboración <organización autora>». Java 2D sin pantalla con Liberation Sans (OFL, del jar de
// PDFBox; decisión 11 del plan 8.2c). sale un PNG con pHYs de 300 dpi
object MapComposer {
    const val LEFT = 230
    const val RIGHT = 60
    const val TOP = 90
    const val LEGEND_ROW = 48
    private const val GAP = 40
    private const val FOOTER = 190
    private const val COLUMNS = 2
    const val OMITTED = "Capas omitidas por nivel de divulgación"
    private const val FONT = "/org/apache/pdfbox/resources/ttf/LiberationSans-Regular.ttf"
    private val GRID = Color(90, 90, 90, 110)

    private val BASE: Font by lazy {
        MapComposer::class.java.getResourceAsStream(FONT)?.use { Font.createFont(Font.TRUETYPE_FONT, it) } ?: error("falta Liberation Sans en el jar de PDFBox")
    }

    private fun font(
        size: Float,
        bold: Boolean = false
    ): Font = BASE.deriveFont(if (bold) Font.BOLD else Font.PLAIN, size)

    fun compose(
        body: BufferedImage,
        frame: MapFrame,
        srid: Int,
        legend: List<LegendEntry>,
        author: String?,
        omitted: Boolean
    ): ByteArray {
        val rows = (legend.size + COLUMNS - 1) / COLUMNS + (if (omitted) 1 else 0)
        val width = LEFT + body.width + RIGHT
        val height = TOP + body.height + GAP + 50 + rows * LEGEND_ROW + GAP + FOOTER
        val canvas = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g = canvas.createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            g.color = Color.WHITE
            g.fillRect(0, 0, width, height)
            g.drawImage(body, LEFT, TOP, null)
            grid(g, frame, body.width, body.height)
            g.color = Color.BLACK
            g.stroke = BasicStroke(3f)
            g.drawRect(LEFT, TOP, body.width, body.height)
            var y = TOP + body.height + GAP
            g.font = font(34f, bold = true)
            g.drawString("Leyenda", LEFT, y + 30)
            y += 50
            val column = body.width / COLUMNS
            legend.forEachIndexed { i, entry ->
                val x = LEFT + (i % COLUMNS) * column
                val top = y + (i / COLUMNS) * LEGEND_ROW
                swatch(g, entry.style, x, top + 6, 60, 30)
                g.color = Color.BLACK
                g.font = font(28f)
                g.drawString(entry.label, x + 76, top + 30)
            }
            y += ((legend.size + COLUMNS - 1) / COLUMNS) * LEGEND_ROW
            if (omitted) {
                g.font = font(28f)
                g.drawString(OMITTED, LEFT, y + 30)
                y += LEGEND_ROW
            }
            y += GAP
            scaleBar(g, frame, y)
            north(g, LEFT + body.width - 70, y)
            g.color = Color.BLACK
            g.font = font(28f)
            g.drawString(crsLabel(srid), LEFT + body.width / 2 - 120, y + 40)
            g.drawString("Fuente: Elaboración ${author ?: "propia"}", LEFT, y + 150)
        } finally {
            g.dispose()
        }
        return png300(canvas)
    }

    private fun grid(
        g: Graphics2D,
        frame: MapFrame,
        width: Int,
        height: Int
    ) {
        val step = MapFraming.gridStep(frame)
        val b = frame.bbox
        g.font = font(24f)
        g.stroke = BasicStroke(1.5f)
        var x = ceil(b.minX / step) * step
        while (x < b.maxX) {
            val px = LEFT + ((x - b.minX) / b.width * width).roundToInt()
            g.color = GRID
            g.drawLine(px, TOP, px, TOP + height)
            g.color = Color.BLACK
            val label = gridLabel(x, 'E')
            g.drawString(label, px - g.fontMetrics.stringWidth(label) / 2, TOP - 16)
            x += step
        }
        var y = ceil(b.minY / step) * step
        while (y < b.maxY) {
            val py = TOP + height - ((y - b.minY) / b.height * height).roundToInt()
            g.color = GRID
            g.drawLine(LEFT, py, LEFT + width, py)
            g.color = Color.BLACK
            val label = gridLabel(y, 'N')
            g.drawString(label, LEFT - 12 - g.fontMetrics.stringWidth(label), py + 9)
            y += step
        }
    }

    // la barra en cuatro tramos alternados, con su largo y la escala numérica debajo
    private fun scaleBar(
        g: Graphics2D,
        frame: MapFrame,
        y: Int
    ) {
        val meters = MapFraming.scaleBar(frame)
        val length = (meters / frame.metersPerPixel).roundToInt()
        val part = length / 4
        for (i in 0 until 4) {
            g.color = if (i % 2 == 0) Color.BLACK else Color.WHITE
            g.fillRect(LEFT + i * part, y + 20, part, 16)
        }
        g.color = Color.BLACK
        g.stroke = BasicStroke(2f)
        g.drawRect(LEFT, y + 20, part * 4, 16)
        g.font = font(24f)
        g.drawString("0", LEFT - 6, y + 64)
        val end = if (meters >= 1_000) "${DecimalFormat("#,##0.##", DecimalFormatSymbols(Locale.US)).format(meters / 1_000.0)} km" else "$meters m"
        g.drawString(end, LEFT + part * 4 - g.fontMetrics.stringWidth(end) / 2, y + 64)
        g.font = font(28f)
        g.drawString(scaleLabel(frame.scale), LEFT, y + 104)
    }

    // el norte de cuadrícula (§8, consecuencia 6): una flecha con la N
    private fun north(
        g: Graphics2D,
        x: Int,
        y: Int
    ) {
        g.color = Color.BLACK
        g.fillPolygon(Polygon(intArrayOf(x, x - 22, x + 22), intArrayOf(y + 10, y + 80, y + 80), 3))
        g.font = font(32f, bold = true)
        g.drawString("N", x - g.fontMetrics.stringWidth("N") / 2, y + 116)
    }

    private fun swatch(
        g: Graphics2D,
        style: LayerStyle,
        x: Int,
        y: Int,
        w: Int,
        h: Int
    ) {
        val stroke = style.stroke?.let(Color::decode) ?: Color.BLACK
        val dash =
            style.dash
                .takeIf { it.isNotEmpty() }
                ?.map { (it * 2).toFloat() }
                ?.toFloatArray()
        g.stroke = BasicStroke(maxOf(2f, style.strokeWidth.toFloat() * 2), BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, dash, 0f)
        when (style.shape) {
            Shape.POLYGON -> {
                style.fill?.let {
                    val base = Color.decode(it)
                    g.color = Color(base.red, base.green, base.blue, (maxOf(style.fillOpacity, 0.2) * 255).roundToInt().coerceIn(0, 255))
                    g.fillRect(x, y, w, h)
                }
                g.color = stroke
                g.drawRect(x, y, w, h)
            }
            Shape.LINE -> {
                g.color = stroke
                g.drawLine(x, y + h / 2, x + w, y + h / 2)
            }
            Shape.POINT -> {
                val size = 24.0
                val cx = x + w / 2.0
                val cy = y + h / 2.0
                g.color = style.fill?.let(Color::decode) ?: stroke
                when (style.mark) {
                    "square" -> g.fillRect((cx - size / 2).roundToInt(), (cy - size / 2).roundToInt(), size.roundToInt(), size.roundToInt())
                    "triangle", "star" ->
                        g.fillPolygon(
                            Polygon(
                                intArrayOf(cx.roundToInt(), (cx - size / 2).roundToInt(), (cx + size / 2).roundToInt()),
                                intArrayOf((cy - size / 2).roundToInt(), (cy + size / 2).roundToInt(), (cy + size / 2).roundToInt()),
                                3
                            )
                        )
                    else -> g.fill(Ellipse2D.Double(cx - size / 2, cy - size / 2, size, size))
                }
            }
        }
    }

    // «9,245,000 N»: coma de miles, sin decimales (RQ-REP-020)
    fun gridLabel(
        value: Double,
        axis: Char
    ): String = "${DecimalFormat("#,##0", DecimalFormatSymbols(Locale.US)).format(value)} $axis"

    // «Escala 1:25 000»
    fun scaleLabel(scale: Int): String = "Escala 1:" + DecimalFormat("#,##0", DecimalFormatSymbols(Locale.US)).format(scale).replace(',', ' ')

    fun crsLabel(srid: Int): String = "WGS 84 / UTM zona ${srid - 32_700}S"

    // el PNG con su resolución física: 300 dpi son 11 811 píxeles por metro
    private fun png300(image: BufferedImage): ByteArray {
        val writer = ImageIO.getImageWritersByFormatName("png").next()
        try {
            val type = ImageTypeSpecifier.createFromRenderedImage(image)
            val metadata = writer.getDefaultImageMetadata(type, null)
            val root = IIOMetadataNode("javax_imageio_png_1.0")
            val phys =
                IIOMetadataNode("pHYs").apply {
                    setAttribute("pixelsPerUnitXAxis", PIXELS_PER_METER)
                    setAttribute("pixelsPerUnitYAxis", PIXELS_PER_METER)
                    setAttribute("unitSpecifier", "meter")
                }
            root.appendChild(phys)
            metadata.mergeTree("javax_imageio_png_1.0", root)
            val out = ByteArrayOutputStream()
            ImageIO.createImageOutputStream(out).use { stream ->
                writer.output = stream
                writer.write(null, IIOImage(image, null, metadata), null)
            }
            return out.toByteArray()
        } finally {
            writer.dispose()
        }
    }

    private const val PIXELS_PER_METER = "11811"
}
