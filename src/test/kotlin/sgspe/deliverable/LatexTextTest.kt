package sgspe.deliverable

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// ASM-139: la única puerta del texto hacia TeX. ninguna entrada hostil deja un comando, un grupo, una notación ^^, un
// comentario o un argumento opcional sin escapar
class LatexTextTest {
    // un texto escapado no tiene más barras invertidas que las de sus propios reemplazos
    private fun commands(escaped: String): List<String> = Regex("""\\[A-Za-z]+|\\.""").findAll(escaped).map { it.value }.toList()

    private val allowed = setOf("\\textbackslash", "\\{", "\\}", "\\$", "\\&", "\\#", "\\textasciicircum", "\\_", "\\%", "\\textasciitilde", "\\newline")

    @Test
    fun `los caracteres especiales de TeX se escapan`() {
        assertEquals(
            "\\textbackslash{}\\{\\}\\$\\&\\#\\textasciicircum{}\\_\\%\\textasciitilde{}{*}{[}{]}",
            LatexText.text("\\{}$&#^_%~*[]")
        )
        assertEquals("Año 2026: 45 \\% más", LatexText.text("Año 2026: 45 % más"))
    }

    @Test
    fun `ninguna entrada hostil deja un comando propio`() {
        val hostile =
            listOf(
                "\\input{/etc/passwd}",
                "\\directlua{os.execute('id')}",
                "\\immediate\\write18{rm -rf /}",
                "\\catcode`\\@=11",
                "^^5cinput^^7b/etc/passwd^^7d",
                "\\def\\x{\\x}\\x",
                "}{\\end{document}",
                "% comentario que se come la línea",
                "\\\\[10cm] texto",
                "\\openout5=x.tex"
            )
        for (raw in hostile) {
            val escaped = LatexText.text(raw)
            val own = commands(escaped).filterNot { it in allowed }
            assertTrue(own.isEmpty(), "$raw → $escaped: $own")
            assertFalse("^^" in escaped, escaped)
            // las llaves que quedan sin barra son solo las de los reemplazos, siempre balanceadas
            val bare = escaped.replace("\\{", "").replace("\\}", "")
            assertEquals(bare.count { it == '{' }, bare.count { it == '}' }, escaped)
        }
    }

    @Test
    fun `los saltos de linea son espacios en text y newline en lines, y los controles se quitan`() {
        assertEquals("uno dos", LatexText.text("uno\ndos"))
        assertEquals("uno\\newline dos", LatexText.lines("uno\ndos"))
        assertEquals("uno", LatexText.lines("\nuno\n"))
        assertEquals("a b c", LatexText.text("a\tb\rc"))
        assertEquals("ab", LatexText.text("a\u0000\u001Bb"))
        assertEquals("a b", LatexText.text("a\u2028b"))
    }

    @Test
    fun `colores y numeros solo con su forma`() {
        assertEquals("1F4E79", LatexText.color("1f4e79"))
        assertNull(LatexText.color("1F4E79}\\x"))
        assertNull(LatexText.color(null))
        assertEquals("12.5", LatexText.number("12.5"))
        assertEquals("-3", LatexText.number("-3"))
        assertEquals("nan", LatexText.number("1e9"))
        assertEquals("nan", LatexText.number("3}\\x"))
        assertEquals("nan", LatexText.number(null))
    }
}
