package sgspe.deliverable

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

// especificación de F8 §2.2.12: número literal por capítulo (T1) o corrido (T2), tablas y figuras aparte, con su marcador
class CaptionNumberingTest {
    @Test
    fun `por capitulo y sin saltos, tablas y figuras aparte`() {
        val numbering = CaptionNumbering()
        assertEquals(CaptionNumbering.Caption("Tabla 5.1", "_Tabla_5_1"), numbering.next(CaptionNumbering.TABLE, 5))
        assertEquals("Tabla 5.2", numbering.next(CaptionNumbering.TABLE, 5).label)
        assertEquals("Figura 5.1", numbering.next(CaptionNumbering.FIGURE, 5).label)
        assertEquals("Tabla 6.1", numbering.next(CaptionNumbering.TABLE, 6).label)
    }

    @Test
    fun `sin capitulo la numeracion es corrida`() {
        val numbering = CaptionNumbering()
        assertEquals("Tabla 1", numbering.next(CaptionNumbering.TABLE).label)
        assertEquals(CaptionNumbering.Caption("Tabla 2", "_Tabla_2"), numbering.next(CaptionNumbering.TABLE))
    }
}
