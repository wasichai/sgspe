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
import sgspe.common.ModelCatalog
import sgspe.common.NotReadable
import sgspe.common.Permissions
import sgspe.common.Records
import sgspe.common.StrictBody
import sgspe.common.StrictQuery
import sgspe.common.Transactions
import sgspe.common.TransitionRoles
import sgspe.common.Values
import sgspe.common.Versions
import sgspe.common.readable
import sgspe.common.toCodedException
import wasichai.core.common.Actions
import wasichai.core.common.ValidationException
import wasichai.core.data.ChangeReason
import wasichai.core.data.RecordResponse
import wasichai.core.identity.AuthenticatedUser
import java.time.Instant
import java.util.UUID

data class CandidateAcceptance(
    val commitment: RecordResponse,
    val candidate: RecordResponse
)

data class CandidateDuplicate(
    val candidate: RecordResponse
)

// los candidatos a compromiso registrados por personas (RF-COM-010, WF-K, api.md §4.5). commitment_candidate es apiOnly: el
// alta, la aceptación y la marca de duplicado son comandos; rechazar es una transición genérica con motivo. aceptar es una
// transición de comando (CommandTransitionGuard) que crea el compromiso en draft con los valores que la persona revisó y lo
// guarda en created_commitment, en una transacción. solo una persona decide (RQ-AI-001, RQ-AI-003): una cuenta de servicio
// recibe 403 PERSON_REQUIRED aunque tenga el rol. los candidatos de la IA llegan en F7
@Service
class Candidates(
    private val records: Records,
    private val callers: Callers,
    private val permissions: Permissions,
    private val transactions: Transactions,
    private val versions: Versions,
    private val catalog: ModelCatalog
) {
    suspend fun create(body: Map<String, Any?>): CommandAnswer {
        StrictBody.requireOnly(body, setOf("attributes"))
        val attributes = StrictBody.objectOf(body, "attributes")
        StrictBody.requireAttributes(catalog, Commitments.CANDIDATE, attributes, SYSTEM)
        val user = callers.require()
        permissions.require(user, "Registrar un candidato a compromiso", Actions.CREATE to Commitments.CANDIDATE)
        val candidate = transactions.run { records.create(Commitments.CANDIDATE, attributes + (AI_GENERATED to false), null) }
        return CommandAnswer(HttpStatus.CREATED, candidate, candidate.updatedAt, "/api/objects/${Commitments.CANDIDATE}/records/${candidate.id}")
    }

    suspend fun accept(
        id: UUID,
        body: Map<String, Any?>,
        reason: String?,
        ifMatch: String?
    ): CommandAnswer {
        val expected = IfMatch.required(ifMatch)
        StrictBody.requireOnly(body, setOf("attributes"))
        val attributes = StrictBody.objectOf(body, "attributes")
        StrictBody.requireAttributes(catalog, Commitments.COMMITMENT, attributes, setOf("project"))
        if (reason.isNullOrBlank()) throw ValidationException("Aceptar un candidato exige un motivo", "reason", "envíe la cabecera X-Change-Reason")
        val user = requirePerson(callers.require())
        permissions.require(user, "Aceptar un candidato", Actions.UPDATE to Commitments.CANDIDATE, Actions.CREATE to Commitments.COMMITMENT)
        TransitionRoles.require(catalog, user, Commitments.CANDIDATE, listOf(ACCEPT))
        val result =
            transactions.locked(lock(id)) {
                val candidate = proposed(id, expected)
                // la interacción de origen la hereda del candidato, salvo que quien revisa la cambie o la quite (null explícito)
                val origin = if (ORIGIN in attributes) attributes[ORIGIN] else candidate.attributes["engagement"]
                val commitment =
                    records.create(
                        Commitments.COMMITMENT,
                        attributes + ("project" to candidate.attributes["project"]) + (ORIGIN to origin),
                        reason
                    )
                records.patch(Commitments.CANDIDATE, id, mapOf(CREATED_COMMITMENT to commitment.id), reason)
                records.transition(Commitments.CANDIDATE, id, ACCEPT, reason)
                CandidateAcceptance(records.get(Commitments.COMMITMENT, UUID.fromString(commitment.id)), records.get(Commitments.CANDIDATE, id))
            }
        return CommandAnswer(
            HttpStatus.CREATED,
            result,
            result.commitment.updatedAt,
            "/api/objects/${Commitments.COMMITMENT}/records/${result.commitment.id}"
        )
    }

    suspend fun duplicate(
        id: UUID,
        body: Map<String, Any?>,
        reason: String?,
        ifMatch: String?
    ): CandidateDuplicate {
        val expected = IfMatch.required(ifMatch)
        StrictBody.requireOnly(body, setOf("duplicateOf"))
        val duplicateOf =
            Records.uuid(body["duplicateOf"]) ?: throw ValidationException("Falta el compromiso", "duplicateOf", "debe ser el id de un commitment")
        if (reason.isNullOrBlank()) throw ValidationException("Marcar un duplicado exige un motivo", "reason", "envíe la cabecera X-Change-Reason")
        val user = requirePerson(callers.require())
        permissions.require(user, "Marcar un candidato como duplicado", Actions.UPDATE to Commitments.CANDIDATE)
        TransitionRoles.require(catalog, user, Commitments.CANDIDATE, listOf(CandidateRules.MARK_DUPLICATE))
        return transactions.locked(lock(id)) {
            val candidate = proposed(id, expected)
            // falla cerrada (P12): inexistente, sin READ o fuera del alcance es lo mismo, sin revelar si existe
            val existing =
                try {
                    records.readable(Commitments.COMMITMENT, duplicateOf)
                } catch (_: NotReadable) {
                    throw Finding("DUPLICATE_NOT_FOUND", "El compromiso no existe o no puede leerlo", "duplicateOf", status = HttpStatus.BAD_REQUEST)
                        .toCodedException()
                }
            if (Values.text(existing.attributes["project"]) != Values.text(candidate.attributes["project"])) {
                throw Finding(
                    "DUPLICATE_OTHER_PROJECT",
                    "El compromiso que lo cubre debe ser del mismo proyecto que el candidato",
                    "duplicateOf",
                    status = HttpStatus.BAD_REQUEST
                ).toCodedException()
            }
            records.patch(Commitments.CANDIDATE, id, mapOf(CandidateRules.DUPLICATE_OF to duplicateOf.toString()), reason)
            CandidateDuplicate(records.transition(Commitments.CANDIDATE, id, CandidateRules.MARK_DUPLICATE, reason))
        }
    }

    private suspend fun proposed(
        id: UUID,
        expected: Instant
    ): RecordResponse {
        val candidate = records.get(Commitments.CANDIDATE, id)
        versions.check(Commitments.CANDIDATE, id, expected, candidate.updatedAt, "El candidato")
        if (candidate.state != PROPOSED) {
            throw Finding("STATE_MISMATCH", "El candidato ya se decidió: está en ${candidate.state}", "state").toCodedException()
        }
        return candidate
    }

    companion object {
        const val ACCEPT = "accept"
        const val PROPOSED = "proposed"
        const val AI_GENERATED = "ai_generated"
        const val CREATED_COMMITMENT = "created_commitment"
        const val ORIGIN = "origin_engagement"

        // lo que fija el sistema y nunca una persona en el alta humana: el origen de IA, la confianza y las decisiones
        val SYSTEM = setOf(AI_GENERATED, "confidence", "ai_interaction", CREATED_COMMITMENT, CandidateRules.DUPLICATE_OF)

        fun lock(id: UUID): String = "${Commitments.CANDIDATE}:$id"

        // solo una persona decide sobre un candidato (RQ-AI-003): una cuenta de servicio nunca, tenga el rol que tenga
        internal fun requirePerson(user: AuthenticatedUser): AuthenticatedUser {
            if (user.serviceAccount != null) {
                throw Finding(
                    "PERSON_REQUIRED",
                    "Solo una persona acepta o descarta un candidato a compromiso; una cuenta de servicio no",
                    status = HttpStatus.FORBIDDEN
                ).toCodedException()
            }
            return user
        }
    }
}

