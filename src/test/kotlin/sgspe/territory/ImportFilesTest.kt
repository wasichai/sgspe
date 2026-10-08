package sgspe.territory

import org.apache.poi.openxml4j.util.ZipSecureFile
import org.apache.poi.xssf.streaming.SXSSFWorkbook
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
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
        val created =
            ImportWorkspace.create().use { workspace ->
                assertEquals(workspace.dir.resolve("capa.shp"), workspace.resolveEntry("capa.shp"))
                assertThrows<ValidationException> { workspace.resolveEntry("../../etc/passwd") }
                assertThrows<ValidationException> { workspace.resolveEntry("/etc/passwd") }
                workspace.dir
            }
        assertTrue(
            !java.nio.file.Files
                .exists(created),
            "la carpeta temporal debe borrarse al cerrar"
        )
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

    // una entidad externa en la hoja (XXE) no se resuelve: el archivo se rechaza por el DOCTYPE y su contenido nunca llega a
    // un valor. el control positivo es el mismo libro sin DOCTYPE, que se lee
    @Test
    fun `un xlsx con una entidad externa se rechaza por el DOCTYPE y sin esa declaracion se lee`() {
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

        fun sheet(
            doctype: String,
            value: String
        ) = """<?xml version="1.0"?>$doctype
               <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>
               <row r="1"><c r="A1" t="inlineStr"><is><t>code</t></is></c></row>
               <row r="2"><c r="A2" t="inlineStr"><is><t>$value</t></is></c></row>
               </sheetData></worksheet>""".toByteArray()
        val clean = File(dir, "limpio.xlsx")
        rewriteEntry(valid, clean, "xl/worksheets/sheet1.xml") { it.write(sheet("", "TST-1")) }
        assertEquals("TST-1", ImportReaders.read(clean, "limpio.xlsx", 10).single().values["code"])
        val xxe = File(dir, "xxe.xlsx")
        rewriteEntry(valid, xxe, "xl/worksheets/sheet1.xml") {
            it.write(sheet("""<!DOCTYPE worksheet [<!ENTITY leak SYSTEM "${secret.toURI()}">]>""", "&leak;"))
        }
        val error = assertThrows<ValidationException> { ImportReaders.read(xxe, "xxe.xlsx", 10) }
        assertEquals("file", error.violations.single().field)
        assertTrue("DOCTYPE" in error.violations.single().message, error.violations.single().message)
        assertTrue("CONTENIDO-LOCAL" !in error.violations.single().message)
    }

    // una fórmula nunca se evalúa (inyección de fórmulas, RQ-SEC-020): en CSV es el texto y en XLSX se lee el valor que Excel
    // dejó guardado, no la fórmula; sin valor guardado la celda queda vacía
    @Test
    fun `una formula no se evalua, en csv es texto y en xlsx se lee su valor guardado`() {
        val csv = ImportReaders.csv("code;note\nTST-1;\"=HYPERLINK(\"\"http://ejemplo.test\"\";\"\"x\"\")\"\n")
        assertEquals("=HYPERLINK(\"http://ejemplo.test\";\"x\")", csv.single().values["note"])
        val file = File(dir, "formulas.xlsx")
        XSSFWorkbook().use { workbook ->
            val sheet = workbook.createSheet("Hoja1")
            sheet.createRow(0).apply {
                listOf("code", "este", "calculado", "sin_valor").forEachIndexed { column, title -> createCell(column).setCellValue(title) }
            }
            sheet.createRow(1).apply {
                createCell(0).setCellValue("TST-1")
                createCell(1).cellFormula = "800000+12345"
                createCell(2).cellFormula = "A2&\"-X\""
            }
            workbook.creationHelper.createFormulaEvaluator().evaluateAll()
            sheet.getRow(1).createCell(3).cellFormula = "1+2"
            file.outputStream().use(workbook::write)
        }
        val row = ImportReaders.read(file, "formulas.xlsx", 10).single()
        assertEquals("812345", row.values["este"])
        assertEquals("TST-1-X", row.values["calculado"])
        assertNull(row.values["sin_valor"])
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

    @Test
    fun `una comilla suelta en mitad de un campo es literal y no se come las filas siguientes`() {
        val rows = ImportReaders.csv("Codigo;Nota;Este\nTST-1;tubo 2\" de acero;800000\nTST-2;ok;800001\nTST-3;ok;800002\n")
        assertEquals(3, rows.size)
        assertEquals("tubo 2\" de acero", rows[0].values["Nota"])
        assertEquals("800000", rows[0].values["Este"])
        assertEquals("TST-3", rows[2].values["Codigo"])
        // una celda citada con salto de línea, comillas dobles y el cierre antes del separador
        val quoted = ImportReaders.csv("a;b\n\"uno\ndos \"\"x\"\"\";2\n").single()
        assertEquals("uno\ndos \"x\"", quoted.values["a"])
        assertEquals("2", quoted.values["b"])
    }

    @Test
    fun `una comilla sin cerrar al final es un 400 sobre file`() {
        val error = assertThrows<ValidationException> { ImportReaders.csv("a;b\nTST-1;\"sin cerrar\nTST-2;3\n") }
        assertEquals("file", error.violations.single().field)
        assertTrue("comilla sin cerrar" in error.violations.single().message)
    }

    @Test
    fun `el separador se mide fuera de las comillas y admite tabulador`() {
        val commaInQuotes = ImportReaders.csv("\"Este, m\";\"Norte, m\"\n812345;9123456\n").single()
        assertEquals("812345", commaInQuotes.values["Este, m"])
        assertEquals("9123456", commaInQuotes.values["Norte, m"])
        val tabs = ImportReaders.csv("Codigo\tEste\nTST-1\t812345,5\n").single()
        assertEquals("TST-1", tabs.values["Codigo"])
        assertEquals(BigDecimal("812345.5"), ImportReaders.number(tabs.values["Este"]))
    }

    @Test
    fun `numeros con separador de miles en las dos convenciones`() {
        assertEquals(BigDecimal("1234.56"), ImportReaders.number("1.234,56"))
        assertEquals(BigDecimal("1234.5"), ImportReaders.number("1,234.5"))
        assertEquals(BigDecimal("1234567.5"), ImportReaders.number("1.234.567,5"))
        assertEquals(BigDecimal("0.5"), ImportReaders.number("0,5"))
    }

    @Test
    fun `un encabezado repetido o una columna con datos sin encabezado es un 400 que lo nombra`() {
        val repeated = assertThrows<ValidationException> { ImportReaders.csv("code;este;este\nTST-1;1;2\n") }
        assertEquals("file", repeated.violations.single().field)
        assertTrue("«este»" in repeated.violations.single().message, repeated.violations.single().message)
        val headless = assertThrows<ValidationException> { ImportReaders.csv("code;;este\nTST-1;x;2\n") }
        assertTrue("columna 2" in headless.violations.single().message, headless.violations.single().message)
        // una columna sin encabezado y sin datos no estorba
        assertEquals("TST-1", ImportReaders.csv("code;;este\nTST-1;;2\n").single().values["code"])
    }

    // el tope corta la lectura: el lector de un CSV infinito termina porque se detiene en la fila maxRows + 1 (si leyera todo
    // antes de contar, nunca volvería). el mensaje nombra el tope y no una cuenta total
    @Test
    fun `un csv enorme se rechaza por el tope de filas sin leerlo entero`() {
        val endless =
            object : java.io.Reader() {
                private val line = "TST-1;812345;9123456\n"
                private var position = 0
                private var header = true

                override fun read(
                    buffer: CharArray,
                    offset: Int,
                    length: Int
                ): Int {
                    val text = if (header) "code;este;norte\n" else line
                    for (i in 0 until length) {
                        buffer[offset + i] = text[position++ % text.length]
                        if (header && position == text.length) {
                            header = false
                            position = 0
                            return i + 1
                        }
                    }
                    return length
                }

                override fun close() = Unit
            }
        val error = assertThrows<ValidationException> { ImportReaders.csv(endless, 100) }
        assertEquals("file", error.violations.single().field)
        assertTrue("más de 100 filas" in error.violations.single().message, error.violations.single().message)
    }

    @Test
    fun `un xlsx grande y legitimo se rechaza por el tope de filas, y las filas vacias no cuentan`() {
        val file = File(dir, "grande.xlsx")
        SXSSFWorkbook(50).use { workbook ->
            val sheet = workbook.createSheet("Hoja1")
            sheet.createRow(0).createCell(0).setCellValue("code")
            (1..200_000).forEach { sheet.createRow(it).createCell(0).setCellValue("TST-$it") }
            file.outputStream().use(workbook::write)
            workbook.dispose()
        }
        val error = assertThrows<ValidationException> { ImportReaders.read(file, "grande.xlsx", 1_000) }
        assertEquals("file", error.violations.single().field)
        assertTrue("más de 1000 filas" in error.violations.single().message, error.violations.single().message)
        // filas con formato pero vacías hasta la 5 000 no cuentan contra el tope
        val sparse = File(dir, "dispersa.xlsx")
        XSSFWorkbook().use { workbook ->
            val sheet = workbook.createSheet("Hoja1")
            sheet.createRow(0).createCell(0).setCellValue("code")
            sheet.createRow(1).createCell(0).setCellValue("TST-1")
            sheet.createRow(5_000).createCell(0)
            sparse.outputStream().use(workbook::write)
        }
        assertEquals(1, ImportReaders.read(sparse, "dispersa.xlsx", 4).size)
    }

    // el tope de bytes inflados de una entrada: se baja a 1 MB solo dentro de la prueba y se restaura
    @Test
    fun `una hoja que se infla mas que el tope de la entrada es un 400 que nombra la descompresion`() {
        val file = File(dir, "inflada.xlsx")
        SXSSFWorkbook(50).use { workbook ->
            val sheet = workbook.createSheet("Hoja1")
            sheet.createRow(0).createCell(0).setCellValue("code")
            val random = java.util.Random(7)
            (1..30_000).forEach {
                sheet.createRow(it).createCell(0).setCellValue(java.lang.Long.toString(random.nextLong(), 36) + java.lang.Long.toString(random.nextLong(), 36))
            }
            file.outputStream().use(workbook::write)
            workbook.dispose()
        }
        assertEquals(30_000, ImportReaders.read(file, "inflada.xlsx", 100_000).size)
        ZipSecureFile.setMaxEntrySize(512L * 1024)
        try {
            val error = assertThrows<ValidationException> { ImportReaders.read(file, "inflada.xlsx", 100_000) }
            assertEquals("file", error.violations.single().field)
            assertTrue("descompresión" in error.violations.single().message, error.violations.single().message)
        } finally {
            ZipSecureFile.setMaxEntrySize(ImportReaders.MAX_ENTRY_BYTES)
        }
    }

    @Test
    fun `un csv en Windows-1252 con la tilde despues del primer bloque se lee igual`() {
        val body = "Código;Nota\n" + (1..5_000).joinToString("") { "TST-$it;ok\n" } + "TST-9;Cajamarca ñ\n"
        val file = File(dir, "ansi-largo.csv").apply { writeBytes(body.toByteArray(charset("windows-1252"))) }
        val rows = ImportReaders.read(file, "ansi-largo.csv", 10_000)
        assertEquals(5_001, rows.size)
        assertEquals("Cajamarca ñ", rows.last().values["Nota"])
    }

    @Test
    fun `geojson sin notacion cientifica, con type al final y un tope que corta la lectura`() {
        val late = File(dir, "tarde.geojson")
        late.writeText(
            """{"features":[{"type":"Feature","properties":{"code":10000000.0,"area":1.5E7,"n":3},"geometry":{"type":"Point","coordinates":[812345.25,9123456.5]}}],"type":"FeatureCollection"}"""
        )
        val row = ImportReaders.read(late, "tarde.geojson", 10).single()
        assertEquals("10000000", row.values["code"])
        assertEquals("15000000", row.values["area"])
        assertEquals("3", row.values["n"])
        assertEquals("Point", row.geometry?.get("type"))
        val single =
            File(dir, "uno.geojson").apply {
                writeText("""{"type":"Feature","properties":{"code":"TST-1"},"geometry":{"type":"Point","coordinates":[0,0]}}""")
            }
        assertEquals("TST-1", ImportReaders.read(single, "uno.geojson", 10).single().values["code"])
        val many = File(dir, "muchos.geojson")
        many.writeText(
            """{"type":"FeatureCollection","features":[""" +
                (1..50).joinToString(",") { """{"type":"Feature","properties":{"code":"TST-$it"},"geometry":null}""" } + "]}"
        )
        val error = assertThrows<ValidationException> { ImportReaders.read(many, "muchos.geojson", 10) }
        assertTrue("más de 10 filas" in error.violations.single().message, error.violations.single().message)
        val broken = File(dir, "roto.geojson").apply { writeText("""{"type":"FeatureCollection","features":[""") }
        assertEquals("file", assertThrows<ValidationException> { ImportReaders.read(broken, "roto.geojson", 10) }.violations.single().field)
    }

    @Test
    fun `una entrada con NUL es un 400 y no repite el nombre`() {
        ImportWorkspace.create().use { workspace ->
            val error = assertThrows<ValidationException> { workspace.resolveEntry("capa\u0000.shp") }
            assertEquals("file", error.violations.single().field)
            assertTrue("capa" !in error.violations.single().message)
        }
    }

    // un exponente enorme no se expande: BigDecimal.toPlainString de «1E+999999999» agota el heap con 3 KB de archivo. cada
    // formato lo rechaza con un 400 sobre file que nombra la celda o el feature, y rápido
    @Test
    @Timeout(20)
    fun `un numero con exponente enorme es un 400 en xlsx, geojson y csv`() {
        val valid = File(dir, "valido.xlsx")
        XSSFWorkbook().use { workbook ->
            workbook
                .createSheet("Hoja1")
                .createRow(0)
                .createCell(0)
                .setCellValue("code")
            valid.outputStream().use(workbook::write)
        }
        listOf("1E+999999999", "1E-999999999", "1E+400").forEach { number ->
            val xlsx = File(dir, "exponente.xlsx")
            rewriteEntry(valid, xlsx, "xl/worksheets/sheet1.xml") {
                it.write(
                    """<?xml version="1.0"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>
                       <row r="1"><c r="A1" t="inlineStr"><is><t>este</t></is></c></row>
                       <row r="2"><c r="A2"><v>$number</v></c></row></sheetData></worksheet>""".toByteArray()
                )
            }
            val fromXlsx = assertThrows<ValidationException> { ImportReaders.read(xlsx, "exponente.xlsx", 10) }.violations.single()
            assertEquals("file", fromXlsx.field)
            assertTrue("fila 2, columna 1" in fromXlsx.message, fromXlsx.message)

            val inProperty =
                File(dir, "propiedad.geojson").apply {
                    writeText("""{"type":"FeatureCollection","features":[{"type":"Feature","properties":{"code":${number.lowercase()}},"geometry":null}]}""")
                }
            val fromProperty = assertThrows<ValidationException> { ImportReaders.read(inProperty, "propiedad.geojson", 10) }.violations.single()
            assertEquals("file", fromProperty.field)
            assertTrue("feature 1" in fromProperty.message, fromProperty.message)
            val inCoordinate =
                File(dir, "coordenada.geojson").apply {
                    writeText("""{"type":"Feature","properties":{},"geometry":{"type":"Point","coordinates":[${number.lowercase()},0.5]}}""")
                }
            assertEquals("file", assertThrows<ValidationException> { ImportReaders.read(inCoordinate, "coordenada.geojson", 10) }.violations.single().field)

            val csv = File(dir, "exponente.csv").apply { writeText("code;este\nTST-1;${number.lowercase()}\n") }
            val fromCsv = assertThrows<ValidationException> { ImportReaders.read(csv, "exponente.csv", 10) }.violations.single()
            assertEquals("file", fromCsv.field)
            assertTrue("fila 2, columna 2" in fromCsv.message, fromCsv.message)
            assertNull(ImportReaders.number(number.lowercase()))
        }
        // dentro de las cotas se lee igual: una coordenada UTM con decimales y un valor pequeño
        assertEquals(BigDecimal("812345.25"), ImportReaders.number("812345.25"))
        val small = File(dir, "pequeno.geojson").apply { writeText("""{"type":"Feature","properties":{"v":1e-7},"geometry":null}""") }
        assertEquals("0.0000001", ImportReaders.read(small, "pequeno.geojson", 10).single().values["v"])
    }

    // BigDecimal(String) es cuadrático en las cifras: un millón de dígitos (más de 20 s si se convirtieran) se rechazan por su largo, sin convertirlos. en el XLSX los
    // dígitos son aleatorios para que la razón de compresión no actúe antes
    @Test
    @Timeout(5)
    fun `una celda numerica de un millon de digitos es un 400 de numero fuera de rango sin convertirla`() {
        val digits = java.util.Random(11).let { random -> buildString { repeat(1_000_000) { append(('0'.code + random.nextInt(10)).toChar()) } } }
        val csvError = assertThrows<ValidationException> { ImportReaders.csv("code;este\nTST-1;$digits\n") }
        assertEquals("Número fuera de rango", csvError.message)
        assertEquals("file", csvError.violations.single().field)
        assertNull(ImportReaders.number(digits))
        // un texto largo que empieza con un número no es un número fuera de rango: es texto
        val note = "2024 " + "nota ".repeat(40)
        assertEquals(note.trim(), ImportReaders.csv("code;nota\nTST-1;$note\n").single().values["nota"])

        val valid = File(dir, "valido.xlsx")
        XSSFWorkbook().use { workbook ->
            workbook
                .createSheet("Hoja1")
                .createRow(0)
                .createCell(0)
                .setCellValue("code")
            valid.outputStream().use(workbook::write)
        }
        val xlsx = File(dir, "digitos.xlsx")
        rewriteEntry(valid, xlsx, "xl/worksheets/sheet1.xml") {
            it.write(
                """<?xml version="1.0"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>
                   <row r="1"><c r="A1" t="inlineStr"><is><t>este</t></is></c></row>
                   <row r="2"><c r="A2"><v>$digits</v></c></row></sheetData></worksheet>""".toByteArray()
            )
        }
        val xlsxError = assertThrows<ValidationException> { ImportReaders.read(xlsx, "digitos.xlsx", 10) }
        assertEquals("Número fuera de rango", xlsxError.message)
        assertTrue("fila 2, columna 1" in xlsxError.violations.single().message, xlsxError.violations.single().message)
    }

    // la cota mide la racha más larga de dígitos, no el largo total: una lista de cifras cortas que alguien pega en una columna de
    // notas (`1,2,3,…`) es texto y no rechaza el archivo; una racha de más de 40 dígitos sí es un número fuera de rango
    @Test
    @Timeout(5)
    fun `una lista de cifras cortas es texto y una racha de mas de 40 digitos es un numero fuera de rango`() {
        val list = (1..60).joinToString(",")
        assertTrue(list.length > 52)
        assertEquals(list, ImportReaders.csv("code;nota\nTST-1;$list\n").single().values["nota"])
        val coordinates = (1..30).joinToString(",") { "-78.$it" }
        assertEquals(coordinates, ImportReaders.csv("code;nota\nTST-1;$coordinates\n").single().values["nota"])
        // la lista larga con una racha de 41 dígitos sí se rechaza, nombrando la fila y la columna
        val long = "1".repeat(41)
        assertEquals("Número fuera de rango", assertThrows<ValidationException> { ImportReaders.csv("code;nota\nTST-1;$list,$long\n") }.message)
        // y 40 dígitos exactos dentro de una lista no
        assertEquals("$list,${"1".repeat(40)}", ImportReaders.csv("code;nota\nTST-1;$list,${"1".repeat(40)}\n").single().values["nota"])
    }

    // la interrupción de un hilo cancelado se propaga como InterruptedException y no se confunde con un archivo inválido,
    // lea un CSV o un XLSX (el SAX envuelve la excepción)
    @Test
    fun `un hilo interrumpido corta la lectura de csv y xlsx con InterruptedException`() {
        val csv = File(dir, "largo.csv").apply { writeText("code\n" + (1..3_000).joinToString("\n") { "TST-$it" }) }
        val xlsx = File(dir, "largo.xlsx")
        XSSFWorkbook().use { workbook ->
            val sheet = workbook.createSheet("Hoja1")
            (0..3_000).forEach { sheet.createRow(it).createCell(0).setCellValue(if (it == 0) "code" else "TST-$it") }
            xlsx.outputStream().use(workbook::write)
        }
        try {
            Thread.currentThread().interrupt()
            assertThrows<InterruptedException> { ImportReaders.read(csv, "largo.csv", 10_000) }
            Thread.currentThread().interrupt()
            assertThrows<InterruptedException> { ImportReaders.read(xlsx, "largo.xlsx", 10_000) }
        } finally {
            Thread.interrupted()
        }
        assertEquals(3_000, ImportReaders.read(xlsx, "largo.xlsx", 10_000).size)
    }

    @Test
    fun `un elemento de features que no es un objeto es un 400 que lo nombra`() {
        val file =
            File(dir, "nulo.geojson").apply {
                writeText(
                    """{"type":"FeatureCollection","features":[{"type":"Feature","properties":{"code":"A"}},null,{"type":"Feature","properties":{"code":"B"}}]}"""
                )
            }
        val error = assertThrows<ValidationException> { ImportReaders.read(file, "nulo.geojson", 10) }.violations.single()
        assertEquals("file", error.field)
        assertTrue("elemento 2" in error.message, error.message)
        val trailing = File(dir, "basura.geojson").apply { writeText("""{"type":"FeatureCollection","features":[]} {"x":1}""") }
        assertEquals("file", assertThrows<ValidationException> { ImportReaders.read(trailing, "basura.geojson", 10) }.violations.single().field)
    }

    // el BOM de Excel UTF-8 no impide que la comilla del encabezado citado abra: la coma de adentro no es un separador
    @Test
    fun `un csv con BOM y encabezado citado con comas se lee en dos columnas`() {
        val file =
            File(dir, "bom-citado.csv").apply {
                writeBytes(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "\"Este, m, UTM\";\"Norte, m\"\n812345;9123456\n".toByteArray())
            }
        val row = ImportReaders.read(file, "bom-citado.csv", 10).single()
        assertEquals("812345", row.values["Este, m, UTM"])
        assertEquals("9123456", row.values["Norte, m"])
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
