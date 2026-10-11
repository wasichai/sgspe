package sgspe.attention

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
import sgspe.common.Attention
import sgspe.common.Callers
import sgspe.common.CommandAnswer
import sgspe.common.Finding
import sgspe.common.Idempotency
import sgspe.common.IdempotencyKey
import sgspe.common.IfMatch
import sgspe.common.ModelCatalog
import sgspe.common.Permissions
import sgspe.common.Records
import sgspe.common.StrictBody
import sgspe.common.StrictQuery
import sgspe.common.Transactions
import sgspe.common.TransitionRoles
import sgspe.common.Values
import sgspe.common.Versions
import sgspe.common.toCodedException
import wasichai.core.common.Actions
import wasichai.core.common.FieldViolation
import wasichai.core.common.ValidationException
import wasichai.core.data.ChangeReason
import wasichai.core.data.RecordResponse
import java.util.UUID

data class RequestAcceptance(
    val request: RecordResponse,
    val commitment: RecordResponse
)

// desde F5 (§3.7 de la especificación de F5): la aceptación que crea un aporte social
data class ContributionAcceptance(
    val request: RecordResponse,
    val contribution: RecordResponse
)

// la aceptación de una solicitud (RF-GRV-004, api.md §4.6): aceptar es responderla. en una transacción escribe response y
// responded_at en la solicitud, crea el compromiso en draft o, desde F5, el aporte en requested con `attributes` (siguen WF-D
// o WF-P, con su aprobación), guarda created_commitment o created_contribution y aplica accept, la transición de comando de
// WF-E3, con el motivo de la petición. todo como quien llama
@Service
class Acceptances(
    private val records: Records,
    private val callers: Callers,
    private val permissions: Permissions,
    private val transactions: Transactions,
    private val versions: Versions,
    private val catalog: ModelCatalog
) {
    suspend fun accept(
        id: UUID,
        body: Map<String, Any?>,
        reason: String?,
        ifMatch: String?
    ): CommandAnswer {
        val expected = IfMatch.required(ifMatch)
        StrictBody.requireOnly(body, setOf(CREATES, "response", RESPONDED_AT, "attributes"))
        val creates = Values.text(body[CREATES])
        if (creates != COMMITMENT && creates != CONTRIBUTION) {
            throw Finding(
                "INVALID_TARGET",
                "Una solicitud aceptada crea un compromiso o un aporte social (creates igual a commitment o social_contribution)",
                CREATES,
                status = HttpStatus.BAD_REQUEST
            ).toCodedException()
        }
        val attributes = StrictBody.objectOf(body, "attributes")
        StrictBody.requireAttributes(catalog, creates, attributes, setOf("project"))
        if (reason.isNullOrBlank()) throw ValidationException("Aceptar una solicitud exige un motivo", "reason", "envíe la cabecera X-Change-Reason")
        val user = callers.require()
        permissions.require(user, "Aceptar una solicitud", Actions.UPDATE to Attention.REQUEST, Actions.CREATE to creates)
        TransitionRoles.require(catalog, user, Attention.REQUEST, listOf(ACCEPT))
        val field = if (creates == COMMITMENT) AttentionRules.CREATED_COMMITMENT else AttentionRules.CREATED_CONTRIBUTION
        val (answer, accepted, created) =
            transactions.locked("${Attention.REQUEST}:$id") {
                val request = records.get(Attention.REQUEST, id)
                versions.check(Attention.REQUEST, id, expected, request.updatedAt, "La solicitud")
                if (request.state != UNDER_EVALUATION) {
                    throw Finding("STATE_MISMATCH", "Solo se acepta una solicitud en evaluación; esta está en ${request.state}", "record").toCodedException()
                }
                // la respuesta primero: AttentionWriteGuard exige response y responded_at para aplicar accept. un respondedAt mal
                // formado lo rechaza la validación de wasichai sobre responded_at: el 400 nombra la propiedad del cuerpo
                try {
                    records.patch(Attention.REQUEST, id, mapOf("response" to body["response"], Attention.RESPONDED_AT to body[RESPONDED_AT]), reason)
                } catch (e: ValidationException) {
                    throw bodyFields(e)
                }
                // el compromiso nace en draft y el aporte en requested; quien acepta es su created_by (P5 de WF-P, D4 de WF-D)
                val record = records.create(creates, attributes + ("project" to request.attributes["project"]), reason)
                records.patch(Attention.REQUEST, id, mapOf(field to record.id), reason)
                records.transition(Attention.REQUEST, id, ACCEPT, reason)
                val acceptedRequest = records.get(Attention.REQUEST, id)
                val createdRecord = records.get(creates, UUID.fromString(record.id))
                val answer: Any =
                    if (creates == COMMITMENT) RequestAcceptance(acceptedRequest, createdRecord) else ContributionAcceptance(acceptedRequest, createdRecord)
                Triple(answer, acceptedRequest, createdRecord)
            }
        return CommandAnswer(HttpStatus.CREATED, answer, accepted.updatedAt, "/api/objects/$creates/records/${created.id}")
    }

    companion object {
        const val CREATES = "creates"
        const val RESPONDED_AT = "respondedAt"

        // la validación de la respuesta con los nombres del cuerpo: responded_at del modelo es respondedAt (response se llama
        // igual en los dos); el mensaje y las demás violaciones no cambian
        fun bodyFields(e: ValidationException): ValidationException =
            ValidationException(
                e.message,
                e.violations.map { if (it.field == Attention.RESPONDED_AT) FieldViolation(RESPONDED_AT, it.message) else it }
            )

        const val COMMITMENT = "commitment"
        const val CONTRIBUTION = "social_contribution"
        const val ACCEPT = "accept"
        const val UNDER_EVALUATION = "under_evaluation"
    }
}

// api.md §4.6: POST /api/sgspe/stakeholder-requests/{id}/accept, con Idempotency-Key
@RestController
@RequestMapping("/api/sgspe/stakeholder-requests")
class AcceptanceController(
    private val acceptances: Acceptances,
    private val idempotency: Idempotency
) {
    @PostMapping("/{id}/accept")
    suspend fun accept(
        @PathVariable id: UUID,
        @RequestBody body: Map<String, Any?>,
        @RequestHeader(name = ChangeReason.HEADER, required = false) reason: String?,
        @RequestHeader(name = IfMatch.HEADER, required = false) ifMatch: String?,
        @RequestHeader(name = IdempotencyKey.HEADER, required = false) key: String?,
        exchange: ServerWebExchange
    ): ResponseEntity<ByteArray> {
        StrictQuery.requireOnly(exchange.request.queryParams, emptySet())
        return idempotency.respond(key, "POST", exchange.request.path.value(), body) {
            acceptances.accept(id, body, ChangeReason.fromHeader(reason), ifMatch)
        }
    }
}