// api.md §4.5. el alta y la aceptación honran Idempotency-Key (decisión D3)
@RestController
@RequestMapping("/api/sgspe/commitment-candidates")
class CandidateController(
    private val candidates: Candidates,
    private val idempotency: Idempotency
) {
    @PostMapping
    suspend fun create(
        @RequestBody body: Map<String, Any?>,
        @RequestHeader(name = IdempotencyKey.HEADER, required = false) key: String?,
        exchange: ServerWebExchange
    ): ResponseEntity<ByteArray> {
        StrictQuery.requireOnly(exchange.request.queryParams, emptySet())
        return idempotency.respond(key, "POST", exchange.request.path.value(), body) { candidates.create(body) }
    }

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
            candidates.accept(id, body, ChangeReason.fromHeader(reason), ifMatch)
        }
    }

    @PostMapping("/{id}/duplicate")
    suspend fun duplicate(
        @PathVariable id: UUID,
        @RequestBody body: Map<String, Any?>,
        @RequestHeader(name = ChangeReason.HEADER, required = false) reason: String?,
        @RequestHeader(name = IfMatch.HEADER, required = false) ifMatch: String?,
        exchange: ServerWebExchange
    ): ResponseEntity<CandidateDuplicate> {
        StrictQuery.requireOnly(exchange.request.queryParams, emptySet())
        val result = candidates.duplicate(id, body, ChangeReason.fromHeader(reason), ifMatch)
        return ResponseEntity.ok().eTag("\"${result.candidate.updatedAt}\"").body(result)
    }
}
