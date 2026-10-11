package sgspe.territory

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Flux
import sgspe.common.Callers
import sgspe.common.Criteria
import sgspe.common.Finding
import sgspe.common.ModelCatalog
import sgspe.common.Permissions
import sgspe.common.Readability
import sgspe.common.Records
import sgspe.common.StrictBody
import sgspe.common.StrictQuery
import sgspe.common.Values
import sgspe.common.safeCell
import sgspe.common.toCodedException
import sgspe.privacy.Disclosure
import sgspe.privacy.ExportFile
import sgspe.privacy.ExportLog
import sgspe.privacy.ExportProjects
import sgspe.privacy.Xlsx
import sgspe.privacy.XlsxSheet
import tools.jackson.databind.json.JsonMapper
import wasichai.core.common.Actions
import wasichai.core.common.ValidationException
import wasichai.core.common.WasichaiException
import wasichai.core.data.RecordCriterion
import wasichai.core.data.RecordResponse
import wasichai.core.metadata.MetadataService
import java.io.IOException
import java.math.BigDecimal
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.util.UUID

// POST /api/sgspe/territory/exports (api.md §4, gis.md §4.6, especificación de F6 §3.5, RQ-GIS-018, ASM-124): los objetos de
// LYR-01 a LYR-16 con una de sus geometrías, en geojson y kml (4326), gpkg y shp (UTM del proyecto filtrado o 4326) o csv y
// xlsx (latitud, longitud, este y norte del punto o del centroide y WKT). antes de leer nada exige READ y EXPORT sobre el
// objeto y CREATE sobre export_log; lee por Records como quien exporta (permisos de campo, alcance de proyecto) con el tope
// sgspe.exports.max-rows; aplica la divulgación por capa (ExportDisclosure) y deja su fila de export_log antes de entregar el
// archivo, como CommitmentMatrix. los proyectos siguen ExportProjects. una exportación no crea registros de negocio: sin
// Idempotency-Key (api.md §3.5)
@Service
class TerritoryExports(
    private val records: Records,
    private val permissions: Permissions,
    private val callers: Callers,
    private val catalog: ModelCatalog,
    private val metadata: MetadataService,
    private val log: ExportLog,
    private val scope: ExportProjects,
    private val geo: GeoSql,
    private val writer: GdalWriter,
    private val gdal: GdalProperties,
    private val tools: GdalTools,
    private val clock: Clock,
    @Value("\${sgspe.exports.max-rows:20000}") private val maxRows: Int
) {
    class Export(
        val file: ExportFile,
        val format: GeoExportFormat
    )

    suspend fun export(body: Map<String, Any?>): Export {
        val user = callers.require()
        StrictBody.requireOnly(body, BODY)
        val objectName = body["object"] as? String
        if (objectName == null || objectName !in ExportDisclosure.EXPORTABLE) {
            throw bad(
                "OBJECT_NOT_EXPORTABLE",
                "Se exportan los objetos de las capas LYR-01 a LYR-16: ${ExportDisclosure.EXPORTABLE.sorted().joinToString(", ")}",
                "object"
            )
        }
        permissions.require(user, "Exportar $objectName", Actions.READ to objectName, EXPORT to objectName, Actions.CREATE to ExportLog.OBJECT)
        val format =
            GeoExportFormat.parse(body["format"])
                ?: throw bad("INVALID_FORMAT", "El formato es ${GeoExportFormat.entries.joinToString(", ") { it.key }}", "format")
        val coordinates = body["coordinates"] ?: LATLON
        if (coordinates != UTM && coordinates != LATLON) throw bad("INVALID_COORDINATES", "coordinates es UTM o LATLON", "coordinates")
        // geojson y kml salen siempre en 4326: pedir UTM con ellos entregaría otro CRS en silencio
        if (coordinates == UTM && (format == GeoExportFormat.GEOJSON || format == GeoExportFormat.KML)) {
            throw bad("INVALID_COORDINATES", "geojson y kml salen siempre en LATLON (EPSG 4326); para UTM use gpkg, shp, csv o xlsx", "coordinates")
        }
        val disclosure =
            Disclosure.parse(body["disclosureLevel"])
                ?: throw bad("INVALID_DISCLOSURE_LEVEL", "El nivel de divulgación debe ser INTERNO, EXTERNO o PUBLICO", "disclosureLevel")
        if (disclosure == Disclosure.EXTERNO && body["checklistConfirmed"] != true) {
            throw bad("CHECKLIST_REQUIRED", "Una exportación EXTERNO exige confirmar la lista de verificación (ASM-037)", "checklistConfirmed")
        }
        val filters = StrictBody.objectOf(body, "filters")
        StrictBody.requireOnly(filters, FILTERS, "filters.")
        val component = texts(filters["componentType"], "filters.componentType", objectName, ExportDisclosure.COMPONENT, "component_type")
        val tenure = texts(filters["tenureType"], "filters.tenureType", objectName, ExportDisclosure.PARCEL, "tenure_type")
        val requested = listOfNotNull(component?.let { "component_type" to it }, tenure?.let { "tenure_type" to it }).toMap()
        val fixed = ExportDisclosure.check(objectName, disclosure, requested)
        val geometry = geometryOf(objectName, body["geometry"])
        requireGdal(format)
        val requestedProjects = uuids(filters["project"])
        // los proyectos solo acotan los objetos que tienen proyecto; para los demás, ni la lectura ni export_log los llevan
        val projects = scope.resolve(user, requestedProjects)?.takeIf { objectName in PROJECT_FILTERED }
        val utm = if (coordinates == UTM) workingSrid(requestedProjects) else null
        val rows = read(objectName, geometry, fixed, projects)
        val readability = Readability(metadata)
        val candidates = columnsOf(objectName).filter { readability.canRead("$objectName.$it") }
        val allowed = ExportDisclosure.columns(objectName, disclosure, candidates + STATE)
        val columns = allowed - STATE
        val withState = STATE in allowed
        val filename = "${objectName.replace('_', '-')}-${disclosure.name.lowercase()}.${format.extension}"
        val export = withContext(Dispatchers.IO) { ExportFile.create(filename, clock.instant(), entry = "capa.${format.extension}") }
        try {
            write(format, export.file.toPath(), objectName, geometry, rows, columns, withState, utm)
            val description = description(objectName, geometry, format, coordinates.toString(), projects, fixed, disclosure)
            log.export(user, objectName, format.key.uppercase(), disclosure, description, rows.size, projects?.singleOrNull())
            return Export(export, format)
        } catch (e: Throwable) {
            export.discard()
            throw e
        }
    }

    // ---- lectura ----

    private suspend fun read(
        objectName: String,
        geometry: String,
        fixed: Map<String, Set<String>>,
        projects: List<UUID>?
    ): List<RecordResponse> {
        val criteria = mutableListOf<RecordCriterion>(Criteria.filled(geometry))
        fixed.forEach { (field, values) -> criteria += Criteria.textIn(field, values) }
        var ids: List<UUID>? = null
        if (projects != null) {
            when (objectName) {
                ExportDisclosure.COMPONENT, "area_of_influence" -> criteria += Criteria.anyOf("project", projects)
                "project" -> ids = projects
                "mining_concession" ->
                    ids =
                        projects.flatMap { records.related("project", it, "project_concession") }.mapNotNull { Records.uuid(it.id) }.distinct()
            }
        }
        // sin geometría legible (un permiso de campo la oculta) no hay qué exportar: así las filas y las formas van alineadas
        return records.all(objectName, criteria = criteria, ids = ids, max = maxRows, field = TOO_MANY_FIELD).filter {
            Records.geometriesOf(it)[geometry] !=
                null
        }
    }

    private suspend fun workingSrid(requested: List<UUID>): Int {
        val project =
            requested.singleOrNull()
                ?: throw ValidationException(
                    "Proyecto requerido",
                    "filters.project",
                    "con coordenadas UTM, filtre un solo proyecto: su CRS de trabajo es el de la exportación"
                )
        return Crs.srid(Values.text(records.get("project", project).attributes["working_crs"]))?.takeIf(Crs::isWorkingZone)
            ?: throw ValidationException("Proyecto sin CRS de trabajo", "filters.project", "el proyecto no tiene working_crs: exporte en LATLON")
    }

    // ---- escritura ----

    private suspend fun write(
        format: GeoExportFormat,
        out: Path,
        objectName: String,
        geometry: String,
        rows: List<RecordResponse>,
        columns: List<String>,
        withState: Boolean,
        utm: Int?
    ) {
        val shapes = rows.map { Records.geometriesOf(it)[geometry]!! }
        when (format) {
            GeoExportFormat.CSV, GeoExportFormat.XLSX -> {
                val points = geo.exportPoints(shapes, utm)
                val header = listOf("id") + columns + listOf("latitude", "longitude", "utm_zone", "easting", "northing", "wkt")
                val lines =
                    rows.mapIndexed { i, record ->
                        val point = points[i]
                        listOf<Any?>(record.id) + columns.map { record.attributes[it] } +
                            listOf(
                                point.latitude,
                                point.longitude,
                                point.srid?.let(Crs::value),
                                point.easting,
                                point.northing,
                                if (utm !=
                                    null
                                ) {
                                    point.wktUtm
                                } else {
                                    point.wkt
                                }
                            )
                    }
                withContext(Dispatchers.IO) {
                    if (format == GeoExportFormat.CSV) {
                        Csv.write(header, lines, out)
                    } else {
                        Files.newOutputStream(out).use { Xlsx.write(listOf(XlsxSheet(objectName, header, lines.asSequence())), it) }
                    }
                }
            }
            else -> {
                val features =
                    rows.mapIndexed { i, record ->
                        val properties =
                            columns.associateWith { record.attributes[it] } + listOfNotNull(record.state?.takeIf { withState }?.let { STATE to it })
                        mapOf("type" to "Feature", "id" to record.id, "geometry" to shapes[i], "properties" to properties)
                    }
                val collection = mapOf("type" to "FeatureCollection", "features" to features)
                if (format == GeoExportFormat.GEOJSON) {
                    withContext(Dispatchers.IO) { JSON.writeValue(out.toFile(), collection) }
                } else {
                    gdalWrite(format, collection, objectName, utm, out)
                }
            }
        }
    }

    private suspend fun gdalWrite(
        format: GeoExportFormat,
        collection: Map<String, Any?>,
        objectName: String,
        utm: Int?,
        out: Path
    ) {
        val source = out.resolveSibling("fuente.geojson")
        try {
            withContext(Dispatchers.IO) { JSON.writeValue(source.toFile(), collection) }
            guardGdal(gdal.timeout.seconds) { writer.writeWithin(format, source, objectName, utm, out, gdal.timeout) }
        } finally {
            withContext(NonCancellable + Dispatchers.IO) { Files.deleteIfExists(source) }
        }
    }

    // GDAL escribe en la capa `format`: sin GDAL o sin el driver es 422 FORMAT_UNAVAILABLE sobre format, antes de leer filas
    private suspend fun requireGdal(format: GeoExportFormat) {
        val driver = format.driver ?: return
        if (!withContext(Dispatchers.IO) { tools.status().supports(driver) }) throw GdalCommand.formatUnavailable(format.key, "format")
    }

    // ---- el cuerpo ----

    // los campos del objeto (sin las geometrías) y sus relaciones MANY_TO_ONE, en el orden del modelo
    private fun columnsOf(objectName: String): List<String> =
        catalog
            .objectOrNull(objectName)!!
            .fields.values
            .filter { it.type != "GEOMETRY" }
            .map { it.name } + catalog.manyToOneFields(objectName).map { it.first }

    private fun geometryOf(
        objectName: String,
        raw: Any?
    ): String {
        val geometries =
            catalog
                .objectOrNull(objectName)!!
                .fields.values
                .filter { it.type == "GEOMETRY" }
                .map { it.name }
        val chosen = raw as? String ?: geometries.singleOrNull()
        return chosen?.takeIf { it in geometries }
            ?: throw bad("INVALID_GEOMETRY", "Elija la geometría de $objectName: ${geometries.joinToString(", ")}", "geometry")
    }

    private fun texts(
        raw: Any?,
        field: String,
        objectName: String,
        owner: String,
        enumField: String
    ): Set<String>? {
        if (raw == null) return null
        if (objectName != owner) throw bad("INVALID_FILTER", "El filtro $field es solo de $owner", field)
        val values = (raw as? List<*> ?: listOf(raw)).map { it as? String ?: throw bad("INVALID_FILTER", "El filtro $field es una lista de textos", field) }
        val known = catalog.enumOf(owner, enumField)
        values.firstOrNull { it !in known }?.let { throw bad("INVALID_FILTER", "El filtro $field admite ${known.joinToString(", ")}", field) }
        return values.toSet()
    }

    private fun uuids(raw: Any?): List<UUID> {
        val values =
            when (raw) {
                null -> emptyList()
                is List<*> -> raw
                else -> listOf(raw)
            }
        return values
            .map {
                Records.uuid(it) ?: throw bad("INVALID_FILTER", "El filtro filters.project debe ser un UUID o una lista de UUID", "filters.project")
            }.distinct()
    }

    private fun description(
        objectName: String,
        geometry: String,
        format: GeoExportFormat,
        coordinates: String,
        projects: List<UUID>?,
        fixed: Map<String, Set<String>>,
        disclosure: Disclosure
    ): String =
        buildString {
            append("filtros: objeto=").append(objectName)
            append("; geometría=").append(geometry)
            append("; formato=").append(format.key)
            append("; coordenadas=").append(coordinates)
            append("; proyectos=").append(projects?.joinToString(",") ?: "todos")
            fixed.toSortedMap().forEach { (field, values) -> append("; ").append(field).append('=').append(values.sorted().joinToString(",")) }
            if (disclosure == Disclosure.EXTERNO) append("; lista de verificación de EXTERNO confirmada")
        }

    private fun bad(
        code: String,
        message: String,
        field: String
    ) = Finding(code, message, field, status = HttpStatus.BAD_REQUEST).toCodedException()

    companion object {
        private val LOG = LoggerFactory.getLogger(TerritoryExports::class.java)

        // el plazo vencido es 503 EXPORT_TIMEOUT; la cancelación de quien llama (cierra la conexión) sigue su camino, aunque
        // CancellationException sea un IllegalStateException; un fallo determinista de GDAL es 500 EXPORT_FAILED sin detalles
        // (rutas ni salida del proceso): la causa queda en el registro del servidor
        internal suspend fun <T> guardGdal(
            seconds: Long,
            block: suspend () -> T
        ): T =
            try {
                block()
            } catch (_: TimeoutCancellationException) {
                throw Finding(
                    "EXPORT_TIMEOUT",
                    "La exportación tardó más de $seconds s",
                    "format",
                    status = HttpStatus.SERVICE_UNAVAILABLE
                ).toCodedException()
            } catch (e: CancellationException) {
                throw e
            } catch (e: IllegalStateException) {
                throw gdalFailure(e)
            } catch (e: IllegalArgumentException) {
                throw gdalFailure(e)
            } catch (e: IOException) {
                throw gdalFailure(e)
            }

        fun gdalFailure(cause: Exception): WasichaiException {
            LOG.warn("Falló la escritura de una exportación geográfica con GDAL", cause)
            return Finding("EXPORT_FAILED", "No se pudo generar el archivo de la exportación", "format", status = HttpStatus.INTERNAL_SERVER_ERROR)
                .toCodedException()
        }

        private const val STATE = "workflow_state"
        private val PROJECT_FILTERED = setOf(ExportDisclosure.COMPONENT, "area_of_influence", "project", "mining_concession")
        const val EXPORT = "EXPORT"
        const val UTM = "UTM"
        const val LATLON = "LATLON"
        private const val TOO_MANY_FIELD = "filters.project"
        private val BODY = setOf("object", "geometry", "format", "coordinates", "filters", "disclosureLevel", "checklistConfirmed")
        private val FILTERS = setOf("project", "componentType", "tenureType")
        private val JSON: JsonMapper = JsonMapper.builder().build()
    }
}

