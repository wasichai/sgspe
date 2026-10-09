package sgspe.commitment

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ServerWebExchange
import sgspe.common.Amounts
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
import wasichai.core.data.RecordResponse
import java.math.BigDecimal
import java.util.UUID

data class DeliveryResult(
    val delivery: RecordResponse,
    val commitment: RecordResponse
)

// la entrega de un compromiso (RF-COM-008, api.md §4.5): solo captura datos. en una transacción crea la delivery (apiOnly), la
// vincula con sus evidencias (evidence_delivery: el acta y las fotos, ya subidas con POST /api/sgspe/evidence) y recalcula
// executed_amount, la suma de delivered_amount de las entregas del compromiso (ASM-063), con la marca y solo si cambia. no
// cambia el estado: el cumplimiento es la transición humana register_fulfillment, con D9. el «Acta de entrega» emitida (AUT-03)
// espera a UPS-23. la corrección (PATCH) la rechaza CommitmentWriteGuard con DELIVERY_FROZEN si el compromiso ya está cumplido.
// todo se lee y se escribe como quien llama (RecordService aplica sus permisos de campo: recipients y conformity_by,
// restringidos, solo los ve quien es su lector), nunca como la plataforma
@Service
class Deliveries(
    private val records: Records,
    private val callers: Callers,
    private val permissions: Permissions,
    private val transactions: Transactions,
    private val versions: Versions,
    private val catalog: ModelCatalog
) {
    suspend fun create(
        commitmentId: UUID,
        body: Map<String, Any?>,
        reason: String?
    ): CommandAnswer {
        StrictBody.requireOnly(body, setOf("attributes", "geometries", "evidence"))
        val attributes = amount(StrictBody.objectOf(body, "attributes"))
        val geometries = StrictBody.objectOf(body, "geometries")
        val evidence = evidence(body["evidence"])
        requireReason(reason)
        val user = callers.require()
        val grants =
            listOf(Actions.CREATE to Commitments.DELIVERY, Actions.UPDATE to Commitments.COMMITMENT) +
                if (evidence.isEmpty()) emptyList() else listOf(Actions.UPDATE to EVIDENCE, Actions.UPDATE to Commitments.DELIVERY)
        permissions.require(user, "Registrar una entrega", *grants.toTypedArray())
        val result =
            transactions.locked(Reschedules.lock(commitmentId)) {
                val commitment = records.get(Commitments.COMMITMENT, commitmentId)
                if (commitment.state !in Commitments.EXECUTING) {
                    throw Finding(
                        "STATE_MISMATCH",
                        "Las entregas se registran con el compromiso en ejecución; este está en ${commitment.state}",
                        "state"
                    ).toCodedException()
                }
                val delivery = records.create(Commitments.DELIVERY, attributes + ("commitment" to commitmentId.toString()), reason, geometries)
                val deliveryId = UUID.fromString(delivery.id)
                evidence.forEach { records.link(EVIDENCE, it, Commitments.EVIDENCE_DELIVERY, deliveryId, reason) }
                DeliveryResult(records.get(Commitments.DELIVERY, deliveryId), refreshExecuted(commitmentId, reason))
            }
        return CommandAnswer(HttpStatus.CREATED, result, result.delivery.updatedAt, "/api/objects/${Commitments.DELIVERY}/records/${result.delivery.id}")
    }

    // corrige los datos de una entrega y recalcula el ejecutado de su compromiso; solo escribe lo que cambia. el compromiso de
    // la entrega no cambia nunca (FIXED): DELIVERY_FROZEN mira el de antes, y moverla la sacaría de uno cumplido
    suspend fun patch(
        id: UUID,
        body: Map<String, Any?>,
        reason: String?,
        ifMatch: String?
    ): DeliveryResult {
        val expected = IfMatch.required(ifMatch)
        StrictBody.requireOnly(body, setOf("attributes", "geometries"))
        val changes = amount(StrictBody.objectOf(body, "attributes"))
        val geometries = StrictBody.objectOf(body, "geometries")
        requireReason(reason)
        val user = callers.require()
        permissions.require(user, "Corregir una entrega", Actions.UPDATE to Commitments.DELIVERY, Actions.UPDATE to Commitments.COMMITMENT)
        val read = records.get(Commitments.DELIVERY, id)
        val commitmentId =
            Records.uuid(read.attributes["commitment"])
                ?: throw Finding("STATE_MISMATCH", "La entrega no es de un compromiso", "commitment").toCodedException()
        return transactions.locked(Reschedules.lock(commitmentId)) {
            val delivery = records.get(Commitments.DELIVERY, id)
            versions.check(Commitments.DELIVERY, id, expected, delivery.updatedAt, "La entrega")
            val diff = changes.filter { (field, value) -> !Values.same(delivery.attributes[field], value) }
            val written = if (diff.isEmpty() && geometries.isEmpty()) delivery else records.patch(Commitments.DELIVERY, id, diff, reason, geometries)
            DeliveryResult(written, refreshExecuted(commitmentId, reason))
        }
    }

    // los atributos de la entrega (campos del objeto, ni derivados ni los que fija el comando) con delivered_amount acotado
    // por Amounts antes de sumarlo (Review Focus 5) y normalizado
    private fun amount(attributes: Map<String, Any?>): Map<String, Any?> {
        StrictBody.requireAttributes(catalog, Commitments.DELIVERY, attributes, FIXED)
        if (AMOUNT !in attributes) return attributes
        return attributes + (AMOUNT to Amounts.parse(attributes[AMOUNT], "attributes.$AMOUNT"))
    }

    // executed_amount = suma de delivered_amount de las entregas del compromiso, escrito solo si cambia (P11). la lectura es
    // acotada: más de MAX_DELIVERIES entregas es un 400 TOO_MANY_RECORDS, nunca una suma de una lista recortada
    private suspend fun refreshExecuted(
        commitmentId: UUID,
        reason: String?
    ): RecordResponse {
        val total =
            records
                .all(Commitments.DELIVERY, filters = mapOf("commitment" to commitmentId.toString()), max = MAX_DELIVERIES, field = "commitment")
                .mapNotNull { Values.decimal(it.attributes[AMOUNT]) }
                .fold(BigDecimal.ZERO, BigDecimal::add)
        val commitment = records.get(Commitments.COMMITMENT, commitmentId)
        return if (Values.same(commitment.attributes[EXECUTED], total)) {
            commitment
        } else {
            records.patch(Commitments.COMMITMENT, commitmentId, mapOf(EXECUTED to total), reason)
        }
    }

    private fun evidence(raw: Any?): List<UUID> {
        if (raw == null) return emptyList()
        val list = raw as? List<*> ?: throw ValidationException("Evidencias inválidas", "evidence", "debe ser una lista de ids de evidence")
        if (list.size > MAX_EVIDENCE) throw ValidationException("Demasiadas evidencias", "evidence", "a lo sumo $MAX_EVIDENCE por entrega")
        return list
            .mapIndexed { index, item ->
                Records.uuid(item) ?: throw ValidationException("Evidencia inválida", "evidence[$index]", "debe ser el id de una evidence")
            }.distinct()
    }

    // escribir executed_amount en el compromiso (requiresReason) exige motivo: se pide antes de escribir nada
    private fun requireReason(reason: String?) {
        if (reason.isNullOrBlank()) throw ValidationException("Registrar una entrega exige un motivo", "reason", "envíe la cabecera X-Change-Reason")
    }

    companion object {
        const val EVIDENCE = "evidence"
        const val AMOUNT = "delivered_amount"
        const val EXECUTED = "executed_amount"

        // la relación con su compromiso la fija el comando; la del aporte social llega en F5
        val FIXED = setOf("commitment", "social_contribution")
        const val MAX_EVIDENCE = 50

        // las entregas de un compromiso que se suman como mucho
        const val MAX_DELIVERIES = 1000
    }
}

