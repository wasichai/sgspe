package sgspe.commitment

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ServerWebExchange
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
import sgspe.common.Values
import sgspe.common.Versions
import sgspe.common.toCodedException
import wasichai.core.common.Actions
import wasichai.core.common.ValidationException
import wasichai.core.data.ChangeReason
import wasichai.core.data.RecordResponse
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.UUID

data class RescheduleResult(
    val reschedule: RecordResponse,
    val commitment: RecordResponse
)

// la reprogramación (RF-COM-003, api.md §4.5): en una transacción crea commitment_reschedule, inmutable, con la fecha anterior
// y la nueva, y cambia due_date, con la marca (la única vía para cambiarla desde approved, CommitmentRecordRules). exige
// If-Match, motivo, CREATE sobre commitment_reschedule y UPDATE sobre commitment. solo en ejecución (approved, in_progress,
// blocked): antes de aprobar, due_date se edita directamente; después de cumplir, ya no hay plazo
@Service
class Reschedules(
    private val records: Records,
    private val callers: Callers,
    private val permissions: Permissions,
    private val transactions: Transactions,
    private val versions: Versions
) {
    suspend fun reschedule(
        id: UUID,
        body: Map<String, Any?>,
        reason: String?,
        ifMatch: String?
    ): CommandAnswer {
        val expected = IfMatch.required(ifMatch)
        StrictBody.requireOnly(body, setOf("attributes"))
        val attributes = StrictBody.objectOf(body, "attributes")
        StrictBody.requireOnly(attributes, FIELDS, "attributes.")
        val newDate = date(attributes["new_due_date"])
        val why = Values.text(attributes["reschedule_reason"]) ?: throw required("reschedule_reason")
        val agreed = attributes["agreed_with_stakeholder"] as? Boolean ?: throw required("agreed_with_stakeholder")
        if (reason.isNullOrBlank()) throw ValidationException("Reprogramar exige un motivo", "reason", "envíe la cabecera X-Change-Reason")
        val user = callers.require()
        permissions.require(user, "Reprogramar un compromiso", Actions.CREATE to Commitments.RESCHEDULE, Actions.UPDATE to Commitments.COMMITMENT)
        val result =
            transactions.locked(lock(id)) {
                val commitment = records.get(Commitments.COMMITMENT, id)
                versions.check(Commitments.COMMITMENT, id, expected, commitment.updatedAt, "El compromiso")
                if (commitment.state !in Commitments.EXECUTING) {
                    throw Finding(
                        "STATE_MISMATCH",
                        "Solo se reprograma un compromiso en ejecución; este está en ${commitment.state}",
                        "state"
                    ).toCodedException()
                }
                // la reprogramación lleva siempre las dos fechas (los tableros KPI-COM leen una sin ambas como corrupta): sin la
                // anterior no se escribe nada
                val previous =
                    Values.text(commitment.attributes["due_date"])
                        ?: throw Finding(
                            "FIELD_REQUIRED",
                            "El compromiso no tiene fecha de vencimiento que reprogramar",
                            "due_date",
                            status = HttpStatus.BAD_REQUEST
                        ).toCodedException()
                if (previous == newDate.toString()) {
                    throw Finding(
                        "SAME_DUE_DATE",
                        "La fecha nueva es la que ya tiene el compromiso",
                        "attributes.new_due_date",
                        status = HttpStatus.BAD_REQUEST
                    ).toCodedException()
                }
                val reschedule =
                    records.create(
                        Commitments.RESCHEDULE,
                        mapOf(
                            "commitment" to id.toString(),
                            "previous_due_date" to previous,
                            "new_due_date" to newDate.toString(),
                            "reschedule_reason" to why,
                            "agreed_with_stakeholder" to agreed
                        ),
                        reason
                    )
                records.patch(Commitments.COMMITMENT, id, mapOf("due_date" to newDate.toString()), reason)
                // se relee: los listeners de la escritura (deadline_status, DeadlineStatusListener) ya escribieron en esta
                // transacción, y la respuesta y su ETag deben ser los del compromiso tal como quedó
                RescheduleResult(reschedule, records.get(Commitments.COMMITMENT, id))
            }
        return CommandAnswer(
            HttpStatus.CREATED,
            result,
            result.commitment.updatedAt,
            "/api/objects/${Commitments.RESCHEDULE}/records/${result.reschedule.id}"
        )
    }

    private fun date(value: Any?): LocalDate {
        val text = Values.text(value) ?: throw required("new_due_date")
        return try {
            LocalDate.parse(text)
        } catch (_: DateTimeParseException) {
            throw ValidationException("Fecha inválida", "attributes.new_due_date", "debe ser una fecha AAAA-MM-DD")
        }
    }

    private fun required(field: String) = Finding("FIELD_REQUIRED", "Falta $field", "attributes.$field", status = HttpStatus.BAD_REQUEST).toCodedException()

    companion object {
        val FIELDS = setOf("new_due_date", "reschedule_reason", "agreed_with_stakeholder")

        // el candado de un compromiso: lo toman la reprogramación, las entregas y JOB-01
        fun lock(id: UUID): String = "${Commitments.COMMITMENT}:$id"
    }
}

// api.md §4.5. honra Idempotency-Key (decisión D3)
@RestController
@RequestMapping("/api/sgspe/commitments/{id}/reschedules")
class RescheduleController(
    private val reschedules: Reschedules,
    private val idempotency: Idempotency
) {
    @PostMapping
    suspend fun reschedule(
        @PathVariable id: UUID,
        @RequestBody body: Map<String, Any?>,
        @RequestHeader(name = ChangeReason.HEADER, required = false) reason: String?,
        @RequestHeader(name = IfMatch.HEADER, required = false) ifMatch: String?,
        @RequestHeader(name = IdempotencyKey.HEADER, required = false) key: String?,
        exchange: ServerWebExchange
    ): ResponseEntity<ByteArray> {
        StrictQuery.requireOnly(exchange.request.queryParams, emptySet())
        return idempotency.respond(key, "POST", exchange.request.path.value(), body) {
            reschedules.reschedule(id, body, ChangeReason.fromHeader(reason), ifMatch)
        }
    }
}
