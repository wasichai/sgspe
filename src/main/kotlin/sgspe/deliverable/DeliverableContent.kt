package sgspe.deliverable

import org.springframework.core.io.buffer.DataBuffer
import org.springframework.http.CacheControl
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.MethodNotAllowedException
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Flux
import sgspe.common.Callers
import sgspe.common.Finding
import sgspe.common.Records
import sgspe.common.StrictQuery
import sgspe.common.UnprocessableContentException
import sgspe.common.toCodedException
import sgspe.privacy.ExportLog
import wasichai.core.common.FieldViolation
import wasichai.core.common.NotFoundException
import wasichai.core.data.RecordResponse
import wasichai.files.FileDownload
import wasichai.files.FileService
import java.nio.charset.StandardCharsets
import java.util.UUID

// las descargas de un entregable (especificación de F8 §3.4, api.md §4.12, security.md §14): leen el registro como quien llama
// (404 si no lo lee), comprueban la acción (DOWNLOAD_RESTRICTED para un borrador de cualquier nivel y una versión INTERNO;
// EXPORT para una versión EXTERNO; si no, 403 ACTION_REQUIRED), abren el archivo con FileService.open como quien llama,
// escriben la fila de export_log (DESCARGA, con el SHA-256 de lo servido) y lo transmiten attachment, nosniff, no-store y
// ETag igual al SHA-256, como EvidenceContent.
// el DOCX fuera de INTERNO y el modelo son 404 (existen, pero no se sirven), un formato o un nivel desconocido es 422. las
// candidatas no tienen ruta: nada sin la marca BORRADOR se sirve antes de emitir (RQ-REP-021)
@Service
class DeliverableContent(
    private val records: Records,
    private val callers: Callers,
    private val permissions: sgspe.common.Permissions,
    private val files: FileService,
    private val exportLog: ExportLog
) {
    suspend fun draft(
        id: UUID,
        level: String?,
        format: String?
    ): ResponseEntity<Flux<DataBuffer>> {
        val chosenLevel = level(level)
        val chosenFormat = format(format)
        val user = callers.require()
        val deliverable = records.get(Deliverables.DELIVERABLE, id)
        val field =
            when {
                chosenFormat == MODEL -> throw notServed()
                chosenLevel == Deliverables.INTERNAL && chosenFormat == DOCX -> Deliverables.DRAFT_INTERNAL_DOCX
                chosenLevel == Deliverables.INTERNAL -> Deliverables.DRAFT_INTERNAL_PDF
                chosenFormat == DOCX -> throw notServed()
                else -> Deliverables.DRAFT_EXTERNAL_PDF
            }
        require(user, Deliverables.DOWNLOAD_RESTRICTED, "Descargar un borrador")
        if (deliverable.attributes[field] == null) throw notServed()
        // la fila, después de abrir el archivo (lo que no se abre no sale) y con el SHA-256 de lo que se sirve
        val download = files.open(Deliverables.DELIVERABLE, id, field)
        exportLog.deliverable(
            user,
            ExportLog.DOWNLOAD,
            chosenLevel,
            chosenFormat,
            draftDescription(deliverable.attributes, download.file.sha256),
            project(deliverable),
            id,
            null
        )
        return stream(download)
    }

    suspend fun version(
        id: UUID,
        number: Int,
        format: String?
    ): ResponseEntity<Flux<DataBuffer>> {
        records.get(Deliverables.DELIVERABLE, id)
        val version =
            records
                .all(Deliverables.VERSION, mapOf(Deliverables.DELIVERABLE to id.toString(), Deliverables.VERSION_NUMBER to number.toString()))
                .singleOrNull() ?: throw NotFoundException("La versión $number no existe o no está a su alcance")
        return version(version, format(format))
    }

    suspend fun versionById(
        versionId: UUID,
        field: String
    ): ResponseEntity<Flux<DataBuffer>> {
        val version = records.get(Deliverables.VERSION, versionId)
        val format =
            when (field) {
                Deliverables.PDF -> PDF
                Deliverables.DOCX -> DOCX
                else -> throw notServed()
            }
        return version(version, format)
    }

    private suspend fun version(
        version: RecordResponse,
        format: String
    ): ResponseEntity<Flux<DataBuffer>> {
        val user = callers.require()
        val level = version.attributes[Deliverables.LEVEL]?.toString()
        if (format == MODEL || (format == DOCX && level != Deliverables.INTERNAL)) throw notServed()
        if (level ==
            Deliverables.INTERNAL
        ) {
            require(user, Deliverables.DOWNLOAD_RESTRICTED, "Descargar una versión INTERNO")
        } else {
            require(user, Deliverables.EXPORT, "Descargar una versión $level")
        }
        val field = if (format == DOCX) Deliverables.DOCX else Deliverables.PDF
        if (version.attributes[field] == null) throw notServed()
        val deliverableId = UUID.fromString(version.attributes[Deliverables.DELIVERABLE].toString())
        val deliverable = records.get(Deliverables.DELIVERABLE, deliverableId)
        val download = files.open(Deliverables.VERSION, UUID.fromString(version.id), field)
        exportLog.deliverable(
            user,
            ExportLog.DOWNLOAD,
            level ?: "",
            format,
            "versión ${version.attributes[Deliverables.VERSION_NUMBER]} (revisión ${version.attributes["revision"]}), archivo con SHA-256 ${download.file.sha256}",
            project(deliverable),
            null,
            UUID.fromString(version.id)
        )
        return stream(download)
    }

    // la ruta nativa de un campo FILE de deliverable: los borradores, como GET …/draft/content; las candidatas, el modelo y lo
    // que no existe, 404
    suspend fun native(
        id: UUID,
        field: String
    ): ResponseEntity<Flux<DataBuffer>> =
        when (field) {
            Deliverables.DRAFT_INTERNAL_DOCX -> draft(id, Deliverables.INTERNAL, DOCX)
            Deliverables.DRAFT_INTERNAL_PDF -> draft(id, Deliverables.INTERNAL, PDF)
            Deliverables.DRAFT_EXTERNAL_PDF -> draft(id, Deliverables.EXTERNAL, PDF)
            else -> throw notServed()
        }

    private suspend fun require(
        user: wasichai.core.identity.AuthenticatedUser,
        action: String,
        what: String
    ) {
        if (!permissions.has(user, action, Deliverables.DELIVERABLE)) {
            throw Finding("ACTION_REQUIRED", "$what exige la acción $action sobre deliverable", "record", status = HttpStatus.FORBIDDEN).toCodedException()
        }
    }

    private fun project(deliverable: RecordResponse): UUID? = deliverable.attributes[Deliverables.PROJECT]?.toString()?.let { UUID.fromString(it) }

    private fun stream(download: FileDownload): ResponseEntity<Flux<DataBuffer>> {
        val file = download.file
        return ResponseEntity
            .ok()
            .contentType(MediaType.parseMediaType(file.contentType))
            .contentLength(file.sizeBytes)
            .eTag(file.sha256)
            .cacheControl(CacheControl.noStore())
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition
                    .attachment()
                    .filename(file.fileName, StandardCharsets.UTF_8)
                    .build()
                    .toString()
            ).header(NOSNIFF, "nosniff")
            .body(download.content)
    }

    private fun level(raw: String?): String =
        when (raw) {
            Deliverables.INTERNAL, Deliverables.EXTERNAL -> raw
            else -> throw UnprocessableContentException(listOf(FieldViolation("level", "INTERNO o EXTERNO")))
        }

    private fun format(raw: String?): String =
        when (raw ?: PDF) {
            PDF, DOCX, MODEL -> raw ?: PDF
            else -> throw UnprocessableContentException(listOf(FieldViolation("format", "pdf o, solo en INTERNO, docx")))
        }

    private fun notServed(): NotFoundException = NotFoundException("Ese archivo del entregable no se sirve")

    companion object {
        const val PDF = "pdf"
        const val DOCX = "docx"
        const val MODEL = "model"
        const val NOSNIFF = "X-Content-Type-Options"

        // la descripción de la fila DESCARGA de un borrador (api.md §4.12): el archivo servido por su SHA-256, que es el ETag.
        // con LISTO, los archivos son los del último intento; fuera de LISTO (EN COLA, EN PROCESO o FALLIDO) siguen siendo los
        // del último borrador LISTO, mientras draft_revision y draft_generated_at ya pueden ser los del intento nuevo: la fila
        // no le atribuye al archivo esa letra
        fun draftDescription(
            a: Map<String, Any?>,
            sha256: String
        ): String {
            val status = a[Deliverables.STATUS]?.toString()
            return if (status == Deliverables.READY) {
                "borrador ${a[Deliverables.DRAFT_REVISION]} del intento del ${a[Deliverables.GENERATED_AT]}, archivo con SHA-256 $sha256"
            } else {
                "último borrador LISTO (el borrador está ahora $status), archivo con SHA-256 $sha256"
            }
        }
    }
}

