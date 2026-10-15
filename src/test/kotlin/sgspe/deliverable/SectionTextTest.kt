package sgspe.deliverable

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import sgspe.privacy.Block
import sgspe.privacy.Cell
import sgspe.privacy.ChartSeries
import sgspe.privacy.Mark
import sgspe.privacy.MentionTarget

// especificación de F8 §3.9 y decisión 16 del plan 8.2c: lo que va al modelo de una sección, sin lo marcado y con las menciones
// por nombre (la seudonimización las reemplaza después)
class SectionTextTest {
    private val person = "person:11111111-1111-1111-1111-111111111111"
    private val people = mapOf(person to MentionTarget("Persona Ejemplo 1", "Presidente", "Comunidad Ejemplo", "GI-111111111111"))

    @Test
    fun `quita lo marcado, resuelve las menciones por nombre y deja fuera mapas e indices`() {
        val text =
            SectionText.render(
                listOf(
                    Block.Heading(3, "Área de influencia directa social (AIDS)"),
                    Block.Paragraph("Se conversó con [[$person]]."),
                    Block.Paragraph("Religión: católica.", Mark.restricted("locality_profile.religion")),
                    Block.Table("Comprobantes", listOf("Tipo", "Número"), listOf(listOf(Cell("BOLETA"), Cell("B001-1", mark = Mark.REGISTRY)))),
                    Block.MapFigure("predio", "Mapa del predio"),
                    Block.Toc("Índice"),
                    Block.LineChart("Confianza", listOf("2026-08", "2026-09"), listOf(ChartSeries("Promedio", listOf("3.00", null))))
                ),
                people
            )
        assertTrue("### Área de influencia directa social (AIDS)" in text, text)
        assertTrue("Se conversó con Persona Ejemplo 1." in text, text)
        assertTrue("católica" !in text && "B001-1" !in text && "Mapa del predio" !in text && "Índice" !in text, text)
        assertTrue("| BOLETA |  |" in text, text)
        assertTrue("Confianza: Promedio 2026-08=3.00, 2026-09=sin dato" in text, text)
        assertEquals("", SectionText.render(emptyList(), people))
    }
}
