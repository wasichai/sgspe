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
        assertEquals(ImportFormat.KMZ, ImportFormat.detect("Comunidades.KMZ", zip("e.kmz", mapOf("doc.kml" to "<kml/>".toByteArray())), 10))
        assertEquals(ImportFormat.GPKG, ImportFormat.detect("capas.gpkg", File(dir, "g").apply { writeBytes("SQLite format 3\u0000".toByteArray()) }, 10))
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
        for (entry in listOf("../fuera.txt", "archivo/../../fuera.txt", "/etc/fuera.txt", "a\\..\\fuera.txt", "C:\\fuera.txt", "C:fuera.txt")) {
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

    @Test
    fun `un ZIP dañado o con entradas que chocan es 400 y no 500`() {
        val good = zip("buena.zip", mapOf("datos.bin" to ByteArray(2000) { (it % 7).toByte() }))
        val bytes = good.readBytes()
        val corrupt = File(dir, "corrupta.zip")
        val at = bytes.indexOf(0x50.toByte()) + 40
        corrupt.writeBytes(bytes.copyOf().also { for (i in at until at + 30) it[i] = 0x55 })
        val clash = zip("choque.zip", linkedMapOf("a" to byteArrayOf(1), "a/b.shp" to byteArrayOf(2)))
        val clash2 = zip("choque2.zip", linkedMapOf("a/" to byteArrayOf(), "a" to byteArrayOf(2)))
        val dup = zip("dup.zip", linkedMapOf("A.txt" to byteArrayOf(1), "a.txt" to byteArrayOf(2)))
        ImportWorkspace.create().use { workspace ->
            for (bad in listOf(corrupt, clash, clash2, dup)) {
                assertEquals(
                    "file",
                    assertThrows<ValidationException> {
                        ImportArchives.extract(bad, workspace, "x-${bad.name}", 10, 1_000_000)
                    }.violations.single().field,
                    bad.name
                )
            }
        }
    }

    // ASM-060: el original queda como evidencia inmutable, así que un ZIP o un KMZ solo trae las entradas de su formato
    @Test
    fun `un ZIP o un KMZ con una entrada ajena a su formato y un gpkg que no es SQLite son 400 sobre file`() {
        val withIndexes =
            shapefile + mapOf("predios.cpg" to byteArrayOf(5), "predios.qix" to byteArrayOf(6), "predios.shp.xml" to byteArrayOf(7), "capas/" to byteArrayOf())
        assertEquals(ImportFormat.SHP, ImportFormat.detect("predios.zip", zip("indices.zip", withIndexes), 10))
        val gdb = mapOf("datos.gdb/a00000001.gdbtable" to byteArrayOf(1), "datos.gdb/gdb" to byteArrayOf(2))
        val kmz = mapOf("doc.kml" to "<kml/>".toByteArray(), "files/icono.png" to byteArrayOf(1))
        assertEquals(ImportFormat.KMZ, ImportFormat.detect("comunidades.kmz", zip("ok.kmz", kmz), 10))
        for ((name, file) in listOf(
            "predios.zip" to zip("exe.zip", shapefile + ("instalar.exe" to byteArrayOf(1))),
            "predios.zip" to zip("anidado.zip", shapefile + ("otro.zip" to byteArrayOf(1))),
            "predios.zip" to zip("leeme.zip", shapefile + ("leeme.txt" to byteArrayOf(1))),
            "datos.zip" to zip("gdb-exe.zip", gdb + ("fuera.exe" to byteArrayOf(1))),
            "comunidades.kmz" to zip("macro.kmz", kmz + ("macro.docm" to byteArrayOf(1))),
            "capas.gpkg" to File(dir, "falso.gpkg").apply { writeText("no es SQLite") }
        )) {
            val e = assertThrows<ValidationException> { ImportFormat.detect(name, file, 10) }
            assertEquals("file", e.violations.single().field, file.name)
        }
        assertFalse(ImportFormat.allowedEntry(ImportFormat.SHP, "predios.exe"))
        assertTrue(ImportFormat.allowedEntry(ImportFormat.GDB, "datos.gdb/timestamps"))
        assertFalse(ImportFormat.allowedEntry(ImportFormat.GDB, "timestamps"))
    }

    @Test
    fun `los archivos de Finder no cuentan al detectar ni al elegir el shp`() {
        val finder = shapefile + mapOf("__MACOSX/._predios.shp" to byteArrayOf(9), "__MACOSX/._predios.dbf" to byteArrayOf(9))
        val file = zip("finder.zip", finder)
        assertEquals(ImportFormat.SHP, ImportFormat.detect("predios.zip", file, 10))
        ImportWorkspace.create().use { workspace ->
            val root = ImportArchives.extract(file, workspace, "f", 10, 1_000)
            assertEquals("predios.shp", ImportArchives.shapefile(root).fileName.toString())
        }
        val onlyJunk = zip("basura.zip", mapOf("__MACOSX/datos.gdb/x" to byteArrayOf(1), "leeme.txt" to byteArrayOf(1)))
        assertThrows<ValidationException> { ImportFormat.detect("basura.zip", onlyJunk, 10) }
    }
}
