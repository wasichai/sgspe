package sgspe.deliverable

import kotlinx.coroutines.reactive.awaitSingle
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.core.io.buffer.DataBufferLimitException
import org.springframework.core.io.buffer.DataBufferUtils
import org.springframework.core.io.buffer.DefaultDataBufferFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Flux
import sgspe.common.Callers
import sgspe.common.CommandAnswer
import sgspe.common.Finding
import sgspe.common.Idempotency
import sgspe.common.IdempotencyKey
import sgspe.common.IfMatch
import sgspe.common.Permissions
import sgspe.common.Records
import sgspe.common.StrictBody
import sgspe.common.StrictQuery
import sgspe.common.Transactions
import sgspe.common.UploadLimits
import sgspe.common.Versions
import sgspe.common.toCodedException
import sgspe.privacy.CanonicalJson
import sgspe.privacy.ExportLog
import wasichai.core.common.Actions
import wasichai.core.common.ValidationException
import wasichai.core.data.ChangeReason
import wasichai.core.data.RecordResponse
import wasichai.files.FileService
import wasichai.files.FileUpload
import java.time.Clock
import java.util.UUID

data class IssueResult(
    val deliverable: RecordResponse,
    val versions: List<RecordResponse>
)

// la emisión (especificación de F8 §2.2.5 y §3.5, deliverables.md §8.4, api.md §4.12), en la petición de quien emite y nunca
// como la plataforma: no genera nada. exige UPDATE sobre deliverable, CREATE sobre deliverable_version y export_log y la
// acción ISSUE, el motivo e If-Match; verifica approved y el borrador LISTO (409 STATE_MISMATCH), el mismo
// draft_model_sha256 que se revisó (409 MODEL_MISMATCH), ningún mapa fallido (409 MAP_FAILED), ninguna sección cambiada (409
// SECTIONS_CHANGED) y la lista de verificación EXTERNO (400 CHECKLIST_REQUIRED). sube como la persona una copia de cada
// candidata y del modelo (FileService.stage) en una transacción propia, que se confirma aunque la emisión falle (las subidas sin
// adjuntar las borra StoredFileCleanup); y en una transacción con el candado del entregable crea la versión INTERNO (DOCX, PDF
// y modelo) y la EXTERNO (solo PDF, derived_from), apunta current_version y current_revision, escribe una fila de export_log
// EMISION por versión y aplica issue al final, con la marca de Records que deja pasar CommandTransitionGuard, para que AUT-12
// vea el registro completo
@Service
class DeliverableIssue(
    private val records: Records,
    private val callers: Callers,
    private val permissions: Permissions,
    private val transactions: Transactions,
    private val versions: Versions,
    private val guard: DeliverableWriteGuard,
    private val files: FileService,
    private val exportLog: ExportLog,
    private val properties: DeliverableProperties,
    private val clock: Clock
) {
    suspend fun issue(
        id: UUID,
        body: Map<String, Any?>,
        reason: String?,
        ifMatch: String?
    ): CommandAnswer {
        val expected = IfMatch.required(ifMatch)
        StrictBody.requireOnly(body, setOf(SHA, CHECKLIST))
        val sha = body[SHA] as? String ?: throw ValidationException("Falta el SHA-256 del modelo revisado", SHA, "envíe draftModelSha256")
        if (reason.isNullOrBlank()) throw ValidationException("Emitir exige un motivo", "reason", "envíe la cabecera X-Change-Reason")
        val user = callers.require()
        permissions.require(
            user,
            "Emitir un entregable",
            Actions.UPDATE to Deliverables.DELIVERABLE,
            Actions.CREATE to Deliverables.VERSION,
            Actions.CREATE to ExportLog.OBJECT,
            Deliverables.ISSUE to Deliverables.DELIVERABLE
        )
        val before = records.get(Deliverables.DELIVERABLE, id)
        // la misma precondición antes de subir nada (se repite dentro del candado): un If-Match viejo no deja copias huérfanas
        versions.check(Deliverables.DELIVERABLE, id, expected, before.updatedAt, "El entregable")
        verify(id, before, sha, body[CHECKLIST] == true)
        // las copias, como la persona, antes de la transacción de la emisión (decisión 9 del plan 8.2b)
        val copies = transactions.independent { copies(id) }
        val model = CanonicalJson.read(copies.getValue(Deliverables.MODEL).bytes)
        val result =
            transactions.locked("${Deliverables.DELIVERABLE}:$id") {
                val current = records.get(Deliverables.DELIVERABLE, id)
                versions.check(Deliverables.DELIVERABLE, id, expected, current.updatedAt, "El entregable")
                verify(id, current, sha, body[CHECKLIST] == true)
                val number =
                    (
                        records.all(Deliverables.VERSION, mapOf(Deliverables.DELIVERABLE to id.toString())).maxOfOrNull {
                            (it.attributes[Deliverables.VERSION_NUMBER] as Number).toInt()
                        }
                            ?: 0
                    ) +
                        1
                val now = clock.instant().toString()
                val common =
                    mapOf(
                        Deliverables.DELIVERABLE to id.toString(),
                        "revision" to model.meta.issueRevision,
                        "issued_at" to now,
                        "issued_by_user_id" to user.userId.toString(),
                        "issued_by_user_email" to user.email,
                        "model_sha256" to sha,
                        "template_sha256" to model.meta.templateSha256,
                        "ai_assisted_sections" to model.aiAssistedSections.joinToString("\n").ifEmpty { null }
                    )
                val internal =
                    records.create(
                        Deliverables.VERSION,
                        common +
                            mapOf(
                                Deliverables.VERSION_NUMBER to number,
                                Deliverables.LEVEL to Deliverables.INTERNAL,
                                Deliverables.DOCX to copies.getValue(Deliverables.DOCX).id,
                                "docx_sha256" to copies.getValue(Deliverables.DOCX).sha256,
                                Deliverables.PDF to copies.getValue(INTERNAL_PDF).id,
                                "pdf_sha256" to copies.getValue(INTERNAL_PDF).sha256,
                                Deliverables.MODEL to copies.getValue(Deliverables.MODEL).id
                            ),
                        reason
                    )
                val external =
                    records.create(
                        Deliverables.VERSION,
                        common +
                            mapOf(
                                Deliverables.VERSION_NUMBER to number + 1,
                                Deliverables.LEVEL to Deliverables.EXTERNAL,
                                Deliverables.PDF to copies.getValue(EXTERNAL_PDF).id,
                                "pdf_sha256" to copies.getValue(EXTERNAL_PDF).sha256,
                                Deliverables.DERIVED_FROM to internal.id
                            ),
                        reason
                    )
                records.patch(
                    Deliverables.DELIVERABLE,
                    id,
                    mapOf(
                        Deliverables.CURRENT_VERSION to internal.id,
                        Deliverables.CURRENT_REVISION to model.meta.issueRevision
                    ),
                    reason
                )
                val project = current.attributes[Deliverables.PROJECT]?.toString()?.let { UUID.fromString(it) }
                for (version in listOf(internal, external)) {
                    val level = version.attributes[Deliverables.LEVEL].toString()
                    val confirmation = if (level == Deliverables.EXTERNAL) "; lista de verificación EXTERNO confirmada" else ""
                    exportLog.deliverable(
                        user,
                        ExportLog.ISSUE,
                        level,
                        if (level == Deliverables.INTERNAL) "docx, pdf" else "pdf",
                        "versión ${version.attributes[Deliverables.VERSION_NUMBER]}, revisión ${model.meta.issueRevision}, modelo $sha$confirmation",
                        project,
                        null,
                        UUID.fromString(version.id)
                    )
                }
                val issued = records.transition(Deliverables.DELIVERABLE, id, Deliverables.ISSUE_TRANSITION, reason)
                IssueResult(
                    issued,
                    listOf(records.get(Deliverables.VERSION, UUID.fromString(internal.id)), records.get(Deliverables.VERSION, UUID.fromString(external.id)))
                )
            }
        return CommandAnswer(HttpStatus.CREATED, result, result.deliverable.updatedAt, "/api/objects/${Deliverables.DELIVERABLE}/records/$id")
    }

    // las comprobaciones de la emisión, antes de subir nada y otra vez dentro del candado
    private suspend fun verify(
        id: UUID,
        deliverable: RecordResponse,
        sha: String,
        checklist: Boolean
    ) {
        val a = deliverable.attributes
        if (deliverable.state != Deliverables.APPROVED || a[Deliverables.STATUS]?.toString() != Deliverables.READY) {
            throw Finding("STATE_MISMATCH", "Se emite un entregable aprobado con su borrador LISTO", "record").toCodedException()
        }
        if (a[Deliverables.MODEL_SHA256]?.toString() != sha) {
            throw Finding("MODEL_MISMATCH", "El modelo del borrador no es el que se revisó", SHA).toCodedException()
        }
        if (DraftGeneration.parse(a[Deliverables.WARNINGS]).any { it.code == Deliverables.MAP_FAILED }) {
            throw Finding("MAP_FAILED", "Un mapa del borrador falló: pida otro borrador cuando GeoServer responda", "draft").toCodedException()
        }
        guard.reviewed(id, a)
        // T1 y T2 preparan EXTERNO (ASM-037)
        if (!checklist) {
            throw Finding(
                "CHECKLIST_REQUIRED",
                "Emitir el nivel EXTERNO exige confirmar la lista de verificación",
                CHECKLIST,
                status = HttpStatus.BAD_REQUEST
            ).toCodedException()
        }
    }

    class Copy(
        val id: String,
        val sha256: String,
        val bytes: ByteArray
    )

    // cada candidata y el modelo, leídos y vueltos a subir como quien emite para su campo de deliverable_version
    private suspend fun copies(id: UUID): Map<String, Copy> =
        listOf(
            Triple(Deliverables.CANDIDATE_INTERNAL_DOCX, Deliverables.DOCX, Deliverables.DOCX),
            Triple(Deliverables.CANDIDATE_INTERNAL_PDF, Deliverables.PDF, INTERNAL_PDF),
            Triple(Deliverables.CANDIDATE_EXTERNAL_PDF, Deliverables.PDF, EXTERNAL_PDF),
            Triple(Deliverables.DRAFT_MODEL, Deliverables.MODEL, Deliverables.MODEL)
        ).associate { (source, target, key) ->
            val download = files.open(Deliverables.DELIVERABLE, id, source)
            val bytes = capped(download.content, properties.maxFileBytes)
            val staged =
                files.stage(
                    Deliverables.VERSION,
                    target,
                    FileUpload(download.file.fileName, download.file.contentType, Flux.just(DefaultDataBufferFactory.sharedInstance.wrap(bytes)))
                )
            key to Copy(staged.id.toString(), staged.sha256, bytes)
        }

    companion object {
        const val SHA = "draftModelSha256"
        const val CHECKLIST = "checklistConfirmed"
        private const val INTERNAL_PDF = "internal_pdf"
        private const val EXTERNAL_PDF = "external_pdf"

        // los bytes de una descarga con el tope de los archivos de entregables: más allá, el 400 FILE_TOO_LARGE de las subidas
        suspend fun capped(
            content: Flux<out DataBuffer>,
            max: Long
        ): ByteArray {
            val joined =
                try {
                    DataBufferUtils.join(content, max.toInt()).awaitSingle()
                } catch (_: DataBufferLimitException) {
                    throw UploadLimits.tooLarge(max)
                }
            return try {
                ByteArray(joined.readableByteCount()).also { joined.read(it) }
            } finally {
                DataBufferUtils.release(joined)
            }
        }
    }
}

// api.md §4.12: POST /api/sgspe/deliverables/{id}/issue con IK, IM y CR → 201 { deliverable, versions[] }
@RestController
class DeliverableIssueController(
    private val issue: DeliverableIssue,
    private val idempotency: Idempotency
) {
    @PostMapping("/api/sgspe/deliverables/{id}/issue")
    suspend fun issue(
        @PathVariable id: UUID,
        @RequestBody body: Map<String, Any?>,
        @RequestHeader(name = ChangeReason.HEADER, required = false) reason: String?,
        @RequestHeader(name = IfMatch.HEADER, required = false) ifMatch: String?,
        @RequestHeader(name = IdempotencyKey.HEADER, required = false) key: String?,
        exchange: ServerWebExchange
    ): ResponseEntity<ByteArray> {
        StrictQuery.requireOnly(exchange.request.queryParams, emptySet())
        return idempotency.respond(key, "POST", exchange.request.path.value(), body) { issue.issue(id, body, ChangeReason.fromHeader(reason), ifMatch) }
    }
}
