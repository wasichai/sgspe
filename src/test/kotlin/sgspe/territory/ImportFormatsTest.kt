package sgspe.territory

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import wasichai.core.common.ValidationException
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

// los formatos de F6 (especificación §3.4) y los controles de ZIP de RQ-SEC-020: ninguna entrada fuera de la carpeta, tope de
// entradas y de bytes inflados. nombres ficticios
class ImportFormatsTest {
    @TempDir
    lateinit var dir: File

    private fun zip(
        name: String,
        entries: Map<String, ByteArray>
    ): File {
        val file = File(dir, name)
        ZipOutputStream(file.outputStream()).use { out ->
            for ((entry, bytes) in entries) {
                out.putNextEntry(ZipEntry(entry))
                out.write(bytes)
                out.closeEntry()
            }
        }
        return file
    }

    private val shapefile =
        mapOf(
            "predios.shp" to byteArrayOf(1),
            "predios.shx" to byteArrayOf(2),
            "predios.dbf" to byteArrayOf(3),
            "predios.prj" to byteArrayOf(4)
        )

    @Test
    fun `la extension decide el formato y un ZIP se mira por dentro`() {
        val empty = File(dir, "x").apply { writeBytes(byteArrayOf(0)) }
        assertEquals(ImportFormat.CSV, ImportFormat.detect("pozos.csv", empty, 10))
        assertEquals(ImportFormat.GEOJSON, ImportFormat.detect("predios.json", empty, 10))
        assertEquals(ImportFormat.KMZ, ImportFormat.detect("Comunidades.KMZ", empty, 10))
        assertEquals(ImportFormat.GPX, ImportFormat.detect("ruta.gpx", empty, 10))
        assertEquals(ImportFormat.SHP, ImportFormat.detect("predios.zip", zip("a.zip", shapefile), 10))
        val gdb = zip("b.zip", mapOf("datos.gdb/a00000001.gdbtable" to byteArrayOf(1), "datos.gdb/gdb" to byteArrayOf(2)))
        assertEquals(ImportFormat.GDB, ImportFormat.detect("datos.zip", gdb, 10))
        assertTrue(ImportFormat.SHP.gdal && !ImportFormat.XLSX.gdal)
        assertEquals(4326, ImportFormat.KML.fixedSrid)
    }

    @Test
    fun `un formato desconocido, un ZIP sin capa o con dos clases y un ZIP roto son 400 sobre file`() {
        val junk = File(dir, "y").apply { writeText("no es un zip") }
        for ((name, file) in listOf("plano.dwg" to junk, "roto.zip" to junk, "vacio.zip" to zip("c.zip", mapOf("leeme.txt" to byteArrayOf(1)))) +
            ("mixto.zip" to zip("d.zip", shapefile + ("datos.gdb/gdb" to byteArrayOf(1))))) {
            assertEquals("file", assertThrows<ValidationException> { ImportFormat.detect(name, file, 10) }.violations.single().field, name)
        }
    }

    @Test
    fun `una entrada con punto punto o absoluta es 400 y no escribe fuera de la carpeta`() {
        for (entry in listOf("../fuera.txt", "archivo/../../fuera.txt", "/etc/fuera.txt")) {
            val bad = zip("malo-${entry.hashCode()}.zip", mapOf(entry to "x".toByteArray()))
            ImportWorkspace.create().use { workspace ->
                assertEquals(
                    "file",
                    assertThrows<ValidationException> { ImportArchives.extract(bad, workspace, "archivo", 10, 1_000) }.violations.single().field
                )
                assertFalse(Files.exists(workspace.dir.parent.resolve("fuera.txt")))
            }
        }
    }

    @Test
    fun `mas entradas o mas bytes inflados que el tope son 400`() {
        val many = zip("muchas.zip", (1..5).associate { "f$it.txt" to byteArrayOf(1) })
        val big = zip("grande.zip", mapOf("ceros.bin" to ByteArray(10_000)))
        ImportWorkspace.create().use { workspace ->
            assertThrows<ValidationException> { ImportArchives.extract(many, workspace, "a", 4, 1_000_000) }
            val e = assertThrows<ValidationException> { ImportArchives.extract(big, workspace, "b", 10, 1_000) }
            assertTrue(
                e.violations
                    .single()
                    .message
                    .contains("descomprimido")
            )
        }
    }

    @Test
    fun `el Shapefile necesita shx y dbf, y el KMZ su kml`() {
        ImportWorkspace.create().use { workspace ->
            val root = ImportArchives.extract(zip("ok.zip", shapefile), workspace, "ok", 10, 1_000)
            assertEquals("predios.shp", ImportArchives.shapefile(root).fileName.toString())
            val partial = ImportArchives.extract(zip("sin-dbf.zip", shapefile - "predios.dbf"), workspace, "sin", 10, 1_000)
            assertTrue(
                assertThrows<ValidationException> { ImportArchives.shapefile(partial) }
                    .violations
                    .single()
                    .message
                    .contains(".dbf")
            )
            val kmz =
                ImportArchives.extract(
                    zip("c.kmz", mapOf("doc.kml" to "<kml/>".toByteArray(), "files/icono.png" to byteArrayOf(1))),
                    workspace,
                    "kmz",
                    10,
                    1_000
                )
            assertEquals("doc.kml", ImportArchives.kml(kmz).fileName.toString())
            val two = ImportArchives.extract(zip("dos.zip", shapefile + ("otros.shp" to byteArrayOf(1))), workspace, "dos", 10, 1_000)
            assertThrows<ValidationException> { ImportArchives.shapefile(two) }
        }
    }
}
