package sgspe.deliverable

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.withContext
import org.springframework.core.io.buffer.DataBufferLimitException
import org.springframework.core.io.buffer.DataBufferUtils
import org.springframework.core.io.buffer.DefaultDataBufferFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.http.codec.multipart.FilePart
import org.springframework.http.codec.multipart.FormFieldPart
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Flux
import sgspe.common.Callers
import sgspe.common.CommandAnswer
import sgspe.common.Idempotency
import sgspe.common.IdempotencyKey
import sgspe.common.Permissions
import sgspe.common.Records
import sgspe.common.StrictBody
import sgspe.common.StrictQuery
import sgspe.common.Transactions
import sgspe.common.UploadLimits
import sgspe.privacy.CanonicalJson
import wasichai.core.common.Actions
import wasichai.core.common.FieldViolation
import wasichai.core.common.GlobalExceptionHandler
import wasichai.core.common.ValidationException
import wasichai.core.common.WasichaiException
import wasichai.files.FileService
import wasichai.files.FileUpload

// 422 TEMPLATE_INVALID (api.md §4.12): errors[0] es file/TEMPLATE_INVALID y el problem+json suma `violations` y `warnings`
// ([{ code, detail }]), que agrega el controlador
class TemplateInvalid(
    val report: TemplateReport
) : WasichaiException(HttpStatus.UNPROCESSABLE_CONTENT, "La plantilla no es válida", listOf(FieldViolation("file", "TEMPLATE_INVALID")))

data class TemplateSummary(
    val id: String,
    val templateKey: String,
    val name: String?,
    val sha256: String?,
    val createdAt: String?,
    val warnings: List<TemplateFinding>
)

// las plantillas Word de la organización (especificación de F8 §3.3, ASM-135): subirlas exige MANAGE_METADATA sobre deliverable
// (en la práctica, ADMIN); TemplateValidator las valida antes de nada; si pasan, se suben como quien llama, declaradas DOCX
// también si son .dotx (el sniffer de wasichai-files, decisión 3 del plan 8.1), y se crea el deliverable_template (apiOnly,
// appendOnly) con sus avisos. listar exige READ sobre deliverable y devuelve la más reciente de cada clave
@Service
class DeliverableTemplates(
    private val records: Records,
    private val callers: Callers,
    private val permissions: Permissions,
    private val files: FileService,
    private val transactions: Transactions,
    private val properties: DeliverableProperties
) {
    private val validator = TemplateValidator(properties.templates)

    suspend fun upload(
        fileName: String,
        bytes: ByteArray,
        templateKey: String,
        name: String?
    ): CommandAnswer {
        val user = callers.require()
        permissions.require(user, "Subir una plantilla", Actions.MANAGE_METADATA to Deliverables.DELIVERABLE)
        if (!KEY.matches(templateKey)) throw ValidationException("Clave inválida", "templateKey", "de 1 a 64 letras minúsculas, dígitos o guiones")
        val report = withContext(Dispatchers.IO) { validator.validate(bytes, fileName) }
        if (!report.valid) throw TemplateInvalid(report)
        // en una transacción propia, como las copias de la emisión (decisión 9 del plan 8.2b): si el alta falla, la subida queda
        // sin adjuntar y la borra StoredFileCleanup, en lugar de un objeto en el almacén sin su fila
        val staged =
            transactions.independent {
                files.stage(
                    Deliverables.TEMPLATE,
                    FILE,
                    FileUpload(fileName, Deliverables.DOCX_TYPE, Flux.just(DefaultDataBufferFactory.sharedInstance.wrap(bytes)))
                )
            }
        val created =
            records.create(
                Deliverables.TEMPLATE,
                mapOf(
                    "template_key" to templateKey,
                    "name" to name,
                    FILE to staged.id.toString(),
                    "validation_warnings" to report.warnings.joinToString("\n") { "${it.code} ${it.detail}" }.ifEmpty { null }
                ).filterValues { it != null },
                "plantilla $templateKey"
            )
        val body = mapOf("id" to created.id, "templateKey" to templateKey, "sha256" to CanonicalJson.sha256(bytes), "warnings" to report.warnings)
        return CommandAnswer(HttpStatus.CREATED, body, created.updatedAt, "/api/objects/${Deliverables.TEMPLATE}/records/${created.id}")
    }

    suspend fun latest(): List<TemplateSummary> {
        val user = callers.require()
        permissions.require(user, "Listar las plantillas", Actions.READ to Deliverables.DELIVERABLE)
        return records
            .all(Deliverables.TEMPLATE)
            .groupBy { it.attributes["template_key"].toString() }
            .map { (_, rows) -> rows.maxBy { it.createdAt ?: java.time.Instant.EPOCH } }
            .sortedBy { it.attributes["template_key"].toString() }
            .map { row ->
                TemplateSummary(
                    row.id,
                    row.attributes["template_key"].toString(),
                    row.attributes["name"]?.toString(),
                    (row.attributes[FILE] as? Map<*, *>)?.get("sha256")?.toString(),
                    row.createdAt?.toString(),
                    row.attributes["validation_warnings"]?.toString().orEmpty().lines().filter { it.isNotBlank() }.map {
                        TemplateFinding(it.substringBefore(' '), it.substringAfter(' ', ""))
                    }
                )
            }
    }

    companion object {
        const val FILE = "file"
        private val KEY = Regex("[a-z0-9][a-z0-9-]{0,63}")
    }
}

