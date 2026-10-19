package sgspe.deliverable

import sgspe.privacy.Cell
import sgspe.privacy.ColumnSpec
import kotlin.math.max

// el ancho y la alineación de cada columna de una tabla según su contenido (ASM-141, deliverables.md §7.5), el mismo para el
// DOCX y el PDF. mide en caracteres: el ideal de una columna es su texto más largo (con tope) y su mínimo, su palabra más
// larga; reparte el ancho de la caja como el diseño automático de tablas de CSS: si los ideales caben, en proporción a ellos;
// si no, cada columna recibe su mínimo y el sobrante se reparte por lo que a cada una le falta para su ideal. las cifras,
// porcentajes, montos y fechas van a la derecha. una celda combinada hacia la derecha no se mide; una que baja varias filas
// ocupa su columna en ellas (la misma ubicación que DocxWriter.grid). `specs` fija pesos y alineaciones por columna. puro
object ColumnWidths {
    enum class Align { LEFT, RIGHT, CENTER }

    data class Column(
        val fraction: Double,
        val align: Align
    )

    // los caracteres de 9 pt que caben en la caja de texto de una página vertical (16 cm)
    const val PORTRAIT_CAPACITY = 100

    // y en una horizontal (24,7 cm)
    const val LANDSCAPE_CAPACITY = 155

    // ninguna columna mide menos de esto, ni su ideal pasa de aquello: una columna de un carácter se vería rota y un
    // párrafo largo no debe llevarse todo el reparto
    private const val MIN_CHARS = 4
    private const val MAX_CHARS = 200

    // el relleno de una celda en caracteres, y la parte mínima de la caja que ocupa una tabla
    private const val PADDING_CHARS = 3
    private const val MIN_SPAN = 0.5

    // cifras con separadores, signo, porcentaje o moneda; fechas dd/mm/aaaa y aaaa-mm-dd
    private val NUMERIC = Regex("""^(?:(?:S/|US\$|\$)\s?)?[-+−]?\d[\d\s.,  ]*(?:\s?%)?$""")
    private val DATE = Regex("""^(?:\d{1,2}/\d{1,2}/\d{2,4}|\d{4}-\d{2}-\d{2})$""")

    fun of(
        header: List<String>,
        rows: List<List<Cell>>,
        capacity: Int = PORTRAIT_CAPACITY,
        specs: List<ColumnSpec>? = null
    ): List<Column> {
        val m = measure(header, rows)
        val computed = widths(m.ideal, m.least, capacity)
        val total = computed.sum()
        return computed.indices
            .map { c ->
                val spec = specs?.getOrNull(c)
                val align = spec?.align?.let { runCatching { Align.valueOf(it) }.getOrNull() } ?: if (m.seen[c] && m.numeric[c]) Align.RIGHT else Align.LEFT
                Column(computed[c] / total, align)
            }.let { weighted(it, specs) }
    }

    // la parte del ancho de la caja que ocupa la tabla: la de su contenido más el relleno de cada celda, entre la mitad y toda
    // la caja; una tabla con pesos propios ocupa la caja entera. una tabla corta no se estira hasta dejar columnas vacías
    fun span(
        header: List<String>,
        rows: List<List<Cell>>,
        capacity: Int = PORTRAIT_CAPACITY,
        specs: List<ColumnSpec>? = null
    ): Double {
        if (!specs.isNullOrEmpty() && specs.all { it.weight != null }) return 1.0
        val m = measure(header, rows)
        return ((m.ideal.sum() + PADDING_CHARS * m.ideal.size).toDouble() / capacity).coerceIn(MIN_SPAN, 1.0)
    }

    private class Measure(
        val ideal: IntArray,
        val least: IntArray,
        val numeric: BooleanArray,
        val seen: BooleanArray
    )

    private fun measure(
        header: List<String>,
        rows: List<List<Cell>>
    ): Measure {
        val columns = max(1, header.size)
        val m = Measure(IntArray(columns) { MIN_CHARS }, IntArray(columns) { MIN_CHARS }, BooleanArray(columns) { true }, BooleanArray(columns))
        // el encabezado va en negrita: su texto pide un carácter más
        header.forEachIndexed { c, text -> measure(text, c, m.ideal, m.least, extra = 1) }
        placed(rows, columns).forEach { (c, cell) ->
            measure(cell.text, c, m.ideal, m.least, extra = 0)
            val text = cell.text.trim()
            if (text.isNotEmpty()) {
                m.seen[c] = true
                if (!NUMERIC.matches(text) && !DATE.matches(text)) m.numeric[c] = false
            }
        }
        return m
    }

    // las celdas con su columna, salvo las que combinan hacia la derecha: cada una en la primera posición libre de su fila
    private fun placed(
        rows: List<List<Cell>>,
        columns: Int
    ): List<Pair<Int, Cell>> {
        val occupied = Array(rows.size) { BooleanArray(columns) }
        val out = mutableListOf<Pair<Int, Cell>>()
        rows.forEachIndexed { r, row ->
            var c = 0
            for (cell in row) {
                while (c < columns && occupied[r][c]) c++
                if (c >= columns) break
                val colSpan = cell.colSpan.coerceIn(1, columns - c)
                val rowSpan = cell.rowSpan.coerceIn(1, rows.size - r)
                for (dr in 0 until rowSpan) for (dc in 0 until colSpan) occupied[r + dr][c + dc] = true
                if (colSpan == 1) out += c to cell
                c += colSpan
            }
        }
        return out
    }

    private fun measure(
        text: String,
        column: Int,
        ideal: IntArray,
        least: IntArray,
        extra: Int
    ) {
        val lines = text.split('\n')
        ideal[column] = max(ideal[column], (lines.maxOf { it.trim().length } + extra).coerceAtMost(MAX_CHARS))
        val word = lines.flatMap { it.split(' ', ' ') }.maxOfOrNull { it.length } ?: 0
        least[column] = max(least[column], (word + extra).coerceAtMost(MAX_CHARS))
    }

    private fun widths(
        ideal: IntArray,
        least: IntArray,
        capacity: Int
    ): DoubleArray {
        val ideals = ideal.sum()
        if (ideals <= capacity) return DoubleArray(ideal.size) { ideal[it].toDouble() }
        val minimums = least.sum()
        if (minimums >= capacity) return DoubleArray(least.size) { least[it].toDouble() }
        val missing = ideal.indices.sumOf { ideal[it] - least[it] }.toDouble()
        val spare = (capacity - minimums).toDouble()
        return DoubleArray(ideal.size) { least[it] + spare * (ideal[it] - least[it]) / missing }
    }

    // los pesos de la tabla, si los trae todos, reemplazan las fracciones calculadas
    private fun weighted(
        columns: List<Column>,
        specs: List<ColumnSpec>?
    ): List<Column> {
        val weights = specs?.map { it.weight?.toBigDecimalOrNull()?.toDouble() }
        if (weights == null || weights.size != columns.size || weights.any { it == null || it <= 0.0 }) return columns
        val sum = weights.sumOf { it!! }
        return columns.mapIndexed { c, column -> column.copy(fraction = weights[c]!! / sum) }
    }
}
