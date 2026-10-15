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
        val arial = variant { name, text -> if (name == "word/styles.xml") text.replaceFirst("w:ascii=\"Liberation Sans\"", "w:ascii=\"Arial\"") else text }
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
}
