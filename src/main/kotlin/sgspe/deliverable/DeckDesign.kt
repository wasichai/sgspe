package sgspe.deliverable

import java.awt.Font
import java.awt.font.FontRenderContext
import java.awt.font.LineBreakMeasurer
import java.awt.font.TextAttribute
import java.text.AttributedString

// lo que el planificador sabe del sistema visual de las láminas (slides/src/design/tokens.ts, deliverables.md §16.6): la
// página, los anchos de la retícula y la escala tipográfica de cada público. una prueba compara estos números con los del
// paquete. con ellos y las mismas fuentes, `lines` cuenta en cuántas líneas cae un texto, para dividir una lámina en vez de
// encoger su letra (RQ-REP-036); el compositor vuelve a verificar con el navegador
object DeckDesign {
    const val VERSION = "1"
    const val CONTENT_WIDTH = 1680.0
    private const val COLUMN = (CONTENT_WIDTH - 32 * 11) / 12

    // el ancho de n columnas con sus medianiles
    fun span(n: Int): Double = COLUMN * n + 32.0 * (n - 1)

    data class Scale(
        val display: Int,
        val title: Int,
        val lead: Int,
        val body: Int,
        val small: Int,
        val figure: Int,
        val min: Int
    )

    private val BASE = Scale(display = 104, title = 60, lead = 40, body = 32, small = 26, figure = 148, min = 24)

    fun scale(audience: Audience): Scale =
        when (audience) {
            Audience.DIRECCION -> BASE
            Audience.INSTITUCIONES -> BASE.copy(title = 54)
            Audience.CAMPO -> BASE.copy(title = 56)
            Audience.INVESTIGACION -> BASE.copy(title = 54, body = 31)
            Audience.COMUNIDAD -> Scale(display = 112, title = 68, lead = 46, body = 38, small = 30, figure = 176, min = 28)
        }

    enum class Face(
        val file: String
    ) {
        DISPLAY("SourceSerif4Display-Semibold.ttf"),
        SERIF("SourceSerif4-Regular.ttf"),
        SANS("SourceSans3-Regular.ttf"),
        SANS_BOLD("SourceSans3-Semibold.ttf")
    }

    private val fonts: Map<Face, Font> =
        Face.entries.associateWith { face ->
            val stream =
                DeckDesign::class.java.getResourceAsStream("/sgspe/deliverable/deck/fonts/${face.file}")
                    ?: error("Falta la fuente ${face.file} de las láminas")
            stream.use { Font.createFont(Font.TRUETYPE_FONT, it) }
        }

    private val context = FontRenderContext(null, true, true)

    // el margen contra el navegador: se mide como si el ancho fuera un 10 % menor
    private const val SAFETY = 0.9

    // en cuántas líneas cae `text` a `size` px en un ancho de `width` px
    fun lines(
        text: String,
        face: Face,
        size: Int,
        width: Double
    ): Int {
        if (text.isBlank()) return 0
        return text.split('\n').sumOf { paragraph ->
            if (paragraph.isBlank()) {
                1
            } else {
                val attributed = AttributedString(paragraph)
                attributed.addAttribute(TextAttribute.FONT, fonts.getValue(face).deriveFont(size.toFloat()))
                val measurer = LineBreakMeasurer(attributed.iterator, context)
                var count = 0
                while (measurer.position < paragraph.length) {
                    measurer.nextLayout((width * SAFETY).toFloat())
                    count++
                }
                count
            }
        }
    }

    fun fits(
        text: String,
        face: Face,
        size: Int,
        width: Double,
        maxLines: Int
    ): Boolean = lines(text, face, size, width) <= maxLines

    // las filas de una tabla de lámina (src/layouts/content.tsx, Table) que caben en el cuerpo, por lámina: el ancho de la tabla
    // es de 7 columnas de la retícula con hasta 3 de datos y de 10 con más; cada columna toma su parte por la raíz del texto más
    // largo (como ColumnWidths) y una fila mide su celda más alta. una fila que no cabe sola va sola a su lámina
    fun tableChunks(
        header: List<String>,
        rows: List<List<String>>,
        audience: Audience,
        maxRows: Int,
        available: Double
    ): List<List<List<String>>> {
        val scale = scale(audience)
        val width = span(if (header.size <= 3) 7 else 10)
        val weights =
            header.indices.map { i ->
                kotlin.math.sqrt(maxOf(header[i].length, rows.maxOfOrNull { it.getOrNull(i)?.length ?: 0 } ?: 0, 4).toDouble())
            }
        val total = weights.sum()
        // el relleno de una celda (24 px a cada lado) sale de su ancho
        val widths = weights.map { (width * it / total - 48).coerceAtLeast(40.0) }

        fun rowHeight(row: List<String>): Double =
            40 + row.indices.maxOf { i -> lines(row[i], if (i == 0) Face.SANS_BOLD else Face.SANS, scale.body, widths[i]) } * scale.body * 1.3
        val head = 40 + header.indices.maxOf { i -> lines(header[i], Face.SANS_BOLD, (scale.small * 1.1).toInt(), widths[i]) } * scale.small * 1.1 * 1.3
        val out = mutableListOf<List<List<String>>>()
        var current = mutableListOf<List<String>>()
        var used = head
        for (row in rows) {
            val h = rowHeight(row)
            if (current.isNotEmpty() && (used + h > available || current.size >= maxRows)) {
                out += current
                current = mutableListOf()
                used = head
            }
            current += row
            used += h
        }
        if (current.isNotEmpty()) out += current
        return out
    }

    // el ancho del título de una lámina de contenido (86 % del ancho útil, src/components/Page.tsx)
    val TITLE_WIDTH = CONTENT_WIDTH * 0.86
}
