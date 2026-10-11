package sgspe.territory

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.withContext
import org.apache.poi.xssf.streaming.SXSSFWorkbook
import org.springframework.core.codec.DecodingException
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.core.io.buffer.DataBufferUtils
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.http.codec.multipart.FilePart
import org.springframework.http.codec.multipart.FormFieldPart
import org.springframework.http.codec.multipart.Part
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ServerWebExchange
import sgspe.common.StrictBody
import sgspe.common.StrictQuery
import sgspe.common.UploadLimits
import sgspe.common.safeCell
import tools.jackson.databind.json.JsonMapper
import wasichai.core.common.GlobalExceptionHandler
import wasichai.core.common.ValidationException
import wasichai.core.common.WasichaiException
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.HexFormat
import java.util.UUID

// las dos rutas de la importación (api.md §4.2). multipart: `file` y los campos `target`, `project`, `sourceCrs` y
// `columns`; la confirmación vuelve a mandar todo eso y agrega `sha256` y `decisions` (un json [{row, action}], action
// APPLY o DISCARD): el servidor no guarda nada entre las dos. una parte desconocida o repetida es un 400 y un parámetro de
// consulta, un 422. desde F6, la parte `layer` (la capa elegida de un archivo de varias) y los formatos de GDAL. el archivo se escribe en la carpeta temporal contando bytes: al pasar el máximo, 400 FILE_TOO_LARGE y la
// carpeta se borra. Idempotency-Key se acepta y se ignora en F2 (api.md §3.5)
@RestController
@RequestMapping("/api/sgspe/territory/imports")
class ImportController(
    private val imports: TerritoryImports,
    private val properties: ImportProperties,
    private val tools: GdalTools,
    private val problems: GlobalExceptionHandler
) {
    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    suspend fun preview(
        exchange: ServerWebExchange,
        @RequestHeader(name = HttpHeaders.ACCEPT, required = false) accept: String?
    ): ResponseEntity<Any> {
        StrictQuery.requireOnly(exchange.request.queryParams, emptySet())
        val parts = parts(exchange, PREVIEW_PARTS)
        return ImportWorkspace.create().use { workspace ->
            val preview = imports.preview(request(parts, workspace))
            if (accept != null && XLSX in accept) {
                val report = withContext(Dispatchers.IO) { DifferencesReport.xlsx(preview) }
                ResponseEntity
                    .ok()
                    .contentType(MediaType.parseMediaType(XLSX))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"diferencias-${preview.importBatchId}.xlsx\"")
                    .body(report)
            } else {
                ResponseEntity.ok(preview)
            }
        }
    }

    @PostMapping("/{importBatchId}/confirm", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    suspend fun confirm(
        @PathVariable importBatchId: UUID,
        exchange: ServerWebExchange
    ): ImportConfirmation {
        StrictQuery.requireOnly(exchange.request.queryParams, emptySet())
        val parts = parts(exchange, CONFIRM_PARTS)
        val decisions = decisions(field(parts, "decisions"))
        return ImportWorkspace.create().use { workspace ->
            imports.confirm(importBatchId, request(parts, workspace), field(parts, "sha256"), decisions)
        }
    }

    // las partes del multipart, una por nombre. Spring guarda en disco la parte de archivo antes de entregarla; sus topes
    // (spring.webflux.multipart.*, application.yml) son 400 con código (ImportUpload.multipartError), no 500
    // GET /api/sgspe/territory/imports/formats (api.md §4.2): qué formatos lee este servidor; los de GDAL, solo si hay GDAL de
    // la versión mínima con su driver (ASM-127). status() corre ogrinfo la primera vez: va en el despachador de E/S
    @GetMapping("/formats")
    suspend fun formats(exchange: ServerWebExchange): Map<String, Any> {
        StrictQuery.requireOnly(exchange.request.queryParams, emptySet())
        val status = withContext(Dispatchers.IO) { tools.status() }
        return mapOf(
            "formats" to
                ImportFormat.entries.map {
                    mapOf(
                        "format" to it.key,
                        "available" to (it.driver?.let(status::supports) ?: true),
                        "multiLayer" to it.multiLayer,
                        "crsFromFile" to it.crsFromFile
                    )
                }
        )
    }

    // LAYER_REQUIRED y el CRS de origen que falta en un formato de GDAL: el problem+json de wasichai más las capas del archivo
    // con su CRS detectado, para que el portal ofrezca elegir y proponga el CRS (especificación de F6 §2.2.6)
    @ExceptionHandler(ImportChoiceRequired::class)
    fun choiceRequired(e: ImportChoiceRequired): ProblemDetail = problems.handleWasichai(e).apply { setProperty("layers", e.layers) }

    private suspend fun parts(
        exchange: ServerWebExchange,
        allowed: Set<String>
    ): Map<String, Part> {
        val all =
            try {
                exchange.multipartData.awaitSingle()
            } catch (e: Exception) {
                throw ImportUpload.multipartError(e, properties.maxBytes) ?: e
            }
        StrictBody.requireOnly(all, allowed)
        all.entries.firstOrNull { it.value.size > 1 }?.let {
            throw ValidationException("Parte repetida", it.key, "envíe la parte ${it.key} una sola vez")
        }
        return all.toSingleValueMap()
    }

    private suspend fun request(
        parts: Map<String, Part>,
        workspace: ImportWorkspace
    ): ImportRequest {
        val file = parts["file"] as? FilePart ?: throw ValidationException("Falta el archivo", "file", "envíe el archivo en la parte file")
        // el nombre lo escribe quien sube: solo el último tramo (también de una ruta de Windows) y acotado
        val filename =
            File(file.filename().substringAfterLast('\\'))
                .name
                .take(MAX_FILENAME)
                .ifBlank { "archivo" }
        val stored = workspace.resolveEntry("upload-${filename.replace(Regex("[^A-Za-z0-9._-]"), "_")}").toFile()
        val sha256 = ImportUpload.receive(file.content().asFlow(), stored, properties.maxBytes)
        return ImportRequest(
            target = field(parts, "target"),
            project = field(parts, "project"),
            sourceCrs = field(parts, "sourceCrs"),
            columns = field(parts, "columns"),
            file = stored,
            filename = filename,
            sha256 = sha256,
            layer = field(parts, "layer")?.take(MAX_FILENAME),
            workspace = workspace
        )
    }

    private fun field(
        parts: Map<String, Part>,
        name: String
    ): String? = (parts[name] as? FormFieldPart)?.value()?.trim()?.ifEmpty { null }

    // [{row, action}]: row entero, action APPLY o DISCARD (lo valida TerritoryImports); nada más en cada decisión
    @Suppress("UNCHECKED_CAST")
    private fun decisions(raw: String?): List<RowDecision> {
        if (raw == null) return emptyList()
        val invalid = ValidationException("Decisiones inválidas", "decisions", "debe ser un json [{\"row\": 7, \"action\": \"DISCARD\"}]")
        val parsed =
            try {
                JSON.readValue(raw, List::class.java) as List<Any?>
            } catch (_: Exception) {
                throw invalid
            }
        return parsed.map { item ->
            val decision = item as? Map<String, Any?> ?: throw invalid
            StrictBody.requireOnly(decision, setOf("row", "action"), "decisions.")
            val row = (decision["row"] as? Number)?.takeIf { it is Int || it is Long }?.toInt() ?: throw invalid
            val action = decision["action"] as? String ?: throw invalid
            RowDecision(row, action)
        }
    }

    companion object {
        const val XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        private const val MAX_FILENAME = 200
        private val JSON: JsonMapper = JsonMapper.builder().build()
        val PREVIEW_PARTS = setOf("file", "target", "project", "sourceCrs", "columns", "layer")
        val CONFIRM_PARTS = PREVIEW_PARTS + setOf("sha256", "decisions")
    }
}

