package sgspe.deliverable

import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.ext.gfm.tables.TableCell
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.node.BlockQuote
import org.commonmark.node.BulletList
import org.commonmark.node.Code
import org.commonmark.node.Emphasis
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Heading
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.StrongEmphasis
import org.commonmark.node.Text
import org.commonmark.parser.Parser
import sgspe.privacy.Block
import sgspe.privacy.Cell
import sgspe.privacy.ColumnSpec
import sgspe.privacy.ReportModel
import sgspe.privacy.ReportSection
import java.util.Base64
import java.util.TreeMap

// el PDF de un nivel compuesto con LuaLaTeX (ADR-0030, ASM-139, deliverables.md §7.7): arma el paquete que compone sgspe-latex,
// con main.tex, la clase sgspe-informe.cls y las imágenes. recibe el modelo ya transformado por ReportDisclosure (no sabe de
// niveles) y recorre los bloques como DocxWriter, con las mismas leyendas (ReportCaptions) y los mismos anchos de columna
// (ColumnWidths): el DOCX y el PDF tienen el mismo contenido y pueden paginar distinto. todo texto del modelo entra por
// LatexText. la sección «portada» es la portada de la clase (sus mapas, con su leyenda; su párrafo repite el título) y los
// títulos llevan su número literal. el borrador y la candidata salen del mismo paquete: sgspe-latex define \sgspeborrador en
// la pasada del borrador. puro y determinista: los mismos bytes en cada armado
class LatexWriter(
    private val photos: DeliverableProperties.Photos
) {
    class Input(
        val model: ReportModel,
        val author: String?,
        val photoBytes: Map<String, ByteArray>,
        // el logo de la organización (PNG o JPEG) y su color de acento RRGGBB (ASM-140)
        val logo: ByteArray? = null,
        val accent: String? = null,
        val chapterOf: (String) -> Int? = { null }
    )

    // los archivos del paquete por su ruta, en orden; main.tex es el documento
    class Bundle(
        val files: Map<String, ByteArray>
    ) {
        val main: String get() = files.getValue(MAIN).toString(Charsets.UTF_8)
    }

    fun write(input: Input): Bundle {
        val files = TreeMap<String, ByteArray>()
        files[CLASS] = classBytes
        val doc = Document(input, files)
        doc.write()
        files[MAIN] = doc.out.toString().toByteArray(Charsets.UTF_8)
        return Bundle(files)
    }

    private inner class Document(
        val input: Input,
        val files: MutableMap<String, ByteArray>
    ) {
        val out = StringBuilder()
        val model = input.model
        val captions = ReportCaptions.of(model, input.chapterOf)

        // T1 lleva índice: su nivel 1 es un capítulo en página nueva; T2, una sección
        val chapters = model.sections.any { s -> s.blocks.any { it is Block.Toc } }
        var landscape = false

        fun write() {
            val meta = model.meta
            line("\\DocumentMetadata{pdfstandard=a-2b, lang=es-PE}")
            line("\\documentclass${if (chapters) "[capitulos]" else ""}{sgspe-informe}")
            LatexText.color(input.accent)?.let { line("\\definecolor{acento}{HTML}{$it}") }
            val level = meta.level?.takeIf { it != Deliverables.INTERNAL }.orEmpty()
            line("\\sgspemeta{${t(meta.code)}}{${t(meta.draftRevision)}}{${t(meta.issueRevision)}}{${t(level)}}{${t(meta.title)}}")
            line("\\hypersetup{pdftitle={${t(meta.title)}},pdfsubject={${t(meta.code)}},pdfkeywords={${t(meta.code)}}}")
            line("\\begin{document}")
            cover()
            for ((sectionIndex, section) in model.sections.withIndex()) {
                if (section.key == COVER_SECTION) continue
                val leading = section.blocks.firstOrNull() as? Block.SectionBreak
                leading?.let { orient(it.landscape) }
                if (section.showTitle != false) heading(section.level, section.title)
                for ((blockIndex, block) in section.blocks.withIndex().drop(if (leading != null) 1 else 0)) {
                    block(section, sectionIndex to blockIndex, block)
                }
            }
            orient(false)
            line("\\end{document}")
        }

        private fun cover() {
            val meta = model.meta
            val logo =
                input.logo
                    ?.let { bytes ->
                        val name = if (isPng(bytes)) "img/logo.png" else "img/logo.jpg"
                        files[name] = bytes
                        name
                    }.orEmpty()
            // los mapas de la sección «portada», con su leyenda (la de T2: «Figura 1. Mapa de ubicación»)
            val content = StringBuilder()
            model.sections.withIndex().firstOrNull { it.value.key == COVER_SECTION }?.let { (sectionIndex, section) ->
                section.blocks.withIndex().filter { it.value is Block.MapFigure }.forEach { (blockIndex, block) ->
                    val map = block as Block.MapFigure
                    val caption = captions.of(sectionIndex to blockIndex)
                    content.append(numbered(caption)).append("\\captionof{figure}{${t(map.title)}}")
                    content.append(mapImage(map, "height=0.42\\textheight")).append("\\par ")
                }
            }
            line(
                "\\sgspeportada{${t(meta.title)}}{${t(meta.project)}}{${t(meta.preparedFor.orEmpty())}}{${t(meta.preparedBy.orEmpty())}}" +
                    "{${t(meta.authorAddress.orEmpty())}}{${t(meta.dateLabel)}}{$logo}{$content}"
            )
        }

        private fun block(
            section: ReportSection,
            at: Pair<Int, Int>,
            block: Block
        ) {
            when (block) {
                is Block.Heading -> heading(block.level, block.text)
                is Block.Paragraph -> markdown(block.markdown, block.footnotes)
                is Block.Table -> table(block, captions.of(at))
                is Block.Photos -> captions.find(at)?.let { photoRows(block, it) }
                is Block.MapFigure -> map(block, captions.of(at))
                is Block.LineChart -> lineChart(block, captions.of(at))
                is Block.ScatterChart -> scatterChart(block, captions.of(at))
                is Block.Toc -> toc(block)
                is Block.CaptionList -> captionList(block)
                is Block.SectionBreak -> orient(block.landscape)
            }
        }

        // pasa a horizontal o vuelve: un entorno landscape abierto hasta el salto siguiente o el final
        private fun orient(wanted: Boolean) {
            if (wanted == landscape) return
            line(if (wanted) "\\begin{landscape}" else "\\end{landscape}")
            landscape = wanted
        }

        // un título con su número literal («4.1 Área…») en el nivel que corresponde; sin número, sin numeración y con entrada en
        // el índice. el nivel 1 es capítulo en T1 y sección en T2
        fun heading(
            level: Int,
            title: String
        ) {
            val commands = if (chapters) CHAPTER_LEVELS else SECTION_LEVELS
            val command = commands[(level - 1).coerceIn(0, commands.size - 1)]
            val match = NUMBERED.matchEntire(title.trim())
            // un título no queda solo al pie: pide sitio para él y unas líneas de lo que sigue (un capítulo empieza página)
            if (command != "chapter") line("\\needspace{7\\baselineskip}")
            if (match != null) {
                line("\\sgspenumero{$command}{${t(match.groupValues[1])}}\\$command{${t(match.groupValues[2])}}")
            } else if (command == "chapter" || command == "section") {
                // \addchap y \addsec de KOMA: sin número, con entrada en el índice y en el encabezado
                line("\\${if (command == "chapter") "addchap" else "addsec"}{${t(title)}}")
            } else {
                line("\\$command*{${t(title)}}\\addcontentsline{toc}{$command}{${t(title)}}")
            }
        }

        // la narrativa: el árbol de commonmark-java hacia LaTeX, nunca el texto crudo; más allá de MAX_DEPTH, texto plano
        fun markdown(
            text: String,
            footnotes: List<String>
        ) {
            val root =
                try {
                    PARSER.parse(text)
                } catch (_: StackOverflowError) {
                    null
                }
            val notes = footnotes.joinToString("") { "\\footnote{${t(it)}}" }
            if (root == null) {
                val parts = text.split(Regex("\\n\\s*\\n")).filter { it.isNotBlank() }
                parts.forEachIndexed { i, part -> paragraph(t(part.trim()) + if (i == parts.lastIndex) notes else "") }
                if (parts.isEmpty() && notes.isNotEmpty()) paragraph(notes)
                return
            }
            val blocks = mutableListOf<String>()
            var node = root.firstChild
            while (node != null) {
                blockOf(node, 0, 0)?.let { blocks += it }
                node = node.next
            }
            if (notes.isNotEmpty()) {
                if (blocks.isEmpty()) blocks += notes else blocks[blocks.lastIndex] = blocks.last().trimEnd() + notes
            }
            blocks.forEach { line(it).also { line("") } }
        }

        private fun paragraph(text: String) {
            line(text)
            line("")
        }

        // un bloque de Markdown como texto LaTeX; `depth` cuenta citas y listas, `lists` solo las listas (LaTeX anida cuatro). los
        // títulos de la narrativa bajan un nivel, como en el DOCX
        private fun blockOf(
            node: Node,
            depth: Int,
            lists: Int
        ): String? {
            if (depth > DocxWriter.MAX_DEPTH) return plain(node).takeIf { it.isNotBlank() }?.let { t(it) }
            return when (node) {
                is Heading -> {
                    val before = out.length
                    heading(node.level + 1, plain(node))
                    val text = out.substring(before)
                    out.setLength(before)
                    text.trimEnd()
                }
                is Paragraph -> inline(node, 0)
                is BulletList, is OrderedList -> list(node, depth, lists)
                is BlockQuote -> {
                    val inner = children(node).mapNotNull { blockOf(it, depth + 1, lists) }.joinToString("\n\n")
                    if (depth >= MAX_QUOTES) inner else "\\begin{quote}\n$inner\n\\end{quote}"
                }
                is FencedCodeBlock -> code(node.literal)
                is IndentedCodeBlock -> code(node.literal)
                is TableBlock -> markdownTable(node)
                else -> null
            }
        }

        private fun list(
            node: Node,
            depth: Int,
            lists: Int
        ): String {
            val ordered = node as? OrderedList
            val items =
                children(node).filterIsInstance<ListItem>().map { item ->
                    children(item).mapNotNull { blockOf(it, depth + 1, lists + 1) }.joinToString("\n\n")
                }
            // más hondo que lo que LaTeX anida: cada elemento como párrafo con su marca
            if (lists >= MAX_LISTS) {
                var n = ordered?.markerStartNumber ?: 1
                return items.joinToString("\n\n") { (if (ordered != null) "${n++}. " else "– ") + it }
            }
            val env = if (ordered != null) "enumerate" else "itemize"
            val start =
                ordered
                    ?.markerStartNumber
                    ?.takeIf { it != 1 }
                    ?.let { "[start=$it]" }
                    .orEmpty()
            return "\\begin{$env}$start\n" + items.joinToString("\n") { "\\item $it" } + "\n\\end{$env}"
        }

        private fun code(literal: String): String = "\\begin{quote}\\small\\ttfamily ${LatexText.lines(literal.trimEnd())}\\end{quote}"

        private fun children(node: Node): List<Node> = generateSequence(node.firstChild) { it.next }.toList()

        // `depth`, los énfasis que contienen el nodo: más allá de MAX_DEPTH, el texto plano
        private fun inline(
            node: Node,
            depth: Int
        ): String {
            val sb = StringBuilder()
            for (child in children(node)) {
                when {
                    child is Text -> sb.append(t(child.literal))
                    child is Code -> sb.append("\\texttt{").append(t(child.literal)).append('}')
                    child is SoftLineBreak -> sb.append(' ')
                    child is HardLineBreak -> sb.append("\\newline ")
                    depth >= DocxWriter.MAX_DEPTH -> sb.append(t(plain(child)))
                    child is StrongEmphasis -> sb.append("\\textbf{").append(inline(child, depth + 1)).append('}')
                    child is Emphasis -> sb.append("\\emph{").append(inline(child, depth + 1)).append('}')
                    else -> sb.append(inline(child, depth + 1))
                }
            }
            return sb.toString()
        }

        // el texto de un nodo y sus descendientes, sin recursión
        private fun plain(node: Node): String {
            val sb = StringBuilder()
            var current: Node? = node.firstChild ?: return (node as? Text)?.literal.orEmpty()
            while (current != null) {
                when (current) {
                    is Text -> sb.append(current.literal)
                    is Code -> sb.append(current.literal)
                    is FencedCodeBlock -> sb.append(current.literal)
                    is IndentedCodeBlock -> sb.append(current.literal)
                    is SoftLineBreak, is HardLineBreak -> sb.append(' ')
                }
                val down = current.firstChild
                if (down != null) {
                    current = down
                    continue
                }
                while (current != null && current !== node && current.next == null) {
                    if (current is Paragraph || current is Heading) sb.append('\n')
                    current = current.parent
                }
                if (current == null || current === node) break
                if (current is Paragraph || current is Heading) sb.append('\n')
                current = current.next
            }
            return sb.toString().trim()
        }

        // una tabla GFM de la narrativa: sin leyenda ni fuente, con encabezado repetido
        private fun markdownTable(node: TableBlock): String {
            val rows = mutableListOf<List<String>>()
            for (part in children(node)) {
                for (row in children(part)) rows += children(row).filterIsInstance<TableCell>().map { plain(it) }
            }
            if (rows.isEmpty()) return ""
            val before = out.length
            longTable(rows.first(), rows.drop(1).map { r -> r.map { Cell(it) } }, null, null, null, null)
            val text = out.substring(before)
            out.setLength(before)
            return text.trimEnd()
        }

        // RQ-REP-006: leyenda numerada arriba, encabezado repetido, celdas combinadas y «Fuente:» al pie de la última página de la
        // tabla; una ancha va en su página horizontal; una tabla figura (la matriz 5 × 5) no se parte
        private fun table(
            block: Block.Table,
            caption: CaptionNumbering.Caption
        ) {
            val own = block.wide && !landscape
            if (own) orient(true)
            val source = sourceText(block.source)
            if (block.figure) {
                line("\\par\\noindent\\begin{minipage}{\\linewidth}")
                line(numbered(caption) + "\\captionof{figure}{${t(block.title)}}")
                line("{\\centering\\sgspetablafuente")
                line(tabular(block.header, block.rows, block.columns, long = false, hairlines = false))
                line("\\par}")
                line("\\sgspefuente{$source}")
                line("\\end{minipage}\\par\\medskip")
            } else {
                longTable(block.header, block.rows, block.columns, caption, block.title, source)
            }
            if (own) orient(false)
        }

        private fun longTable(
            header: List<String>,
            rows: List<List<Cell>>,
            specs: List<ColumnSpec>?,
            caption: CaptionNumbering.Caption?,
            title: String?,
            source: String?
        ) {
            line("\\begingroup\\sgspetablafuente")
            caption?.let { line(numbered(it)) }
            line(tabular(header, rows, specs, long = true, caption = title, source = source))
            line("\\endgroup")
            line("")
        }

        // el cuerpo de un longtable o un tabular: columnas de ColumnWidths en `p` sobre el ancho de la caja, filetes de booktabs,
        // una línea fina entre filas (salvo donde una celda baja varias filas), \multicolumn y \multirow
        private fun tabular(
            header: List<String>,
            rows: List<List<Cell>>,
            specs: List<ColumnSpec>?,
            long: Boolean,
            caption: String? = null,
            source: String? = null,
            hairlines: Boolean = true
        ): String {
            val n = maxOf(1, header.size)
            val capacity = if (landscape) ColumnWidths.LANDSCAPE_CAPACITY else ColumnWidths.PORTRAIT_CAPACITY
            val widths = ColumnWidths.of(header, rows, capacity, specs)
            // la parte de la caja que ocupa la tabla y la de cada columna, en diezmilésimos que suman exacto
            val share = Math.round(ColumnWidths.span(header, rows, capacity, specs) * 10000)
            val parts = widths.map { Math.round(it.fraction * 10000) }.toMutableList()
            parts[parts.lastIndex] = 10000 - parts.dropLast(1).sum()
            val gaps = 2 * (n - 1)

            fun width(
                from: Int,
                span: Int
            ): String {
                val sum = parts.subList(from, from + span).sum()
                val extra = if (span > 1) "+\\tabcolsep*${2 * (span - 1)}" else ""
                return "\\dimexpr(\\linewidth*$share/10000-\\tabcolsep*$gaps)*$sum/10000$extra\\relax"
            }

            fun column(
                from: Int,
                span: Int
            ): String {
                val align = widths.getOrNull(from)?.align
                val ragged =
                    when (align) {
                        ColumnWidths.Align.RIGHT -> "\\RaggedLeft"
                        ColumnWidths.Align.CENTER -> "\\Centering"
                        else -> "\\RaggedRight"
                    }
                val left = if (from == 0) "@{}" else ""
                val right = if (from + span == n) "@{}" else ""
                return "$left>{$ragged\\arraybackslash}p{${width(from, span)}}$right"
            }
            val spec = (0 until n).joinToString("") { column(it, 1) }
            val head = (0 until n).joinToString(" & ") { c -> "\\textbf{${header.getOrNull(c)?.let { t(it) }.orEmpty()}}" } + "\\\\"
            val body = StringBuilder()
            val occupied = Array(rows.size) { BooleanArray(n) }
            // las columnas cubiertas por una celda que baja desde una fila anterior: ahí no se dibuja la línea fina
            val continuing = Array(rows.size) { BooleanArray(n) }
            val grid = Array(rows.size) { arrayOfNulls<String>(n) }
            rows.forEachIndexed { r, row ->
                var c = 0
                for (cell in row) {
                    while (c < n && occupied[r][c]) c++
                    if (c >= n) break
                    val colSpan = cell.colSpan.coerceIn(1, n - c)
                    val rowSpan = cell.rowSpan.coerceIn(1, rows.size - r)
                    for (dr in 0 until rowSpan) {
                        for (dc in 0 until colSpan) {
                            occupied[r + dr][c + dc] = true
                            if (dr > 0) continuing[r + dr][c + dc] = true
                        }
                    }
                    var content = LatexText.lines(cell.text)
                    LatexText.color(cell.shade)?.let { content = "\\cellcolor[HTML]{$it}$content" }
                    if (rowSpan > 1) content = "\\multirow[t]{$rowSpan}{=}{$content}"
                    if (colSpan > 1) content = "\\multicolumn{$colSpan}{${column(c, colSpan)}}{$content}"
                    grid[r][c] = content
                    // las posiciones cubiertas hacia abajo quedan vacías; hacia la derecha, las ocupa el \multicolumn
                    for (dr in 1 until rowSpan) {
                        grid[r + dr][c] = if (colSpan > 1) "\\multicolumn{$colSpan}{${column(c, colSpan)}}{}" else ""
                    }
                    c += colSpan
                }
            }
            rows.indices.forEach { r ->
                val cells = mutableListOf<String>()
                var c = 0
                while (c < n) {
                    val content = grid[r][c]
                    cells += content.orEmpty()
                    val span =
                        content?.let {
                            MULTICOLUMN
                                .find(it)
                                ?.groupValues
                                ?.get(1)
                                ?.toInt()
                        } ?: 1
                    c += span
                }
                body.append(cells.joinToString(" & ")).append("\\\\\n")
                if (hairlines && r < rows.lastIndex) {
                    val open = (0 until n).filter { !continuing[r + 1][it] }
                    if (open.isNotEmpty()) {
                        body.append("\\sgspefina")
                        ranges(open).forEach { (a, b) -> body.append("\\cmidrule{${a + 1}-${b + 1}}") }
                        body.append("\\sgspefuerte\n")
                    }
                }
            }
            if (!long) {
                return "\\begin{tabular}{$spec}\n\\toprule\n$head\n\\midrule\n$body\\bottomrule\n\\end{tabular}"
            }
            val sb = StringBuilder()
            // la leyenda del ancho de la tabla: longtable centra la caja de la leyenda sobre la tabla
            sb.append("\\setlength{\\LTcapwidth}{\\dimexpr\\linewidth*$share/10000\\relax}\n")
            sb.append("\\begin{longtable}{$spec}\n")
            if (caption != null) sb.append("\\caption{${t(caption)}}\\\\\n")
            sb.append("\\toprule\n$head\n\\midrule\n\\endfirsthead\n")
            if (caption != null) sb.append("\\multicolumn{$n}{@{}l@{}}{\\sgspecontinua}\\\\\n")
            sb.append("\\toprule\n$head\n\\midrule\n\\endhead\n")
            sb.append("\\bottomrule\n")
            if (source != null) sb.append("\\multicolumn{$n}{@{}p{\\linewidth}@{}}{\\sgspefuenteentabla{$source}}\\\\\n")
            sb.append("\\endlastfoot\n")
            sb.append(body)
            sb.append("\\end{longtable}")
            return sb.toString()
        }

        // los tramos seguidos de columnas: [0,1,3] → (0,1), (3,3)
        private fun ranges(columns: List<Int>): List<Pair<Int, Int>> {
            val out = mutableListOf<Pair<Int, Int>>()
            var start = columns.first()
            var prev = start
            for (c in columns.drop(1)) {
                if (c != prev + 1) {
                    out += start to prev
                    start = c
                }
                prev = c
            }
            out += start to prev
            return out
        }

        private fun sourceText(source: String?): String {
            val text = source ?: "Fuente: Elaboración ${input.author ?: "propia"}"
            return LatexText.lines(if (text.startsWith("Fuente:")) text else "Fuente: $text")
        }

        // el número literal de la leyenda en el contador de tabla o de figura («Tabla 5.2» → table 5.2)
        private fun numbered(caption: CaptionNumbering.Caption): String {
            val counter = if (caption.label.startsWith(CaptionNumbering.TABLE)) "table" else "figure"
            return "\\sgspenumero{$counter}{${t(caption.label.substringAfter(' '))}}"
        }

        // RQ-REP-010: la leyenda y luego las fotos de a dos, reducidas, con texto alternativo
        private fun photoRows(
            block: Block.Photos,
            caption: CaptionNumbering.Caption
        ) {
            val refs = block.evidence.mapNotNull { id -> model.photos[id]?.let { id to it } }
            if (refs.isEmpty()) return
            line("\\par\\needspace{0.35\\textheight}")
            line(numbered(caption) + "\\captionof{figure}{${t(block.title)}}")
            for (pair in refs.chunked(2)) {
                val cells =
                    pair.map { (id, ref) ->
                        val bytes = input.photoBytes[id]?.let { PhotoResize.jpeg(it, photos.maxSide, photos.jpegQuality, photos.maxMegapixels) }
                        val image =
                            if (bytes == null) {
                                "\\sgspeausente{Foto no disponible}"
                            } else {
                                val name = "img/foto-${safeName(id)}.jpg"
                                files[name] = bytes.jpeg
                                "\\includegraphics[width=\\linewidth,height=7cm,keepaspectratio,alt={${t(ref.alt)}}]{$name}"
                            }
                        "\\begin{minipage}[t]{0.485\\linewidth}\\centering $image\\end{minipage}"
                    }
                line("\\noindent " + cells.joinToString("\\hfill ") + "\\par\\medskip")
            }
            line("\\sgspefuente{${sourceText(block.source)}}")
        }

        private fun map(
            block: Block.MapFigure,
            caption: CaptionNumbering.Caption
        ) {
            line("\\par\\noindent\\begin{minipage}{\\linewidth}")
            line(numbered(caption) + "\\captionof{figure}{${t(block.title)}}")
            line(mapImage(block, "height=\\sgspealtomapa"))
            line("\\sgspefuente{${sourceText(block.source)}}")
            line("\\end{minipage}\\par\\medskip")
        }

        // la imagen de un mapa centrada, o su aviso; con la nota de capas omitidas por nivel
        private fun mapImage(
            block: Block.MapFigure,
            height: String
        ): String {
            val image = model.maps[block.key]?.internal ?: return "{\\centering\\sgspeausente{${t("Mapa no disponible: ${Deliverables.MAP_FAILED}")}}\\par}"
            val name = "img/mapa-${safeName(block.key)}.png"
            files[name] = Base64.getDecoder().decode(image.pngBase64)
            val note = if (image.omittedLayers) "\\par{\\sffamily\\small ${t(DocxWriter.OMITTED_LAYERS)}}" else ""
            return "{\\centering\\includegraphics[width=\\linewidth,$height,keepaspectratio,alt={${t(block.title)}}]{$name}$note\\par}"
        }

        // la tendencia (deliverables.md §7.9): pgfplots con las categorías como rótulos y una serie por nombre; un valor que no es
        // un decimal es un hueco
        private fun lineChart(
            block: Block.LineChart,
            caption: CaptionNumbering.Caption
        ) {
            val n = block.categories.size
            val labels = block.categories.joinToString(",") { "{${t(it)}}" }
            val rotate = if (n > 8) ", xticklabel style={rotate=45,anchor=north east}" else ""
            line("\\par\\noindent\\begin{minipage}{\\linewidth}")
            line(numbered(caption) + "\\captionof{figure}{${t(block.title)}}")
            line("{\\centering\\begin{tikzpicture}\\begin{axis}[sgspe, xtick={0,...,${maxOf(0, n - 1)}}, xticklabels={$labels}$rotate]")
            for (series in block.series) {
                val points = series.values.withIndex().joinToString(" ") { (i, v) -> "($i,${LatexText.number(v)})" }
                line("\\addplot+[thick] coordinates {$points};")
                line("\\addlegendentry{${t(series.name)}}")
            }
            line("\\end{axis}\\end{tikzpicture}\\par}")
            line("\\sgspefuente{${sourceText(block.source)}}")
            line("\\end{minipage}\\par\\medskip")
        }

        // el gráfico de influencia (deliverables.md §7.9): una dispersión con los umbrales como líneas discontinuas, sin rótulos
        private fun scatterChart(
            block: Block.ScatterChart,
            caption: CaptionNumbering.Caption
        ) {
            val min = LatexText.number(block.minimum)
            val max = LatexText.number(block.maximum)
            line("\\par\\noindent\\begin{minipage}{\\linewidth}")
            line(numbered(caption) + "\\captionof{figure}{${t(block.title)}}")
            line(
                "{\\centering\\begin{tikzpicture}\\begin{axis}[sgspe, height=9cm, width=0.8\\linewidth, xmin=$min, xmax=$max, ymin=$min, " +
                    "ymax=$max, xtick distance=1, ytick distance=1, xmajorgrids, xlabel={${t(block.xAxis)}}, ylabel={${t(block.yAxis)}}]"
            )
            val points = block.points.joinToString(" ") { "(${LatexText.number(it.x)},${LatexText.number(it.y)})" }
            line("\\addplot[only marks, mark=*, mark size=2.2pt, color=acento] coordinates {$points};")
            block.xThreshold?.let { line("\\draw[dashed, sgspegris, thick] (axis cs:${LatexText.number(it)},$min) -- (axis cs:${LatexText.number(it)},$max);") }
            block.yThreshold?.let { line("\\draw[dashed, sgspegris, thick] (axis cs:$min,${LatexText.number(it)}) -- (axis cs:$max,${LatexText.number(it)});") }
            line("\\end{axis}\\end{tikzpicture}\\par}")
            line("\\sgspefuente{${sourceText(block.source)}}")
            line("\\end{minipage}\\par\\medskip")
        }

        // el índice hasta el nivel del bloque: en T1 el capítulo es el nivel 0 de LaTeX
        private fun toc(block: Block.Toc) {
            val depth = if (chapters) block.levels - 1 else block.levels
            line("\\renewcommand*{\\contentsname}{${t(block.title)}}\\setcounter{tocdepth}{$depth}\\tableofcontents")
        }

        private fun captionList(block: Block.CaptionList) {
            if (block.kind == CaptionNumbering.TABLE) {
                line("\\renewcommand*{\\listtablename}{${t(block.title)}}\\listoftables")
            } else {
                line("\\renewcommand*{\\listfigurename}{${t(block.title)}}\\listoffigures")
            }
        }

        private fun t(raw: String): String = LatexText.text(raw)

        fun line(text: String) {
            out.append(text).append('\n')
        }
    }

    // un id o clave como nombre de archivo: solo letras, cifras y guiones
    private fun safeName(raw: String): String = raw.replace(Regex("[^A-Za-z0-9-]"), "_")

    private fun isPng(bytes: ByteArray): Boolean = bytes.size > 8 && bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte()

    companion object {
        const val MAIN = "main.tex"
        const val CLASS = "sgspe-informe.cls"
        const val CLASS_RESOURCE = "/sgspe/deliverable/latex/sgspe-informe.cls"

        // la sección cuyo contenido es la portada (T1 y T2)
        const val COVER_SECTION = "portada"

        // LaTeX anida cuatro listas; las citas se dejan de sangrar más allá de cuatro
        private const val MAX_LISTS = 4
        private const val MAX_QUOTES = 4

        private val CHAPTER_LEVELS = listOf("chapter", "section", "subsection", "subsubsection", "paragraph", "subparagraph")
        private val SECTION_LEVELS = listOf("section", "subsection", "subsubsection", "paragraph", "subparagraph")

        // «4.1 Área…», «10. Anexos», «9.1.1.2 Reuniones»: el número y el título
        private val NUMBERED = Regex("""^(\d+(?:\.\d+)*\.?)\s+(.+)$""", RegexOption.DOT_MATCHES_ALL)
        private val MULTICOLUMN = Regex("""^\\multicolumn\{(\d+)}""")

        private val PARSER: Parser = Parser.builder().extensions(listOf(TablesExtension.create())).build()

        val classBytes: ByteArray by lazy {
            LatexWriter::class.java.getResourceAsStream(CLASS_RESOURCE)?.use { it.readAllBytes() } ?: error("falta la clase $CLASS_RESOURCE")
        }
    }
}
