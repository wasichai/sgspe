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
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.MethodNotAllowedException
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Flux
import sgspe.common.Callers
import sgspe.common.CommandAnswer
import sgspe.common.Finding
import sgspe.common.Idempotency
import sgspe.common.IdempotencyKey
import sgspe.common.Permissions
import sgspe.common.Records
import sgspe.common.StrictQuery
import sgspe.common.Transactions
import sgspe.common.UnprocessableContentException
import sgspe.common.toCodedException
import sgspe.privacy.CanonicalJson
import sgspe.privacy.Disclosure
import sgspe.privacy.ExportLog
import wasichai.core.common.Actions
import wasichai.core.common.FieldViolation
import wasichai.core.common.NotFoundException
import wasichai.core.data.RecordResponse
import wasichai.core.identity.AuthenticatedUser
import wasichai.files.FileService
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.util.UUID

// el estado de una presentación (api.md §4.12): sin la especificación, el PDF ni el MP4, que se descargan por sus rutas
data class PresentationStatus(
    val id: String,
    val versionNumber: Int?,
    val audience: String?,
    val level: String?,
    val status: String?,
    val failureCode: String?,
    val slideCount: Int?,
    val requestedAt: String?,
    val generatedAt: String?,
    val modelSha256: String?,
    val specSha256: String?,
    val pdfSha256: String?,
    val plannerVersion: String?,
    val designVersion: String?,
    val rendererVersion: String?,
    // el MP4 a pedido (ASM-148): vacíos si no se pidió
    val videoStatus: String? = null,
    val videoFailureCode: String? = null,
    val videoRequestedAt: String? = null,
    val videoGeneratedAt: String? = null,
    val videoSha256: String? = null
)