// las rutas propias de descarga y las nativas de wasichai-files de los tres objetos (especificación de F8 §2.2.6): el
// segmento literal gana a {object}, así la API genérica no se salta export_log ni la marca de agua. la subida y el reemplazo
// por la API genérica, 403 FILE_WRITE_NOT_ALLOWED: los archivos de entregables los escriben JOB-05, la emisión y POST
// /api/sgspe/deliverable-templates. la descarga nativa de una plantilla queda como la de wasichai (READ)
@RestController
class DeliverableContentController(
    private val content: DeliverableContent
) {
    @GetMapping("/api/sgspe/deliverables/{id}/draft/content")
    suspend fun draft(
        @PathVariable id: UUID,
        @RequestParam(required = false) level: String?,
        @RequestParam(required = false) format: String?,
        exchange: ServerWebExchange
    ): ResponseEntity<Flux<DataBuffer>> {
        onlyGet(exchange)
        StrictQuery.requireOnly(exchange.request.queryParams, setOf("level", "format"))
        return content.draft(id, level, format)
    }

    @GetMapping("/api/sgspe/deliverables/{id}/versions/{versionNumber}/content")
    suspend fun version(
        @PathVariable id: UUID,
        @PathVariable versionNumber: Int,
        @RequestParam(required = false) format: String?,
        exchange: ServerWebExchange
    ): ResponseEntity<Flux<DataBuffer>> {
        onlyGet(exchange)
        StrictQuery.requireOnly(exchange.request.queryParams, setOf("format"))
        return content.version(id, versionNumber, format)
    }

    @GetMapping("/api/objects/deliverable/records/{id}/files/{field}")
    suspend fun nativeDraft(
        @PathVariable id: UUID,
        @PathVariable field: String,
        exchange: ServerWebExchange
    ): ResponseEntity<Flux<DataBuffer>> {
        onlyGet(exchange)
        return content.native(id, field)
    }

    @GetMapping("/api/objects/deliverable_version/records/{id}/files/{field}")
    suspend fun nativeVersion(
        @PathVariable id: UUID,
        @PathVariable field: String,
        exchange: ServerWebExchange
    ): ResponseEntity<Flux<DataBuffer>> {
        onlyGet(exchange)
        return content.versionById(id, field)
    }

    // Spring atiende HEAD con el GET: aquí no, porque cada descarga escribe su fila de export_log aunque no se sirva nada
    private fun onlyGet(exchange: ServerWebExchange) {
        if (exchange.request.method == HttpMethod.HEAD) throw MethodNotAllowedException(HttpMethod.HEAD, setOf(HttpMethod.GET))
    }

    // no suspend: la reflexión de Kotlin no halla la Continuation de un manejador suspend sin parámetros que devuelve Nothing
    // y Spring respondía 500 en vez del 403
    @PostMapping(
        "/api/objects/deliverable/files/{field}",
        "/api/objects/deliverable_version/files/{field}",
        "/api/objects/deliverable_template/files/{field}",
        "/api/objects/deliverable/records/{id}/files/{field}",
        "/api/objects/deliverable_version/records/{id}/files/{field}",
        "/api/objects/deliverable_template/records/{id}/files/{field}"
    )
    fun upload(): ResponseEntity<Void> =
        throw Finding(
            "FILE_WRITE_NOT_ALLOWED",
            "Los archivos de entregables y plantillas no se suben por la API genérica",
            "file",
            status = HttpStatus.FORBIDDEN
        ).toCodedException()
}
