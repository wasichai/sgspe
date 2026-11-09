package sgspe.deliverable

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

// design-system.md §3.3 (paleta cartográfica): los mapas de informe son claros y sus capas, publicadas o en línea, pintan con los
// tokens del mapa del tema claro de docs/ux/tokens.css, nunca con un tono semántico; los centros poblados son un círculo
// `surface` con borde `lyr-place`, no un rojo que se confunda con `danger`
class ReportLayerStylesTest {
    private val light: Map<String, String> by lazy {
        val css = File("docs/ux/tokens.css").readText()
        val start = css.indexOf(":root {")
        Regex("""--sg-([a-z0-9-]+)\s*:\s*(#[0-9a-fA-F]{6})\s*;""")
            .findAll(css.substring(start, css.indexOf('}', start)))
            .associate { it.groupValues[1] to it.groupValues[2].lowercase() }
    }

    private val all = ReportLayerStyles.PUBLISHED + ReportLayerStyles.INLINE

    private fun colors(style: LayerStyle) = listOfNotNull(style.stroke, style.fill, style.label, style.halo).map { it.lowercase() }

    @Test
    fun `cada color de un mapa de informe es un token del mapa o una superficie del tema claro`() {
        val allowed =
            light.filterKeys { it.startsWith("lyr-") || it.startsWith("map-") || it in setOf("surface", "text-secondary") }.values.toSet()
        val strays = all.flatMap { (name, style) -> colors(style).filter { it !in allowed }.map { "$name $it" } }
        assertEquals(emptyList<String>(), strays)
    }

    @Test
    fun `los centros poblados son un circulo surface con borde lyr-place y el proyecto va en lyr-project`() {
        val place = ReportLayerStyles.PUBLISHED.getValue("sgspe-lyr-02-location")
        assertEquals(listOf("circle", light["surface"], light["lyr-place"]), listOf(place.mark, place.fill, place.stroke))
        assertEquals(2.0, place.strokeWidth)
        for (name in listOf("LYR-09-area", "LYR-13-AIDS", "LYR-13-AIIS")) {
            assertEquals(light["lyr-project"], ReportLayerStyles.INLINE.getValue(name).stroke, name)
        }
        assertEquals(light["lyr-water"], ReportLayerStyles.INLINE.getValue("LYR-08").stroke)
        assertEquals(light["lyr-access"], ReportLayerStyles.INLINE.getValue("LYR-10").fill)
    }

    @Test
    fun `dos capas de un mismo mapa nunca se dibujan igual`() {
        val drawn = all.mapValues { (_, s) -> listOf(s.shape, s.stroke, s.strokeWidth, s.dash, s.fill, s.fillOpacity, s.mark, s.size) }
        val twins =
            drawn.entries
                .groupBy({ it.value }, { it.key })
                .values
                .filter { it.size > 1 }
        assertTrue(twins.isEmpty(), "$twins")
    }
}
