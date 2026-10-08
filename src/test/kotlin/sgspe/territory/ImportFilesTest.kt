package sgspe.territory

import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import wasichai.core.common.ValidationException
import java.io.File
import java.math.BigDecimal
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

// la lectura de las hojas de F2 (gis.md §4.1) y los controles de la carpeta temporal (RQ-SEC-020). los códigos son
// ficticios (RQ-SEC-018)
class ImportFilesTest {
    @TempDir
    lateinit var dir: File

    @Test
    fun `csv con punto y coma, comillas y coma decimal`() {
        val rows = ImportReaders.csv("Codigo;Este;Norte;Nota\r\nTST-001;\"812345,5\";9123456,25;\"dice \"\"hola\"\"; y algo\"\nTST-002;812400;9123500;\n")
        assertEquals(2, rows.size)
        assertEquals(2, rows[0].row)
        assertEquals("TST-001", rows[0].values["Codigo"])
        assertEquals(BigDecimal("812345.5"), ImportReaders.number(rows[0].values["Este"]))
        assertEquals("dice \"hola\"; y algo", rows[0].values["Nota"])
        assertNull(rows[1].values["Nota"])
        assertEquals(BigDecimal("1234.5"), ImportReaders.number("1,234.5"))
        assertNull(ImportReaders.number("norte"))
    }

    @Test
    fun `un csv exportado de Excel con BOM se lee igual`() {
        val file =
            File(dir, "excel.csv").apply {
                writeBytes(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "Codigo;Este\nTST-001;812345,5\n".toByteArray())
            }
        val row = ImportReaders.read(file, "excel.csv", 10).single()
        assertEquals("TST-001", row.values["Codigo"])
        assertEquals(BigDecimal("812345.5"), ImportReaders.number(row.values["Este"]))
    }

    @Test
    fun `xlsx de la primera hoja con encabezado, numeros sin notacion cientifica`() {
        val file = File(dir, "puntos.xlsx")
        XSSFWorkbook().use { workbook ->
            val sheet = workbook.createSheet("Hoja1")
            sheet.createRow(0).apply {
                createCell(0).setCellValue("Hole ID")
                createCell(1).setCellValue("Este")
                createCell(2).setCellValue("Norte")
            }
            sheet.createRow(1).apply {
                createCell(0).setCellValue("TST-DDH01")
                createCell(1).setCellValue(812345.0)
                createCell(2).setCellValue(9123456.75)
            }
            file.outputStream().use(workbook::write)
        }
        val rows = ImportReaders.read(file, "puntos.xlsx", 10)
        assertEquals("TST-DDH01", rows.single().values["Hole ID"])
        assertEquals("812345", rows.single().values["Este"])
        assertEquals("9123456.75", rows.single().values["Norte"])
    }

    @Test
    fun `geojson con sus propiedades y su geometria, y un formato que no es de F2`() {
        val file = File(dir, "predios.geojson")
        file.writeText(
            """{"type":"FeatureCollection","features":[{"type":"Feature","properties":{"code":"TST-P1","area":2.5},
               "geometry":{"type":"Polygon","coordinates":[[[0,0],[1,0],[1,1],[0,0]]]}}]}"""
        )
        val row = ImportReaders.read(file, "predios.geojson", 10).single()
        assertEquals("TST-P1", row.values["code"])
        assertEquals("Polygon", row.geometry?.get("type"))
        val shapefile = File(dir, "capa.zip").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        assertEquals("file", assertThrows<ValidationException> { ImportReaders.read(shapefile, "capa.zip", 10) }.violations.single().field)
    }

    @Test
    fun `mas filas que el maximo y un xlsx roto son 400 sobre file`() {
        val csv = File(dir, "muchas.csv").apply { writeText("code\n" + (1..5).joinToString("\n") { "TST-$it" }) }
        assertThrows<ValidationException> { ImportReaders.read(csv, "muchas.csv", 4) }
        val broken = File(dir, "roto.xlsx").apply { writeText("no es un zip") }
        assertEquals("file", assertThrows<ValidationException> { ImportReaders.read(broken, "roto.xlsx", 10) }.violations.single().field)
    }

