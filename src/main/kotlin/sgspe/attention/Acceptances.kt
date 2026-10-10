package sgspe.attention

import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
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
import sgspe.common.ConfidentialRelease
import sgspe.common.Criteria
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
import wasichai.core.common.ValidationException
import wasichai.core.data.ChangeReason
import wasichai.core.data.RecordChange
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordChangeListener
import wasichai.core.data.RecordResponse
import wasichai.core.platform.ChangeOrigin
import java.util.UUID

data class RequestAcceptance(
    val request: RecordResponse,
    val commitment: RecordResponse
)

// la aceptación de una solicitud (RF-GRV-004, api.md §4.6): aceptar es responderla. en una transacción escribe response y
// responded_at en la solicitud, crea el compromiso en draft con `attributes` (sigue WF-D, con su aprobación), guarda
// created_commitment y aplica accept, la transición de comando de WF-E3, con el motivo de la petición. el aporte social
// (creates igual a social_contribution) llega en F5: hoy es 400 INVALID_TARGET. todo como quien llama
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
        StrictBody.requireOnly(body, setOf(CREATES, "response", "respondedAt", "attributes"))
        if (Values.text(body[CREATES]) != COMMITMENT) {
            throw Finding(
                "INVALID_TARGET",
                "Una solicitud aceptada crea un compromiso (creates igual a commitment); el aporte social llega en F5",
                CREATES,
                status = HttpStatus.BAD_REQUEST
            ).toCodedException()
        }
        val attributes = StrictBody.objectOf(body, "attributes")
        StrictBody.requireAttributes(catalog, COMMITMENT, attributes, setOf("project"))
        if (reason.isNullOrBlank()) throw ValidationException("Aceptar una solicitud exige un motivo", "reason", "envíe la cabecera X-Change-Reason")
        val user = callers.require()
        permissions.require(user, "Aceptar una solicitud", Actions.UPDATE to Attention.REQUEST, Actions.CREATE to COMMITMENT)
        TransitionRoles.require(catalog, user, Attention.REQUEST, listOf(ACCEPT))
        val result =
            transactions.locked("${Attention.REQUEST}:$id") {
                val request = records.get(Attention.REQUEST, id)
                versions.check(Attention.REQUEST, id, expected, request.updatedAt, "La solicitud")
                if (request.state != UNDER_EVALUATION) {
                    throw Finding("STATE_MISMATCH", "Solo se acepta una solicitud en evaluación; esta está en ${request.state}", "record").toCodedException()
                }
                // la respuesta primero: AttentionWriteGuard exige response y responded_at para aplicar accept
                records.patch(Attention.REQUEST, id, mapOf("response" to body["response"], Attention.RESPONDED_AT to body["respondedAt"]), reason)
                val commitment = records.create(COMMITMENT, attributes + ("project" to request.attributes["project"]), reason)
                records.patch(Attention.REQUEST, id, mapOf(AttentionRules.CREATED_COMMITMENT to commitment.id), reason)
                records.transition(Attention.REQUEST, id, ACCEPT, reason)
                RequestAcceptance(records.get(Attention.REQUEST, id), records.get(COMMITMENT, UUID.fromString(commitment.id)))
            }
        return CommandAnswer(HttpStatus.CREATED, result, result.request.updatedAt, "/api/objects/$COMMITMENT/records/${result.commitment.id}")
    }

    companion object {
        const val CREATES = "creates"
        const val COMMITMENT = "commitment"
        const val ACCEPT = "accept"
        const val UNDER_EVALUATION = "under_evaluation"
    }
}

// RequestClosureListener (RN-GRV-001, ASM-068, workflows.md WF-E3): en la transacción de `close` de WF-D aplica close_accepted
// (transición de comando, con la marca) a la solicitud aceptada que originó el compromiso, con el motivo «RN-GRV-001:
// compromiso <código> cerrado»; si no puede, lanza y el cierre se revierte. es una acción del sistema, no de quien cierra: la
// solicitud puede ser confidencial y quien cierra no leerla (decisión 4 del plan 4.2a). wasichai no deja ser la plataforma
// dentro de una petición (RecordService.asPlatform lanza) ni aplicar una transición sin usuario, así que corre como quien
// cierra, pero dentro de ConfidentialRelease, con el que ConfidentialAttentionScope admite además las solicitudes de este
// compromiso, y solo ellas, mientras dura el bloque; y con el origen SOURCE en la auditoría (ChangeOrigin), que distingue el
// cierre automático de uno a mano
@Component
@Order(130)
class RequestClosureListener(
    private val records: Records
) : RecordChangeListener {
    override suspend fun recordChanged(change: RecordChange) {
        if (change.objectName != Acceptances.COMMITMENT || change.kind != RecordChangeKind.TRANSITIONED || change.transition != CLOSE) return
        val code = Values.text((change.after ?: change.before).orEmpty()["code"]) ?: change.recordId.toString()
        ConfidentialRelease.within(Attention.REQUEST, AttentionRules.CREATED_COMMITMENT, change.recordId) {
            ChangeOrigin.within(SOURCE) {
                val requests =
                    records.all(
                        Attention.REQUEST,
                        filters = mapOf(AttentionRules.CREATED_COMMITMENT to change.recordId.toString()),
                        criteria = listOf(Criteria.inState(listOf(ACCEPTED)))
                    )
                for (request in requests) {
                    records.transition(Attention.REQUEST, UUID.fromString(request.id), CLOSE_ACCEPTED, "RN-GRV-001: compromiso $code cerrado")
                }
            }
        }
    }

    companion object {
        const val CLOSE = "close"
        const val ACCEPTED = "accepted"
        const val CLOSE_ACCEPTED = "close_accepted"

        // el origen de sus escrituras en la auditoría (WAS-ADR-0050): el cierre automático, no una acción de quien cierra
        const val SOURCE = "listener:request-closure"
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
