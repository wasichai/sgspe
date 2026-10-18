package sgspe.territory

import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import wasichai.files.ContentSniffer
import java.security.MessageDigest
import java.time.LocalDate
import java.util.HexFormat
import java.util.UUID

// ASM-060: el original de una importación como evidence. lo que evidence.file admite (los 8 contentTypes del modelo) va tal
// cual; lo que no (un ZIP de Shapefile, KMZ o FileGDB, un GeoPackage, un CSV que no es UTF-8) va adjunto, byte a byte, en un PDF
class ImportEvidenceFilesTest {
    // los contentTypes de evidence.file en model.json
    private val allowed =
        listOf(
            "application/pdf",
            "image/*",
            "text/csv",
            "text/plain",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.oasis.opendocument.text",
            "application/vnd.oasis.opendocument.spreadsheet"
        )
    private val batch = UUID.fromString("00000000-0000-4000-8000-000000000060")
    private val zip = byteArrayOf(0x50, 0x4B, 0x03, 0x04, 1, 2, 3, 4)

    private fun sha(bytes: ByteArray) = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))

    private fun pack(
        name: String,
        bytes: ByteArray,
        format: ImportFormat,
        allowList: List<String>? = allowed
    ) = ImportEvidenceFiles.pack(ImportOriginal(name, bytes, format, sha(bytes), batch, "land_parcel"), allowList)

    @Test
    fun `cada formato se declara con el tipo que el sniffer de wasichai-files reconoce`() {
        assertEquals("text/csv", ImportEvidenceFiles.declaredType(ImportFormat.CSV))
        assertEquals(ImportEvidenceFiles.XLSX, ImportEvidenceFiles.declaredType(ImportFormat.XLSX))
        for (format in listOf(ImportFormat.GEOJSON, ImportFormat.KML, ImportFormat.GPX)) {
            assertEquals("text/plain", ImportEvidenceFiles.declaredType(format), format.key)
        }
        for (format in listOf(ImportFormat.SHP, ImportFormat.KMZ, ImportFormat.GDB)) {
            assertEquals("application/zip", ImportEvidenceFiles.declaredType(format), format.key)
        }
        assertEquals(ContentSniffer.OCTET_STREAM, ImportEvidenceFiles.declaredType(ImportFormat.GPKG))
    }

    @Test
    fun `lo que evidence file admite va tal cual, con su nombre y su tipo`() {
        val csv = "Codigo;Este;Norte\nTST-1;812345;9123456".toByteArray()
        val packed = pack("pozos.csv", csv, ImportFormat.CSV)
        assertFalse(packed.wrapped)
        assertEquals("pozos.csv", packed.fileName)
        assertEquals("text/csv", packed.contentType)
        assertArrayEquals(csv, packed.bytes)
        val geojson = """{"type":"FeatureCollection","features":[]}""".toByteArray()
        assertEquals("text/plain", pack("predios.geojson", geojson, ImportFormat.GEOJSON).contentType)
        val kml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><kml/>".toByteArray()
        assertFalse(pack("predios.kml", kml, ImportFormat.KML).wrapped)
        // un campo sin lista admite todo: también el ZIP
        assertFalse(pack("predios.zip", zip, ImportFormat.SHP, allowList = null).wrapped)
    }

    @Test
    fun `un ZIP, un GeoPackage o un CSV que no es UTF-8 van adjuntos byte a byte en un PDF`() {
        val latin1 = "Codigo;Descripción\nTST-1;Señal".toByteArray(Charsets.ISO_8859_1)
        val gpkg = "SQLite format 3\u0000resto".toByteArray(Charsets.ISO_8859_1)
        for ((name, bytes, format) in listOf(
            Triple("predios.zip", zip, ImportFormat.SHP),
            Triple("predios.kmz", zip, ImportFormat.KMZ),
            Triple("predios.gpkg", gpkg, ImportFormat.GPKG),
            Triple("pozos.csv", latin1, ImportFormat.CSV)
        )) {
            val packed = pack(name, bytes, format)
            assertTrue(packed.wrapped, name)
            assertEquals("$name.pdf", packed.fileName)
            assertEquals(ImportEvidenceFiles.PDF, packed.contentType)
            assertEquals(ImportEvidenceFiles.PDF, ContentSniffer.detect(packed.bytes, packed.contentType), name)
            val (embeddedName, embedded) = ImportEvidenceFiles.embedded(packed.bytes)
            assertEquals(name, embeddedName)
            assertArrayEquals(bytes, embedded, name)
            assertEquals(sha(bytes), sha(embedded))
        }
    }

    @Test
    fun `la portada dice lote, destino, formato y huella pero no el nombre del archivo`() {
        val name = "padron-titulares-\u4e2d-\u0001.zip"
        val packed = pack(name, zip, ImportFormat.SHP)
        val text = Loader.loadPDF(packed.bytes).use { PDFTextStripper().getText(it) }
        assertTrue(text.contains(batch.toString()), text)
        assertTrue(text.contains("predios"), text)
        assertTrue(text.contains("shp"), text)
        assertTrue(text.contains(sha(zip)), text)
        assertFalse(text.contains("padron"), text)
        assertEquals(name, ImportEvidenceFiles.embedded(packed.bytes).first)
    }

    @Test
    fun `solo la hoja de componentes es INTERNA, los padrones del territorio son DATOS PERSONALES`() {
        assertEquals("INTERNA", ImportEvidenceFiles.classification("project_component"))
        for (target in listOf("land_parcel", "mining_concession", "community", "environmental_feature")) {
            assertEquals("DATOS PERSONALES", ImportEvidenceFiles.classification(target), target)
        }
    }

    @Test
    fun `el titulo nombra el destino y la fecha, nunca el archivo`() {
        assertEquals("Importación de predios del 2026-10-18", ImportEvidenceFiles.title("land_parcel", LocalDate.of(2026, 10, 18)))
        assertEquals("Importación de componentes del 2026-10-18", ImportEvidenceFiles.title("project_component", LocalDate.of(2026, 10, 18)))
        assertEquals("Importación de otro_destino del 2026-10-18", ImportEvidenceFiles.title("otro_destino", LocalDate.of(2026, 10, 18)))
    }
}
