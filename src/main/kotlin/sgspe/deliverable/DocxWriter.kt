package sgspe.deliverable

import org.apache.poi.common.usermodel.PictureType
import org.apache.poi.openxml4j.opc.OPCPackage
import org.apache.poi.util.Units
import org.apache.poi.wp.usermodel.HeaderFooterType
import org.apache.poi.xddf.usermodel.chart.AxisCrosses
import org.apache.poi.xddf.usermodel.chart.AxisPosition
import org.apache.poi.xddf.usermodel.chart.ChartTypes
import org.apache.poi.xddf.usermodel.chart.LegendPosition
import org.apache.poi.xddf.usermodel.chart.MarkerStyle
import org.apache.poi.xddf.usermodel.chart.XDDFDataSourcesFactory
import org.apache.poi.xddf.usermodel.chart.XDDFLineChartData
import org.apache.poi.xwpf.usermodel.ParagraphAlignment
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.XWPFParagraph
import org.apache.poi.xwpf.usermodel.XWPFRelation
import org.apache.poi.xwpf.usermodel.XWPFRun
import org.apache.poi.xwpf.usermodel.XWPFTable
import org.apache.poi.xwpf.usermodel.XWPFTableCell
import org.apache.xmlbeans.impl.xb.xmlschema.SpaceAttribute
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
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STFldCharType
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STMerge
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STPageOrientation
import sgspe.privacy.Block
import sgspe.privacy.Cell
import sgspe.privacy.PhotoRef
import sgspe.privacy.ReportModel
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.util.Base64
import java.util.IdentityHashMap
import javax.imageio.ImageIO

