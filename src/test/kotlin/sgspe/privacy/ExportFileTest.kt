package sgspe.privacy

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.nio.file.attribute.PosixFilePermissions
import java.time.Instant

// el archivo temporal de una exportación (datos personales): carpeta 0700 y barrido de las que quedaron sin enviarse
class ExportFileTest {
    private val now = Instant.parse("2026-10-08T12:00:00Z")

    private fun folder(
        root: Path,
        name: String,
        age: Long
    ): Path =
        Files.createDirectory(root.resolve(name)).also {
            Files.writeString(it.resolve("registro.xlsx"), "x")
            Files.setLastModifiedTime(it, FileTime.from(now.minusSeconds(age)))
        }

    @Test
    fun `el barrido borra las carpetas de mas de una hora con su archivo y deja lo demas`(
        @TempDir root: Path
    ) {
        val old = folder(root, "sgspe-export-viejo", 3_601)
        val fresh = folder(root, "sgspe-export-reciente", 3_599)
        val other = folder(root, "otra-cosa", 86_400)
        ExportFile.sweep(root, now)
        assertFalse(Files.exists(old))
        assertTrue(Files.exists(fresh.resolve("registro.xlsx")))
        assertTrue(Files.exists(other))
    }

    @Test
    fun `crear un archivo barre primero y lo deja en una carpeta nueva solo de quien corre el servidor`(
        @TempDir root: Path
    ) {
        val old = folder(root, "sgspe-export-viejo", 7_200)
        val export = ExportFile.create("registro.xlsx", now, root)
        assertFalse(Files.exists(old))
        assertTrue(export.file.exists())
        assertEquals(
            root.toRealPath(),
            export.file.parentFile
                .toPath()
                .parent
                .toRealPath()
        )
        assertEquals("rwx------", PosixFilePermissions.toString(Files.getPosixFilePermissions(export.file.parentFile.toPath())))
        assertEquals("rw-------", PosixFilePermissions.toString(Files.getPosixFilePermissions(export.file.toPath())))
        export.discard()
        assertFalse(export.file.parentFile.exists())
    }

    @Test
    fun `un directorio temporal que no existe no rompe el barrido`() {
        ExportFile.sweep(Path.of("/no/existe/sgspe"), now)
    }
}
