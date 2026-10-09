package sgspe.evidence

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PatchMapping
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
import java.util.UUID

// un vínculo pedido: un registro de uno de los destinos de evidence_<objeto> (database-model.md §16)
data class EvidenceLink(
    val objectName: String,
    val recordId: UUID
)

// las evidencias (api.md §4.9). el archivo ya está en el almacenamiento: el portal lo sube antes con la ruta de wasichai-files
// (POST /api/objects/evidence/files/file, multipart, con CREATE sobre evidence), que valida tamaño y tipo, calcula el SHA-256
// y devuelve el id que aquí llega en attributes.file (decisión D1, WAS-ADR-0061). este comando crea el registro, que es
// apiOnly, y sus vínculos en una transacción, como quien llama: StoredFileGuard de wasichai exige que el archivo lo haya
// subido esa misma persona para evidence.file. los EXIF (fecha y lugar de captura) los lee el portal y llegan como
// attributes (captured_at, location en geometries)
@Service
class Evidences(
    private val records: Records,
    private val callers: Callers,
    private val permissions: Permissions,
    private val transactions: Transactions,
    private val versions: Versions,
    private val catalog: ModelCatalog
) {
    suspend fun create(
        body: Map<String, Any?>,
        reason: String?
    ): CommandAnswer {
        StrictBody.requireOnly(body, setOf("attributes", "geometries", "links"))
        val attributes = StrictBody.objectOf(body, "attributes")
        val geometries = StrictBody.objectOf(body, "geometries")
        StrictBody.requireAttributes(catalog, EvidenceRules.EVIDENCE, attributes)
        val links = links(body["links"])
        val user = callers.require()
        val grants = listOf(Actions.CREATE to EvidenceRules.EVIDENCE) + linkGrants(links)
        permissions.require(user, "Registrar una evidencia", *grants.toTypedArray())
        requireReasonFor(links.map { it.objectName }, reason)
        val evidence =
            transactions.run {
                val created = records.create(EvidenceRules.EVIDENCE, attributes, reason, geometries)
                val id = UUID.fromString(created.id)
                links.forEach { records.link(EvidenceRules.EVIDENCE, id, EvidenceRules.linkRelationship(it.objectName), it.recordId, reason) }
                created
            }
        return CommandAnswer(HttpStatus.CREATED, evidence, evidence.updatedAt, "/api/objects/evidence/records/${evidence.id}")
    }

    // los datos descriptivos; solo escribe lo que cambia, así un PATCH sin cambios deja el updatedAt como estaba
    suspend fun patch(
        id: UUID,
        body: Map<String, Any?>,
        reason: String?,
        ifMatch: String?
    ): RecordResponse {
        val expected = IfMatch.required(ifMatch)
        StrictBody.requireOnly(body, setOf("attributes"))
        val changes = StrictBody.objectOf(body, "attributes")
        val notEditable = changes.keys.filter { it !in EvidenceRules.DESCRIPTIVE }.sorted()
        if (notEditable.isNotEmpty()) {
            throw Finding(
                "FIELD_NOT_EDITABLE",
                "Solo cambian los datos descriptivos de una evidencia (${EvidenceRules.DESCRIPTIVE.sorted().joinToString(", ")}); el archivo nunca",
                "attributes.${notEditable.first()}",
                status = HttpStatus.BAD_REQUEST
            ).toCodedException()
        }
        val user = callers.require()
        permissions.require(user, "Cambiar una evidencia", Actions.UPDATE to EvidenceRules.EVIDENCE)
        return transactions.locked(lock(id)) {
            val current = records.get(EvidenceRules.EVIDENCE, id)
            versions.check(EvidenceRules.EVIDENCE, id, expected, current.updatedAt, "La evidencia")
            val diff = changes.filter { (field, value) -> !Values.same(current.attributes[field], value) }
            if (diff.isEmpty()) current else records.patch(EvidenceRules.EVIDENCE, id, diff, reason)
        }
    }

    suspend fun link(
        id: UUID,
        body: Map<String, Any?>,
        reason: String?
    ) {
        StrictBody.requireOnly(body, setOf("object", "recordId"))
        val target = linkOf(body, "")
        val user = callers.require()
        permissions.require(user, "Vincular una evidencia", *linkGrants(listOf(target)).toTypedArray())
        requireReasonFor(listOf(target.objectName), reason)
        transactions.run { records.link(EvidenceRules.EVIDENCE, id, EvidenceRules.linkRelationship(target.objectName), target.recordId, reason) }
    }

    suspend fun unlink(
        id: UUID,
        objectName: String,
        recordId: UUID,
        reason: String?
    ) {
        val target = linkOf(mapOf("object" to objectName, "recordId" to recordId.toString()), "")
        val user = callers.require()
        permissions.require(user, "Desvincular una evidencia", *linkGrants(listOf(target)).toTypedArray())
        requireReasonFor(listOf(target.objectName), reason)
        transactions.run { records.unlink(EvidenceRules.EVIDENCE, id, EvidenceRules.linkRelationship(target.objectName), target.recordId, reason) }
    }

    // ---- validación ----

    private fun links(raw: Any?): List<EvidenceLink> {
        if (raw == null) return emptyList()
        val list = raw as? List<*> ?: throw ValidationException("Vínculos inválidos", "links", "debe ser una lista de { object, recordId }")
        if (list.size > MAX_LINKS) throw ValidationException("Demasiados vínculos", "links", "a lo sumo $MAX_LINKS por evidencia")
        return list
            .mapIndexed { index, item ->
                @Suppress("UNCHECKED_CAST")
                val entry = item as? Map<String, Any?> ?: throw ValidationException("Vínculo inválido", "links[$index]", "debe ser { object, recordId }")
                StrictBody.requireOnly(entry, setOf("object", "recordId"), "links[$index].")
                linkOf(entry, "links[$index].")
            }.distinct()
    }

    // { object, recordId }: el objeto debe ser un destino de evidence_<objeto> del modelo; el id, un UUID
    private fun linkOf(
        entry: Map<String, Any?>,
        prefix: String
    ): EvidenceLink {
        val objectName = Values.text(entry["object"])
        if (objectName == null || catalog.manyToMany(EvidenceRules.linkRelationship(objectName))?.source != EvidenceRules.EVIDENCE) {
            throw Finding(
                "INVALID_LINK",
                "Una evidencia no se vincula con ${objectName ?: "ese objeto"}",
                "${prefix}object",
                status = HttpStatus.BAD_REQUEST
            ).toCodedException()
        }
        val recordId = Records.uuid(entry["recordId"]) ?: throw ValidationException("Vínculo inválido", "${prefix}recordId", "debe ser un UUID")
        return EvidenceLink(objectName, recordId)
    }

    // un vínculo escribe en los dos registros (WAS-ADR-0025): UPDATE sobre evidence y sobre cada objeto destino
    private fun linkGrants(links: List<EvidenceLink>): List<Pair<String, String>> =
        if (links.isEmpty()) emptyList() else listOf(Actions.UPDATE to EvidenceRules.EVIDENCE) + links.map { Actions.UPDATE to it.objectName }.distinct()

    // un vínculo con un objeto requiresReason exige motivo en los dos registros (WAS-ADR-0041): se pide antes de escribir
    private fun requireReasonFor(
        objects: List<String>,
        reason: String?
    ) {
        if (!reason.isNullOrBlank()) return
        val needs = objects.firstOrNull { catalog.objectOrNull(it)?.requiresReason == true } ?: return
        throw Finding(
            "REASON_REQUIRED",
            "Vincular una evidencia con $needs exige un motivo (X-Change-Reason)",
            "reason",
            status = HttpStatus.BAD_REQUEST
        ).toCodedException()
    }

    companion object {
        // el tope de vínculos de un alta: una evidencia prueba unos pocos registros, nunca cientos
        const val MAX_LINKS = 50

        fun lock(id: UUID): String = "${EvidenceRules.EVIDENCE}:$id"
    }
}

