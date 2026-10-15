package sgspe.deliverable

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import sgspe.privacy.Block
import sgspe.privacy.Cell
import sgspe.privacy.ChartSeries
import sgspe.privacy.Mark
import sgspe.privacy.MentionTarget
import java.util.UUID

// especificación de F8 §3.9 y decisión 16 del plan 8.2c: lo que va al modelo de una sección, sin lo marcado y con las menciones
// por nombre solo si la seudonimización de la corrida las reemplaza (`nameable`); si no, con la cita de EXTERNO
class SectionTextTest {
    private val person = "person:11111111-1111-1111-1111-111111111111"
    private val other = "person:22222222-2222-2222-2222-222222222222"
    private val people =
        mapOf(
            person to MentionTarget("Persona Ejemplo 1", "Presidente", "Comunidad Ejemplo", "GI-111111111111"),
            other to MentionTarget("Persona Ejemplo 2", null, null, null, "Localidad Ejemplo")
        )
    private val personId = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val otherId = UUID.fromString("22222222-2222-2222-2222-222222222222")
    private val holders = mapOf(person to personId, other to otherId)
    private val always: (String, UUID?) -> Boolean = { _, _ -> true }

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
                people,
                holders,
                always
            )
        assertTrue("### Área de influencia directa social (AIDS)" in text, text)
        assertTrue("Se conversó con «Persona Ejemplo 1»." in text, text)
        assertTrue("católica" !in text && "B001-1" !in text && "Mapa del predio" !in text && "Índice" !in text, text)
        assertTrue("| BOLETA |  |" in text, text)
        assertTrue("Confianza: Promedio 2026-08=3.00, 2026-09=sin dato" in text, text)
        assertEquals("", SectionText.render(emptyList(), people, holders, always))
    }

    @Test
    fun `sin EXTERNO ni titulares ni tablas marcadas, con notas y menciones en titulos y series`() {
        val text =
            SectionText.render(
                listOf(
                    Block.Paragraph("Se reservó contenido restringido.", Mark.EXTERNAL_ONLY),
                    Block.Paragraph("Emisor: Comercial Ejemplo.", Mark.HOLDER),
                    Block.Table("Titulares", listOf("Nombre"), listOf(listOf(Cell("Titular Ejemplo"))), mark = Mark.HOLDER),
                    Block.Table(
                        "Registro de [[$person]]",
                        listOf("Emisor", "Monto"),
                        listOf(listOf(Cell("Comercial Ejemplo", mark = Mark.HOLDER), Cell("10.00")))
                    ),
                    Block.Paragraph("Acuerdo con la comunidad.", footnotes = listOf("Según [[$other]].")),
                    Block.LineChart("Confianza de [[$person]]", listOf("2026-08"), listOf(ChartSeries("Según [[$other]]", listOf("2.50"))))
                ),
                people,
                holders,
                always
            )
        assertFalse("reservó" in text || "Comercial Ejemplo" in text || "Titular Ejemplo" in text || "Titulares" in text, text)
        assertTrue("**Registro de «Persona Ejemplo 1»**\n| Emisor | Monto |\n|  | 10.00 |" in text, text)
        assertTrue("Acuerdo con la comunidad.\nNota: Según «Persona Ejemplo 2»." in text, text)
        assertTrue("Confianza de «Persona Ejemplo 1»: Según «Persona Ejemplo 2» 2026-08=2.50" in text, text)
    }

    @Test
    fun `una mencion que la corrida no seudonimiza sale con la cita de EXTERNO, nunca con el nombre`() {
        val text =
            SectionText.render(
                listOf(Block.Paragraph("Con [[$person]] y [[$other]]."), Block.Heading(2, "Visita de [[$other]]")),
                people,
                holders
            ) { name, _ -> name == "Persona Ejemplo 1" }
        assertTrue("Con «Persona Ejemplo 1» y una persona de Localidad Ejemplo." in text, text)
        assertTrue("## Visita de una persona de Localidad Ejemplo" in text, text)
        assertFalse("Persona Ejemplo 2" in text, text)
        val none = SectionText.render(listOf(Block.Paragraph("Con [[$person]].")), people, holders) { _, _ -> false }
        assertEquals("Con Presidente, Comunidad Ejemplo.", none)
    }

    // revisión final de 8.2c, Minor 5: `nameable` recibe la persona detrás de cada mención (la de un cargo o un grupo de interés,
    // su titular); sin persona conocida, null
    @Test
    fun `nameable recibe la persona detras de la mencion`() {
        val position = "position:33333333-3333-3333-3333-333333333333"
        val stakeholder = "stakeholder:44444444-4444-4444-4444-444444444444"
        val seen = mutableListOf<Pair<String, UUID?>>()
        SectionText.render(
            listOf(Block.Paragraph("[[$person]], [[$position]] y [[$stakeholder]].")),
            people +
                mapOf(
                    position to MentionTarget("Persona Ejemplo 2", "Tesorera", "Asociación Ejemplo"),
                    stakeholder to MentionTarget("Asociación Ejemplo", null, "Asociación Ejemplo", "GI-444444444444")
                ),
            holders + (position to otherId)
        ) { name, id ->
            seen += name to id
            false
        }
        assertEquals(listOf("Persona Ejemplo 1" to personId, "Persona Ejemplo 2" to otherId, "Asociación Ejemplo" to null), seen)
    }
}
