package sgspe.deliverable

import sgspe.territory.MapExtent
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

// el marco de un mapa de informe (especificación de F8 §3.7 paso 1): su recuadro centrado en el CRS de trabajo, su escala
// estándar y su tamaño en píxeles a 300 dpi
data class MapFrame(
    val bbox: MapExtent,
    val scale: Int,
    val widthPx: Int,
    val heightPx: Int
) {
    // metros del terreno por píxel
    val metersPerPixel: Double get() = bbox.width / widthPx
}

// el encuadre: la extensión con un margen de 10 % por lado, la escala siguiente de 1:10 000, 1:25 000, 1:50 000 o 1:100 000
// que la deja entrar en el marco del tipo (16 × 12 cm, o 24 × 16 cm horizontal en el Anexo 1) y, por encima, el múltiplo
// siguiente de 100 000 (decisión 9 del plan 8.2c). una extensión de menos de 100 m (un punto) se trata como de 100 m
object MapFraming {
    const val DPI = 300
    private const val MARGIN = 0.10
    private const val MIN_SIDE_M = 100.0
    val STANDARD: List<Int> = listOf(10_000, 25_000, 50_000, 100_000)
    private val GRID_STEPS = listOf(250, 500, 1_000, 2_000, 2_500, 5_000, 10_000, 20_000, 25_000, 50_000, 100_000)
    private val BAR_LENGTHS = listOf(100, 200, 250, 500, 1_000, 2_000, 2_500, 5_000, 10_000, 20_000, 25_000, 50_000)

    fun frame(
        extent: MapExtent,
        widthCm: Double,
        heightCm: Double
    ): MapFrame {
        val width = max(extent.width, MIN_SIDE_M) * (1 + 2 * MARGIN)
        val height = max(extent.height, MIN_SIDE_M) * (1 + 2 * MARGIN)
        val needed = max(width / (widthCm / 100), height / (heightCm / 100))
        val scale = STANDARD.firstOrNull { it >= needed } ?: (ceil(needed / 100_000) * 100_000).toInt()
        val halfWidth = widthCm / 100 * scale / 2
        val halfHeight = heightCm / 100 * scale / 2
        val cx = (extent.minX + extent.maxX) / 2
        val cy = (extent.minY + extent.maxY) / 2
        return MapFrame(MapExtent(cx - halfWidth, cy - halfHeight, cx + halfWidth, cy + halfHeight), scale, px(widthCm), px(heightCm))
    }

    fun px(cm: Double): Int = (cm / 2.54 * DPI).roundToInt()

    // medio píxel en metros: la tolerancia de simplificación de las geometrías en línea (§2.2.11)
    fun tolerance(frame: MapFrame): Double = frame.metersPerPixel / 2

    // el intervalo de la grilla UTM que deja a lo más ocho líneas a lo ancho
    fun gridStep(frame: MapFrame): Int = GRID_STEPS.firstOrNull { frame.bbox.width / it <= 8 } ?: GRID_STEPS.last()

    // el largo redondo de la barra de escala: el mayor que entra en un cuarto del ancho
    fun scaleBar(frame: MapFrame): Int = BAR_LENGTHS.lastOrNull { it <= frame.bbox.width / 4 } ?: BAR_LENGTHS.first()
}