// api.md §4.5. el alta honra Idempotency-Key (decisión D3)
@RestController
class DeliveryController(
    private val deliveries: Deliveries,
    private val idempotency: Idempotency
) {
    @PostMapping("/api/sgspe/commitments/{id}/deliveries")
    suspend fun create(
        @PathVariable id: UUID,
        @RequestBody body: Map<String, Any?>,
        @RequestHeader(name = ChangeReason.HEADER, required = false) reason: String?,
        @RequestHeader(name = IdempotencyKey.HEADER, required = false) key: String?,
        exchange: ServerWebExchange
    ): ResponseEntity<ByteArray> {
        StrictQuery.requireOnly(exchange.request.queryParams, emptySet())
        return idempotency.respond(key, "POST", exchange.request.path.value(), body) { deliveries.create(id, body, ChangeReason.fromHeader(reason)) }
    }

    @PatchMapping("/api/sgspe/deliveries/{id}")
    suspend fun patch(
        @PathVariable id: UUID,
        @RequestBody body: Map<String, Any?>,
        @RequestHeader(name = ChangeReason.HEADER, required = false) reason: String?,
        @RequestHeader(name = IfMatch.HEADER, required = false) ifMatch: String?,
        exchange: ServerWebExchange
    ): ResponseEntity<DeliveryResult> {
        StrictQuery.requireOnly(exchange.request.queryParams, emptySet())
        val result = deliveries.patch(id, body, ChangeReason.fromHeader(reason), ifMatch)
        return ResponseEntity.ok().eTag("\"${result.delivery.updatedAt}\"").body(result)
    }
}