    @Test
    fun `una entrada que sale de la carpeta temporal se rechaza`() {
        ImportWorkspace.create().use { workspace ->
            assertEquals(workspace.dir.resolve("capa.shp"), workspace.resolveEntry("capa.shp"))
            assertThrows<ValidationException> { workspace.resolveEntry("../../etc/passwd") }
            assertThrows<ValidationException> { workspace.resolveEntry("/etc/passwd") }
        }
    }

    @Test
    fun `ejes invertidos y mapeo de columnas con constantes`() {
        assertEquals(null, Axes.utmSwapped(BigDecimal(812345), BigDecimal(9123456)))
        assertEquals(true, Axes.utmSwapped(BigDecimal(9123456), BigDecimal(812345)) != null)
        assertEquals(true, Axes.latLonSwapped(BigDecimal("-78.5"), BigDecimal("-7.9")) != null)
        assertEquals(null, Axes.latLonSwapped(BigDecimal("-7.9"), BigDecimal("-78.5")))
        val mapping = ColumnMapping.parse("""{"code": "Hole ID", "component_type": "=SONDAJE"}""")
        val row = SourceRow(2, mapOf("Hole ID" to "TST-1", "easting" to "812345"))
        assertEquals("TST-1", mapping.value(row, "code"))
        assertEquals("SONDAJE", mapping.value(row, "component_type"))
        assertEquals("812345", mapping.value(row, "easting"))
        assertThrows<ValidationException> { ColumnMapping.parse("[1,2]") }
        // las claves del portal (Fase 2.3) en camelCase, y depth por depth_m
        val portal = ColumnMapping.parse("""{"drillType": "Tipo", "depth": "Prof"}""")
        val drill = SourceRow(3, mapOf("Tipo" to "DIAMANTINA", "Prof" to "250,5"))
        assertEquals("DIAMANTINA", portal.value(drill, "drill_type"))
        assertEquals("250,5", portal.value(drill, "depth_m"))
    }

    // una hoja de 60 MB de espacios cabe en menos de 64 KB comprimida: POI la corta por la razón de compresión
    // (ZipSecureFile). el 400 debe nombrar ese límite, y no solo decir que el archivo está roto (RQ-SEC-020)
    @Test
    fun `una bomba zip es un 400 sobre file que nombra el limite de descompresion`() {
        val valid = File(dir, "valido.xlsx")
        XSSFWorkbook().use { workbook ->
            workbook
                .createSheet("Hoja1")
                .createRow(0)
                .createCell(0)
                .setCellValue("code")
            valid.outputStream().use(workbook::write)
        }
        val bomb = File(dir, "bomba.xlsx")
        rewriteEntry(valid, bomb, "xl/worksheets/sheet1.xml") { sink ->
            sink.write(
                """<?xml version="1.0" encoding="UTF-8"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>"""
                    .toByteArray()
            )
            val chunk = ByteArray(1024 * 1024) { ' '.code.toByte() }
            repeat(60) { sink.write(chunk) }
            sink.write("</sheetData></worksheet>".toByteArray())
        }
        assertTrue(bomb.length() < 64 * 1024, "la bomba debe pesar menos de 64 KB y pesa ${bomb.length()}")
        val error = assertThrows<ValidationException> { ImportReaders.read(bomb, "bomba.xlsx", 10) }
        assertEquals("file", error.violations.single().field)
        assertTrue("descompresión" in error.violations.single().message, error.violations.single().message)
        // el mismo archivo sin la hoja inflada sí se lee: el rechazo viene del límite y no de la estructura
        assertEquals(0, ImportReaders.read(valid, "valido.xlsx", 10).size)
    }

