package sgspe.deliverable

import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

// ASM-135, RQ-SEC-019, criterio 18 de deliverables.md §13.2: la base de SGSPE pasa; cada construcción prohibida se rechaza
// con su código, y una fuente que solo está en la tabla de fuentes es un aviso
class TemplateValidatorTest {
    private val validator = TemplateValidator(DeliverableProperties.Templates())

    private fun codes(
        bytes: ByteArray,
        name: String = "plantilla.dotx"
    ): List<String> = validator.validate(bytes, name).violations.map { it.code }

    // la base con sus partes cambiadas: `edit` recibe el nombre y el texto de cada parte XML; `extra`, partes nuevas
    private fun variant(
        extra: Map<String, String> = emptyMap(),
        bin: Map<String, ByteArray> = emptyMap(),
        edit: (String, String) -> String = { _, text -> text }
    ): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            ZipInputStream(ByteArrayInputStream(BaseTemplate.bytes)).use { input ->
                while (true) {
                    val entry = input.nextEntry ?: break
                    val bytes = input.readAllBytes()
                    zip.putNextEntry(ZipEntry(entry.name))
                    zip.write(
                        if (entry.name.endsWith(".xml") ||
                            entry.name.endsWith(".rels")
                        ) {
                            edit(entry.name, bytes.toString(Charsets.UTF_8)).toByteArray()
                        } else {
                            bytes
                        }
                    )
                }
            }
            extra.forEach { (name, text) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(text.toByteArray())
            }
            bin.forEach { (name, data) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(data)
            }
        }
        return out.toByteArray()
    }

    @Test
    fun `la plantilla base de SGSPE es valida`() {
        val report = validator.validate(BaseTemplate.bytes, "sgspe-base.dotx")
        assertEquals(emptyList<TemplateFinding>(), report.violations)
        assertEquals(emptyList<TemplateFinding>(), report.warnings)
    }

    @Test
    fun `macros, OLE, ActiveX, controles de contenido y vinculos externos se rechazan`() {
        assertTrue(TemplateValidator.MACROS in codes(BaseTemplate.bytes, "plantilla.dotm"))
        assertTrue(TemplateValidator.MACROS in codes(variant(mapOf("word/vbaProject.bin" to "x"))))
        // por su tipo declarado, no por la carpeta: un OLE en [Content_Types].xml y un control ActiveX en las relaciones
        val oleType = """<Override PartName="/word/datos.bin" ContentType="application/vnd.openxmlformats-officedocument.oleObject"/></Types>"""
        assertTrue(
            TemplateValidator.OLE_OBJECT in codes(variant { name, text -> if (name == "[Content_Types].xml") text.replace("</Types>", oleType) else text })
        )
        val control =
            """<Relationship Id="rId97" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/control" """ +
                """Target="x.xml"/></Relationships>"""
        assertTrue(
            TemplateValidator.ACTIVEX in
                codes(variant { name, text -> if (name == "word/_rels/document.xml.rels") text.replace("</Relationships>", control) else text })
        )
        assertTrue(
            TemplateValidator.CONTENT_CONTROL in
                codes(variant { name, text -> if (name == "word/document.xml") text.replace("<w:body>", "<w:body><w:sdt></w:sdt>") else text })
        )
        val external = """<Relationship Id="rId99" Type="http://x" Target="http://ejemplo.invalid/a.png" TargetMode="External"/></Relationships>"""
        assertTrue(
            TemplateValidator.EXTERNAL_RELATIONSHIP in
                codes(variant { name, text -> if (name == "word/_rels/document.xml.rels") text.replace("</Relationships>", external) else text })
        )
    }

    @Test
    fun `un marcador desconocido, un estilo faltante o una fuente prohibida se rechazan`() {
        val unknown = variant { name, text -> if (name == "word/document.xml") text.replace("{{fecha}}", "{{fecha}} {{precio}}") else text }
        assertEquals(listOf(TemplateFinding(TemplateValidator.UNKNOWN_PLACEHOLDER, "{{precio}}")), validator.validate(unknown, "p.dotx").violations)
        val bare =
            ByteArrayOutputStream()
                .also { out ->
                    XWPFDocument().use {
                        it.createParagraph().createRun().setText("{{titulo}}")
                        it.write(out)
                    }
                }.toByteArray()
        assertTrue(TemplateValidator.MISSING_STYLE in codes(bare, "p.docx"))
        val arial = variant { name, text -> if (name == "word/styles.xml") text.replaceFirst("w:ascii=\"Caladea\"", "w:ascii=\"Arial\"") else text }
        assertEquals(listOf(TemplateFinding(TemplateValidator.FONT_NOT_ALLOWED, "Arial")), validator.validate(arial, "p.dotx").violations)
    }

    @Test
    fun `una fuente que solo esta en la tabla de fuentes es un aviso`() {
        val table =
            """<?xml version="1.0"?><w:fonts xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">""" +
                """<w:font w:name="Calibri"/></w:fonts>"""
        val report = validator.validate(variant(mapOf("word/fontTable.xml" to table)), "p.dotx")
        assertTrue(report.valid, "${report.violations}")
        assertEquals(listOf(TemplateFinding(TemplateValidator.FONT_IN_TABLE, "Calibri")), report.warnings)
    }

    @Test
    fun `lo que no es un ZIP de Word o pasa los topes se rechaza sin abrirlo`() {
        assertTrue(TemplateValidator.NOT_WORD in codes("no soy un zip".toByteArray()))
        val small = TemplateValidator(DeliverableProperties.Templates(maxBytes = 1024, maxEntries = 10, maxInflatedBytes = 1024))
        assertTrue(small.validate(BaseTemplate.bytes, "p.dotx").violations.any { it.code == TemplateValidator.TOO_LARGE })
    }

    // revisión del plan 8.2b (I8): por tipo y por namespace, no por ruta ni por prefijo
    @Test
    fun `otro prefijo, un OLE fuera de embeddings y un campo que lee la red tambien se rechazan`() {
        val prefixed =
            variant { name, text ->
                if (name ==
                    "word/document.xml"
                ) {
                    text.replace("<w:body>", "<w:body><x:sdt xmlns:x=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"/>")
                } else {
                    text
                }
            }
        assertTrue(TemplateValidator.CONTENT_CONTROL in codes(prefixed))
        val ole =
            """<Relationship Id="rId98" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/oleObject" """ +
                """Target="media/datos.bin"/></Relationships>"""
        assertTrue(
            TemplateValidator.OLE_OBJECT in
                codes(variant { name, text -> if (name == "word/_rels/document.xml.rels") text.replace("</Relationships>", ole) else text })
        )
        val include =
            variant { name, text ->
                if (name == "word/document.xml") {
                    text.replace("<w:body>", "<w:body><w:p><w:fldSimple w:instr=\" INCLUDEPICTURE &quot;http://ejemplo.invalid/a.png&quot; \"/></w:p>")
                } else {
                    text
                }
            }
        assertEquals(listOf(TemplateFinding(TemplateValidator.FIELD_NOT_ALLOWED, "INCLUDEPICTURE")), validator.validate(include, "p.dotx").violations)
    }

    @Test
    fun `los estilos requeridos se reconocen sin distinguir mayusculas`() {
        val capitalized = variant { name, text -> if (name == "word/styles.xml") text.replace("w:val=\"heading 1\"", "w:val=\"Heading 1\"") else text }
        assertEquals(emptyList<TemplateFinding>(), validator.validate(capitalized, "p.dotx").violations)
    }

    // revisión de la Tarea 6 (decisión 21): ninguna plantilla hace que el conversor lea la red
    private val wNs = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"

    private fun body(xml: String): ByteArray = variant { name, text -> if (name == "word/document.xml") text.replace("<w:body>", "<w:body>$xml") else text }

    private fun field(vararg instructions: String): String =
        "<w:p><w:r><w:fldChar w:fldCharType=\"begin\"/></w:r>" +
            instructions.joinToString("") { "<w:r><w:instrText xml:space=\"preserve\">$it</w:instrText></w:r>" } +
            "<w:r><w:fldChar w:fldCharType=\"end\"/></w:r></w:p>"

    private fun part(inner: String) = """<?xml version="1.0"?><w:hdr xmlns:w="$wNs">$inner</w:hdr>"""

    @Test
    fun `un altChunk y una relacion aFChunk son contenido externo`() {
        val chunk = body("""<w:altChunk xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" r:id="rId5"/>""")
        assertTrue(TemplateValidator.EXTERNAL_CONTENT in codes(chunk))
        val rel =
            """<Relationship Id="rId96" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/aFChunk" """ +
                """Target="x.html"/></Relationships>"""
        assertTrue(
            TemplateValidator.EXTERNAL_CONTENT in
                codes(variant { name, text -> if (name == "word/_rels/document.xml.rels") text.replace("</Relationships>", rel) else text })
        )
    }

    @Test
    fun `INCLUDEPICTURE se detecta en una corrida y partido en varias`() {
        val single = TemplateFinding(TemplateValidator.FIELD_NOT_ALLOWED, "INCLUDEPICTURE")
        assertEquals(listOf(single), validator.validate(body(field(" INCLUDEPICTURE \"http://ejemplo.invalid/a.png\" ")), "p.dotx").violations)
        assertEquals(listOf(single), validator.validate(body(field("INCLUDE", "PICTURE \"http://ejemplo.invalid/a.png\"")), "p.dotx").violations)
        assertEquals(
            listOf(TemplateFinding(TemplateValidator.FIELD_NOT_ALLOWED, "DATABASE")),
            validator.validate(body(field("DATABASE")), "p.dotx").violations
        )
        // un campo permitido no se confunde con uno prohibido
        assertEquals(emptyList<TemplateFinding>(), validator.validate(body(field(" PAGE ")), "p.dotx").violations)
    }

    @Test
    fun `lo prohibido en una parte fuera de word o con otra extension se rechaza`() {
        val include = part("""<w:p><w:fldSimple w:instr="INCLUDEPICTURE &quot;http://ejemplo.invalid/a.png&quot;"/></w:p>""")
        val field = listOf(TemplateFinding(TemplateValidator.FIELD_NOT_ALLOWED, "INCLUDEPICTURE"))
        assertEquals(field, validator.validate(variant(mapOf("custom/header9.xml" to include)), "p.dotx").violations)
        // `.dat` declarada como encabezado por un Override
        val declared =
            variant(mapOf("word/h.dat" to include)) { name, text ->
                if (name == "[Content_Types].xml") {
                    text.replace(
                        "</Types>",
                        """<Override PartName="/word/h.dat" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.header+xml"/></Types>"""
                    )
                } else {
                    text
                }
            }
        assertEquals(field, validator.validate(declared, "p.dotx").violations)
        // y sin declararla
        assertEquals(field, validator.validate(variant(mapOf("word/h2.dat" to include)), "p.dotx").violations)
        // algo que no es XML ni un medio
        assertTrue(TemplateValidator.NOT_WORD in codes(variant(mapOf("word/raro.dat" to "no soy xml"))))
        val strict =
            """<?xml version="1.0"?><w:hdr xmlns:w="http://purl.oclc.org/ooxml/wordprocessingml/main"><w:fldSimple w:instr="LINK x"/></w:hdr>"""
        assertTrue(TemplateValidator.FIELD_NOT_ALLOWED in codes(variant(mapOf("custom/strict.xml" to strict))))
    }

    @Test
    fun `una relacion externa en cualquier carpeta y un atributo con direccion se rechazan`() {
        val rels =
            """<?xml version="1.0"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
                """<Relationship Id="rId1" Type="http://x" Target="http://ejemplo.invalid/a" TargetMode="External"/></Relationships>"""
        assertTrue(TemplateValidator.EXTERNAL_RELATIONSHIP in codes(variant(mapOf("custom/_rels/x.rels" to rels))))
        val vml = part("""<v:imagedata xmlns:v="urn:schemas-microsoft-com:vml" src="HTTP://ejemplo.invalid/a.png"/>""")
        assertTrue(TemplateValidator.EXTERNAL_RELATIONSHIP in codes(variant(mapOf("word/header9.xml" to vml))))
        val unc = part("""<v:imagedata xmlns:v="urn:schemas-microsoft-com:vml" src="\\\\servidor\\a.png"/>""")
        assertTrue(TemplateValidator.EXTERNAL_RELATIONSHIP in codes(variant(mapOf("word/header9.xml" to unc))))
    }

    @Test
    fun `un DOCTYPE y un ZIP truncado se rechazan como no Word`() {
        val xxe = """<?xml version="1.0"?><!DOCTYPE a [<!ENTITY e SYSTEM "file:///etc/passwd">]><a>&e;</a>"""
        assertTrue(TemplateValidator.NOT_WORD in codes(variant(mapOf("custom/x.xml" to xxe))))
        assertTrue(TemplateValidator.NOT_WORD in codes(BaseTemplate.bytes.copyOf(BaseTemplate.bytes.size / 2)))
    }

    // ronda 2 de la revisión de la Tarea 6: listas cerradas, falla cerrada
    private val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0)

    private fun types(add: String): (String, String) -> String =
        { name, text ->
            if (name ==
                "[Content_Types].xml"
            ) {
                text.replace("</Types>", "$add</Types>")
            } else {
                text
            }
        }

    private val includePicture = TemplateFinding(TemplateValidator.FIELD_NOT_ALLOWED, "INCLUDEPICTURE")
    private val includeInHeader = part(field("INCLUDEPICTURE \"http://ejemplo.invalid/a.png\""))

    @Test
    fun `el tipo de contenido declarado no esconde una parte XML`() {
        val asPng = variant(mapOf("word/header1.xml" to includeInHeader), edit = types("""<Override PartName="/word/header1.xml" ContentType="image/png"/>"""))
        assertEquals(listOf(includePicture), validator.validate(asPng, "p.dotx").violations)
        val defaultPng =
            variant(mapOf("word/header1.xml" to includeInHeader)) { name, text ->
                if (name == "[Content_Types].xml") text.replace("""ContentType="application/xml"""", """ContentType="image/png"""") else text
            }
        assertEquals(listOf(includePicture), validator.validate(defaultPng, "p.dotx").violations)
    }

    @Test
    fun `un medio solo pasa si su tipo esta permitido y sus bytes lo confirman`() {
        val png = types("""<Default Extension="png" ContentType="image/png"/>""")
        assertTrue(TemplateValidator.NOT_WORD in codes(variant(bin = mapOf("word/media/a.png" to "no soy un PNG".toByteArray()), edit = png)))
        assertTrue(TemplateValidator.NOT_WORD in codes(variant(bin = mapOf("word/media/a.png" to this.png))))
        assertEquals(emptyList<TemplateFinding>(), validator.validate(variant(bin = mapOf("word/media/a.png" to this.png), edit = png), "p.dotx").violations)
    }

    @Test
    fun `un logo en el encabezado y un customXml con schemaRef no son hallazgos`() {
        val logo =
            part(
                """<w:p><w:r><w:drawing><wp:inline xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing">""" +
                    """<a:graphic xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">""" +
                    """<a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture"/></a:graphic></wp:inline></w:drawing></w:r></w:p>"""
            )
        val header = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/header"
        val image = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/image"
        val withLogo =
            variant(
                mapOf(
                    "word/header1.xml" to logo,
                    "word/_rels/header1.xml.rels" to
                        """<?xml version="1.0"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
                        """<Relationship Id="rId1" Type="$image" Target="media/logo.png"/></Relationships>"""
                ),
                mapOf("word/media/logo.png" to png)
            ) { name, text ->
                when (name) {
                    "[Content_Types].xml" -> text.replace("</Types>", """<Default Extension="png" ContentType="image/png"/></Types>""")
                    "word/_rels/document.xml.rels" ->
                        text.replace("</Relationships>", """<Relationship Id="rId9" Type="$header" Target="header1.xml"/></Relationships>""")
                    else -> text
                }
            }
        assertEquals(TemplateReport(emptyList(), emptyList()), validator.validate(withLogo, "p.dotx"))
        val ds = "http://schemas.openxmlformats.org/officeDocument/2006/customXml"
        val custom =
            variant(
                mapOf(
                    "customXml/item1.xml" to "<?xml version=\"1.0\"?><datos/>",
                    "customXml/itemProps1.xml" to
                        """<?xml version="1.0"?><ds:datastoreItem xmlns:ds="$ds" ds:itemID="{00000000-0000-0000-0000-000000000000}">""" +
                        """<ds:schemaRefs><ds:schemaRef ds:uri="http://ejemplo.invalid/esquema"/></ds:schemaRefs></ds:datastoreItem>"""
                )
            )
        assertEquals(TemplateReport(emptyList(), emptyList()), validator.validate(custom, "p.dotx"))
    }

    @Test
    fun `los atributos con esquema, dos barras o barras invertidas se rechazan`() {
        for (value in listOf("smb://servidor/x", "//servidor/x", "mhtml:http://ejemplo.invalid/a", " \u00A0http://ejemplo.invalid/a")) {
            val vml = part("""<v:imagedata xmlns:v="urn:schemas-microsoft-com:vml" src="$value"/>""")
            assertTrue(TemplateValidator.EXTERNAL_RELATIONSHIP in codes(variant(mapOf("word/header9.xml" to vml))), value)
        }
    }

    @Test
    fun `dos atributos con el mismo nombre local no son un Word valido`() {
        val decoy =
            body("""<w:fldSimple xmlns:A="urn:x" A:instr="PAGE" w:instr="INCLUDEPICTURE &quot;http://ejemplo.invalid/a.png&quot;"/>""")
        assertTrue(TemplateValidator.NOT_WORD in codes(decoy))
        val rel =
            """<Relationship xmlns:A="urn:x" Id="rId95" Type="http://x" Target="http://ejemplo.invalid/a" A:TargetMode="Internal" TargetMode="External"/>"""
        assertTrue(
            TemplateValidator.NOT_WORD in
                codes(variant { name, text -> if (name == "word/_rels/document.xml.rels") text.replace("</Relationships>", "$rel</Relationships>") else text })
        )
    }

    @Test
    fun `solo los campos de la lista cerrada pasan, y cada uno por su cuenta`() {
        val stray = """<w:p><w:r><w:instrText>INCLUDEPICTURE "http://ejemplo.invalid/a.png"</w:instrText></w:r></w:p>""" + field(" PAGE ")
        assertEquals(listOf(includePicture), validator.validate(body(stray), "p.dotx").violations)
        val quoted = body(field("\"INCLUDEPICTURE\" \"http://ejemplo.invalid/a.png\""))
        assertEquals(listOf(includePicture), validator.validate(quoted, "p.dotx").violations)
        assertEquals(listOf(includePicture), validator.validate(body(field("\u00A0INCLUDEPICTURE \"x\"")), "p.dotx").violations)
        val deleted =
            body(
                "<w:p><w:r><w:fldChar w:fldCharType=\"begin\"/></w:r><w:r><w:delInstrText>INCLUDEPICTURE \"x\"</w:delInstrText></w:r>" +
                    "<w:r><w:fldChar w:fldCharType=\"end\"/></w:r></w:p>"
            )
        assertEquals(listOf(includePicture), validator.validate(deleted, "p.dotx").violations)
        val nested =
            body(
                "<w:p><w:r><w:fldChar w:fldCharType=\"begin\"/></w:r><w:r><w:instrText> REF </w:instrText></w:r>" +
                    "<w:r><w:fldChar w:fldCharType=\"begin\"/></w:r><w:r><w:instrText>INCLUDEPICTURE \"x\"</w:instrText></w:r>" +
                    "<w:r><w:fldChar w:fldCharType=\"end\"/></w:r><w:r><w:fldChar w:fldCharType=\"end\"/></w:r></w:p>"
            )
        assertEquals(listOf(includePicture), validator.validate(nested, "p.dotx").violations)
        val after =
            body(
                "<w:p><w:r><w:fldChar w:fldCharType=\"begin\"/></w:r><w:r><w:instrText> PAGE </w:instrText></w:r>" +
                    "<w:r><w:fldChar w:fldCharType=\"separate\"/></w:r><w:r><w:instrText>INCLUDEPICTURE \"x\"</w:instrText></w:r>" +
                    "<w:r><w:fldChar w:fldCharType=\"end\"/></w:r></w:p>"
            )
        assertEquals(listOf(includePicture), validator.validate(after, "p.dotx").violations)
        assertEquals(
            listOf(TemplateFinding(TemplateValidator.FIELD_NOT_ALLOWED, "MERGEFIELD")),
            validator.validate(body(field(" MERGEFIELD x ")), "p.dotx").violations
        )
        val unbalanced = body("<w:p><w:r><w:fldChar w:fldCharType=\"begin\"/></w:r><w:r><w:instrText> PAGE </w:instrText></w:r></w:p>")
        assertTrue(TemplateValidator.FIELD_NOT_ALLOWED in codes(unbalanced))
        val footer = part(field(" PAGE ") + field(" NUMPAGES ") + field(" DATE \\@ \"dd/MM/yyyy\" ") + "<w:fldSimple w:instr=\" PAGE \"/>")
        assertEquals(TemplateReport(emptyList(), emptyList()), validator.validate(variant(mapOf("word/footer1.xml" to footer)), "p.dotx"))
    }

    @Test
    fun `los estilos requeridos salen solo de la parte styles del documento principal`() {
        val missing =
            variant(mapOf("custom/x.xml" to """<w:styles xmlns:w="$wNs"><w:style><w:name w:val="List Paragraph"/></w:style></w:styles>""")) { name, text ->
                if (name == "word/styles.xml") text.replace("w:val=\"List Paragraph\"", "w:val=\"Lista\"") else text
            }
        assertEquals(listOf(TemplateFinding(TemplateValidator.MISSING_STYLE, "List Paragraph")), validator.validate(missing, "p.dotx").violations)
    }

    // ronda 3 de la revisión de la Tarea 6
    private fun fieldCodes(xml: String) = validator.validate(body(xml), "p.dotx").violations.map { it.code }

    private fun begin() = "<w:r><w:fldChar w:fldCharType=\"begin\"/></w:r>"

    private fun end() = "<w:r><w:fldChar w:fldCharType=\"end\"/></w:r>"

    private fun instr(text: String) = "<w:r><w:instrText xml:space=\"preserve\">$text</w:instrText></w:r>"

    @Test
    fun `un campo sin nombre propio no arma otro a partir de campos anidados`() {
        val forbidden = listOf(TemplateValidator.FIELD_NOT_ALLOWED)
        // { { REF a } { REF b } \\d }: el campo exterior no tiene nombre
        val built = "<w:p>" + begin() + instr(" ") + begin() + instr(" REF a ") + end() + begin() + instr(" REF b ") + end() + instr(" \\d ") + end() + "</w:p>"
        assertEquals(forbidden, fieldCodes(built).distinct())
        assertEquals(forbidden, fieldCodes("""<w:p><w:fldSimple w:instr=" \d"/></w:p>""").distinct())
        assertEquals(forbidden, fieldCodes("<w:p>" + begin() + instr("\"\" INCLUDEPICTURE \"http://ejemplo.invalid/a.png\"") + end() + "</w:p>").distinct())
        assertEquals(forbidden, fieldCodes("<w:p>" + begin() + end() + "</w:p>").distinct())
        // una comilla o un U+200B antes del campo anidado no son un nombre (revisión final del plan 8.2b, M2)
        val nested = TemplateFinding(TemplateValidator.FIELD_NOT_ALLOWED, "campo anidado antes del nombre")
        for (fake in listOf("\"", "\u200B", " \" ")) {
            val hidden = "<w:p>" + begin() + instr(fake) + begin() + instr(" REF a ") + end() + instr(" REF b ") + end() + "</w:p>"
            assertTrue(nested in validator.validate(body(hidden), "p.dotx").violations, fake)
        }
        // un campo anidado después del nombre del exterior sigue valiendo
        val legit = "<w:p>" + begin() + instr(" REF ") + begin() + instr(" SEQ x ") + end() + instr(" \\h ") + end() + "</w:p>"
        assertEquals(emptyList<TemplateFinding>(), validator.validate(body(legit), "p.dotx").violations)
    }

    @Test
    fun `el SVG no se permite`() {
        val svg = """<svg xmlns="http://www.w3.org/2000/svg"/>"""
        assertTrue(TemplateValidator.NOT_WORD in codes(variant(mapOf("word/media/a.svg" to svg))))
        val declared = types("""<Default Extension="svg" ContentType="image/svg+xml"/>""")
        assertTrue(TemplateValidator.NOT_WORD in codes(variant(mapOf("word/media/a.svg" to "<datos/>"), edit = declared)))
        // también dentro de otra parte XML, no solo como raíz (revisión final del plan 8.2b, M2)
        val inside = part("""<svg xmlns="http://www.w3.org/2000/svg"/>""")
        assertTrue(
            TemplateFinding(TemplateValidator.NOT_WORD, "SVG no permitido, use PNG (word/header9.xml)") in
                validator.validate(variant(mapOf("word/header9.xml" to inside)), "p.dotx").violations
        )
    }

    @Test
    fun `los caracteres de formato no esconden una direccion y el texto alternativo no es una direccion`() {
        val zeroWidth = "\u200B"
        val vml =
            part(
                """<v:imagedata xmlns:v="urn:schemas-microsoft-com:vml" xmlns:o="urn:schemas-microsoft-com:office:office" """ +
                    """o:href="${zeroWidth}http://ejemplo.invalid/x"/>"""
            )
        assertTrue(TemplateValidator.EXTERNAL_RELATIONSHIP in codes(variant(mapOf("word/header9.xml" to vml))))
        val alt =
            part(
                """<wp:docPr xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing" id="1" name="Logo" """ +
                    """descr="Logo: municipalidad" title="Escudo: oficial"/>"""
            )
        assertEquals(TemplateReport(emptyList(), emptyList()), validator.validate(variant(mapOf("word/header9.xml" to alt)), "p.dotx"))
    }
}