// la subida de un archivo a la carpeta temporal (RQ-SEC-020): cuenta los bytes mientras escribe y calcula el SHA-256 con
// el que la confirmación se ata a su vista previa. al pasar el máximo corta con un 400 FILE_TOO_LARGE sobre file
object ImportUpload {
    suspend fun receive(
        content: Flow<DataBuffer>,
        target: File,
        maxBytes: Long
    ): String {
        val digest = MessageDigest.getInstance("SHA-256")
        var size = 0L
        withContext(Dispatchers.IO) {
            FileOutputStream(target).use { out ->
                content.collect { buffer ->
                    try {
                        size += buffer.readableByteCount()
                        if (size > maxBytes) throw tooLarge(maxBytes)
                        val bytes = ByteArray(buffer.readableByteCount())
                        buffer.read(bytes)
                        digest.update(bytes)
                        out.write(bytes)
                    } finally {
                        DataBufferUtils.release(buffer)
                    }
                }
            }
        }
        return HexFormat.of().formatHex(digest.digest())
    }

    // los límites del multipart de Spring, que no nombran la parte: los traduce UploadLimits (FILE_TOO_LARGE sobre file, la
    // única parte de archivo que se admite, o PART_TOO_LARGE con el texto de la importación); un multipart que Spring no
    // puede decodificar (más partes que max-parts) es INVALID_MULTIPART. null si `e` no viene de ahí
    fun multipartError(
        e: Throwable,
        maxBytes: Long
    ): WasichaiException? =
        UploadLimits.error(e, maxBytes, PART_MESSAGE)
            ?: if (UploadLimits.causes(e).any { it is DecodingException }) {
                importError("INVALID_MULTIPART", "multipart", "El multipart no se pudo leer: tiene demasiadas partes o está mal formado")
            } else {
                null
            }

