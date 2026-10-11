package sgspe.investment

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ServerWebExchange
import sgspe.commitment.CommitmentDeliveries
import sgspe.commitment.ContributionDeliveryResult
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
import sgspe.common.Values
import sgspe.common.Versions
import sgspe.common.toCodedException
import wasichai.core.common.Actions
import wasichai.core.common.ValidationException
import wasichai.core.data.ChangeReason
import java.util.UUID

// la entrega de un aporte (especificación de F5 §3.5, api.md §4.6): solo captura datos. en una transacción y con el candado
// del aporte comprueba que está en procured y que no tiene entrega, crea la delivery (apiOnly) con su social_contribution y la
// vincula con sus evidencias (evidence_delivery: el acta de recepción firmada, la foto y el comprobante, ya subidos con POST
// /api/sgspe/evidence). no cambia el estado: register_delivery es la transición humana que sigue (P8). sin delivered_amount:
// el ejecutado de un aporte es su total_amount (ASM-063). el «Acta de recepción» emitida (AUT-04) espera a UPS-23. todo como
// quien llama, nunca como la plataforma
@Service
class ContributionDeliveries(
    private val records: Records,
    private val callers: Callers,
    private val permissions: Permissions,
    private val transactions: Transactions,
    private val versions: Versions,
    private val catalog: ModelCatalog
) {
    suspend fun create(
        contributionId: UUID,
        body: Map<String, Any?>,
        reason: String?,
        ifMatch: String?
    ): CommandAnswer {
        val expected = IfMatch.required(ifMatch)
        StrictBody.requireOnly(body, setOf("attributes", "geometries", "evidence"))
        val attributes = StrictBody.objectOf(body, "attributes")
        StrictBody.requireAttributes(catalog, Contributions.DELIVERY, attributes, CommitmentDeliveries.FIXED)
        if (Values.text(attributes[ContributionDelivery.AMOUNT]) != null) {
            throw ContributionDelivery.amountNotAllowed("attributes.${ContributionDelivery.AMOUNT}").toCodedException()
        }
        val geometries = StrictBody.objectOf(body, "geometries")
        val evidence = CommitmentDeliveries.evidenceIds(body["evidence"])
        if (reason.isNullOrBlank()) throw ValidationException("Registrar una entrega exige un motivo", "reason", "envíe la cabecera X-Change-Reason")
        val user = callers.require()
        val grants =
            listOf(Actions.CREATE to Contributions.DELIVERY, Actions.UPDATE to Contributions.CONTRIBUTION) +
                if (evidence.isEmpty()) emptyList() else listOf(Actions.UPDATE to EVIDENCE, Actions.UPDATE to Contributions.DELIVERY)
        permissions.require(user, "Registrar la entrega de un aporte", *grants.toTypedArray())
        val result =
            transactions.locked(Contributions.lock(contributionId)) {
                val contribution = records.get(Contributions.CONTRIBUTION, contributionId)
                versions.check(Contributions.CONTRIBUTION, contributionId, expected, contribution.updatedAt, "El aporte")
                if (contribution.state != Contributions.PROCURED) {
                    throw Finding(
                        "STATE_MISMATCH",
                        "La entrega se registra con el aporte adquirido (procured); este está en ${contribution.state}",
                        "record"
                    ).toCodedException()
                }
                if (records.count(Contributions.DELIVERY, filters = mapOf(Contributions.CONTRIBUTION to contributionId.toString())) > 0) {
                    throw ContributionDelivery.exists().toCodedException()
                }
                val delivery =
                    records.create(Contributions.DELIVERY, attributes + (Contributions.CONTRIBUTION to contributionId.toString()), reason, geometries)
                val deliveryId = UUID.fromString(delivery.id)
                evidence.forEach { records.link(EVIDENCE, it, ContributionDelivery.EVIDENCE_DELIVERY, deliveryId, reason) }
                ContributionDeliveryResult(records.get(Contributions.DELIVERY, deliveryId), records.get(Contributions.CONTRIBUTION, contributionId))
            }
        return CommandAnswer(HttpStatus.CREATED, result, result.delivery.updatedAt, "/api/objects/${Contributions.DELIVERY}/records/${result.delivery.id}")
    }

    companion object {
        const val EVIDENCE = "evidence"
    }
}

// api.md §4.6: el alta honra Idempotency-Key, como la de las entregas de compromisos
@RestController
class ContributionDeliveryController(
    private val deliveries: ContributionDeliveries,
    private val idempotency: Idempotency
) {
    @PostMapping("/api/sgspe/social-contributions/{id}/deliveries")
    suspend fun create(
        @PathVariable id: UUID,
        @RequestBody body: Map<String, Any?>,
        @RequestHeader(name = ChangeReason.HEADER, required = false) reason: String?,
        @RequestHeader(name = IfMatch.HEADER, required = false) ifMatch: String?,
        @RequestHeader(name = IdempotencyKey.HEADER, required = false) key: String?,
        exchange: ServerWebExchange
    ): ResponseEntity<ByteArray> {
        StrictQuery.requireOnly(exchange.request.queryParams, emptySet())
        return idempotency.respond(key, "POST", exchange.request.path.value(), body) {
            deliveries.create(id, body, ChangeReason.fromHeader(reason), ifMatch)
        }
    }
}
