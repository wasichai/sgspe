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
import sgspe.common.ModelCatalog
import sgspe.common.Permissions
import sgspe.common.Records
import sgspe.common.StrictBody
import sgspe.common.StrictQuery
import sgspe.common.Transactions
import sgspe.common.Values
import sgspe.common.readable
import sgspe.common.toCodedException
import wasichai.core.common.Actions
import wasichai.core.common.ValidationException
import wasichai.core.data.ChangeReason
import wasichai.core.data.RecordResponse
import java.time.Clock
import java.util.UUID

data class Referral(
    val question: RecordResponse,
    val created: RecordResponse
)

// la derivación de una pregunta de reunión pendiente a una QCS (RF-ENG-006, api.md §4.4): en una transacción crea la QCS de `target` con
// `attributes` (sujetos a AttentionWriteGuard) más origin_question, origin_engagement (la interacción de la pregunta), el
// proyecto de la interacción, el canal REUNION y la recepción igual al held_at de la interacción, y escribe referred_at en la
// pregunta con la marca: EngagementWriteGuard la cuenta como atendida (RN-ENG-005) sin leer la QCS, que puede ser confidencial.
// todo como quien llama: escribir referred_at exige UPDATE sobre engagement_question, que tienen COMMUNITY_RELATIONS_OFFICER
// (CRU) y GRIEVANCE_MANAGER (`u`, solo los derivados; decisión 3 del plan 4.2a)
@Service
class Referrals(
    private val records: Records,
    private val callers: Callers,
    private val permissions: Permissions,
    private val transactions: Transactions,
    private val catalog: ModelCatalog,
    private val clock: Clock
) {
    suspend fun refer(
        id: UUID,
        body: Map<String, Any?>,
        reason: String?
    ): CommandAnswer {
        StrictBody.requireOnly(body, setOf(TARGET, "attributes"))
        val target = Values.text(body[TARGET])
        if (target == null || target !in Attention.OBJECTS) {
            throw Finding("INVALID_TARGET", "target es grievance, inquiry o stakeholder_request", TARGET, status = HttpStatus.BAD_REQUEST).toCodedException()
        }
        val attributes = StrictBody.objectOf(body, "attributes")
        StrictBody.requireAttributes(catalog, target, attributes, FIXED + Attention.RECLASSIFIED_FROM.getValue(target).keys)
        if (reason.isNullOrBlank()) throw ValidationException("Derivar una pregunta exige un motivo", "reason", "envíe la cabecera X-Change-Reason")
        val user = callers.require()
        permissions.require(user, "Derivar una pregunta", Actions.UPDATE to QUESTION, Actions.CREATE to target)
        val result =
            transactions.locked("$QUESTION:$id") {
                val question = records.get(QUESTION, id)
                if (Values.text(question.attributes[REFERRED_AT]) != null) {
                    throw Finding("ALREADY_REFERRED", "La pregunta ya se derivó a una QCS", "record").toCodedException()
                }
                if (answered(question.attributes)) {
                    throw Finding("STATE_MISMATCH", "Solo se deriva una pregunta pendiente: esta ya se respondió", "record").toCodedException()
                }
                val engagementId =
                    Records.uuid(question.attributes[ENGAGEMENT])
                        ?: throw Finding("STATE_MISMATCH", "La pregunta no tiene interacción", ENGAGEMENT).toCodedException()
                val engagement = records.readable(ENGAGEMENT, engagementId)
                if (engagement.state !in HELD) {
                    throw Finding(
                        "STATE_MISMATCH",
                        "Solo se deriva una pregunta de una interacción realizada y sin cerrar; esta está en ${engagement.state}",
                        ENGAGEMENT
                    ).toCodedException()
                }
                val fixed =
                    mapOf(
                        "origin_question" to id.toString(),
                        "origin_engagement" to engagement.id,
                        "project" to engagement.attributes["project"],
                        "channel" to CHANNEL,
                        Attention.RECEIVED_AT to engagement.attributes["held_at"]
                    )
                val created = records.create(target, attributes + fixed, reason)
                records.patch(QUESTION, id, mapOf(REFERRED_AT to clock.instant().toString()), reason)
                Referral(records.get(QUESTION, id), records.get(target, UUID.fromString(created.id)))
            }
        return CommandAnswer(HttpStatus.CREATED, result, result.created.updatedAt, "/api/objects/$target/records/${result.created.id}")
    }

    companion object {
        const val TARGET = "target"
        const val QUESTION = "engagement_question"
        const val ENGAGEMENT = "engagement"
        const val REFERRED_AT = "referred_at"
        const val CHANNEL = "REUNION"

        // lo que fija la derivación: la pregunta y la interacción de origen, el proyecto, el canal y la recepción
        val FIXED: Set<String> = setOf("origin_question", "origin_engagement", "project", "channel", Attention.RECEIVED_AT)

        // respondida en el lugar o después (answered_at): ya no está pendiente y no se deriva (api.md §4.4). la misma lectura que
        // EngagementWriteGuard.answered sin referred_at, que la derivación juzga aparte (ALREADY_REFERRED)
        fun answered(question: Map<String, Any?>): Boolean = Values.bool(question["answered_on_site"]) == true || Values.text(question["answered_at"]) != null

        // de held a minutes_approved: realizada y sin cerrar (WF-C)
        val HELD: Set<String> = setOf("held", "minutes_drafted", "minutes_in_review", "minutes_approved")
    }
}

// api.md §4.4: POST /api/sgspe/engagement-questions/{id}/referral, con Idempotency-Key
@RestController
@RequestMapping("/api/sgspe/engagement-questions")
class ReferralController(
    private val referrals: Referrals,
    private val idempotency: Idempotency
) {
    @PostMapping("/{id}/referral")
    suspend fun refer(
        @PathVariable id: UUID,
        @RequestBody body: Map<String, Any?>,
        @RequestHeader(name = ChangeReason.HEADER, required = false) reason: String?,
        @RequestHeader(name = IdempotencyKey.HEADER, required = false) key: String?,
        exchange: ServerWebExchange
    ): ResponseEntity<ByteArray> {
        StrictQuery.requireOnly(exchange.request.queryParams, emptySet())
        return idempotency.respond(key, "POST", exchange.request.path.value(), body) { referrals.refer(id, body, ChangeReason.fromHeader(reason)) }
    }
}