    fun tooLarge(maxBytes: Long): WasichaiException = UploadLimits.tooLarge(maxBytes)

    private const val PART_MESSAGE = "${UploadLimits.PART_MESSAGE}: target, project, sourceCrs, columns, sha256 y decisions caben en 1 MB"
}

// el reporte de diferencias como hoja (gis.md §4.4): una fila por fila válida con su acción, y aparte las rechazadas y las
// que caen fuera de zona. el código, los campos y los motivos repiten texto del archivo subido: cada celda de texto pasa por
// safeCell para que una hoja de cálculo no la tome como fórmula (security.md §11.4); los números van como números
object DifferencesReport {
    private val DIFFERENCES =
        listOf(
            "Fila",
            "Código",
            "Acción",
            "Versión vigente",
            "Distancia (m)",
            "Este anterior",
            "Norte anterior",
            "Este nuevo",
            "Norte nuevo",
            "Área anterior (ha)",
            "Área nueva (ha)"
        )

    fun xlsx(preview: ImportPreview): ByteArray {
        val workbook = SXSSFWorkbook(100)
        try {
            sheet(
                workbook,
                "Diferencias",
                DIFFERENCES,
                preview.differences.map {
                    listOf(
                        it.row,
                        it.code,
                        it.outcome,
                        it.currentVersion,
                        it.distanceM,
                        it.previousEasting,
                        it.previousNorthing,
                        it.newEasting,
                        it.newNorthing,
                        it.areaHaBefore,
                        it.areaHaAfter
                    )
                }
            )
            sheet(workbook, "Rechazadas", listOf("Fila", "Campo", "Motivo"), preview.invalid.map { listOf(it.row, it.field, it.message) })
            sheet(workbook, "Fuera de zona", listOf("Fila", "Código", "Motivo"), preview.outOfZone.map { listOf(it.row, it.code, it.message) })
            return ByteArrayOutputStream().also { workbook.write(it) }.toByteArray()
        } finally {
            // los archivos temporales de SXSSF
            workbook.dispose()
            workbook.close()
        }
    }

    private fun sheet(
        workbook: SXSSFWorkbook,
        name: String,
        header: List<String>,
        rows: List<List<Any?>>
    ) {
        val sheet = workbook.createSheet(name)
        (listOf(header) + rows).forEachIndexed { index, values ->
            val row = sheet.createRow(index)
            values.forEachIndexed { column, value ->
                val cell = row.createCell(column)
                when (value) {
                    null -> cell.setBlank()
                    is Number -> cell.setCellValue(value.toDouble())
                    else -> cell.setCellValue(safeCell(value.toString()))
                }
            }
        }
    }
}
