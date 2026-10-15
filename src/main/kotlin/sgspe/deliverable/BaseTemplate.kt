package sgspe.deliverable

import org.apache.poi.openxml4j.opc.OPCPackage
import org.apache.poi.xwpf.usermodel.BreakType
import org.apache.poi.xwpf.usermodel.ParagraphAlignment
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.XWPFRelation
import org.apache.poi.xwpf.usermodel.XWPFStyle
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTFonts
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTStyle
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STStyleType
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.time.LocalDateTime
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

// la plantilla base de SGSPE (especificación de F8 §3.3, ASM-135): rige cuando el entregable no tiene template_key. la arma
// este código con POI, con solo Liberation Sans y los estilos de TemplateStyles, y la portada con los marcadores fijos;
// TemplateValidatorTest la valida como a una subida. es código y no un .dotx versionado (decisión 6 del plan 8.2b): se revisa
// en un diff y no envejece fuera de la lista de estilos. sale como .dotx (el tipo de contenido de una plantilla de Word), con
// los mismos bytes en cada armado (`deterministic`)
object BaseTemplate {
    const val SHA_LABEL = "sgspe-base"
    private const val FONT = "Liberation Sans"

    val bytes: ByteArray by lazy { build() }

    internal fun buildForTest(): ByteArray = build()

    private fun build(): ByteArray {
        val docx =
            XWPFDocument().use { doc ->
                val styles = doc.createStyles()
                styles.setDefaultFonts(fonts())
                style(doc, "Normal", TemplateStyles.NORMAL, 22, bold = false, outline = null)
                style(doc, "Title", TemplateStyles.TITLE, 40, bold = true, outline = null)
                style(doc, "Heading1", TemplateStyles.HEADING_1, 28, bold = true, outline = 0)
                style(doc, "Heading2", TemplateStyles.HEADING_2, 24, bold = true, outline = 1)
                style(doc, "Heading3", TemplateStyles.HEADING_3, 22, bold = true, outline = 2)
                style(doc, "Caption", TemplateStyles.CAPTION, 20, bold = true, outline = null)
                style(doc, "ListParagraph", TemplateStyles.LIST, 22, bold = false, outline = null)
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

    private fun fonts(): CTFonts =
        CTFonts.Factory.newInstance().apply {
            ascii = FONT
            hAnsi = FONT
            cs = FONT
            eastAsia = FONT
        }

    private fun style(
        doc: XWPFDocument,
        id: String,
        name: String,
        halfPoints: Int,
        bold: Boolean,
        outline: Int?
    ) {
        val ct = CTStyle.Factory.newInstance()
        ct.styleId = id
        ct.type = STStyleType.PARAGRAPH
        ct.addNewName().setVal(name)
        if (id != "Normal") ct.addNewBasedOn().setVal("Normal")
        ct.addNewQFormat()
        outline?.let { ct.addNewPPr().addNewOutlineLvl().setVal(BigInteger.valueOf(it.toLong())) }
        val rpr = ct.addNewRPr()
        rpr.addNewRFonts().set(fonts())
        rpr.addNewSz().setVal(BigInteger.valueOf(halfPoints.toLong()))
        if (bold) rpr.addNewB()
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