// las presentaciones de una versión emitida (ADR-0031, deliverables.md §16.8), como la persona. el pedido lee el entregable y la
// versión (404 si no los lee), exige el permiso del nivel (DOWNLOAD_RESTRICTED en INTERNO, EXPORT en EXTERNO y PUBLICO) y
// CREATE sobre deliverable_presentation, rechaza un nivel menos restrictivo que el de la versión (422 LEVEL_NOT_ALLOWED), un
// público que no admite el nivel (422 AUDIENCE_LEVEL_MISMATCH) y un modelo sin hechos tipados (422 PRESENTATION_NOT_AVAILABLE;
// en PUBLICO, PUBLIC_NOT_AVAILABLE), y crea la presentación EN COLA con el SHA-256 del modelo; JOB-09 la compone. la misma
// versión, público, nivel y planificador devuelve la existente, y una FALLIDO vuelve a la cola. las descargas exigen el mismo
// permiso, LISTO y, en EXTERNO y PUBLICO, la lista de verificación (ASM-147), y dejan una fila de export_log. el MP4 (ASM-148)
// se pide aparte sobre una presentación LISTO, con los mismos permisos que el pedido, y se baja como el PDF
@Service
class DeliverablePresentations(
    private val records: Records,
    private val callers: Callers,
    private val permissions: Permissions,
    private val transactions: Transactions,
    private val files: FileService,
    private val exportLog: ExportLog,
    private val properties: DeliverableProperties,
    private val clock: Clock
) {
    suspend fun request(
        id: UUID,
        versionNumber: Int,
        body: Map<String, Any?>
    ): CommandAnswer {
        val user = callers.require()
        val unknown = body.keys - setOf(AUDIENCE, LEVEL)
        if (unknown.isNotEmpty()) throw UnprocessableContentException(unknown.map { FieldViolation(it, "campo desconocido") })
        val audience =
            Audience.of(body[AUDIENCE]?.toString()) ?: throw UnprocessableContentException(listOf(FieldViolation(AUDIENCE, Audience.entries.joinToString())))
        val level =
            Disclosure.entries.firstOrNull { it.name == body[LEVEL]?.toString() }
                ?: throw UnprocessableContentException(listOf(FieldViolation(LEVEL, "INTERNO, EXTERNO o PUBLICO")))
        records.get(Deliverables.DELIVERABLE, id)
        val version = version(id, versionNumber)
        val versionLevel = Disclosure.valueOf(version.attributes[Deliverables.LEVEL].toString())
        if (!Presentations.allowed(level, versionLevel)) {
            throw unprocessable("LEVEL_NOT_ALLOWED", "Una presentación de una versión $versionLevel no puede salir en $level", LEVEL)
        }
        if (!audience.admits(level)) throw unprocessable("AUDIENCE_LEVEL_MISMATCH", "El público $audience no admite el nivel $level", AUDIENCE)
        permissions.require(user, "Pedir una presentación $level", action(level) to Deliverables.DELIVERABLE, Actions.CREATE to Presentations.OBJECT)
        val base = Records.uuid(version.attributes[Deliverables.DERIVED_FROM])?.let { records.get(Deliverables.VERSION, it) } ?: version
        val modelSha = base.attributes[Presentations.MODEL_SHA256]?.toString() ?: throw notAvailable(level)
        // el modelo de la base, leído como quien pide y comprobado por su SHA-256: un modelo sin hechos tipados no da presentación
        val download = files.open(Deliverables.VERSION, UUID.fromString(base.id), Deliverables.MODEL)
        val bytes = DeliverableIssue.capped(download.content, properties.maxFileBytes)
        if (CanonicalJson.sha256(bytes) !=
            modelSha
        ) {
            throw unprocessable(Presentations.MODEL_MISMATCH, "El modelo de la versión no coincide con su SHA-256", Deliverables.MODEL)
        }
        val model = CanonicalJson.read(bytes)
        if (model.meta.typeVersion < 2 || model.facts == null) throw notAvailable(level)
        val versionId = version.id
        val saved =
            transactions.locked("${Presentations.OBJECT}:$versionId") {
                val same =
                    records
                        .all(
                            Presentations.OBJECT,
                            mapOf(
                                Presentations.VERSION to versionId,
                                Presentations.AUDIENCE to audience.name,
                                Presentations.LEVEL to level.name,
                                Presentations.PLANNER to DeckPlanner.VERSION
                            )
                        ).firstOrNull()
                when {
                    same == null ->
                        records.create(
                            Presentations.OBJECT,
                            mapOf(
                                Presentations.DELIVERABLE to id.toString(),
                                Presentations.VERSION to versionId,
                                Presentations.AUDIENCE to audience.name,
                                Presentations.LEVEL to level.name,
                                Presentations.STATUS to Presentations.QUEUED,
                                Presentations.REQUESTED_AT to clock.instant().toString(),
                                Presentations.MODEL_SHA256 to modelSha,
                                Presentations.PLANNER to DeckPlanner.VERSION
                            ),
                            "presentación ${audience.name} ${level.name} de la versión $versionNumber"
                        )
                    same.attributes[Presentations.STATUS]?.toString() == Presentations.FAILED ->
                        records.patch(
                            Presentations.OBJECT,
                            UUID.fromString(same.id),
                            mapOf(
                                Presentations.STATUS to Presentations.QUEUED,
                                Presentations.REQUESTED_AT to clock.instant().toString(),
                                Presentations.FAILURE to null
                            ),
                            "presentación pedida otra vez"
                        )
                    else -> same
                }
            }
        return CommandAnswer(
            HttpStatus.ACCEPTED,
            mapOf("presentation" to status(saved, versionNumber)),
            saved.updatedAt,
            "/api/sgspe/presentations/${saved.id}"
        )
    }

    // el pedido del MP4 de una presentación LISTO (ASM-148): lo pone EN COLA con video_requested_at, como la persona (UPDATE); uno
    // ya pedido, en proceso o LISTO se devuelve como está, y uno FALLIDO vuelve a la cola
    suspend fun requestVideo(presentationId: UUID): CommandAnswer {
        val user = callers.require()
        val presentation = records.get(Presentations.OBJECT, presentationId)
        val level = Disclosure.valueOf(presentation.attributes[Presentations.LEVEL].toString())
        permissions.require(
            user,
            "Pedir el video de una presentación $level",
            action(level) to Deliverables.DELIVERABLE,
            Actions.CREATE to Presentations.OBJECT
        )
        val saved =
            transactions.locked("${Presentations.OBJECT}:video:$presentationId") {
                val current = records.get(Presentations.OBJECT, presentationId)
                val a = current.attributes
                if (a[Presentations.STATUS]?.toString() != Presentations.READY) {
                    throw Finding("STATE_MISMATCH", "La presentación no está LISTO", "record", status = HttpStatus.CONFLICT).toCodedException()
                }
                when (a[Presentations.VIDEO_STATUS]?.toString()) {
                    null, Presentations.FAILED ->
                        records.patch(
                            Presentations.OBJECT,
                            presentationId,
                            mapOf(
                                Presentations.VIDEO_STATUS to Presentations.QUEUED,
                                Presentations.VIDEO_REQUESTED_AT to clock.instant().toString(),
                                Presentations.VIDEO_FAILURE to null
                            ),
                            "video de la presentación pedido"
                        )
                    else -> current
                }
            }
        val version = Records.uuid(saved.attributes[Presentations.VERSION])?.let { records.find(Deliverables.VERSION, it) }
        return CommandAnswer(
            HttpStatus.ACCEPTED,
            mapOf("presentation" to status(saved, number(version))),
            saved.updatedAt,
            "/api/sgspe/presentations/${saved.id}"
        )
    }

    suspend fun list(id: UUID): List<PresentationStatus> {
        records.get(Deliverables.DELIVERABLE, id)
        val versions = records.all(Deliverables.VERSION, mapOf(Deliverables.DELIVERABLE to id.toString())).associateBy { it.id }
        return records
            .all(Presentations.OBJECT, mapOf(Presentations.DELIVERABLE to id.toString()))
            .sortedByDescending { it.createdAt }
            .map { status(it, number(versions[it.attributes[Presentations.VERSION]?.toString()])) }
    }

    suspend fun get(presentationId: UUID): PresentationStatus {
        val presentation = records.get(Presentations.OBJECT, presentationId)
        val version = Records.uuid(presentation.attributes[Presentations.VERSION])?.let { records.find(Deliverables.VERSION, it) }
        return status(presentation, number(version))
    }

    // el PDF (format pdf), la especificación (format deck) o el MP4 (format mp4, con el video LISTO) de una presentación LISTO
    suspend fun content(
        presentationId: UUID,
        field: String,
        checklist: Boolean
    ): ResponseEntity<Flux<DataBuffer>> {
        val user = callers.require()
        val presentation = records.get(Presentations.OBJECT, presentationId)
        val a = presentation.attributes
        val level = Disclosure.valueOf(a[Presentations.LEVEL].toString())
        require(user, level)
        if (a[Presentations.STATUS]?.toString() != Presentations.READY || a[field] == null) {
            throw Finding("STATE_MISMATCH", "La presentación no está LISTO", "record", status = HttpStatus.CONFLICT).toCodedException()
        }
        if (field == Presentations.VIDEO && a[Presentations.VIDEO_STATUS]?.toString() != Presentations.READY) {
            throw Finding("STATE_MISMATCH", "El video de la presentación no está LISTO", "record", status = HttpStatus.CONFLICT).toCodedException()
        }
        if (level != Disclosure.INTERNO && !checklist) {
            throw Finding(
                "CHECKLIST_REQUIRED",
                "Una presentación $level exige confirmar la lista de verificación",
                CHECKLIST,
                status = HttpStatus.BAD_REQUEST
            ).toCodedException()
        }
        val deliverableId = UUID.fromString(a[Presentations.DELIVERABLE].toString())
        val deliverable = records.get(Deliverables.DELIVERABLE, deliverableId)
        val download = files.open(Presentations.OBJECT, presentationId, field)
        val format =
            when (field) {
                Presentations.SPEC -> DECK
                Presentations.VIDEO -> MP4
                else -> DeliverableContent.PDF
            }
        exportLog.presentation(
            user,
            level.name,
            format,
            "presentación ${a[Presentations.AUDIENCE]} ${level.name}, archivo con SHA-256 ${download.file.sha256}" +
                if (level != Disclosure.INTERNO) ", lista de verificación confirmada" else "",
            Records.uuid(deliverable.attributes[Deliverables.PROJECT]),
            deliverableId,
            presentationId
        )
        val file = download.file
        val type =
            when (field) {
                Presentations.SPEC -> MediaType.APPLICATION_JSON
                Presentations.VIDEO -> MediaType.parseMediaType(Presentations.VIDEO_TYPE)
                else -> MediaType.parseMediaType(file.contentType)
            }
        return ResponseEntity
            .ok()
            .contentType(type)
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
            ).header(DeliverableContent.NOSNIFF, "nosniff")
            .body(download.content)
    }

    private suspend fun version(
        id: UUID,
        number: Int
    ): RecordResponse =
        records
            .all(Deliverables.VERSION, mapOf(Deliverables.DELIVERABLE to id.toString(), Deliverables.VERSION_NUMBER to number.toString()))
            .singleOrNull() ?: throw NotFoundException("La versión $number no existe o no está a su alcance")

    private suspend fun require(
        user: AuthenticatedUser,
        level: Disclosure
    ) {
        val action = action(level)
        if (!permissions.has(user, action, Deliverables.DELIVERABLE)) {
            throw Finding(
                "ACTION_REQUIRED",
                "Descargar una presentación ${level.name} exige la acción $action sobre deliverable",
                "record",
                status = HttpStatus.FORBIDDEN
            ).toCodedException()
        }
    }

    private fun action(level: Disclosure): String = if (level == Disclosure.INTERNO) Deliverables.DOWNLOAD_RESTRICTED else Deliverables.EXPORT

    private fun notAvailable(level: Disclosure) =
        unprocessable(
            if (level == Disclosure.PUBLICO) "PUBLIC_NOT_AVAILABLE" else "PRESENTATION_NOT_AVAILABLE",
            "La versión no trae los hechos tipados que una presentación necesita: vuelva a emitir el entregable",
            Deliverables.MODEL
        )

    private fun unprocessable(
        code: String,
        message: String,
        field: String
    ) = Finding(code, message, field, status = HttpStatus.UNPROCESSABLE_CONTENT).toCodedException()

    private fun number(version: RecordResponse?): Int? = (version?.attributes?.get(Deliverables.VERSION_NUMBER) as? Number)?.toInt()

    private fun status(
        p: RecordResponse,
        versionNumber: Int?
    ): PresentationStatus {
        val a = p.attributes

        fun text(field: String) = a[field]?.toString()
        return PresentationStatus(
            p.id,
            versionNumber,
            text(Presentations.AUDIENCE),
            text(Presentations.LEVEL),
            text(Presentations.STATUS),
            text(Presentations.FAILURE),
            (a[Presentations.SLIDES] as? Number)?.toInt(),
            text(Presentations.REQUESTED_AT),
            text(Presentations.GENERATED_AT),
            text(Presentations.MODEL_SHA256),
            text(Presentations.SPEC_SHA256),
            text(Presentations.PDF_SHA256),
            text(Presentations.PLANNER),
            text(Presentations.DESIGN),
            text(Presentations.RENDERER),
            text(Presentations.VIDEO_STATUS),
            text(Presentations.VIDEO_FAILURE),
            text(Presentations.VIDEO_REQUESTED_AT),
            text(Presentations.VIDEO_GENERATED_AT),
            text(Presentations.VIDEO_SHA256)
        )
    }

    companion object {
        const val AUDIENCE = "audience"
        const val LEVEL = "level"
        const val CHECKLIST = "checklistConfirmed"
        const val DECK = "deck"
        const val MP4 = "mp4"
    }
}