// CSV RFC 4180 con BOM UTF-8 (Excel lo abre con las tildes), coma de separador y punto decimal; cada texto pasa por safeCell
// (security.md §11.4: un código o un nombre que vienen de un archivo importado no deben volverse fórmula)
internal object Csv {
    fun write(
        header: List<String>,
        rows: List<List<Any?>>,
        out: Path
    ) {
        Files.newBufferedWriter(out, Charsets.UTF_8).use { writer ->
            writer.write("\uFEFF")
            for (line in listOf(header) + rows) {
                writer.write(line.joinToString(",") { cell(it) })
                writer.write("\r\n")
            }
        }
    }

    private fun cell(value: Any?): String =
        when (value) {
            null -> ""
            is BigDecimal -> value.toPlainString()
            is Number -> value.toString()
            else -> quote(safeCell(value.toString()))
        }

    private fun quote(text: String): String =
        if (text.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + text.replace("\"", "\"\"") + "\"" else text
}

// la respuesta: el archivo en streaming con su fila de export_log ya escrita; se borra al terminar el envío (ExportFile)
@RestController
@RequestMapping("/api/sgspe/territory/exports")
class TerritoryExportsController(
    private val exports: TerritoryExports
) {
    @PostMapping
    suspend fun export(
        @RequestBody body: Map<String, Any?>,
        exchange: ServerWebExchange
    ): ResponseEntity<Flux<DataBuffer>> {
        StrictQuery.requireOnly(exchange.request.queryParams, emptySet())
        val export = exports.export(body)
        return ResponseEntity
            .ok()
            .contentType(MediaType.parseMediaType(export.format.mediaType))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"${export.file.filename}\"")
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .body(export.file.stream())
    }
}