    // una entidad externa en la hoja (XXE) no se resuelve: el archivo se rechaza y su contenido nunca llega a un valor
    @Test
    fun `un xlsx con una entidad externa se rechaza sin leer el archivo local`() {
        val secret = File(dir, "secreto.txt").apply { writeText("CONTENIDO-LOCAL") }
        val valid = File(dir, "valido.xlsx")
        XSSFWorkbook().use { workbook ->
            workbook
                .createSheet("Hoja1")
                .createRow(0)
                .createCell(0)
                .setCellValue("code")
            valid.outputStream().use(workbook::write)
        }
        val xxe = File(dir, "xxe.xlsx")
        rewriteEntry(valid, xxe, "xl/worksheets/sheet1.xml") { sink ->
            sink.write(
                """<?xml version="1.0"?><!DOCTYPE worksheet [<!ENTITY leak SYSTEM "${secret.toURI()}">]>
                   <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>
                   <row r="1"><c r="A1" t="inlineStr"><is><t>code</t></is></c></row>
                   <row r="2"><c r="A2" t="inlineStr"><is><t>&leak;</t></is></c></row>
                   </sheetData></worksheet>""".toByteArray()
            )
        }
        val error = assertThrows<ValidationException> { ImportReaders.read(xxe, "xxe.xlsx", 10) }
        assertEquals("file", error.violations.single().field)
        assertTrue("CONTENIDO-LOCAL" !in error.violations.single().message)
    }

    // una fórmula se lee como texto y nunca se evalúa (inyección de fórmulas, RQ-SEC-020): en CSV y en XLSX
    @Test
    fun `una formula se lee como texto y no se evalua`() {
        val csv = ImportReaders.csv("code;note\nTST-1;\"=HYPERLINK(\"\"http://ejemplo.test\"\";\"\"x\"\")\"\n")
        assertEquals("=HYPERLINK(\"http://ejemplo.test\";\"x\")", csv.single().values["note"])
        val file = File(dir, "formulas.xlsx")
        XSSFWorkbook().use { workbook ->
            val sheet = workbook.createSheet("Hoja1")
            sheet.createRow(0).apply {
                createCell(0).setCellValue("code")
                createCell(1).setCellValue("note")
            }
            sheet.createRow(1).apply {
                createCell(0).setCellValue("TST-1")
                createCell(1).cellFormula = "1+2"
            }
            file.outputStream().use(workbook::write)
        }
        val note = ImportReaders.read(file, "formulas.xlsx", 10).single().values["note"]
        assertEquals("1+2", note)
    }

    // Excel en español guarda «CSV» en Windows-1252 si no se elige UTF-8: las tildes no se pierden
    @Test
    fun `un csv en Windows-1252 conserva las tildes`() {
        val file = File(dir, "ansi.csv").apply { writeBytes("Código;Ubicación\nTST-001;Cajamarca ñ\n".toByteArray(charset("windows-1252"))) }
        val row = ImportReaders.read(file, "ansi.csv", 10).single()
        assertEquals("TST-001", row.values["Código"])
        assertEquals("Cajamarca ñ", row.values["Ubicación"])
    }

    // el tope de filas de un XLSX se verifica antes de materializar las filas
    @Test
    fun `un xlsx con mas filas que el maximo se rechaza`() {
        val file = File(dir, "muchas.xlsx")
        XSSFWorkbook().use { workbook ->
            val sheet = workbook.createSheet("Hoja1")
            (0..5).forEach { sheet.createRow(it).createCell(0).setCellValue(if (it == 0) "code" else "TST-$it") }
            file.outputStream().use(workbook::write)
        }
        val error = assertThrows<ValidationException> { ImportReaders.read(file, "muchas.xlsx", 4) }
        assertEquals("file", error.violations.single().field)
    }

    private fun rewriteEntry(
        source: File,
        target: File,
        entry: String,
        content: (java.io.OutputStream) -> Unit
    ) {
        ZipFile(source).use { zip ->
            ZipOutputStream(target.outputStream()).use { out ->
                zip.entries().asSequence().forEach { item ->
                    out.putNextEntry(ZipEntry(item.name))
                    if (item.name == entry) content(out) else zip.getInputStream(item).use { it.copyTo(out) }
                    out.closeEntry()
                }
            }
        }
    }
}