// api.md §4.9. el alta honra Idempotency-Key (decisión D3)
@RestController
@RequestMapping("/api/sgspe/evidence")
class EvidenceController(
    private val evidences: Evidences,
    private val idempotency: Idempotency
) {
    @PostMapping
    suspend fun create(
        @RequestBody body: Map<String, Any?>,
        @RequestHeader(name = ChangeReason.HEADER, required = false) reason: String?,
        @RequestHeader(name = IdempotencyKey.HEADER, required = false) key: String?,
        exchange: ServerWebExchange
    ): ResponseEntity<ByteArray> {
        StrictQuery.requireOnly(exchange.request.queryParams, emptySet())
        return idempotency.respond(key, "POST", exchange.request.path.value(), body) { evidences.create(body, ChangeReason.fromHeader(reason)) }
    }

    @PatchMapping("/{id}")
    suspend fun patch(
        @PathVariable id: UUID,
        @RequestBody body: Map<String, Any?>,
        @RequestHeader(name = ChangeReason.HEADER, required = false) reason: String?,
        @RequestHeader(name = IfMatch.HEADER, required = false) ifMatch: String?,
        exchange: ServerWebExchange
    ): ResponseEntity<RecordResponse> {
        StrictQuery.requireOnly(exchange.request.queryParams, emptySet())
        val record = evidences.patch(id, body, ChangeReason.fromHeader(reason), ifMatch)
        return ResponseEntity.ok().eTag("\"${record.updatedAt}\"").body(record)
    }

    @PostMapping("/{id}/links")
    suspend fun link(
        @PathVariable id: UUID,
        @RequestBody body: Map<String, Any?>,
        @RequestHeader(name = ChangeReason.HEADER, required = false) reason: String?,
        exchange: ServerWebExchange
    ): ResponseEntity<Void> {
        StrictQuery.requireOnly(exchange.request.queryParams, emptySet())
        evidences.link(id, body, ChangeReason.fromHeader(reason))
        return ResponseEntity.noContent().build()
    }

    @DeleteMapping("/{id}/links/{object}/{recordId}")
    suspend fun unlink(
        @PathVariable id: UUID,
        @PathVariable("object") objectName: String,
        @PathVariable recordId: UUID,
        @RequestHeader(name = ChangeReason.HEADER, required = false) reason: String?,
        exchange: ServerWebExchange
    ): ResponseEntity<Void> {
        StrictQuery.requireOnly(exchange.request.queryParams, emptySet())
        evidences.unlink(id, objectName, recordId, ChangeReason.fromHeader(reason))
        return ResponseEntity.noContent().build()
    }
}
