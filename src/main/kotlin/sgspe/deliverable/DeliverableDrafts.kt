package sgspe.deliverable

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ServerWebExchange
import sgspe.common.Callers
import sgspe.common.CommandAnswer
import sgspe.common.Finding
import sgspe.common.Idempotency
import sgspe.common.IdempotencyKey
import sgspe.common.Permissions
import sgspe.common.Records
import sgspe.common.RuleException
import sgspe.common.StrictQuery
import sgspe.common.Transactions
import sgspe.common.toCodedException
import wasichai.core.common.Actions
import wasichai.core.common.FieldViolation
import wasichai.core.common.ValidationException
import wasichai.core.data.ChangeReason
import wasichai.core.data.RecordResponse
import java.time.Clock
import java.util.UUID

// el estado del último borrador (api.md §4.12): de cada candidata solo su SHA-256 (nunca se sirve)
data class DraftStatus(
    val status: String?,
    val requestedAt: String?,
    val generatedAt: String?,
    val revision: String?,
    val modelSha256: String?,
    val pending: Boolean,
    val levels: List<DraftLevel>,
    val warnings: List<DraftWarning>
)

data class DraftLevel(
    val level: String,
    val candidateSha256: String?
)

data class DraftWarning(
    val code: String,
    val section: String?
)

