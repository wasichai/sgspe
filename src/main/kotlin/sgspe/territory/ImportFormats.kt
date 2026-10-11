package sgspe.territory

import wasichai.core.common.ValidationException
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipException
import java.util.zip.ZipFile
import kotlin.io.path.isDirectory
import kotlin.io.path.name

// los formatos de la importación (gis.md §4.1, especificación de F6 §3.4): los de F2 los lee ImportReaders; los de F6, GDAL
// con su driver de la lista blanca (ASM-127). KML, KMZ y GPX están siempre en EPSG 4326; Shapefile, GeoPackage y FileGDB
// traen su CRS, que es solo una propuesta. multiLayer: el archivo puede traer varias capas
enum class ImportFormat(
    val key: String,
    val driver: String?,
    val multiLayer: Boolean,
    val crsFromFile: Boolean,
    val fixedSrid: Int?
) {
    XLSX("xlsx", null, false, false, null),
    CSV("csv", null, false, false, null),
    GEOJSON("geojson", null, false, false, null),
    SHP("shp", "ESRI Shapefile", false, true, null),
    KML("kml", "KML", true, false, Crs.WGS84),
    KMZ("kmz", "KML", true, false, Crs.WGS84),
    GPKG("gpkg", "GPKG", true, true, null),
    GDB("gdb", "OpenFileGDB", true, true, null),
    GPX("gpx", "GPX", true, false, Crs.WGS84);

    val gdal: Boolean get() = driver != null

    companion object {
        private const val SUPPORTED = "se importan XLSX, CSV, GeoJSON, Shapefile o FileGDB en ZIP, KML, KMZ, GeoPackage y GPX"

        // por la extensión del nombre; un ZIP, por su contenido (un .shp o una carpeta .gdb)
        fun detect(
            filename: String,
            file: File,
            maxEntries: Int
        ): ImportFormat {
            val name = filename.lowercase()
            return when {
                name.endsWith(".xlsx") -> XLSX
                name.endsWith(".csv") -> CSV
                name.endsWith(".geojson") || name.endsWith(".json") -> GEOJSON
                name.endsWith(".kml") -> KML
                name.endsWith(".kmz") -> KMZ
                name.endsWith(".gpkg") -> GPKG
                name.endsWith(".gpx") -> GPX
                name.endsWith(".zip") -> zipped(file, maxEntries)
                else -> throw ValidationException("Formato no admitido", "file", SUPPORTED)
            }
        }

        private fun zipped(
            file: File,
            maxEntries: Int
        ): ImportFormat {
            val names = ImportArchives.names(file, maxEntries).filterNot(ImportArchives::junk).map { it.lowercase() }
            val shp = names.any { it.endsWith(".shp") }
            val gdb = names.any { name -> name.split('/').dropLast(1).any { it.endsWith(".gdb") } }
            return when {
                shp && gdb -> throw ValidationException("Formato no admitido", "file", "el ZIP trae un Shapefile y una FileGDB: suba uno por archivo")
                shp -> SHP
                gdb -> GDB
                else -> throw ValidationException("Formato no admitido", "file", "un ZIP debe traer un Shapefile (.shp, .shx, .dbf) o una carpeta .gdb")
            }
        }
    }
}

// los ZIP de F6 (Shapefile, FileGDB, KMZ) se descomprimen en el workspace con los controles de RQ-SEC-020: cada entrada se
// resuelve dentro de la carpeta (../ o una ruta absoluta es un 400), con tope de entradas y de bytes inflados (una bomba zip es
// un 400 que nombra la descompresión). GDAL abre después una ruta del workspace, nunca /vsizip/ ni el ZIP del cliente.
// se escribe con FileOutputStream (su canal no se cierra con la interrupción del hilo) y se atiende la interrupción cada tanto
object ImportArchives {
    private const val BUFFER = 64 * 1024
    private val DRIVE = Regex("^[A-Za-z]:")

    // lo que añade el Finder de macOS: la carpeta __MACOSX y los archivos ._* (no son datos del cliente)
    fun junk(name: String): Boolean = name.split('/').any { it == "__MACOSX" || it.startsWith("._") }

    fun names(
        zip: File,
        maxEntries: Int
    ): List<String> =
        open(zip).use { archive ->
            if (archive.size() > maxEntries) throw tooManyEntries(maxEntries)
            archive.entries().toList().map { it.name }
        }

