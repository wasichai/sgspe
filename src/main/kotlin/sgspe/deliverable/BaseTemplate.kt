package sgspe.deliverable

import org.apache.poi.openxml4j.opc.OPCPackage
import org.apache.poi.xwpf.usermodel.BreakType
import org.apache.poi.xwpf.usermodel.ParagraphAlignment
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.XWPFRelation
import org.apache.poi.xwpf.usermodel.XWPFStyle
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTFonts
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTStyle
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STJc
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STLineSpacingRule
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STStyleType
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.time.LocalDateTime
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

// la plantilla base de SGSPE (especificación de F8 §3.3, ASM-135): rige cuando el entregable no tiene template_key. la arma
// este código con POI, con Caladea y Carlito, el diseño de ReportDesign (ASM-141: A4 con sus márgenes, cuerpo justificado con
// guiones en español, títulos en el color de acento que no quedan solos al pie) y los estilos de TemplateStyles, y la portada
// con los marcadores fijos;
// TemplateValidatorTest la valida como a una subida. es código y no un .dotx versionado (decisión 6 del plan 8.2b): se revisa
// en un diff y no envejece fuera de la lista de estilos. sale como .dotx (el tipo de contenido de una plantilla de Word), con
// los mismos bytes en cada armado (`deterministic`)
object BaseTemplate {
    const val SHA_LABEL = "sgspe-base"
    private const val LANGUAGE = "es-PE"
    private const val A4_WIDTH = 11906L
    private const val A4_HEIGHT = 16838L

    val bytes: ByteArray by lazy { build() }

    internal fun buildForTest(): ByteArray = build()

    private fun build(): ByteArray {
        val docx =
            XWPFDocument().use { doc ->
                val styles = doc.createStyles()
                styles.setDefaultFonts(fonts(ReportDesign.DOCX_SERIF))
                defaults(doc)
                val body = Para(ParagraphAlignment.BOTH, after = ReportDesign.PARAGRAPH_AFTER_PT)
                style(doc, "Normal", TemplateStyles.NORMAL, ReportDesign.BODY_PT, ReportDesign.DOCX_SERIF, para = body)
                style(
                    doc,
                    "Title",
                    TemplateStyles.TITLE,
                    26.0,
                    ReportDesign.DOCX_SANS,
                    bold = true,
                    color = ReportDesign.ACCENT,
                    para = Para(ParagraphAlignment.CENTER, after = 12.0)
                )
                val headings = listOf(TemplateStyles.HEADING_1, TemplateStyles.HEADING_2, TemplateStyles.HEADING_3, TemplateStyles.HEADING_4)
                headings.forEachIndexed { level, name ->
                    val (size, before, after) = ReportDesign.HEADINGS[level]
                    style(
                        doc,
                        "Heading${level + 1}",
                        name,
                        size,
                        ReportDesign.DOCX_SANS,
                        bold = true,
                        italic = level == 3,
                        color = ReportDesign.ACCENT,
                        outline = level,
                        para = Para(ParagraphAlignment.LEFT, before = before, after = after, keep = true)
                    )
                }
                style(
                    doc,
                    "Caption",
                    TemplateStyles.CAPTION,
                    ReportDesign.CAPTION_PT,
                    ReportDesign.DOCX_SANS,
                    bold = true,
                    color = ReportDesign.ACCENT,
                    para = Para(ParagraphAlignment.LEFT, before = 9.0, after = 3.0, keep = true)
                )
                style(
                    doc,
                    "ListParagraph",
                    TemplateStyles.LIST,
                    ReportDesign.BODY_PT,
                    ReportDesign.DOCX_SERIF,
                    para = Para(ParagraphAlignment.BOTH, after = 3.0, hanging = 0.5)
                )
                style(
                    doc,
                    "SgspeFuente",
                    TemplateStyles.SOURCE,
                    ReportDesign.SOURCE_PT,
                    ReportDesign.DOCX_SANS,
                    italic = true,
                    color = ReportDesign.MUTED,
                    para = Para(ParagraphAlignment.LEFT, before = 3.0, after = 12.0)
                )
                style(
                    doc,
                    "SgspeTituloLista",
                    TemplateStyles.LIST_TITLE,
                    ReportDesign.HEADINGS[0].first,
                    ReportDesign.DOCX_SANS,
                    bold = true,
                    color = ReportDesign.ACCENT,
                    para = Para(ParagraphAlignment.LEFT, before = 0.0, after = 10.0, keep = true)
                )
                page(doc)
                cover(doc, "Title", "{{titulo}}")
                cover(doc, null, "{{proyecto}}")
                cover(doc, null, "{{mapa_portada}}")
                cover(doc, null, "Preparado para: {{preparado_para}}")
                cover(doc, null, "Suscrito por: {{suscrito_por}}")
                cover(doc, null, "{{direccion_autora}}")
                cover(doc, null, "{{fecha}}")
                doc.createParagraph().createRun().addBreak(BreakType.PAGE)
                ByteArrayOutputStream().also { doc.write(it) }.toByteArray()
            }
        // del tipo de documento al de plantilla (.dotx)
        val dotx =
            OPCPackage.open(ByteArrayInputStream(docx)).use { pkg ->
                pkg.replaceContentType(XWPFRelation.DOCUMENT.contentType, XWPFRelation.TEMPLATE.contentType)
                ByteArrayOutputStream().also { pkg.save(it) }.toByteArray()
            }
        return deterministic(dotx)
    }

