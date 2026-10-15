package sgspe.deliverable

import org.apache.poi.openxml4j.opc.OPCPackage
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.XWPFTable
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.util.zip.ZipException
import java.util.zip.ZipInputStream
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

// una violación o un aviso de una plantilla: el código y un detalle sin datos del archivo más allá de nombres de estilos,
// fuentes, marcadores y campos
data class TemplateFinding(
    val code: String,
    val detail: String
)

data class TemplateReport(
    val violations: List<TemplateFinding>,
    val warnings: List<TemplateFinding>
) {
    val valid: Boolean get() = violations.isEmpty()
}

// la validación de una plantilla Word (especificación de F8 §2.2.12 y §3.3, ASM-135, RQ-SEC-019). primero el ZIP con límites de
// entradas y de bytes descomprimidos, sin abrirlo con POI (una bomba no llega a POI). después, por TIPO y no por nombre de
// archivo ni prefijo XML (una plantilla puede guardar un OLE fuera de word/embeddings o declarar el namespace con otro
// prefijo, y Word y LibreOffice la abren igual): los tipos de contenido de [Content_Types].xml (macros, VBA, OLE, ActiveX), los
// tipos de relación de cada .rels (oleObject, package, control, activeXControl, vbaProject, aFChunk) y TargetMode="External",
// y cada parte XML de word/ leída con un parser con namespaces, sin DTD ni entidades externas: controles de contenido (sdt),
// objetos (object), controles (control), fragmentos importados (altChunk) y campos que leen la red o archivos (INCLUDEPICTURE,
// INCLUDETEXT, LINK, DDE, DDEAUTO, IMPORT), que en 8.2c LibreOffice podría resolver dentro de Gotenberg. con POI: los marcadores
// desconocidos y los estilos requeridos que faltan (por nombre, sin distinguir mayúsculas). las fuentes fuera de la lista en un
// estilo, en el texto o en el tema que citan son violaciones; una que solo figura en la tabla de fuentes, un aviso. trabajo
// bloqueante: quien llama lo corre en Dispatchers.IO
class TemplateValidator(
    private val limits: DeliverableProperties.Templates
) {
    fun validate(
        bytes: ByteArray,
        fileName: String
    ): TemplateReport {
        val violations = mutableListOf<TemplateFinding>()
        val warnings = mutableListOf<TemplateFinding>()
        val name = fileName.lowercase()
        if (name.endsWith(".dotm") || name.endsWith(".docm")) violations += TemplateFinding(MACROS, "el tipo de archivo admite macros")
        if (!(name.endsWith(".dotx") || name.endsWith(".docx") || name.endsWith(".dotm") || name.endsWith(".docm"))) {
            violations += TemplateFinding(NOT_WORD, "solo plantillas .dotx o .docx")
        }
        if (bytes.size > limits.maxBytes) return TemplateReport(violations + TemplateFinding(TOO_LARGE, "pasa de ${limits.maxBytes} bytes"), warnings)
        val entries = entries(bytes) ?: return TemplateReport(violations + TemplateFinding(NOT_WORD, "no es un documento OOXML"), warnings)
        if (entries.overflow != null) return TemplateReport(violations + TemplateFinding(TOO_LARGE, entries.overflow), warnings)
        val parts = entries.parts
        if ("word/document.xml" !in parts) return TemplateReport(violations + TemplateFinding(NOT_WORD, "falta word/document.xml"), warnings)
        val xml =
            try {
                parts.filterKeys { it.endsWith(".xml") || it.endsWith(".rels") }.mapValues { (_, text) -> parse(text) }
            } catch (_: Exception) {
                return TemplateReport(violations + TemplateFinding(NOT_WORD, "una parte XML no se puede leer"), warnings)
            }
        violations += packaging(parts.keys, xml)
        val word = xml.filterKeys { it.startsWith("word/") && it.endsWith(".xml") }
        violations += content(word.values)
        if (violations.isNotEmpty()) return TemplateReport(violations.distinct(), warnings)
        try {
            XWPFDocument(OPCPackage.open(ByteArrayInputStream(bytes))).use { doc ->
                placeholders(doc).filter { it !in TemplateStyles.PLACEHOLDERS }.distinct().forEach {
                    violations += TemplateFinding(UNKNOWN_PLACEHOLDER, "{{$it}}")
                }
            }
        } catch (_: Exception) {
            return TemplateReport(violations + TemplateFinding(NOT_WORD, "POI no puede abrir el documento"), warnings)
        }
        val styleNames = xml["word/styles.xml"]?.let { elements(it, W, "name").map { e -> e.getAttributeNS(W, "val").lowercase() } }.orEmpty().toSet()
        TemplateStyles.REQUIRED.filter { it.lowercase() !in styleNames }.forEach { violations += TemplateFinding(MISSING_STYLE, it) }
        val fontParts = word.filterKeys { it == "word/styles.xml" || it == "word/document.xml" || HEADER_FOOTER.matches(it) }.values
        val fonts = fontParts.flatMap { elements(it, W, "rFonts") }
        val used = fonts.flatMap { e -> FONT_ATTRIBUTES.map { e.getAttributeNS(W, it) } }.filter { it.isNotEmpty() }.toSet()
        val themed = fonts.any { e -> FONT_ATTRIBUTES.any { e.hasAttributeNS(W, "${it}Theme") } }
        val theme =
            if (themed) {
                xml["word/theme/theme1.xml"]
                    ?.let {
                        elements(it, A, "latin").map { e ->
                            e.getAttribute("typeface")
                        }
                    }.orEmpty()
                    .filter { it.isNotEmpty() }
                    .toSet()
            } else {
                emptySet()
            }
        (used + theme).filter { it !in TemplateStyles.FONTS }.sorted().forEach { violations += TemplateFinding(FONT_NOT_ALLOWED, it) }
        val table = xml["word/fontTable.xml"]?.let { elements(it, W, "font").map { e -> e.getAttributeNS(W, "name") } }.orEmpty().toSet()
        (table - used - theme).filter { it.isNotEmpty() && it !in TemplateStyles.FONTS }.sorted().forEach { warnings += TemplateFinding(FONT_IN_TABLE, it) }
        return TemplateReport(violations.distinct(), warnings)
    }

    // lo que el paquete declara por tipo: macros y VBA en [Content_Types].xml, partes binarias peligrosas por su tipo de
    // relación y cualquier relación externa
    private fun packaging(
        names: Set<String>,
        xml: Map<String, Document>
    ): List<TemplateFinding> {
        val found = mutableListOf<TemplateFinding>()
        val types =
            xml["[Content_Types].xml"]
                ?.let { doc ->
                    (elements(doc, CT, "Default") + elements(doc, CT, "Override")).map { it.getAttribute("ContentType").lowercase() }
                }.orEmpty()
        if (names.any { it.substringAfterLast('/').equals("vbaProject.bin", ignoreCase = true) } || types.any { "macroenabled" in it || "vbaproject" in it }) {
            found += TemplateFinding(MACROS, "la plantilla trae macros")
        }
        if (types.any { "oleobject" in it || it.endsWith(".package") }) found += TemplateFinding(OLE_OBJECT, "la plantilla trae objetos incrustados")
        if (types.any { "activex" in it }) found += TemplateFinding(ACTIVEX, "la plantilla trae controles ActiveX")
        for ((part, doc) in xml.filterKeys { it.endsWith(".rels") }) {
            for (rel in elements(doc, RELS, "Relationship")) {
                val type = rel.getAttribute("Type").substringAfterLast('/')
                when (type) {
                    "oleObject", "package" -> found += TemplateFinding(OLE_OBJECT, "la plantilla trae objetos incrustados")
                    "control", "activeXControl", "activeXControlBinary" -> found += TemplateFinding(ACTIVEX, "la plantilla trae controles ActiveX")
                    "vbaProject", "wordVbaData" -> found += TemplateFinding(MACROS, "la plantilla trae macros")
                    "aFChunk" -> found += TemplateFinding(EXTERNAL_CONTENT, "la plantilla importa contenido (altChunk)")
                }
                if (rel.getAttribute("TargetMode").equals("External", ignoreCase = true)) {
                    found += TemplateFinding(EXTERNAL_RELATIONSHIP, "la plantilla cita archivos o direcciones externas ($part)")
                }
            }
        }
        return found
    }

    // lo que el contenido de Word trae, por namespace y no por prefijo
    private fun content(parts: Collection<Document>): List<TemplateFinding> {
        val found = mutableListOf<TemplateFinding>()
        for (doc in parts) {
            if (elements(doc, W, "sdt").isNotEmpty()) found += TemplateFinding(CONTENT_CONTROL, "la plantilla trae controles de contenido")
            if (elements(doc, W, "object").isNotEmpty() ||
                elements(doc, VML_OFFICE, "OLEObject").isNotEmpty()
            ) {
                found += TemplateFinding(OLE_OBJECT, "la plantilla trae objetos incrustados")
            }
            if (elements(doc, W, "control").isNotEmpty()) found += TemplateFinding(ACTIVEX, "la plantilla trae controles ActiveX")
            if (elements(doc, W, "altChunk").isNotEmpty()) found += TemplateFinding(EXTERNAL_CONTENT, "la plantilla importa contenido (altChunk)")
            val instructions = elements(doc, W, "instrText").map { it.textContent } + elements(doc, W, "fldSimple").map { it.getAttributeNS(W, "instr") }
            instructions.mapNotNull { FIELD.find(it.trim().uppercase())?.groupValues?.get(1) }.distinct().forEach {
                found += TemplateFinding(FIELD_NOT_ALLOWED, it)
            }
        }
        return found
    }

    // los marcadores del cuerpo (también en tablas), los encabezados y los pies, sobre el texto del párrafo (que ya junta las
    // corridas partidas)
    private fun placeholders(doc: XWPFDocument): List<String> {
        fun tables(list: List<XWPFTable>) = list.flatMap { t -> t.rows.flatMap { r -> r.tableCells.flatMap { it.paragraphs } } }
        val paragraphs =
            doc.paragraphs + tables(doc.tables) + doc.headerList.flatMap { it.paragraphs + tables(it.tables) } +
                doc.footerList.flatMap { it.paragraphs + tables(it.tables) }
        return paragraphs.flatMap { p ->
            TemplateStyles.PLACEHOLDER
                .findAll(p.text)
                .map { it.groupValues[1] }
                .toList()
        }
    }

    private fun elements(
        doc: Document,
        namespace: String,
        local: String
    ): List<Element> {
        val list = doc.getElementsByTagNameNS(namespace, local)
        return (0 until list.length).map { list.item(it) as Element }
    }

    // un parser sin DTD ni entidades externas (XXE): una plantilla no es de confianza
    private fun parse(text: String): Document {
        val factory =
            DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                isXIncludeAware = false
                isExpandEntityReferences = false
                setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            }
        return factory.newDocumentBuilder().parse(ByteArrayInputStream(text.toByteArray(Charsets.UTF_8)))
    }

    private class Entries(
        val parts: Map<String, String>,
        val overflow: String?
    )

    // las partes XML y .rels como texto y los nombres de las demás, con los topes de entradas y de bytes descomprimidos
    private fun entries(bytes: ByteArray): Entries? {
        val parts = linkedMapOf<String, String>()
        var count = 0
        var inflated = 0L
        try {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (++count > limits.maxEntries) return Entries(parts, "más de ${limits.maxEntries} entradas")
                    val buffer = java.io.ByteArrayOutputStream()
                    val chunk = ByteArray(BUFFER)
                    while (true) {
                        val n = zip.read(chunk)
                        if (n < 0) break
                        inflated += n
                        if (inflated > limits.maxInflatedBytes) return Entries(parts, "más de ${limits.maxInflatedBytes} bytes descomprimidos")
                        if (entry.name.endsWith(".xml") || entry.name.endsWith(".rels")) buffer.write(chunk, 0, n)
                    }
                    parts[entry.name] = buffer.toString(Charsets.UTF_8)
                }
            }
        } catch (_: ZipException) {
            return null
        }
        return if (parts.isEmpty()) null else Entries(parts, null)
    }

    companion object {
        const val MACROS = "MACROS"
        const val OLE_OBJECT = "OLE_OBJECT"
        const val ACTIVEX = "ACTIVEX"
        const val CONTENT_CONTROL = "CONTENT_CONTROL"
        const val EXTERNAL_RELATIONSHIP = "EXTERNAL_RELATIONSHIP"
        const val EXTERNAL_CONTENT = "EXTERNAL_CONTENT"
        const val FIELD_NOT_ALLOWED = "FIELD_NOT_ALLOWED"
        const val UNKNOWN_PLACEHOLDER = "UNKNOWN_PLACEHOLDER"
        const val MISSING_STYLE = "MISSING_STYLE"
        const val FONT_NOT_ALLOWED = "FONT_NOT_ALLOWED"
        const val FONT_IN_TABLE = "FONT_IN_TABLE"
        const val NOT_WORD = "NOT_WORD"
        const val TOO_LARGE = "TOO_LARGE"
        private const val BUFFER = 8192

        private const val W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
        private const val A = "http://schemas.openxmlformats.org/drawingml/2006/main"
        private const val CT = "http://schemas.openxmlformats.org/package/2006/content-types"
        private const val RELS = "http://schemas.openxmlformats.org/package/2006/relationships"
        private const val VML_OFFICE = "urn:schemas-microsoft-com:office:office"
        private val FONT_ATTRIBUTES = listOf("ascii", "hAnsi", "cs", "eastAsia")
        private val HEADER_FOOTER = Regex("""word/(header|footer)\d*\.xml""")

        // los campos que leen un archivo o la red al actualizarse
        private val FIELD = Regex("""^(INCLUDEPICTURE|INCLUDETEXT|LINK|DDEAUTO|DDE|IMPORT)\b""")
    }
}