    fun extract(
        zip: File,
        workspace: ImportWorkspace,
        folder: String,
        maxEntries: Int,
        maxBytes: Long
    ): Path {
        val root = workspace.resolveEntry(folder)
        Files.createDirectories(root)
        var total = 0L
        open(zip).use { archive ->
            if (archive.size() > maxEntries) throw tooManyEntries(maxEntries)
            val buffer = ByteArray(BUFFER)
            val seen = HashSet<String>()
            for (entry in archive.entries()) {
                val name = entry.name
                // una entrada absoluta, con letra de unidad, con \ o con un tramo .. no se descomprime aunque, normalizada, cayera dentro
                if (name.startsWith("/") || name.contains('\\') || DRIVE.containsMatchIn(name) || name.split('/').any { it == ".." }) throw outside()
                if (junk(name)) continue
                if (!seen.add(name.lowercase())) throw ValidationException("Archivo inválido", "file", "el ZIP repite una entrada")
                val target = workspace.resolveEntry("$folder/$name")
                if (!target.startsWith(root)) throw outside()
                if (entry.isDirectory) {
                    Files.createDirectories(target)
                    continue
                }
                try {
                    Files.createDirectories(target.parent)
                    archive.getInputStream(entry).use { input ->
                        FileOutputStream(target.toFile()).use { out ->
                            while (true) {
                                if (Thread.interrupted()) throw InterruptedException("importación cancelada")
                                val read = input.read(buffer)
                                if (read < 0) break
                                total += read
                                if (total > maxBytes) {
                                    throw ValidationException("Archivo demasiado grande", "file", "el ZIP descomprimido pasa del máximo de $maxBytes bytes")
                                }
                                out.write(buffer, 0, read)
                            }
                        }
                    }
                } catch (_: IOException) {
                    // datos dañados, o una entrada que choca con otra (archivo y carpeta con el mismo nombre)
                    throw ValidationException("Archivo inválido", "file", "el ZIP está dañado o sus entradas se contradicen")
                }
            }
        }
        return root
    }

    // el único archivo (o carpeta, en FileGDB) con esa terminación bajo `root`; ninguno o varios, 400
    fun single(
        root: Path,
        suffix: String,
        directory: Boolean
    ): Path {
        val found =
            Files.walk(root).use { paths ->
                paths.filter { it != root && !junk(it.name) && it.name.lowercase().endsWith(suffix) && it.isDirectory() == directory }.toList()
            }
        return when (found.size) {
            1 -> found.single()
            0 -> throw ValidationException("Archivo inválido", "file", "el archivo no trae un $suffix")
            else -> throw ValidationException("Archivo inválido", "file", "el archivo trae ${found.size} $suffix: suba uno por archivo")
        }
    }

    // el .shp con su .shx y su .dbf al lado (el .prj y el .cpg son opcionales)
    fun shapefile(root: Path): Path {
        val shp = single(root, ".shp", directory = false)
        val base = shp.name.dropLast(4)
        val siblings = Files.list(shp.parent).use { list -> list.map { it.name.lowercase() }.toList() }.toSet()
        val missing = listOf(".shx", ".dbf").filter { "${base.lowercase()}$it" !in siblings }
        if (missing.isNotEmpty()) throw ValidationException("Archivo inválido", "file", "al Shapefile le falta ${missing.joinToString(" y ")}")
        return shp
    }

    // el doc.kml de un KMZ o, si no está, su único .kml
    fun kml(root: Path): Path {
        val doc = root.resolve("doc.kml")
        return if (Files.isRegularFile(doc)) doc else single(root, ".kml", directory = false)
    }

    private fun open(zip: File): ZipFile =
        try {
            ZipFile(zip)
        } catch (_: ZipException) {
            throw ValidationException("Archivo inválido", "file", "no es un ZIP válido")
        } catch (_: IOException) {
            throw ValidationException("Archivo inválido", "file", "no es un ZIP válido")
        }

    private fun tooManyEntries(max: Int) = ValidationException("Archivo inválido", "file", "el ZIP tiene más de $max entradas")

    // no se repite el nombre de la entrada: lo escribe quien sube el archivo
    private fun outside() = ValidationException("Archivo inválido", "file", "una entrada del archivo apunta fuera de su carpeta")
}
