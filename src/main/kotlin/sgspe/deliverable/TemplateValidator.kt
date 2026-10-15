package sgspe.deliverable

import org.apache.poi.openxml4j.opc.OPCPackage
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.XWPFTable
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
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

// la validación de una plantilla Word (especificación de F8 §2.2.12 y §3.3, ASM-135, RQ-SEC-019, decisión 21: ninguna plantilla
// hace que el conversor de PDF lea la red ni archivos). primero el ZIP con límites de entradas y de bytes descomprimidos, sin
// abrirlo con POI (una bomba no llega a POI). después, por TIPO y por nombre local de elemento, no por carpeta, extensión,
// prefijo ni versión del namespace (en OPC el papel de una parte lo da su relación; Word y LibreOffice abren un encabezado en
// `custom/h.dat` o un documento en OOXML Strict igual): toda entrada que no sea un medio `image/*` (el SVG es XML y se lee) se
// parsea como XML sin DTD ni entidades externas, y si no es XML es NOT_WORD. En cada parte: relaciones (oleObject, package,
// control, activeXControl, vbaProject, aFChunk y TargetMode="External"), controles de contenido (sdt), objetos (object),
// controles (control), fragmentos importados (altChunk), cualquier atributo que empiece por http:, https:, file:, ftp: o dos
// barras invertidas (VML src, o:href) y campos que leen la red o archivos (INCLUDEPICTURE, INCLUDETEXT, LINK, DDE, DDEAUTO,
// IMPORT, DATABASE, RD), con la instrucción de cada campo complejo juntada entre sus fldChar aunque se parta en varias
// corridas. Con POI: los marcadores desconocidos. Los estilos requeridos que faltan (por nombre, sin distinguir mayúsculas) se
// leen de la parte `styles`, y las fuentes fuera de la lista en cualquier parte, en el texto o en el tema que citan, son
// violaciones; una que solo figura en la tabla de fuentes, un aviso. trabajo bloqueante: quien llama lo corre en Dispatchers.IO
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
        val typesData = parts[CONTENT_TYPES] ?: return TemplateReport(violations + TemplateFinding(NOT_WORD, "falta $CONTENT_TYPES"), warnings)
        val contentTypes =
            try {
                parse(typesData)
            } catch (_: Exception) {
                return TemplateReport(violations + TemplateFinding(NOT_WORD, "$CONTENT_TYPES no se puede leer"), warnings)
            }
        val declared = declaredTypes(contentTypes)
        val xml = linkedMapOf<String, Document>()
        for ((part, data) in parts) {
            if (part == CONTENT_TYPES || isMedia(part, declared)) continue
            try {
                xml[part] = parse(data)
            } catch (_: Exception) {
                violations += TemplateFinding(NOT_WORD, "una parte no es XML ni un medio permitido ($part)")
            }
        }
        xml[CONTENT_TYPES] = contentTypes
        violations += packaging(parts.keys, declared, xml)
        violations += content(xml)
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
        val styleNames =
            xml.values
                .filter { root(it) == "styles" }
                .flatMap { doc -> local(doc, "name").map { e -> attribute(e, "val").lowercase() } }
                .toSet()
        TemplateStyles.REQUIRED.filter { it.lowercase() !in styleNames }.forEach { violations += TemplateFinding(MISSING_STYLE, it) }
        val fonts = xml.values.flatMap { local(it, "rFonts") }
        val used = fonts.flatMap { e -> FONT_ATTRIBUTES.map { attribute(e, it) } }.filter { it.isNotEmpty() }.toSet()
        val themed = fonts.any { e -> attributes(e).any { it.first.endsWith("theme", ignoreCase = true) } }
        val theme =
            if (themed) {
                xml.values
                    .filter { root(it) == "theme" }
                    .flatMap { doc -> local(doc, "latin").map { e -> e.getAttribute("typeface") } }
                    .filter { it.isNotEmpty() }
                    .toSet()
            } else {
                emptySet()
            }
        (used + theme).filter { it !in TemplateStyles.FONTS }.sorted().forEach { violations += TemplateFinding(FONT_NOT_ALLOWED, it) }
        val table =
            xml.values
                .filter { root(it) == "fonts" }
                .flatMap { doc -> local(doc, "font").map { e -> attribute(e, "name") } }
                .toSet()
        (table - used - theme).filter { it.isNotEmpty() && it !in TemplateStyles.FONTS }.sorted().forEach { warnings += TemplateFinding(FONT_IN_TABLE, it) }
        return TemplateReport(violations.distinct(), warnings)
    }

    // los tipos de contenido declarados, en minúsculas: por extensión (Default) y por parte (Override)
    private class Declared(
        val byExtension: Map<String, String>,
        val byPart: Map<String, String>
    ) {
        val all: Collection<String> get() = byExtension.values + byPart.values
    }

    private fun declaredTypes(doc: Document): Declared =
        Declared(
            local(doc, "Default").associate { attribute(it, "Extension").lowercase() to attribute(it, "ContentType").lowercase() },
            local(doc, "Override").associate { attribute(it, "PartName").lowercase().removePrefix("/") to attribute(it, "ContentType").lowercase() }
        )

    // un medio permitido es una parte con tipo image/* que no es SVG; todo lo demás se lee como XML
    private fun isMedia(
        part: String,
        declared: Declared
    ): Boolean {
        if (part.endsWith("/")) return true
        val type = declared.byPart[part.lowercase()] ?: declared.byExtension[part.substringAfterLast('.', "").lowercase()] ?: return false
        return type.startsWith("image/") && "svg" !in type
    }

    // lo que el paquete declara por tipo: macros y VBA en [Content_Types].xml, partes binarias peligrosas por su tipo de
    // relación y cualquier relación externa, en cualquier parte
    private fun packaging(
        names: Set<String>,
        declared: Declared,
        xml: Map<String, Document>
    ): List<TemplateFinding> {
        val found = mutableListOf<TemplateFinding>()
        val types = declared.all
        if (names.any { it.substringAfterLast('/').equals("vbaProject.bin", ignoreCase = true) } || types.any { "macroenabled" in it || "vbaproject" in it }) {
            found += TemplateFinding(MACROS, "la plantilla trae macros")
        }
        if (types.any { "oleobject" in it || it.endsWith(".package") }) found += TemplateFinding(OLE_OBJECT, "la plantilla trae objetos incrustados")
        if (types.any { "activex" in it }) found += TemplateFinding(ACTIVEX, "la plantilla trae controles ActiveX")
        for ((part, doc) in xml) {
            for (rel in local(doc, "Relationship")) {
                when (attribute(rel, "Type").substringAfterLast('/')) {
                    "oleObject", "package" -> found += TemplateFinding(OLE_OBJECT, "la plantilla trae objetos incrustados")
                    "control", "activeXControl", "activeXControlBinary" -> found += TemplateFinding(ACTIVEX, "la plantilla trae controles ActiveX")
                    "vbaProject", "wordVbaData" -> found += TemplateFinding(MACROS, "la plantilla trae macros")
                    "aFChunk" -> found += TemplateFinding(EXTERNAL_CONTENT, "la plantilla importa contenido (altChunk)")
                }
                if (attribute(rel, "TargetMode").equals("External", ignoreCase = true)) {
                    found += TemplateFinding(EXTERNAL_RELATIONSHIP, "la plantilla cita archivos o direcciones externas ($part)")
                }
            }
        }
        return found
    }

    // lo que cada parte trae, por nombre local y no por prefijo ni namespace
    private fun content(parts: Map<String, Document>): List<TemplateFinding> {
        val found = mutableListOf<TemplateFinding>()
        for ((part, doc) in parts) {
            if (local(doc, "sdt").isNotEmpty()) found += TemplateFinding(CONTENT_CONTROL, "la plantilla trae controles de contenido")
            if (local(doc, "object").isNotEmpty() || local(doc, "OLEObject").isNotEmpty()) {
                found += TemplateFinding(OLE_OBJECT, "la plantilla trae objetos incrustados")
            }
            if (local(doc, "control").isNotEmpty()) found += TemplateFinding(ACTIVEX, "la plantilla trae controles ActiveX")
            if (local(doc, "altChunk").isNotEmpty()) found += TemplateFinding(EXTERNAL_CONTENT, "la plantilla importa contenido (altChunk)")
            val addressed =
                local(doc, "*").any { e ->
                    attributes(e).any { (attr, value) ->
                        !(e.localName == "Relationship" && attr == "Type") && attr != "schemaLocation" && ADDRESS.containsMatchIn(value.trim())
                    }
                }
            if (addressed) found += TemplateFinding(EXTERNAL_RELATIONSHIP, "la plantilla cita archivos o direcciones externas ($part)")
            fields(doc).mapNotNull { FIELD.find(it)?.groupValues?.get(1) }.distinct().forEach {
                found += TemplateFinding(FIELD_NOT_ALLOWED, it)
            }
        }
        return found
    }

    // la instrucción de cada campo: la de los complejos juntada entre su fldChar begin y separate o end (con anidamiento), así se
    // parta en varias corridas, y la de los simples; sin espacios y en mayúsculas
    private fun fields(doc: Document): List<String> {
        val result = mutableListOf<String>()
        val open = ArrayDeque<StringBuilder>()
        val inResult = ArrayDeque<Boolean>()
        val loose = StringBuilder()

        fun flush(builder: StringBuilder) {
            if (builder.isNotEmpty()) result += builder.toString().filterNot { it.isWhitespace() }.uppercase()
            builder.setLength(0)
        }
        for (e in local(doc, "*")) {
            when (e.localName) {
                "fldSimple" -> result += attribute(e, "instr").filterNot { it.isWhitespace() }.uppercase()
                "fldChar" ->
                    when (attribute(e, "fldCharType")) {
                        "begin" -> {
                            flush(loose)
                            open.addLast(StringBuilder())
                            inResult.addLast(false)
                        }
                        "separate" ->
                            if (open.isNotEmpty() && !inResult.last()) {
                                flush(open.last())
                                inResult[inResult.lastIndex] = true
                            }
                        "end" ->
                            if (open.isNotEmpty()) {
                                flush(open.removeLast())
                                inResult.removeLast()
                            }
                    }
                "instrText" -> (if (open.isNotEmpty() && !inResult.last()) open.last() else loose).append(e.textContent)
            }
        }
        open.forEach { flush(it) }
        flush(loose)
        return result
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

    // los elementos por nombre local en cualquier namespace ("*" son todos, en orden del documento)
    private fun local(
        doc: Document,
        local: String
    ): List<Element> {
        val list = doc.getElementsByTagNameNS("*", local)
        return (0 until list.length).map { list.item(it) as Element }
    }

    private fun root(doc: Document): String = doc.documentElement.localName.orEmpty()

    // los atributos (nombre local, valor) sin las declaraciones de namespace
    private fun attributes(e: Element): List<Pair<String, String>> =
        (0 until e.attributes.length)
            .map { e.attributes.item(it) }
            .filter { it.namespaceURI != XMLNS }
            .map { (it.localName ?: it.nodeName) to it.nodeValue }

    private fun attribute(
        e: Element,
        name: String
    ): String = attributes(e).firstOrNull { it.first == name }?.second.orEmpty()

    // un parser sin DTD ni entidades externas (XXE): una plantilla no es de confianza
    private fun parse(data: ByteArray): Document {
        val factory =
            DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                isXIncludeAware = false
                isExpandEntityReferences = false
                setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            }
        return factory.newDocumentBuilder().parse(ByteArrayInputStream(data))
    }

    private class Entries(
        val parts: Map<String, ByteArray>,
        val overflow: String?
    )

    // todas las entradas, con los topes de entradas y de bytes descomprimidos; un ZIP roto o truncado, o con un nombre que no
    // es UTF-8, no es un documento
    private fun entries(bytes: ByteArray): Entries? {
        val parts = linkedMapOf<String, ByteArray>()
        var count = 0
        var inflated = 0L
        try {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (++count > limits.maxEntries) return Entries(parts, "más de ${limits.maxEntries} entradas")
                    val buffer = ByteArrayOutputStream()
                    val chunk = ByteArray(BUFFER)
                    while (true) {
                        val n = zip.read(chunk)
                        if (n < 0) break
                        inflated += n
                        if (inflated > limits.maxInflatedBytes) return Entries(parts, "más de ${limits.maxInflatedBytes} bytes descomprimidos")
                        buffer.write(chunk, 0, n)
                    }
                    parts[entry.name] = buffer.toByteArray()
                }
            }
        } catch (_: IOException) {
            return null
        } catch (_: IllegalArgumentException) {
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
        private const val CONTENT_TYPES = "[Content_Types].xml"
        private const val XMLNS = "http://www.w3.org/2000/xmlns/"
        private val FONT_ATTRIBUTES = listOf("ascii", "hAnsi", "cs", "eastAsia")

        // un valor que apunta a la red o a un archivo
        private val ADDRESS = Regex("""^(?i:(?:https?|file|ftp):|\\\\)""")

        // los campos que leen un archivo o la red al actualizarse (instrucción sin espacios y en mayúsculas)
        private val FIELD = Regex("""^(INCLUDEPICTURE|INCLUDETEXT|LINK|DDEAUTO|DDE|IMPORT|DATABASE|RD)""")
    }
}
