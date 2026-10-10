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
import wasichai.core.common.ValidationException
import wasichai.core.data.ChangeReason
import wasichai.core.data.RecordResponse
import java.util.UUID

data class Reclassification(
    val original: RecordResponse,
    val created: RecordResponse
)

// la reclasificación de una QCS (RF-GRV-007, ASM-072, api.md §4.6): en una transacción crea la QCS de `targetType` con los
// campos comunes del original (la fecha de recepción es la del primer registro de la cadena, que el original ya trae) más
// reclassified_from_<tipo>, y cierra el original con la transición de comando close_reclassified_* de su estado, con el motivo
// de la petición. `attributes` completa o cambia los campos no derivados, salvo received_at, el proyecto y los vínculos de
// reclasificación. el código no se copia: cada tipo numera sus registros. todo como quien llama (las QCS confidenciales las
// copia quien las lee, con su marca y su receptor)
@Service
class Reclassifications(
    private val records: Records,
    private val callers: Callers,
    private val permissions: Permissions,
    private val transactions: Transactions,
    private val versions: Versions,
    private val catalog: ModelCatalog
) {
    suspend fun reclassify(
        source: String,
        id: UUID,
        body: Map<String, Any?>,
        reason: String?,
        ifMatch: String?
    ): CommandAnswer {
        val expected = IfMatch.required(ifMatch)
        StrictBody.requireOnly(body, setOf(TARGET, "attributes"))
        val target = Values.text(body[TARGET])
        if (target == null || target == source || target !in Attention.OBJECTS) {
            throw Finding(
                "INVALID_TARGET",
                "targetType es grievance, inquiry o stakeholder_request, distinto del tipo de la QCS que se reclasifica",
                TARGET,
                status = HttpStatus.BAD_REQUEST
            ).toCodedException()
        }
        val attributes = StrictBody.objectOf(body, "attributes")
        StrictBody.requireAttributes(catalog, target, attributes, fixed(target))
        if (reason.isNullOrBlank()) throw ValidationException("Reclasificar exige un motivo", "reason", "envíe la cabecera X-Change-Reason")
        val user = callers.require()
        permissions.require(user, "Reclasificar una QCS", Actions.UPDATE to source, Actions.CREATE to target)
        val result =
            transactions.locked("$source:$id") {
                val original = records.get(source, id)
                versions.check(source, id, expected, original.updatedAt, "La QCS")
                val close =
                    closing(catalog, source, original.state)
                        ?: throw Finding(
                            "STATE_MISMATCH",
                            "Una QCS en ${original.state} no se reclasifica: no tiene transición de reclasificación desde ese estado",
                            "record"
                        ).toCodedException()
                TransitionRoles.require(catalog, user, source, listOf(close))
                val copied = COPIED.filter { it in original.attributes }.associateWith { original.attributes[it] }
                val geometries = Records.geometriesOf(original).filterValues { it != null }
                val created =
                    records.create(target, copied + attributes + (Attention.reclassifiedField(target, source) to id.toString()), reason, geometries)
                records.transition(source, id, close, reason)
                Reclassification(records.get(source, id), records.get(target, UUID.fromString(created.id)))
            }
        return CommandAnswer(HttpStatus.CREATED, result, result.created.updatedAt, "/api/objects/$target/records/${result.created.id}")
    }

    // lo que una reclasificación fija y `attributes` no cambia
    private fun fixed(target: String): Set<String> = setOf(Attention.RECEIVED_AT, "project") + Attention.RECLASSIFIED_FROM.getValue(target).keys

    companion object {
        const val TARGET = "targetType"
        const val CLOSE_PREFIX = "close_reclassified"

        // la transición de reclasificación que sale del estado del original, de model/workflows (close_reclassified_*)
        fun closing(
            catalog: ModelCatalog,
            source: String,
            state: String?
        ): String? = catalog.transitions(source).firstOrNull { it.name.startsWith(CLOSE_PREFIX) && it.from == state }?.name

        // los campos comunes que pasan del original al registro nuevo (api.md §4.6): canal, recepción, quien recibió,
        // anonimato, confidencialidad, categoría, descripción, prioridad, responsable, grupo de interés, persona, territorio,
        // tema, interacción y pregunta de origen y el proyecto; la ubicación va por geometries
        val COPIED: List<String> =
            listOf(
                "project",
                "channel",
                Attention.RECEIVED_AT,
                Attention.RECEIVER,
                "received_by_user_email",
                "anonymous",
                Attention.CONFIDENTIAL,
                "category",
                "description",
                "priority",
                Attention.OWNER,
                "owner_user_email",
                "stakeholder",
                "person",
                "locality",
                "community",
                "topic",
                "origin_engagement",
                "origin_question"
            )

        // la ruta de cada QCS en /api/sgspe
        val PATHS: Map<String, String> =
            mapOf("grievances" to Attention.GRIEVANCE, "inquiries" to Attention.INQUIRY, "stakeholder-requests" to Attention.REQUEST)
    }
}

// api.md §4.6: POST /api/sgspe/{grievances|inquiries|stakeholder-requests}/{id}/reclassify, con Idempotency-Key
@RestController
@RequestMapping("/api/sgspe")
class ReclassificationController(
    private val reclassifications: Reclassifications,
    private val idempotency: Idempotency
) {
    @PostMapping("/{kind:grievances|inquiries|stakeholder-requests}/{id}/reclassify")
    suspend fun reclassify(
        @PathVariable kind: String,
        @PathVariable id: UUID,
        @RequestBody body: Map<String, Any?>,
        @RequestHeader(name = ChangeReason.HEADER, required = false) reason: String?,
        @RequestHeader(name = IfMatch.HEADER, required = false) ifMatch: String?,
        @RequestHeader(name = IdempotencyKey.HEADER, required = false) key: String?,
        exchange: ServerWebExchange
    ): ResponseEntity<ByteArray> {
        StrictQuery.requireOnly(exchange.request.queryParams, emptySet())
        val source = Reclassifications.PATHS.getValue(kind)
        return idempotency.respond(key, "POST", exchange.request.path.value(), body) {
            reclassifications.reclassify(source, id, body, ChangeReason.fromHeader(reason), ifMatch)
        }
    }
}