// api.md §4.12: POST multipart (file, templateKey, name; Idempotency-Key: el cuerpo de la clave es la clave, el nombre y el
// SHA-256 del archivo) y GET
@RestController
class DeliverableTemplatesController(
    private val templates: DeliverableTemplates,
    private val idempotency: Idempotency,
    private val properties: DeliverableProperties,
    private val problems: GlobalExceptionHandler
) {
    @PostMapping("/api/sgspe/deliverable-templates", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    suspend fun upload(
        exchange: ServerWebExchange,
        @RequestHeader(name = IdempotencyKey.HEADER, required = false) key: String?
    ): ResponseEntity<ByteArray> {
        StrictQuery.requireOnly(exchange.request.queryParams, emptySet())
        val parts = exchange.multipartData.awaitSingle()
        StrictBody.requireOnly(parts, setOf("file", "templateKey", "name"))
        val file = parts.getFirst("file") as? FilePart ?: throw ValidationException("Falta el archivo", "file", "envíe la plantilla en la parte file")
        val templateKey =
            (parts.getFirst("templateKey") as? FormFieldPart)?.value()?.trim()
                ?: throw ValidationException("Falta la clave", "templateKey", "envíe templateKey")
        val name = (parts.getFirst("name") as? FormFieldPart)?.value()?.trim()?.ifEmpty { null }
        val bytes =
            try {
                val joined = DataBufferUtils.join(file.content(), properties.templates.maxBytes.toInt()).awaitSingle()
                ByteArray(joined.readableByteCount()).also { joined.read(it) }.also { DataBufferUtils.release(joined) }
            } catch (_: DataBufferLimitException) {
                throw UploadLimits.tooLarge(properties.templates.maxBytes)
            }
        val fileName =
            file
                .filename()
                .substringAfterLast('/')
                .substringAfterLast('\\')
                .take(MAX_NAME)
        val keyBody = mapOf("templateKey" to templateKey, "name" to name, "sha256" to CanonicalJson.sha256(bytes))
        return idempotency.respond(key, "POST", exchange.request.path.value(), keyBody) { templates.upload(fileName, bytes, templateKey, name) }
    }

    @GetMapping("/api/sgspe/deliverable-templates")
    suspend fun latest(exchange: ServerWebExchange): List<TemplateSummary> {
        StrictQuery.requireOnly(exchange.request.queryParams, emptySet())
        return templates.latest()
    }

    @ExceptionHandler(TemplateInvalid::class)
    fun invalid(e: TemplateInvalid): ProblemDetail =
        problems.handleWasichai(e).apply {
            setProperty("violations", e.report.violations)
            setProperty("warnings", e.report.warnings)
        }

    private companion object {
        const val MAX_NAME = 200
    }
}
