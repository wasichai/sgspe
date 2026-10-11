package sgspe.territory

import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

// GDAL para las pruebas: la carpeta con ogrinfo y ogr2ogr del PATH o de las rutas de siempre (Homebrew en la máquina de
// desarrollo, /usr/bin en la CI). sin GDAL, una prueba que lo necesita se salta en la máquina de desarrollo y falla en la CI,
// que lo instala (ci.yml, paso GDAL)
object GdalFixtures {
    val bin: Path? by lazy {
        val path =
            System
                .getenv("PATH")
                .orEmpty()
                .split(File.pathSeparator)
                .filter { it.isNotBlank() }
        (path + listOf("/opt/homebrew/bin", "/usr/local/bin", "/usr/bin"))
            .map { Path.of(it) }
            .firstOrNull { Files.isExecutable(it.resolve("ogrinfo")) && Files.isExecutable(it.resolve("ogr2ogr")) }
    }

    fun requireGdal(): Path {
        val found = bin
        if (found == null) {
            if (System.getenv("CI") == "true") fail<Unit>("GDAL no está instalado: el paso GDAL de ci.yml lo instala")
            assumeTrue(false, "sin GDAL en el PATH: la prueba se salta")
        }
        return found!!
    }
}
