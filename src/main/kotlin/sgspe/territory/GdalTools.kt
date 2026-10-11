package sgspe.territory

import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.UUID
import java.util.concurrent.TimeUnit

// GDAL (sgspe.territory.gdal, ASM-127): la carpeta de ogrinfo y ogr2ogr (vacía: la primera del PATH que tenga los dos), la
// versión mínima y el plazo de una exportación (la importación usa el de ImportProperties)
@ConfigurationProperties("sgspe.territory.gdal")
data class GdalProperties(
    val path: String = "",
    val minVersion: String = "3.8",
    val timeout: Duration = Duration.ofSeconds(60)
)

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GdalProperties::class)
class GdalConfiguration

data class GdalStatus(
    val available: Boolean,
    val version: String?,
    val drivers: Set<String>,
    val ogrinfo: Path?,
    val ogr2ogr: Path?,
    val reason: String?
) {
    fun supports(driver: String): Boolean = available && driver in drivers
}

// los procesos de GDAL. status() se mide una vez por proceso, en el primer uso: quien lo llama ya está en Dispatchers.IO
// (ImportGate.blocking, una exportación o imports/formats), porque corre ogrinfo. run() corre un comando de GdalCommand con
// el entorno limpio, la salida y el error en archivos del workspace (nada se lee de una tubería que pueda llenarse) y espera
// interrumpible: el plazo vencido (ImportGate o el de la exportación) interrumpe el hilo y el proceso muere con sus hijos
@Component
class GdalTools(
    private val properties: GdalProperties
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val probed: GdalStatus by lazy { probe() }

    fun status(): GdalStatus = probed

    fun require(
        driver: String,
        format: String
    ): GdalStatus {
        val status = status()
        if (!status.supports(driver)) throw GdalCommand.formatUnavailable(format)
        return status
    }

    fun run(
        command: List<String>,
        workdir: Path,
        stdout: Path
    ): Int {
        val stderr = workdir.resolve("gdal-${UUID.randomUUID()}.err")
        val builder = ProcessBuilder(command).directory(workdir.toFile()).redirectOutput(stdout.toFile()).redirectError(stderr.toFile())
        builder.environment().clear()
        builder.environment().putAll(GdalCommand.environment(System.getenv()))
        val process = builder.start()
        try {
            val exit = process.waitFor()
            // el error puede repetir valores del archivo: no va al log (RQ-SEC-010), solo el código
            if (exit != 0) log.warn("GDAL terminó con el código {}", exit)
            return exit
        } catch (e: InterruptedException) {
            process.descendants().forEach { it.destroyForcibly() }
            process.destroyForcibly()
            throw e
        }
    }

    private fun probe(): GdalStatus {
        val dir = locate() ?: return unavailable("no se encontraron ogrinfo y ogr2ogr")
        val ogrinfo = dir.resolve("ogrinfo")
        val output = capture(listOf(ogrinfo.toString(), "--version")) ?: return unavailable("ogrinfo --version no respondió")
        val version = GdalCommand.version(output) ?: return unavailable("no se reconoce la versión de GDAL")
        val label = "${version.first}.${version.second}"
        if (!GdalCommand.atLeast(version, properties.minVersion)) return unavailable("GDAL $label es menor que ${properties.minVersion}", label)
        val drivers = GdalCommand.drivers(capture(listOf(ogrinfo.toString(), "--formats")).orEmpty())
        log.info("GDAL {} en {} con {} drivers vectoriales", label, dir, drivers.size)
        return GdalStatus(true, label, drivers, ogrinfo, dir.resolve("ogr2ogr"), null)
    }

    private fun unavailable(
        reason: String,
        version: String? = null
    ): GdalStatus {
        log.warn("GDAL no disponible: {}; los formatos de GDAL responden FORMAT_UNAVAILABLE", reason)
        return GdalStatus(false, version, emptySet(), null, null, reason)
    }

    private fun locate(): Path? {
        val candidates =
            if (properties.path.isBlank()) {
                System
                    .getenv("PATH")
                    .orEmpty()
                    .split(File.pathSeparator)
                    .filter { it.isNotBlank() }
                    .map { Path.of(it) }
            } else {
                listOf(Path.of(properties.path))
            }
        return candidates.firstOrNull { Files.isExecutable(it.resolve("ogrinfo")) && Files.isExecutable(it.resolve("ogr2ogr")) }
    }

    // la salida de un comando corto (la versión, los drivers), con 10 s de plazo
    private fun capture(command: List<String>): String? {
        val out = Files.createTempFile("sgspe-gdal-", ".out")
        return try {
            val builder = ProcessBuilder(command).redirectErrorStream(true).redirectOutput(out.toFile())
            builder.environment().clear()
            builder.environment().putAll(GdalCommand.environment(System.getenv()))
            val process = builder.start()
            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                null
            } else if (process.exitValue() == 0) {
                Files.readString(out)
            } else {
                null
            }
        } catch (_: IOException) {
            null
        } finally {
            Files.deleteIfExists(out)
        }
    }
}