// el DOCX de un nivel sobre la plantilla (especificación de F8 §3.3, deliverables.md §7.5): recibe el modelo ya transformado
// por ReportDisclosure (no sabe de niveles: solo imprime el que trae meta.level) y los bytes de las fotos. rellena los
// marcadores fijos de la plantilla normalizando las corridas partidas; recorre la narrativa con commonmark-java y su extensión
// de tablas GFM hacia los estilos de la plantilla (resueltos por su nombre integrado, TemplateStyles); arma tablas con título
// arriba, «Fuente:» abajo, encabezado repetido, celdas combinadas y sombreadas; numera las leyendas con número literal y
// marcador (CaptionNumbering, por el capítulo de la sección en T1) en una pasada previa, para que el índice de T1 (campo TOC)
// y sus listas de tablas y de figuras (PAGEREF) vayan primero; pone fotos reducidas de a dos por fila en una tabla sin
// bordes, con texto alternativo; inserta los mapas y los gráficos de líneas (XDDF); pasa a horizontal y vuelve con los saltos
// de sección; agrega al pie el código, la revisión, el nivel fuera de INTERNO y «Página X de Y» (PAGE y NUMPAGES) y, en el
// borrador, «BORRADOR» en el encabezado de cada sección; y pone título, asunto y palabras clave en las propiedades. sin HTML:
// un bloque HTML de la narrativa se ignora. trabajo bloqueante de POI: JOB-05 lo llama en Dispatchers.IO
class DocxWriter(
    private val photos: DeliverableProperties.Photos
) {
    class Input(
        val model: ReportModel,
        val template: ByteArray,
        val draft: Boolean,
        val author: String?,
        val photoBytes: Map<String, ByteArray>,
        // T1 numera por capítulo (8.2c); T2, corrido
        val chapterOf: (String) -> Int? = { null }
    )

    fun write(input: Input): ByteArray {
        val model = input.model
        val revision = if (input.draft) model.meta.draftRevision else model.meta.issueRevision
        return open(input.template).use { doc ->
            val styles = StyleIds(doc)
            // un pie de la plantilla con su propio código o revisión no lleva otra vez esa línea: solo «Página X de Y»
            val ownFooter = doc.footerList.any { footer -> FOOTER_MARKERS.any { it in footer.text } }
            fillPlaceholders(doc, placeholders(model, revision), model)
            headerAndFooter(doc, model, revision, input.draft, ownFooter)
            properties(doc, model, revision)
            // las leyendas se numeran antes de escribir: las listas de tablas y de figuras del índice van primero (T1)
            val captions = captions(model, input)
            var landscape = false
            for (section in model.sections) {
                // un salto como primer bloque se aplica antes del título: el Anexo 1 empieza en su página horizontal
                val leading = section.blocks.firstOrNull() as? Block.SectionBreak
                if (leading != null) {
                    if (leading.landscape != landscape) endSection(doc, landscape)
                    landscape = leading.landscape
                }
                // la portada lleva su título en la plantilla y el índice, el suyo (un título «Portada» o «Índice» de más saldría en el índice)
                if (section.key !in UNTITLED) paragraph(doc, styles.heading(section.level)).createRun().setText(section.title)
                for (block in section.blocks.drop(if (leading != null) 1 else 0)) {
                    when (block) {
                        is Block.Heading -> paragraph(doc, styles.heading(block.level)).createRun().setText(block.text)
                        is Block.Paragraph -> markdown(doc, styles, block.markdown, block.footnotes)
                        is Block.Table -> table(doc, styles, block, captions.of(block), input.author, landscape)
                        is Block.Photos -> captions.find(block)?.let { photoRows(doc, styles, block, model, input, it) }
                        is Block.MapFigure -> map(doc, styles, block, model, captions.of(block), input.author)
                        is Block.LineChart -> lineChart(doc, styles, block, captions.of(block), input.author)
                        is Block.Toc -> toc(doc, block)
                        is Block.CaptionList -> captionList(doc, styles, block, captions)
                        is Block.SectionBreak -> {
                            if (block.landscape != landscape) endSection(doc, landscape)
                            landscape = block.landscape
                        }
                    }
                }
            }
            orient(bodySection(doc), landscape)
            ByteArrayOutputStream().also { doc.write(it) }.toByteArray()
        }
    }

    // la plantilla .dotx o .docx abierta como documento: una plantilla de Word cambia su tipo de contenido al de documento
    private fun open(template: ByteArray): XWPFDocument {
        val pkg = OPCPackage.open(ByteArrayInputStream(template))
        pkg.replaceContentType(XWPFRelation.TEMPLATE.contentType, XWPFRelation.DOCUMENT.contentType)
        return XWPFDocument(pkg)
    }

    private fun placeholders(
        model: ReportModel,
        revision: String
    ): Map<String, String> {
        val meta = model.meta
        return mapOf(
            "titulo" to meta.title,
            "proyecto" to meta.project,
            "preparado_para" to meta.preparedFor.orEmpty(),
            "suscrito_por" to meta.preparedBy.orEmpty(),
            "direccion_autora" to meta.authorAddress.orEmpty(),
            "fecha" to meta.dateLabel,
            "codigo" to meta.code,
            "revision" to revision,
            "nivel" to levelLabel(meta.level)
        )
    }

    // el nivel solo se imprime fuera de INTERNO (RQ-REP-016)
    private fun levelLabel(level: String?): String = if (level == null || level == Deliverables.INTERNAL) "" else level

    // los marcadores del cuerpo (también en tablas), los encabezados y los pies. una corrida partida por Word ("{{ti" + "tulo}}")
    // se normaliza: el texto entero del párrafo queda en su primera corrida, con su formato. {{mapa_portada}} pone la imagen
    private fun fillPlaceholders(
        doc: XWPFDocument,
        values: Map<String, String>,
        model: ReportModel
    ) {
        val paragraphs =
            doc.paragraphs + doc.tables.flatMap(::tableParagraphs) +
                doc.headerList.flatMap { it.paragraphs + it.tables.flatMap(::tableParagraphs) } +
                doc.footerList.flatMap { it.paragraphs + it.tables.flatMap(::tableParagraphs) }
        for (p in paragraphs) {
            val text = p.text
            if ("{{" !in text) continue
            val cover = TemplateStyles.PLACEHOLDER.findAll(text).any { it.groupValues[1] == TemplateStyles.COVER_MAP }
            val replaced = TemplateStyles.PLACEHOLDER.replace(text) { values[it.groupValues[1]] ?: "" }
            for (i in p.runs.size - 1 downTo 1) p.removeRun(i)
            val run = p.runs.firstOrNull() ?: p.createRun()
            while (run.ctr.sizeOfTArray() > 1) run.ctr.removeT(1)
            run.setText(replaced, 0)
            if (cover) {
                val image = model.meta.coverMap?.let { model.maps[it]?.internal }
                if (image != null) png(run, Base64.getDecoder().decode(image.pngBase64), "mapa-portada.png", "Mapa de ubicación", COVER_WIDTH_CM)
            }
        }
    }

    private fun tableParagraphs(table: XWPFTable): List<XWPFParagraph> = table.rows.flatMap { row -> row.tableCells.flatMap { it.paragraphs } }

    // el pie con el código, la revisión, el nivel y «Página X de Y» y, en el borrador, «BORRADOR» en cada encabezado. todas las
    // secciones citan el mismo encabezado y pie (los saltos copian las referencias de la sección del cuerpo)
    private fun headerAndFooter(
        doc: XWPFDocument,
        model: ReportModel,
        revision: String,
        draft: Boolean,
        ownFooter: Boolean
    ) {
        val header = doc.headerList.firstOrNull() ?: doc.createHeader(HeaderFooterType.DEFAULT)
        val headers = (doc.headerList + header).distinct()
        if (draft) {
            for (h in headers) {
                val p = h.createParagraph()
                p.alignment = ParagraphAlignment.CENTER
                p.createRun().apply {
                    setText(WATERMARK)
                    isBold = true
                    fontSize = 28
                    color = "C00000"
                }
            }
        }
        val footer = doc.footerList.firstOrNull() ?: doc.createFooter(HeaderFooterType.DEFAULT)
        val p = footer.createParagraph()
        p.alignment = ParagraphAlignment.CENTER
        val level = levelLabel(model.meta.level).let { if (it.isEmpty()) "" else " · $it" }
        p.createRun().setText(if (ownFooter) "Página " else "${model.meta.code} · Rev. $revision$level · Página ")
        field(p, "PAGE")
        p.createRun().setText(" de ")
        field(p, "NUMPAGES")
    }

    private fun field(
        p: XWPFParagraph,
        instruction: String
    ) {
        val simple = p.ctp.addNewFldSimple()
        simple.instr = instruction
        simple.addNewR().addNewT().stringValue = "1"
    }

    private fun paragraph(
        doc: XWPFDocument,
        style: String?
    ): XWPFParagraph = doc.createParagraph().also { p -> style?.let { p.style = it } }

    // la narrativa: CommonMark con tablas GFM. los títulos de la narrativa bajan un nivel (el 1 es el de la sección); una
    // lista va con su viñeta o su número literal en el estilo de lista; el HTML se ignora
    private fun markdown(
        doc: XWPFDocument,
        styles: StyleIds,
        text: String,
        footnotes: List<String>
    ) {
        // el parser de CommonMark recorre los énfasis anidados de forma recursiva: un anidado de miles de niveles le agota la
        // pila. entonces la narrativa sale como texto plano, un párrafo por bloque, en lugar de tumbar el intento
        val root =
            try {
                PARSER.parse(text)
            } catch (_: StackOverflowError) {
                null
            }
        var last: XWPFParagraph? = null
        if (root == null) {
            text.split(Regex("\\n\\s*\\n")).filter { it.isNotBlank() }.forEach { last = literal(doc, styles, it.trim()) }
        }
        var node = root?.firstChild
        while (node != null) {
            last = markdownBlock(doc, styles, node, null, 0) ?: last
            node = node.next
        }
        if (footnotes.isNotEmpty()) {
            val target = last ?: paragraph(doc, styles.normal)
            for (note in footnotes) {
                val footnote = doc.createFootnote()
                target.addFootnoteReference(footnote)
                footnote.createParagraph().createRun().setText(note)
            }
        }
    }

    // `depth`, las citas y listas que contienen el nodo: más allá de MAX_DEPTH el contenido sale como un párrafo de texto plano,
    // así un anidado de miles de niveles no desborda la pila de JOB-05
    private fun markdownBlock(
        doc: XWPFDocument,
        styles: StyleIds,
        node: Node,
        prefix: String?,
        depth: Int
    ): XWPFParagraph? =
        when {
            depth > MAX_DEPTH -> plainText(node).takeIf { it.isNotBlank() }?.let { text -> literal(doc, styles, (prefix ?: "") + text) }
            else -> markdownNode(doc, styles, node, prefix, depth)
        }

    private fun markdownNode(
        doc: XWPFDocument,
        styles: StyleIds,
        node: Node,
        prefix: String?,
        depth: Int
    ): XWPFParagraph? =
        when (node) {
            is Heading -> paragraph(doc, styles.heading(node.level + 1)).also { inline(it, node, false, false, 0) }
            is Paragraph ->
                paragraph(doc, if (prefix == null) styles.normal else styles.list).also { p ->
                    prefix?.let { p.createRun().setText(it) }
                    inline(p, node, false, false, 0)
                }
            is BulletList, is OrderedList -> {
                var last: XWPFParagraph? = null
                var item = node.firstChild
                var n = (node as? OrderedList)?.markerStartNumber ?: 1
                while (item != null) {
                    if (item is ListItem) {
                        val marker = if (node is OrderedList) "${n++}. " else "• "
                        var child = item.firstChild
                        var first = true
                        while (child != null) {
                            last = markdownBlock(doc, styles, child, if (first) marker else "", depth + 1) ?: last
                            first = false
                            child = child.next
                        }
                    }
                    item = item.next
                }
                last
            }
            is BlockQuote -> {
                var last: XWPFParagraph? = null
                var child = node.firstChild
                while (child != null) {
                    last = markdownBlock(doc, styles, child, prefix, depth + 1) ?: last
                    child = child.next
                }
                last
            }
            is FencedCodeBlock -> literal(doc, styles, node.literal)
            is IndentedCodeBlock -> literal(doc, styles, node.literal)
            is TableBlock -> {
                markdownTable(doc, styles, node)
                null
            }
            else -> null
        }

    private fun literal(
        doc: XWPFDocument,
        styles: StyleIds,
        text: String
    ): XWPFParagraph = paragraph(doc, styles.normal).also { it.createRun().setText(text.trimEnd()) }

    // `depth`, los énfasis y enlaces que contienen el nodo: más allá de MAX_DEPTH, el texto plano con el formato que lleva
    private fun inline(
        p: XWPFParagraph,
        node: Node,
        bold: Boolean,
        italic: Boolean,
        depth: Int
    ) {
        var child = node.firstChild
        while (child != null) {
            when {
                child is Text -> run(p, child.literal, bold, italic)
                child is Code -> run(p, child.literal, bold, italic)
                child is SoftLineBreak -> run(p, " ", bold, italic)
                child is HardLineBreak -> p.createRun().addBreak()
                depth >= MAX_DEPTH -> run(p, plainText(child), bold, italic)
                child is StrongEmphasis -> inline(p, child, true, italic, depth + 1)
                child is Emphasis -> inline(p, child, bold, true, depth + 1)
                else -> inline(p, child, bold, italic, depth + 1)
            }
            child = child.next
        }
    }

    // el texto de un nodo y sus descendientes, en orden y sin recursión: un salto de línea entre bloques, un espacio por cada
    // salto suave
    private fun plainText(node: Node): String {
        val out = StringBuilder()
        var current: Node? = node.firstChild ?: return ""
        while (current != null) {
            when (current) {
                is Text -> out.append(current.literal)
                is Code -> out.append(current.literal)
                is FencedCodeBlock -> out.append(current.literal)
                is IndentedCodeBlock -> out.append(current.literal)
                is SoftLineBreak, is HardLineBreak -> out.append(' ')
            }
            val down = current.firstChild
            if (down != null) {
                current = down
                continue
            }
            while (current != null && current !== node && current.next == null) {
                if (current is Paragraph || current is Heading) out.append('\n')
                current = current.parent
            }
            if (current == null || current === node) break
            if (current is Paragraph || current is Heading) out.append('\n')
            current = current.next
        }
        return out.toString().trim()
    }

    private fun run(
        p: XWPFParagraph,
        text: String,
        bold: Boolean,
        italic: Boolean
    ) {
        p.createRun().apply {
            setText(text)
            if (bold) isBold = true
            if (italic) isItalic = true
        }
    }

    // una tabla GFM de la narrativa: sin título ni fuente (los pone quien la escribe en el texto), con encabezado repetido
    private fun markdownTable(
        doc: XWPFDocument,
        styles: StyleIds,
        node: TableBlock
    ) {
        val rows = mutableListOf<List<String>>()
        var section = node.firstChild
        while (section != null) {
            var row = section.firstChild
            while (row != null) {
                val cells = mutableListOf<String>()
                var cell = row.firstChild
                while (cell != null) {
                    if (cell is TableCell) cells += plain(cell)
                    cell = cell.next
                }
                rows += cells
                row = row.next
            }
            section = section.next
        }
        if (rows.isEmpty()) return
        val header = rows.first()
        grid(doc, styles, header, rows.drop(1).map { r -> r.map { Cell(it) } })
    }

    private fun plain(node: Node): String {
        val out = StringBuilder()
        var child = node.firstChild
        while (child != null) {
            when (child) {
                is Text -> out.append(child.literal)
                is Code -> out.append(child.literal)
                is SoftLineBreak, is HardLineBreak -> out.append(' ')
                else -> out.append(plain(child))
            }
            child = child.next
        }
        return out.toString()
    }

    // RQ-REP-006: título numerado arriba (con su marcador), encabezado repetido, celdas combinadas y «Fuente:» abajo
    private fun table(
        doc: XWPFDocument,
        styles: StyleIds,
        block: Block.Table,
        caption: CaptionNumbering.Caption,
        author: String?,
        landscape: Boolean
    ) {
        // una tabla ancha va en su propia sección horizontal, salvo que ya esté en una
        val own = block.wide && !landscape
        if (own) endSection(doc, false)
        captionParagraph(doc, styles, caption, block.title)
        grid(doc, styles, block.header, block.rows)
        sourceParagraph(doc, styles, block.source, author)
        if (own) endSection(doc, true)
    }

    // la grilla de una tabla: cada celda en la primera posición libre de su fila; colSpan con gridSpan, rowSpan con vMerge
    private fun grid(
        doc: XWPFDocument,
        styles: StyleIds,
        header: List<String>,
        rows: List<List<Cell>>
    ): XWPFTable {
        val columns = maxOf(1, header.size)
        val table = doc.createTable(rows.size + 1, columns)
        styles.table?.let { table.styleID = it }
        header.forEachIndexed { c, text -> cellText(table.getRow(0).getCell(c), text, bold = true) }
        table.getRow(0).isRepeatHeader = true
        val occupied = Array(rows.size) { BooleanArray(columns) }
        val removed = Array(rows.size) { mutableSetOf<Int>() }
        rows.forEachIndexed { r, row ->
            var c = 0
            for (cell in row) {
                while (c < columns && occupied[r][c]) c++
                if (c >= columns) break
                val colSpan = cell.colSpan.coerceIn(1, columns - c)
                val rowSpan = cell.rowSpan.coerceIn(1, rows.size - r)
                for (dr in 0 until rowSpan) {
                    for (dc in 0 until colSpan) {
                        occupied[r + dr][c + dc] = true
                        if (dc > 0) removed[r + dr] += c + dc
                    }
                    val target = table.getRow(r + dr + 1).getCell(c)
                    val pr = target.ctTc.tcPr ?: target.ctTc.addNewTcPr()
                    if (colSpan > 1) pr.addNewGridSpan().setVal(BigInteger.valueOf(colSpan.toLong()))
                    if (rowSpan > 1) pr.addNewVMerge().setVal(if (dr == 0) STMerge.RESTART else STMerge.CONTINUE)
                }
                cellText(table.getRow(r + 1).getCell(c), cell.text, bold = false)
                cell.shade?.let { table.getRow(r + 1).getCell(c).color = it }
                c += colSpan
            }
        }
        removed.forEachIndexed { r, columnsToRemove -> columnsToRemove.sortedDescending().forEach { table.getRow(r + 1).removeCell(it) } }
        return table
    }

    private fun cellText(
        cell: XWPFTableCell,
        text: String,
        bold: Boolean
    ) {
        val p = cell.paragraphs.firstOrNull() ?: cell.addParagraph()
        p.createRun().apply {
            setText(text)
            if (bold) isBold = true
        }
    }

    private fun captionParagraph(
        doc: XWPFDocument,
        styles: StyleIds,
        caption: CaptionNumbering.Caption,
        title: String
    ) {
        val p = paragraph(doc, styles.caption)
        val id = BigInteger.valueOf((++styles.lastBookmark).toLong())
        p.ctp.addNewBookmarkStart().apply {
            name = caption.bookmark
            this.id = id
        }
        p.createRun().setText("${caption.label}. $title")
        p.ctp.addNewBookmarkEnd().id = id
    }

    private fun sourceParagraph(
        doc: XWPFDocument,
        styles: StyleIds,
        source: String?,
        author: String?
    ) {
        val text = source ?: "Fuente: Elaboración ${author ?: "propia"}"
        paragraph(doc, styles.normal).createRun().apply {
            setText(if (text.startsWith("Fuente:")) text else "Fuente: $text")
            isItalic = true
            fontSize = 9
        }
    }

    // RQ-REP-010: de a dos por fila, reducidas, con texto alternativo, en una tabla sin bordes
    private fun photoRows(
        doc: XWPFDocument,
        styles: StyleIds,
        block: Block.Photos,
        model: ReportModel,
        input: Input,
        caption: CaptionNumbering.Caption
    ) {
        val refs = block.evidence.mapNotNull { model.photos[it] }
        if (refs.isEmpty()) return
        captionParagraph(doc, styles, caption, block.title)
        val pairs = refs.chunked(2)
        val table = doc.createTable(pairs.size, 2)
        listOf(
            XWPFTable::setTopBorder,
            XWPFTable::setBottomBorder,
            XWPFTable::setLeftBorder,
            XWPFTable::setRightBorder,
            XWPFTable::setInsideHBorder,
            XWPFTable::setInsideVBorder
        ).forEach { it(table, XWPFTable.XWPFBorderType.NONE, 0, 0, "auto") }
        pairs.forEachIndexed { r, pair ->
            pair.forEachIndexed { c, ref -> photo(table.getRow(r).getCell(c), ref, input.photoBytes[ref.evidence]) }
        }
        sourceParagraph(doc, styles, block.source, input.author)
    }

    private fun photo(
        cell: XWPFTableCell,
        ref: PhotoRef,
        bytes: ByteArray?
    ) {
        val p = cell.paragraphs.firstOrNull() ?: cell.addParagraph()
        p.alignment = ParagraphAlignment.CENTER
        val resized = bytes?.let { PhotoResize.jpeg(it, photos.maxSide, photos.jpegQuality, photos.maxMegapixels) }
        if (resized == null) {
            p.createRun().setText("[Foto no disponible]")
            return
        }
        val widthPt = PHOTO_WIDTH_CM * POINTS_PER_CM
        val heightPt = widthPt * resized.height / resized.width
        val run = p.createRun()
        run.addPicture(ByteArrayInputStream(resized.jpeg), PictureType.JPEG, "foto-${ref.evidence}.jpg", Units.toEMU(widthPt), Units.toEMU(heightPt))
        alt(run, ref.alt)
    }

    private fun map(
        doc: XWPFDocument,
        styles: StyleIds,
        block: Block.MapFigure,
        model: ReportModel,
        caption: CaptionNumbering.Caption,
        author: String?
    ) {
        captionParagraph(doc, styles, caption, block.title)
        val image = model.maps[block.key]?.internal
        val p = paragraph(doc, styles.normal)
        p.alignment = ParagraphAlignment.CENTER
        if (image == null) {
            p.createRun().setText("[Mapa no disponible: ${Deliverables.MAP_FAILED}]")
        } else {
            png(p.createRun(), Base64.getDecoder().decode(image.pngBase64), "mapa-${block.key}.png", block.title, MAP_WIDTH_CM)
            if (image.omittedLayers) paragraph(doc, styles.normal).createRun().setText(OMITTED_LAYERS)
        }
        sourceParagraph(doc, styles, block.source, author)
    }

    private fun png(
        run: XWPFRun,
        bytes: ByteArray,
        name: String,
        alt: String,
        widthCm: Double
    ) {
        val image = ImageIO.read(ByteArrayInputStream(bytes)) ?: return
        val widthPt = widthCm * POINTS_PER_CM
        val heightPt = widthPt * image.height / image.width
        run.addPicture(ByteArrayInputStream(bytes), PictureType.PNG, name, Units.toEMU(widthPt), Units.toEMU(heightPt))
        alt(run, alt)
    }

    // el texto alternativo de la última imagen de la corrida (RQ-REP-028): docPr/@descr
    private fun alt(
        run: XWPFRun,
        text: String
    ) {
        val drawings = run.ctr.drawingList
        drawings
            .lastOrNull()
            ?.inlineList
            ?.lastOrNull()
            ?.docPr
            ?.descr = text
    }

    // cierra la sección en curso con su orientación: un párrafo con una copia de la sección del cuerpo (tamaño, márgenes,
    // encabezado y pie), así cada sección lleva el mismo encabezado con la marca
    private fun endSection(
        doc: XWPFDocument,
        landscape: Boolean
    ) {
        val copy = bodySection(doc).copy() as CTSectPr
        orient(copy, landscape)
        val p = doc.createParagraph()
        (p.ctp.pPr ?: p.ctp.addNewPPr()).sectPr = copy
    }

    private fun bodySection(doc: XWPFDocument): CTSectPr {
        val body = doc.document.body
        return if (body.isSetSectPr) body.sectPr else body.addNewSectPr()
    }

    private fun orient(
        section: CTSectPr,
        landscape: Boolean
    ) {
        val size = if (section.isSetPgSz) section.pgSz else section.addNewPgSz()
        val w = (size.w as? BigInteger) ?: BigInteger.valueOf(A4_WIDTH)
        val h = (size.h as? BigInteger) ?: BigInteger.valueOf(A4_HEIGHT)
        val (short, long) = if (w < h) w to h else h to w
        size.w = if (landscape) long else short
        size.h = if (landscape) short else long
        size.orient = if (landscape) STPageOrientation.LANDSCAPE else STPageOrientation.PORTRAIT
    }

    // las leyendas del documento en su orden, numeradas por capítulo (o corridas sin capítulo), con su título
    private class Captions {
        val byBlock = IdentityHashMap<Block, CaptionNumbering.Caption>()
        val ordered = mutableListOf<Pair<CaptionNumbering.Caption, String>>()

        fun find(block: Block): CaptionNumbering.Caption? = byBlock[block]

        fun of(block: Block): CaptionNumbering.Caption = byBlock[block] ?: error("un bloque con leyenda quedó sin numerar")
    }

    // la misma regla que el recorrido: una tabla (o una tabla figura), un bloque de fotos con alguna foto del nivel, un mapa y
    // un gráfico llevan leyenda
    private fun captions(
        model: ReportModel,
        input: Input
    ): Captions {
        val out = Captions()
        val numbering = CaptionNumbering()
        for (section in model.sections) {
            val chapter = input.chapterOf(section.key) ?: section.chapter
            for (block in section.blocks) {
                val (kind, title) =
                    when (block) {
                        is Block.Table -> (if (block.figure) CaptionNumbering.FIGURE else CaptionNumbering.TABLE) to block.title
                        is Block.Photos -> if (block.evidence.any { it in model.photos }) CaptionNumbering.FIGURE to block.title else continue
                        is Block.MapFigure -> CaptionNumbering.FIGURE to block.title
                        is Block.LineChart -> CaptionNumbering.FIGURE to block.title
                        else -> continue
                    }
                val caption = numbering.next(kind, chapter)
                out.byBlock[block] = caption
                out.ordered += caption to title
            }
        }
        return out
    }

    // el índice: el campo TOC complejo, que Word y LibreOffice reconocen; y que Word lo actualice al abrir
    private fun toc(
        doc: XWPFDocument,
        block: Block.Toc
    ) {
        listTitle(doc, block.title)
        complexField(doc.createParagraph(), "TOC \\o \"1-${block.levels}\" \\h \\z \\u", TOC_PLACEHOLDER)
        doc.enforceUpdateFields()
    }

    // la lista de tablas o de figuras: «Tabla 5.1. Título», un tabulador y la página por PAGEREF a su marcador
    private fun captionList(
        doc: XWPFDocument,
        styles: StyleIds,
        block: Block.CaptionList,
        captions: Captions
    ) {
        listTitle(doc, block.title)
        for ((caption, title) in captions.ordered.filter { it.first.label.startsWith("${block.kind} ") }) {
            val p = paragraph(doc, styles.normal)
            p.createRun().setText("${caption.label}. $title")
            p.createRun().addTab()
            complexField(p, "PAGEREF ${caption.bookmark} \\h", "1")
        }
    }

    private fun listTitle(
        doc: XWPFDocument,
        title: String
    ) {
        doc.createParagraph().createRun().apply {
            setText(title)
            isBold = true
            fontSize = 14
        }
    }

    // un campo complejo (begin, instrucción, separate, resultado, end): el resultado es lo que se ve hasta que se actualiza
    private fun complexField(
        p: XWPFParagraph,
        instruction: String,
        placeholder: String
    ) {
        p
            .createRun()
            .ctr
            .addNewFldChar()
            .fldCharType = STFldCharType.BEGIN
        p.createRun().ctr.addNewInstrText().apply {
            stringValue = " $instruction "
            space = SpaceAttribute.Space.PRESERVE
        }
        p
            .createRun()
            .ctr
            .addNewFldChar()
            .fldCharType = STFldCharType.SEPARATE
        p.createRun().setText(placeholder)
        p
            .createRun()
            .ctr
            .addNewFldChar()
            .fldCharType = STFldCharType.END
    }

    // la tendencia (deliverables.md §7.9): un gráfico de líneas nativo con XDDF, con leyenda abajo, editable en Word
    private fun lineChart(
        doc: XWPFDocument,
        styles: StyleIds,
        block: Block.LineChart,
        caption: CaptionNumbering.Caption,
        author: String?
    ) {
        captionParagraph(doc, styles, caption, block.title)
        val p = paragraph(doc, styles.normal)
        p.alignment = ParagraphAlignment.CENTER
        val chart = doc.createChart(p.createRun(), Units.toEMU(CHART_WIDTH_CM * POINTS_PER_CM), Units.toEMU(CHART_HEIGHT_CM * POINTS_PER_CM))
        val bottom = chart.createCategoryAxis(AxisPosition.BOTTOM)
        val left = chart.createValueAxis(AxisPosition.LEFT)
        left.crosses = AxisCrosses.AUTO_ZERO
        val data = chart.createData(ChartTypes.LINE, bottom, left) as XDDFLineChartData
        val categories = XDDFDataSourcesFactory.fromArray(block.categories.toTypedArray())
        for (series in block.series) {
            val values = XDDFDataSourcesFactory.fromArray(series.values.map { it?.toBigDecimalOrNull()?.toDouble() }.toTypedArray())
            (data.addSeries(categories, values) as XDDFLineChartData.Series).apply {
                setTitle(series.name, null)
                setSmooth(false)
                setMarkerStyle(MarkerStyle.CIRCLE)
            }
        }
        chart.plot(data)
        chart.orAddLegend.position = LegendPosition.BOTTOM
        sourceParagraph(doc, styles, block.source, author)
    }

    // título, asunto y palabras clave del documento (decisión 3 del plan 8.2c): LibreOffice los lleva al PDF y a su XMP, así
    // Gotenberg no recibe metadatos y PDF/A-2b no pasa por exiftool
    private fun properties(
        doc: XWPFDocument,
        model: ReportModel,
        revision: String
    ) {
        val core = doc.properties.coreProperties
        core.title = model.meta.title
        core.setSubjectProperty("${model.meta.code} · Rev. $revision")
        core.keywords = model.meta.code
    }

    // los ids de los estilos de la plantilla por su nombre integrado (TemplateStyles): el validador ya exigió que existan
    // sin distinguir mayúsculas: Word guarda «heading 1» y LibreOffice o Google Docs, «Heading 1» (decisión 7 del plan 8.2b)
    // también lleva el contador de marcadores de la corrida de write (no es estado del DocxWriter, que JOB-05 comparte entre
    // generaciones): arranca sobre el mayor id de marcador de la plantilla
    private class StyleIds(
        doc: XWPFDocument
    ) {
        var lastBookmark: Int =
            (doc.paragraphs + doc.tables.flatMap { t -> t.rows.flatMap { r -> r.tableCells.flatMap { it.paragraphs } } })
                .flatMap { it.ctp.bookmarkStartList }
                .maxOfOrNull { it.id.toInt() } ?: 0

        private val byName: Map<String, String> =
            runCatching { doc.style.styleList }
                .getOrDefault(emptyList())
                .filter { it.name?.getVal() != null && it.styleId != null }
                .associate { it.name.getVal().lowercase() to it.styleId }

        private fun id(name: String): String? = byName[name.lowercase()]

        val normal: String? get() = id(TemplateStyles.NORMAL)
        val caption: String? get() = id(TemplateStyles.CAPTION)
        val list: String? get() = id(TemplateStyles.LIST)
        val table: String? get() = id("Table Grid")

        fun heading(level: Int): String? =
            when (level.coerceIn(1, 4)) {
                1 -> id(TemplateStyles.HEADING_1)
                2 -> id(TemplateStyles.HEADING_2)
                3 -> id(TemplateStyles.HEADING_3)
                else -> id(TemplateStyles.HEADING_4) ?: id(TemplateStyles.HEADING_3)
            }
    }

    companion object {
        const val WATERMARK = "BORRADOR"
        private val UNTITLED = setOf("portada", "indice", "perfiles")
        private val FOOTER_MARKERS = listOf("{{codigo}}", "{{revision}}")
        const val OMITTED_LAYERS = "Capas omitidas por nivel de divulgación"

        // lo que dice el índice hasta que LibreOffice (updateIndexes) o Word lo actualizan
        const val TOC_PLACEHOLDER = "El índice se actualiza al convertir a PDF o al abrir el documento"
        private const val CHART_WIDTH_CM = 15.0
        private const val CHART_HEIGHT_CM = 8.0
        private const val POINTS_PER_CM = 72.0 / 2.54
        private const val PHOTO_WIDTH_CM = 7.5
        private const val MAP_WIDTH_CM = 16.0
        private const val COVER_WIDTH_CM = 12.0
        private const val A4_WIDTH = 11906L
        private const val A4_HEIGHT = 16838L

        // las citas, las listas y los énfasis anidados que se escriben con su forma; más adentro, texto plano
        const val MAX_DEPTH = 32

        private val PARSER: Parser = Parser.builder().extensions(listOf(TablesExtension.create())).build()
    }
}
