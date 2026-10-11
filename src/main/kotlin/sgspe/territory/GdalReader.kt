package sgspe.territory

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import tools.jackson.core.JacksonException
import wasichai.core.common.FieldViolation
import wasichai.core.common.ValidationException
import wasichai.core.common.WasichaiException
import java.io.File
import java.nio.file.Files
import java.util.UUID

// lo que GDAL dejó listo para el flujo de F2: el GeoJSON de la capa elegida en su CRS de origen, la capa y todas las capas con
// elementos del archivo
data class GdalSource(
    val geojson: File,
    val layer: SourceLayer,
    val layers: List<SourceLayer>
)

// una elección que falta para leer el archivo: la capa (LAYER_REQUIRED) o el CRS de origen de un formato de GDAL. es un 400
// con el problem+json de siempre más las capas del archivo y su CRS detectado (ImportController lo agrega como `layers`)
class ImportChoiceRequired(
    field: String,
    reason: String,
    message: String,
    val layers: List<SourceLayer>
) : WasichaiException(HttpStatus.BAD_REQUEST, message, listOf(FieldViolation(field, reason))) {
    companion object {
        fun layer(layers: List<SourceLayer>) = ImportChoiceRequired("layer", "LAYER_REQUIRED", "El archivo tiene varias capas: elija una", layers)

        fun sourceCrs(
            layers: List<SourceLayer>,
            catalogued: List<String>
        ) = ImportChoiceRequired(
            "sourceCrs",
            "es obligatorio y sale del catálogo «CRS de origen» (${catalogued.joinToString(", ")}): confirme el CRS detectado o elija otro",
            "CRS de origen inválido",
            layers
        )
    }
}

// la lectura con GDAL de los formatos de F6 (gis.md §4.1, ASM-127, especificación de F6 §3.4): el archivo ya está en el
// workspace (sus límites de tamaño); los ZIP y el KMZ se descomprimen ahí con ImportArchives; ogrinfo lista las capas con el
// driver de la lista blanca; solo cuentan las que tienen elementos (un GPX lista siempre cinco); con varias y sin `layer`, 400
// LAYER_REQUIRED; una capa con más elementos que maxRows es 400 sin convertirla; ogr2ogr saca la capa elegida a GeoJSON sin
// reproyectar (ImportReaders la lee después con el CRS de origen confirmado). bloqueante e interrumpible: quien llama la corre
// en ImportGate.blocking, dentro del plazo de la importación
@Component
class GdalReader(
    private val tools: GdalTools,
    private val properties: ImportProperties,
    private val maxListingBytes: Long = MAX_LISTING_BYTES
) {
    fun read(
        format: ImportFormat,
        upload: File,
        workspace: ImportWorkspace,
        layer: String?
    ): GdalSource {
        val driver = format.driver?.takeIf { it in DRIVERS } ?: error("$format no se lee con GDAL")
        val status = tools.require(driver, format.key)
        val source =
            when (format) {
                ImportFormat.SHP -> ImportArchives.shapefile(extract(upload, workspace))
                ImportFormat.GDB -> ImportArchives.single(extract(upload, workspace), ".gdb", directory = true)
                ImportFormat.KMZ -> ImportArchives.kml(extract(upload, workspace))
                else -> upload.toPath()
            }
        val listing = workspace.resolveEntry("gdal-${UUID.randomUUID()}.json")
        if (tools.run(GdalCommand.info(status.ogrinfo!!, driver, source), workspace.dir, listing) != 0) {
            throw ValidationException("Archivo inválido", "file", "GDAL no pudo leer el archivo como ${format.key.uppercase()}")
        }
        if (Files.size(listing) > maxListingBytes) {
            throw ValidationException("Archivo inválido", "file", "el archivo tiene demasiadas capas o campos para listarlos")
        }
        val layers =
            try {
                GdalInfo.layers(Files.readString(listing))
            } catch (_: JacksonException) {
                throw ValidationException("Archivo inválido", "file", "GDAL no pudo listar las capas del archivo")
            }.filter { it.featureCount != 0L }
        if (layers.isEmpty()) throw ValidationException("Archivo vacío", "file", "el archivo no tiene elementos en ninguna capa")
        val chosen =
            when {
                layer != null ->
                    layers.firstOrNull { it.name == layer }
                        ?: throw ValidationException("Capa inválida", "layer", "no es una capa con elementos de este archivo")
                layers.size == 1 -> layers.single()
                else -> throw ImportChoiceRequired.layer(layers)
            }
        chosen.featureCount?.let {
            if (it >
                properties.maxRows
            ) {
                throw ValidationException("Archivo demasiado grande", "file", "la capa tiene $it elementos; el máximo es ${properties.maxRows}")
            }
        }
        val target = workspace.resolveEntry("gdal-${UUID.randomUUID()}.geojson")
        val log = workspace.resolveEntry("gdal-${UUID.randomUUID()}.out")
        val exit = tools.run(GdalCommand.toGeoJson(status.ogr2ogr!!, driver, source, chosen.name, target, properties.maxRows + 1), workspace.dir, log)
        if (exit != 0 || !Files.exists(target)) throw ValidationException("Archivo inválido", "file", "GDAL no pudo convertir la capa elegida")
        return GdalSource(target.toFile(), chosen, layers)
    }

    private fun extract(
        upload: File,
        workspace: ImportWorkspace
    ) = ImportArchives.extract(upload, workspace, "archivo", properties.maxEntries, properties.maxInflatedBytes)

    companion object {
        // la lista blanca de drivers (ASM-127): nunca LIBKML, que sigue enlaces
        private val DRIVERS = setOf("ESRI Shapefile", "KML", "GPKG", "OpenFileGDB", "GPX")
        private const val MAX_LISTING_BYTES = 4L * 1024 * 1024
    }
}