// api.md §4.12: el pedido (READ, la acción del nivel, CREATE; Idempotency-Key) → 202 con Location; la lista y el estado (READ);
// el pedido del MP4 (lo mismo, sobre una presentación LISTO) → 202; el PDF, la especificación y el MP4 (la acción del nivel y,
// en EXTERNO y PUBLICO, ?checklistConfirmed=true); las rutas nativas de los archivos de deliverable_presentation, 404 al leer
// (se descargan por las propias, que dejan export_log) y 403 al subir
@RestController
class DeliverablePresentationsController(
    private val presentations: DeliverablePresentations,
    private val idempotency: Idempotency
) {
    @PostMapping("/api/sgspe/deliverables/{id}/versions/{versionNumber}/presentations")
    suspend fun request(
        @PathVariable id: UUID,
        @PathVariable versionNumber: Int,
        @RequestBody body: Map<String, Any?>,
        @RequestHeader(name = IdempotencyKey.HEADER, required = false) key: String?,
        exchange: ServerWebExchange
    ): ResponseEntity<ByteArray> {
        StrictQuery.requireOnly(exchange.request.queryParams, emptySet())
        return idempotency.respond(key, "POST", exchange.request.path.value(), body) {
            presentations.request(id, versionNumber, body)
        }
    }

    @GetMapping("/api/sgspe/deliverables/{id}/presentations")
    suspend fun list(
        @PathVariable id: UUID,
        exchange: ServerWebExchange
    ): List<PresentationStatus> {
        StrictQuery.requireOnly(exchange.request.queryParams, emptySet())
        return presentations.list(id)
    }

    @GetMapping("/api/sgspe/presentations/{id}")
    suspend fun get(
        @PathVariable id: UUID,
        exchange: ServerWebExchange
    ): PresentationStatus {
        StrictQuery.requireOnly(exchange.request.queryParams, emptySet())
        return presentations.get(id)
    }

    @GetMapping("/api/sgspe/presentations/{id}/content")
    suspend fun content(
        @PathVariable id: UUID,
        @RequestParam(name = DeliverablePresentations.CHECKLIST, required = false) checklist: Boolean?,
        exchange: ServerWebExchange
    ): ResponseEntity<Flux<DataBuffer>> {
        onlyGet(exchange)
        StrictQuery.requireOnly(exchange.request.queryParams, setOf(DeliverablePresentations.CHECKLIST))
        return presentations.content(id, Presentations.PDF, checklist == true)
    }

    @GetMapping("/api/sgspe/presentations/{id}/spec")
    suspend fun spec(
        @PathVariable id: UUID,
        @RequestParam(name = DeliverablePresentations.CHECKLIST, required = false) checklist: Boolean?,
        exchange: ServerWebExchange
    ): ResponseEntity<Flux<DataBuffer>> {
        onlyGet(exchange)
        StrictQuery.requireOnly(exchange.request.queryParams, setOf(DeliverablePresentations.CHECKLIST))
        return presentations.content(id, Presentations.SPEC, checklist == true)
    }

    @PostMapping("/api/sgspe/presentations/{id}/video")
    suspend fun requestVideo(
        @PathVariable id: UUID,
        @RequestHeader(name = IdempotencyKey.HEADER, required = false) key: String?,
        exchange: ServerWebExchange
    ): ResponseEntity<ByteArray> {
        StrictQuery.requireOnly(exchange.request.queryParams, emptySet())
        return idempotency.respond(key, "POST", exchange.request.path.value(), emptyMap<String, Any?>()) {
            presentations.requestVideo(id)
        }
    }

    @GetMapping("/api/sgspe/presentations/{id}/video")
    suspend fun video(
        @PathVariable id: UUID,
        @RequestParam(name = DeliverablePresentations.CHECKLIST, required = false) checklist: Boolean?,
        exchange: ServerWebExchange
    ): ResponseEntity<Flux<DataBuffer>> {
        onlyGet(exchange)
        StrictQuery.requireOnly(exchange.request.queryParams, setOf(DeliverablePresentations.CHECKLIST))
        return presentations.content(id, Presentations.VIDEO, checklist == true)
    }

    // la ruta nativa de wasichai-files no sirve los archivos de una presentación: se saltaría el permiso del nivel, la lista de
    // verificación y export_log
    @GetMapping("/api/objects/deliverable_presentation/records/{id}/files/{field}")
    fun native(): ResponseEntity<Void> = throw NotFoundException("Los archivos de una presentación se descargan por /api/sgspe/presentations/{id}")

    @PostMapping("/api/objects/deliverable_presentation/files/{field}", "/api/objects/deliverable_presentation/records/{id}/files/{field}")
    fun upload(): ResponseEntity<Void> =
        throw Finding(
            "FILE_WRITE_NOT_ALLOWED",
            "Los archivos de una presentación los escribe JOB-09",
            "file",
            status = HttpStatus.FORBIDDEN
        ).toCodedException()

    private fun onlyGet(exchange: ServerWebExchange) {
        if (exchange.request.method == HttpMethod.HEAD) throw MethodNotAllowedException(HttpMethod.HEAD, setOf(HttpMethod.GET))
    }
}
