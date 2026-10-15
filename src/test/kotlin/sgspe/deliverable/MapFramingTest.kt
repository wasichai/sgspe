package sgspe.deliverable

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import sgspe.territory.MapExtent

// especificación de F8 §3.7 paso 1 y §5 («Mapas»): margen de 10 %, la escala estándar siguiente para el marco del tipo a
// 300 dpi, y la grilla y la barra que la acompañan
class MapFramingTest {
    private fun extent(
        width: Double,
        height: Double
    ) = MapExtent(780_000.0, 9_240_000.0, 780_000.0 + width, 9_240_000.0 + height)

    @Test
    fun `elige la escala estandar siguiente con el margen y centra el recuadro`() {
        val frame = MapFraming.frame(extent(3_000.0, 2_000.0), 16.0, 12.0)
        // 3 600 m en 16 cm piden 1:22 500; 2 400 m en 12 cm, 1:20 000: la siguiente es 1:25 000
        assertEquals(25_000, frame.scale)
        assertEquals(4_000.0, frame.bbox.width, 0.001)
        assertEquals(3_000.0, frame.bbox.height, 0.001)
        assertEquals(781_500.0, (frame.bbox.minX + frame.bbox.maxX) / 2, 0.001)
        assertEquals(1890 to 1417, frame.widthPx to frame.heightPx)
        assertEquals(10_000, MapFraming.frame(extent(1_000.0, 800.0), 16.0, 12.0).scale)
        assertEquals(100_000, MapFraming.frame(extent(12_000.0, 9_000.0), 16.0, 12.0).scale)
    }

    @Test
    fun `por encima de 1 a 100 000 sube al multiplo siguiente de 100 000 y un punto tiene escala`() {
        assertEquals(300_000, MapFraming.frame(extent(30_000.0, 1_000.0), 16.0, 12.0).scale)
        assertEquals(10_000, MapFraming.frame(extent(0.0, 0.0), 16.0, 12.0).scale)
    }

    @Test
    fun `el marco horizontal del Anexo 1, la tolerancia de medio pixel, la grilla y la barra`() {
        val frame = MapFraming.frame(extent(3_000.0, 2_000.0), 24.0, 16.0)
        assertEquals(2835 to 1890, frame.widthPx to frame.heightPx)
        val portrait = MapFraming.frame(extent(3_000.0, 2_000.0), 16.0, 12.0)
        assertEquals(4_000.0 / 1890 / 2, MapFraming.tolerance(portrait), 0.0001)
        assertEquals(500, MapFraming.gridStep(portrait))
        assertEquals(1_000, MapFraming.scaleBar(portrait))
    }
}
