package sgspe.privacy

import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.core.io.buffer.DataBufferUtils
import org.springframework.core.io.buffer.DefaultDataBufferFactory
import reactor.core.publisher.Flux
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.time.Duration
import java.time.Instant

// un archivo listo para descargar, en una carpeta propia de permisos 0700 (el XLSX lleva datos personales). stream() lo envía y
// lo borra con su carpeta cuando el flujo termina, falla o se cancela (Flux.using); discard() lo borra sin enviarlo. si el
// flujo nunca llega a suscribirse (la respuesta se descarta antes), nada lo borra en ese momento: al comenzar cada exportación
// se barren las carpetas sgspe-export-* de más de una hora (sweep), así que un archivo así dura a lo sumo hasta la primera
// exportación pasada la hora, no para siempre. si el servidor no recibe más exportaciones, queda hasta que se reinicie el
// directorio temporal del sistema
class ExportFile(
    val file: File,
    val filename: String
) {
    fun stream(): Flux<DataBuffer> =
        Flux.using(
            { file },
            { DataBufferUtils.read(FileSystemResource(it), DefaultDataBufferFactory.sharedInstance, BUFFER_BYTES) },
            { discard() }
        )

    fun discard() {
        file.delete()
        file.parentFile?.delete()
    }

    companion object {
        const val PREFIX = "sgspe-export-"
        val MAX_AGE: Duration = Duration.ofHours(1)
        private const val BUFFER_BYTES = 64 * 1024
        private val PRIVATE_DIRECTORY = PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------"))
        private val PRIVATE_FILE = PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"))

        // barre las carpetas viejas y crea un archivo vacío dentro de una carpeta nueva solo de quien corre el servidor
        fun create(
            filename: String,
            now: Instant,
            root: Path = Path.of(System.getProperty("java.io.tmpdir"))
        ): ExportFile {
            sweep(root, now)
            val directory = Files.createTempDirectory(root, PREFIX, PRIVATE_DIRECTORY)
            return ExportFile(Files.createFile(directory.resolve("registro.xlsx"), PRIVATE_FILE).toFile(), filename)
        }

        // borra las carpetas sgspe-export-* de `root` modificadas hace más de MAX_AGE, con lo que tengan dentro. un error al
        // borrar una no detiene la exportación: se reintenta en la siguiente
        fun sweep(
            root: Path,
            now: Instant
        ) {
            val limit = now.minus(MAX_AGE)
            try {
                Files.newDirectoryStream(root, "$PREFIX*").use { entries ->
                    for (entry in entries) {
                        try {
                            if (Files.isDirectory(entry, LinkOption.NOFOLLOW_LINKS) &&
                                Files.getLastModifiedTime(entry, LinkOption.NOFOLLOW_LINKS).toInstant().isBefore(limit)
                            ) {
                                Files.walk(entry).use { tree -> tree.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
                            }
                        } catch (_: IOException) {
                            continue
                        }
                    }
                }
            } catch (_: IOException) {
                return
            }
        }
    }
}