// pedir un borrador y leer su estado (especificación de F8 §3.5, deliverables.md §8.2), como la persona: el pedido exige UPDATE
// y GENERATE sobre deliverable, procede solo en draft u observed (409 STATE_MISMATCH), con el tipo disponible (422
// TYPE_NOT_AVAILABLE), en T1 con el AIDS aprobado (422 DELIVERABLE_PARAMETERS_MISSING sobre area_of_influence), sus
// parámetros (422 DELIVERABLE_PARAMETERS_MISSING, un error por campo) y, si tiene template_key, una plantilla de esa clave (422 TEMPLATE_UNAVAILABLE), y escribe solo generation_requested_at con la
// marca de Records y el motivo; JOB-05 lo toma después. todo dentro del candado de transacción del entregable, que no es el
// de la generación (Deliverables.generationLock): pedir no espera a que termine un borrador en curso
@Service
class DeliverableDrafts(
    private val records: Records,
    private val callers: Callers,
    private val permissions: Permissions,
    private val transactions: Transactions,
    private val types: DeliverableTypes,
    private val clock: Clock
) {
    suspend fun request(
        id: UUID,
        reason: String?
    ): CommandAnswer {
        if (reason.isNullOrBlank()) throw ValidationException("Pedir un borrador exige un motivo", "reason", "envíe la cabecera X-Change-Reason")
        val user = callers.require()
        permissions.require(
            user,
            "Pedir un borrador",
            Actions.UPDATE to Deliverables.DELIVERABLE,
            Deliverables.GENERATE to Deliverables.DELIVERABLE
        )
        val updated =
            transactions.locked("${Deliverables.DELIVERABLE}:$id") {
                val deliverable = records.get(Deliverables.DELIVERABLE, id)
                if (deliverable.state !in Deliverables.REQUESTABLE) {
                    throw Finding(
                        "STATE_MISMATCH",
                        "Un borrador se pide en draft u observed; este entregable está en ${deliverable.state}",
                        "record"
                    ).toCodedException()
                }
                val type =
                    types.of(deliverable.attributes[Deliverables.TYPE]?.toString())
                        ?: throw Finding(
                            TYPE_NOT_AVAILABLE,
                            "El tipo de entregable todavía no se genera en esta versión",
                            Deliverables.TYPE,
                            status = HttpStatus.UNPROCESSABLE_CONTENT
                        ).toCodedException()
                val project = records.get(Deliverables.PROJECT, UUID.fromString(deliverable.attributes[Deliverables.PROJECT].toString()))
                val missing = type.missingParameters(deliverable.attributes, project.attributes)
                if (missing.isNotEmpty()) {
                    throw RuleException(
                        HttpStatus.UNPROCESSABLE_CONTENT,
                        "Faltan parámetros del tipo para pedir un borrador",
                        missing.map { FieldViolation(it, PARAMETERS_MISSING) }
                    )
                }
                if (type.requiresApprovedArea) approvedArea(deliverable)
                // la plantilla de la clave tiene que existir ya: si no, el borrador fallaría con TEMPLATE_UNAVAILABLE
                deliverable.attributes[Deliverables.TEMPLATE_KEY]?.toString()?.let { key ->
                    if (records.count(Deliverables.TEMPLATE, filters = mapOf("template_key" to key)) == 0L) {
                        throw Finding(
                            Deliverables.TEMPLATE_UNAVAILABLE,
                            "No hay una plantilla con la clave $key",
                            Deliverables.TEMPLATE_KEY,
                            status = HttpStatus.UNPROCESSABLE_CONTENT
                        ).toCodedException()
                    }
                }
                records.patch(Deliverables.DELIVERABLE, id, mapOf(Deliverables.REQUESTED_AT to clock.instant().toString()), reason)
            }
        return CommandAnswer(HttpStatus.ACCEPTED, mapOf("deliverable" to updated), updated.updatedAt, "/api/sgspe/deliverables/$id/draft")
    }

    // el AIDS del diagnóstico, leído como quien pide, en approved (decisión 15 del plan 8.2c)
    private suspend fun approvedArea(deliverable: RecordResponse) {
        val area = Records.uuid(deliverable.attributes[AREA])?.let { records.find(AREA, it) }
        if (area == null || area.state != APPROVED_AREA || area.attributes["area_type"]?.toString() != AIDS) {
            throw RuleException(
                HttpStatus.UNPROCESSABLE_CONTENT,
                "El diagnóstico social exige un área de influencia directa social aprobada",
                listOf(FieldViolation(AREA, PARAMETERS_MISSING))
            )
        }
    }

    suspend fun status(id: UUID): DraftStatus = status(records.get(Deliverables.DELIVERABLE, id))

    companion object {
        const val PARAMETERS_MISSING = "DELIVERABLE_PARAMETERS_MISSING"
        const val TYPE_NOT_AVAILABLE = "TYPE_NOT_AVAILABLE"
        private const val AREA = "area_of_influence"
        private const val APPROVED_AREA = "approved"
        private const val AIDS = "AIDS"

        fun status(deliverable: RecordResponse): DraftStatus {
            val a = deliverable.attributes
            val status = a[Deliverables.STATUS]?.toString()

            fun sha(field: String): String? = (a[field] as? Map<*, *>)?.get("sha256")?.toString()
            val requested = Deliverables.instant(a[Deliverables.REQUESTED_AT])
            val generated = Deliverables.instant(a[Deliverables.GENERATED_AT])
            val pending =
                status == Deliverables.QUEUED || status == Deliverables.RUNNING ||
                    (requested != null && (generated == null || requested.isAfter(generated)))
            return DraftStatus(
                status,
                requested?.toString(),
                generated?.toString(),
                a[Deliverables.DRAFT_REVISION]?.toString(),
                a[Deliverables.MODEL_SHA256]?.toString(),
                pending,
                listOf(
                    DraftLevel(Deliverables.INTERNAL, sha(Deliverables.CANDIDATE_INTERNAL_PDF)),
                    DraftLevel(Deliverables.EXTERNAL, sha(Deliverables.CANDIDATE_EXTERNAL_PDF))
                ),
                DraftGeneration.parse(a[Deliverables.WARNINGS]).map { DraftWarning(it.code, it.section) }
            )
        }
    }
}

// api.md §4.12: POST …/drafts (CR, Idempotency-Key) → 202 con Location del estado; GET …/draft → el estado (READ)
@RestController
class DeliverableDraftsController(
    private val drafts: DeliverableDrafts,
    private val idempotency: Idempotency
) {
    @PostMapping("/api/sgspe/deliverables/{id}/drafts")
    suspend fun request(
        @PathVariable id: UUID,
        @RequestHeader(name = ChangeReason.HEADER, required = false) reason: String?,
        @RequestHeader(name = IdempotencyKey.HEADER, required = false) key: String?,
        exchange: ServerWebExchange
    ): ResponseEntity<ByteArray> {
        StrictQuery.requireOnly(exchange.request.queryParams, emptySet())
        return idempotency.respond(key, "POST", exchange.request.path.value(), emptyMap<String, Any?>()) {
            drafts.request(id, ChangeReason.fromHeader(reason))
        }
    }

    @GetMapping("/api/sgspe/deliverables/{id}/draft")
    suspend fun status(
        @PathVariable id: UUID,
        exchange: ServerWebExchange
    ): DraftStatus {
        StrictQuery.requireOnly(exchange.request.queryParams, emptySet())
        return drafts.status(id)
    }
}