    // los mismos bytes en cada armado (decisión 6 del plan 8.2b): el SHA-256 de la base va en el modelo congelado y en
    // deliverable_version.template_sha256, así que no puede depender del arranque. POI escribe la hora en cada entrada del ZIP
    // y en docProps/core.xml: aquí se rearma el ZIP con las entradas en orden fijo ([Content_Types].xml primero), la misma
    // hora y las fechas de core.xml fijas
    internal fun deterministic(bytes: ByteArray): ByteArray {
        val parts = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                parts[entry.name] = zip.readAllBytes()
            }
        }
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            val names = parts.keys.sortedWith(compareBy({ it != CONTENT_TYPES }, { it }))
            for (name in names) {
                var content = parts.getValue(name)
                if (name ==
                    CORE
                ) {
                    content = CORE_DATE.replace(content.toString(Charsets.UTF_8)) { "${it.groupValues[1]}$FIXED_DATE${it.groupValues[3]}" }.toByteArray()
                }
                zip.putNextEntry(ZipEntry(name).apply { timeLocal = FIXED_TIME })
                zip.write(content)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private const val CONTENT_TYPES = "[Content_Types].xml"
    private const val CORE = "docProps/core.xml"
    private const val FIXED_DATE = "2026-01-01T00:00:00Z"
    private val FIXED_TIME: LocalDateTime = LocalDateTime.of(2026, 1, 1, 0, 0)
    private val CORE_DATE = Regex("""(<dcterms:(?:created|modified)\b[^>]*>)([^<]*)(</dcterms:(?:created|modified)>)""")

    private fun fonts(font: String): CTFonts =
        CTFonts.Factory.newInstance().apply {
            ascii = font
            hAnsi = font
            cs = font
            eastAsia = font
        }

    // el párrafo de un estilo: alineación, espacio antes y después en puntos, que no quede solo al pie (`keep`) y la sangría
    // francesa en centímetros
    private class Para(
        val align: ParagraphAlignment,
        val before: Double = 0.0,
        val after: Double = 0.0,
        val keep: Boolean = false,
        val hanging: Double? = null
    )

    // el idioma (guiones en español) y el interlineado de todo el documento, y los guiones automáticos
    private fun defaults(doc: XWPFDocument) {
        val ctStyles = doc.styles.ctStyles
        val defaults = if (ctStyles.isSetDocDefaults) ctStyles.docDefaults else ctStyles.addNewDocDefaults()
        val rpr = (if (defaults.isSetRPrDefault) defaults.rPrDefault else defaults.addNewRPrDefault()).let { if (it.isSetRPr) it.rPr else it.addNewRPr() }
        rpr.addNewLang().setVal(LANGUAGE)
        val ppr = (if (defaults.isSetPPrDefault) defaults.pPrDefault else defaults.addNewPPrDefault()).let { if (it.isSetPPr) it.pPr else it.addNewPPr() }
        ppr.addNewSpacing().apply {
            line = BigInteger.valueOf(ReportDesign.LINE_240THS.toLong())
            lineRule = STLineSpacingRule.AUTO
        }
        doc.settings.ctSettings.addNewAutoHyphenation()
    }

    // A4 con los márgenes del diseño, el encabezado y el pie
    private fun page(doc: XWPFDocument) {
        val body = doc.document.body
        val section = if (body.isSetSectPr) body.sectPr else body.addNewSectPr()
        section.addNewPgSz().apply {
            w = BigInteger.valueOf(A4_WIDTH)
            h = BigInteger.valueOf(A4_HEIGHT)
        }
        section.addNewPgMar().apply {
            top = BigInteger.valueOf(ReportDesign.twips(ReportDesign.MARGIN_CM))
            bottom = BigInteger.valueOf(ReportDesign.twips(ReportDesign.BOTTOM_MARGIN_CM))
            left = BigInteger.valueOf(ReportDesign.twips(ReportDesign.MARGIN_CM))
            right = BigInteger.valueOf(ReportDesign.twips(ReportDesign.MARGIN_CM))
            header = BigInteger.valueOf(ReportDesign.twips(ReportDesign.HEADER_CM))
            footer = BigInteger.valueOf(ReportDesign.twips(ReportDesign.FOOTER_CM))
            gutter = BigInteger.ZERO
        }
    }

    private fun style(
        doc: XWPFDocument,
        id: String,
        name: String,
        size: Double,
        font: String,
        bold: Boolean = false,
        italic: Boolean = false,
        color: String? = null,
        outline: Int? = null,
        para: Para
    ) {
        val ct = CTStyle.Factory.newInstance()
        ct.styleId = id
        ct.type = STStyleType.PARAGRAPH
        ct.addNewName().setVal(name)
        if (id != "Normal") ct.addNewBasedOn().setVal("Normal")
        if (id != "Normal" && id != "ListParagraph") ct.addNewNext().setVal("Normal")
        ct.addNewQFormat()
        val ppr = ct.addNewPPr()
        if (para.keep) {
            ppr.addNewKeepNext()
            ppr.addNewKeepLines()
        }
        ppr.addNewSpacing().apply {
            before = BigInteger.valueOf(ReportDesign.twipsOfPoints(para.before))
            after = BigInteger.valueOf(ReportDesign.twipsOfPoints(para.after))
        }
        para.hanging?.let { cm ->
            ppr.addNewInd().apply {
                left = BigInteger.valueOf(ReportDesign.twips(cm))
                hanging = BigInteger.valueOf(ReportDesign.twips(cm))
            }
        }
        ppr.addNewJc().setVal(STJc.Enum.forInt(para.align.value))
        outline?.let { ppr.addNewOutlineLvl().setVal(BigInteger.valueOf(it.toLong())) }
        val rpr = ct.addNewRPr()
        rpr.addNewRFonts().set(fonts(font))
        if (bold) rpr.addNewB()
        if (italic) rpr.addNewI()
        color?.let { rpr.addNewColor().setVal(it) }
        rpr.addNewSz().setVal(BigInteger.valueOf(ReportDesign.halfPoints(size)))
        doc.styles.addStyle(XWPFStyle(ct))
    }

    private fun cover(
        doc: XWPFDocument,
        style: String?,
        text: String
    ) {
        val p = doc.createParagraph()
        style?.let { p.style = it }
        p.alignment = ParagraphAlignment.CENTER
        p.createRun().setText(text)
    }
}
