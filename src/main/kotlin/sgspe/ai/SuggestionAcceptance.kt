package sgspe.ai

import com.fasterxml.jackson.annotation.JsonInclude
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
import sgspe.common.PseudonymMarkers
import sgspe.common.Records
import sgspe.common.StrictBody
import sgspe.common.StrictQuery
import sgspe.common.Transactions
import sgspe.common.TransitionRoles
import sgspe.common.Values
import sgspe.common.Versions
import sgspe.common.readable
import sgspe.common.toCodedException
import sgspe.deliverable.DeliverableSections
import wasichai.core.common.Actions
import wasichai.core.common.ValidationException
import wasichai.core.data.ChangeReason
import wasichai.core.data.RecordResponse
import wasichai.core.identity.AuthenticatedUser
import wasichai.core.metadata.MetadataService
import java.time.Clock
import java.time.temporal.ChronoUnit
import java.util.UUID

// la respuesta de la aceptación (api.md §4.14): la sugerencia en accepted y el registro que el comando creó o cambió (null si solo
// se registra la decisión); un actor con su grupo de interés lo trae aparte
data class AcceptedSuggestion(
    val suggestion: RecordResponse,
    val result: RecordResponse?,
    @get:JsonInclude(JsonInclude.Include.NON_NULL) val stakeholder: RecordResponse? = null
)

// POST /api/sgspe/ai-suggestions/{id}/accept (ai-architecture.md §4.2, api.md §4.14, domain-model.md §7.2): ejecuta el único campo o
// comando que la lista cerrada permite para la fila de la sugerencia, con los valores que la persona revisó en `attributes` (nunca
// los guardados: ningún marcador llega a un registro de dominio) y su motivo, y lleva la sugerencia a accepted, todo en una
// transacción con el candado de la sugerencia. las escrituras corren como la persona con la marca SgspeWrite, así que las guardas del
// destino se aplican como en cualquier otra escritura; su motivo lleva el origen «IA aceptada» hasta UPS-03 (RQ-AUD-012). no
// depende del proveedor ni de la activación: aceptar funciona con la IA apagada (§12.1). en este orden: If-Match (428), el cuerpo y
// el motivo (400), una persona, UPDATE sobre ai_suggestion y el rol de accept (403), la sugerencia (404, 412, 409 STATE_MISMATCH),
// su fila (409 SUGGESTION_NOT_ACCEPTABLE), los valores revisados (400 y los 403 del comando)
@Service
class SuggestionAcceptance(
    private val records: Records,
    private val callers: Callers,
    private val permissions: Permissions,
    private val transactions: Transactions,
    private val versions: Versions,
    private val catalog: ModelCatalog,
    private val metadata: MetadataService,
    private val sections: DeliverableSections,
    private val clock: Clock
) {
    suspend fun accept(
        id: UUID,
        body: Map<String, Any?>,
        reason: String?,
        ifMatch: String?
    ): CommandAnswer {
        val expected = IfMatch.required(ifMatch)
        StrictBody.requireOnly(body, setOf(ATTRIBUTES))
        val attributes = StrictBody.objectOf(body, ATTRIBUTES)
        if (reason.isNullOrBlank()) throw ValidationException("Aceptar una sugerencia exige un motivo", "reason", "envíe la cabecera X-Change-Reason")
        val user = callers.require()
        // por su marca o por su correo de respaldo, como la guarda de WF-K
        SuggestionDecisionGuard.decide(user).firstOrNull()?.let { throw it.toCodedException() }
        permissions.require(user, "Aceptar una sugerencia de IA", Actions.UPDATE to SuggestionWriter.SUGGESTION)
        TransitionRoles.require(catalog, user, SuggestionWriter.SUGGESTION, listOf(ACCEPT))
        val answer =
            transactions.locked("${SuggestionWriter.SUGGESTION}:$id") {
                val suggestion = records.get(SuggestionWriter.SUGGESTION, id)
                versions.check(SuggestionWriter.SUGGESTION, id, expected, suggestion.updatedAt, "La sugerencia")
                if (suggestion.state != SuggestionWriter.PROPOSED) {
                    throw Finding("STATE_MISMATCH", "La sugerencia ya se decidió: está en ${suggestion.state}", "state").toCodedException()
                }
                val stored = suggestion.attributes
                val target = Values.text(stored["target_object"])
                val row =
                    AllowList.rowOf(Values.text(stored["suggestion_kind"]), target, Values.text(stored["suggested_value"]))
                        ?: throw Finding(
                            "SUGGESTION_NOT_ACCEPTABLE",
                            "Esta sugerencia no tiene una aceptación en la lista permitida: solo se rechaza o se marca duplicada",
                            "suggestion_kind"
                        ).toCodedException()
                StrictBody.requireOnly(attributes, AllowList.BODY_KEYS.getValue(row), "$ATTRIBUTES.")
                AllowList.misplaced(row, attributes)?.let { throw fieldNotAllowed(it) }
                // una adopción en una sección convierte [PERSONA:<id>] y rechaza lo demás (DeliverableSections.markers)
                if (row !in AllowList.ADOPTIONS) PseudonymMarkers.requireNone(attributes)
                val context =
                    Context(
                        user,
                        target!!,
                        Records.uuid(stored["target_record_id"]),
                        Records.uuid(stored["project"]),
                        attributes,
                        Proposals.parse(Values.text(stored["suggested_value"])).orEmpty(),
                        "IA aceptada (ai_suggestion $id): $reason",
                        id
                    )
                val (result, stakeholder) = execute(row, context)
                val accepted = records.transition(SuggestionWriter.SUGGESTION, id, ACCEPT, reason)
                AcceptedSuggestion(accepted, result, stakeholder)
            }
        return CommandAnswer(HttpStatus.OK, answer, answer.suggestion.updatedAt)
    }

    private data class Context(
        val user: AuthenticatedUser,
        val target: String,
        val targetId: UUID?,
        val project: UUID?,
        val attributes: Map<String, Any?>,
        val proposed: Map<String, Any?>,
        val reason: String,
        // la sugerencia que se acepta: el vínculo ai_suggestion de la sección adoptada
        val suggestion: UUID
    ) {
        // el campo de una clasificación, un lugar o una fecha es el que propuso la IA: quien acepta corrige el valor, no el campo
        // (§4.2, «único campo»; la aceptación no audita un cambio que la IA no propuso, RQ-AUD-012). omitirlo usa el guardado
        fun proposedField(): String {
            val stored = Values.text(proposed["field"]) ?: throw fieldNotAllowed("field")
            val sent = text("field")
            if (sent != null && sent != stored) throw fieldNotAllowed("field")
            return stored
        }

        // un valor de texto es un escalar: una lista o un objeto no se escribe como su toString
        fun text(key: String): String? =
            when (val value = attributes[key]) {
                is Map<*, *>, is Collection<*> -> throw invalidValue(key)
                else -> Values.text(value)
            }

        fun requireText(key: String): String = text(key) ?: throw fieldRequired(key)
    }

    private suspend fun execute(
        row: AcceptRow,
        c: Context
    ): Pair<RecordResponse?, RecordResponse?> =
        when (row) {
            AcceptRow.CLASSIFICATION -> classification(c) to null
            AcceptRow.TOPIC -> topic(c) to null
            AcceptRow.PLACE -> place(c) to null
            AcceptRow.DATE -> date(c) to null
            AcceptRow.CLAUSE -> clause(c) to null
            AcceptRow.ORGANIZATION -> actor(c, ORGANIZATION) { organization(c) }
            AcceptRow.PERSON -> actor(c, PERSON) { person(c) }
            AcceptRow.MINUTES -> update(c, mapOf("minutes" to c.requireText("minutes"))) to null
            AcceptRow.GRIEVANCE_DUPLICATE -> grievanceDuplicate(c) to null
            AcceptRow.ORGANIZATION_DUPLICATE -> organizationDuplicate(c) to null
            AcceptRow.DECISION_DUPLICATE -> {
                targetId(c)
                other(c)
                null to null
            }
            // la sección es el destino: su proyecto, si la sugerencia lo trae
            AcceptRow.SECTION -> adopt(c, targetId(c), listOf(c.project), requireKnown = false) to null
            // el destino de un resumen ejecutivo es el proyecto: la sección es de él y del de la sugerencia, si lo trae
            AcceptRow.SUMMARY_SECTION ->
                adopt(c, Records.uuid(c.requireText("section")) ?: throw invalidValue("section"), listOf(c.project, c.targetId), requireKnown = true) to
                    null
        }

    // CLASIFICACION: category, priority o topic de una QCS; topic de una pregunta; tone o topic de una mención
    private suspend fun classification(c: Context): RecordResponse {
        val field = c.proposedField()
        if (field !in AllowList.CLASSIFICATION_FIELDS.getValue(c.target)) throw fieldNotAllowed("field")
        val value = c.requireText("value")
        if (field == TOPIC) {
            readableId(TOPIC, value, "value")
        } else if (value !in catalog.enumOf(c.target, field)) {
            throw invalidValue("value")
        }
        return update(c, mapOf(field to value))
    }

    // EXTRACCION de un tema: el vínculo engagement_topic con un tema que se lee
    private suspend fun topic(c: Context): RecordResponse {
        val topic = readableId(TOPIC, c.requireText(TOPIC), TOPIC)
        val engagement = targetId(c)
        permissions.require(c.user, "Vincular un tema a la interacción", Actions.UPDATE to c.target)
        records.link(c.target, engagement, "engagement_topic", topic, c.reason)
        return records.get(c.target, engagement)
    }

    // EXTRACCION de un lugar: locality o community de la interacción, con un registro que se lee
    private suspend fun place(c: Context): RecordResponse {
        val field = c.proposedField()
        if (field !in AllowList.PLACE_FIELDS) throw fieldNotAllowed("field")
        val place = readableId(field, c.requireText("value"), "value")
        return update(c, mapOf(field to place.toString()))
    }

    // EXTRACCION de una fecha del acuerdo, con su motivo (requiresReason)
    private suspend fun date(c: Context): RecordResponse {
        val field = c.proposedField()
        if (field !in AllowList.DATE_FIELDS) throw fieldNotAllowed("field")
        val date = Proposals.date(c.text("value")) ?: throw invalidValue("value")
        return update(c, mapOf(field to date.toString()))
    }

    // EXTRACCION de un acuerdo: una agreement_clause nueva del acuerdo destino
    private suspend fun clause(c: Context): RecordResponse {
        val agreement = targetId(c)
        permissions.require(c.user, "Registrar la cláusula", Actions.CREATE to CLAUSE)
        val creates = c.attributes["creates_commitment"]?.let { it as? Boolean ?: throw invalidValue("creates_commitment") } ?: false
        return records.create(
            CLAUSE,
            mapOf(
                "agreement" to agreement.toString(),
                "clause_number" to c.requireText("clause_number"),
                "clause_text" to c.requireText("clause_text"),
                "creates_commitment" to creates
            ),
            c.reason
        )
    }

    // EXTRACCION de un actor: el existente que nombra target_record_id (sin cambiarlo; name, organization_type y locality no se
    // escriben) o uno nuevo con CREATE sobre su objeto; y, si se pide, su grupo de interés del proyecto en identified, con CREATE
    // sobre stakeholder. la forma del grupo pedido, su tipo, el proyecto y el permiso se comprueban antes de escribir nada; el tipo
    // contra el catálogo, después del actor (si falla, la transacción revierte también al actor)
    private suspend fun actor(
        c: Context,
        objectName: String,
        create: suspend () -> RecordResponse
    ): Pair<RecordResponse, RecordResponse?> {
        val type = c.attributes["stakeholder"]?.let { requestedType(it) }
        val project =
            if (type == null) {
                null
            } else {
                val project =
                    c.project ?: throw Finding(
                        "PROJECT_REQUIRED",
                        "El grupo de interés es de un proyecto: la sugerencia no lo tiene",
                        "$ATTRIBUTES.stakeholder",
                        status = HttpStatus.BAD_REQUEST
                    ).toCodedException()
                permissions.require(c.user, "Registrar el grupo de interés", Actions.CREATE to STAKEHOLDER)
                project
            }
        val actor =
            c.targetId?.let {
                try {
                    records.readable(objectName, it)
                } catch (_: NotReadable) {
                    throw notReadable(objectName)
                }
            } ?: create()
        if (type == null || project == null) return actor to null
        if (type !in catalog.enumOf(STAKEHOLDER, "stakeholder_type")) throw invalidValue("stakeholder.stakeholder_type")
        val stakeholder =
            records.create(
                STAKEHOLDER,
                mapOf("project" to project.toString(), objectName to actor.id, "stakeholder_type" to type),
                c.reason
            )
        return actor to stakeholder
    }

    // el grupo de interés pedido: un objeto con solo stakeholder_type, de texto y obligatorio
    private fun requestedType(group: Any): String {
        if (group !is Map<*, *> || group.keys.any { it != "stakeholder_type" }) throw invalidValue("stakeholder")
        val type = group["stakeholder_type"]
        if (type is Map<*, *> || type is Collection<*>) throw invalidValue("stakeholder.stakeholder_type")
        return Values.text(type) ?: throw fieldRequired("stakeholder.stakeholder_type")
    }

    private suspend fun organization(c: Context): RecordResponse {
        permissions.require(c.user, "Registrar la organización", Actions.CREATE to ORGANIZATION)
        val type = c.text("organization_type")
        if (type != null && type !in catalog.enumOf(ORGANIZATION, "organization_type")) throw invalidValue("organization_type")
        val locality = c.text("locality")?.let { readableId("locality", it, "locality").toString() }
        return records.create(
            ORGANIZATION,
            mapOf("name" to c.requireText("name"), "organization_type" to type, "locality" to locality).filterValues {
                it !=
                    null
            },
            c.reason
        )
    }

    // una persona nueva la registra quien puede escribir sus nombres, restringidos (security.md §7.9): la sugerencia solo guarda el
    // marcador, así que los nombres los escribe quien acepta
    private suspend fun person(c: Context): RecordResponse {
        permissions.require(c.user, "Registrar la persona", Actions.CREATE to PERSON)
        val writable =
            metadata
                .definitionOf(PERSON)
                .fields
                .filter { it.editable }
                .map { it.name }
                .toSet()
        for (field in listOf("given_names", "surnames")) {
            if (field !in writable) {
                throw Finding("FIELD_NOT_WRITABLE", "Registrar una persona exige escribir sus nombres", "$ATTRIBUTES.$field", status = HttpStatus.FORBIDDEN)
                    .toCodedException()
            }
        }
        val sex = c.text("sex")
        if (sex != null && sex !in catalog.enumOf(PERSON, "sex")) throw invalidValue("sex")
        val locality = c.text("residence_locality")?.let { readableId("locality", it, "residence_locality").toString() }
        return records.create(
            PERSON,
            mapOf("given_names" to c.requireText("given_names"), "surnames" to c.requireText("surnames"), "sex" to sex, "residence_locality" to locality)
                .filterValues { it != null },
            c.reason
        )
    }

    // DUPLICADO de una queja: duplicate_of con la otra queja del mismo proyecto; con `response`, además la respuesta a quien la
    // planteó y su rechazo como duplicado por la transición de rechazo de su estado (WF-E1), con los roles de esa transición
    private suspend fun grievanceDuplicate(c: Context): RecordResponse {
        val other = other(c)
        val grievance = records.get(c.target, targetId(c))
        if (Values.text(other.attributes["project"]) != Values.text(grievance.attributes["project"])) {
            throw Finding(
                "PROJECT_MISMATCH",
                "La queja que lo cubre debe ser del mismo proyecto",
                "$ATTRIBUTES.other_record_id",
                status = HttpStatus.CONFLICT
            ).toCodedException()
        }
        val response = c.text("response") ?: return update(c, mapOf("duplicate_of" to other.id))
        val reject =
            catalog.transitions(c.target).firstOrNull { it.from == grievance.state && it.to == REJECTED }
                ?: throw Finding("STATE_MISMATCH", "La queja ya no se puede rechazar: está en ${grievance.state}", "$ATTRIBUTES.response").toCodedException()
        TransitionRoles.require(catalog, c.user, c.target, listOf(reject.name))
        val respondedAt = clock.instant().truncatedTo(ChronoUnit.SECONDS)
        update(c, mapOf("duplicate_of" to other.id, "response" to response, "responded_at" to respondedAt.toString()))
        return records.transition(c.target, targetId(c), reject.name, c.reason)
    }

    // DUPLICADO de una organización: el nombre de la otra se agrega a alternative_names de la que se conserva (el destino)
    private suspend fun organizationDuplicate(c: Context): RecordResponse {
        val other = other(c)
        val kept = records.get(c.target, targetId(c))
        val name = Values.text(other.attributes["name"]) ?: return kept
        val names =
            Values
                .text(kept.attributes["alternative_names"])
                ?.split(";")
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                .orEmpty()
        if (names.any { Proposals.folded(it) == Proposals.folded(name) }) return kept
        return update(c, mapOf("alternative_names" to (names + name).joinToString("; ")))
    }

    // BORRADOR DE SECCION y RESUMEN EJECUTIVO copiado (especificación de F8 §3.9): el texto revisado, el origen ASISTIDO POR IA y
    // el vínculo con la sugerencia, como quien acepta y con el motivo «IA aceptada»
    private suspend fun adopt(
        c: Context,
        section: UUID,
        projects: List<UUID?>,
        requireKnown: Boolean
    ): RecordResponse = sections.adopt(section, c.attributes["content"], c.suggestion, projects, requireKnown, c.reason)

    // el otro registro del par: del mismo objeto, distinto del destino y que se lee
    private suspend fun other(c: Context): RecordResponse {
        val id = Records.uuid(c.requireText("other_record_id")) ?: throw invalidValue("other_record_id")
        if (id == c.targetId) throw invalidValue("other_record_id")
        return try {
            records.readable(c.target, id)
        } catch (_: NotReadable) {
            throw Finding("DUPLICATE_NOT_FOUND", "El otro registro no existe o no puede leerlo", "$ATTRIBUTES.other_record_id", status = HttpStatus.BAD_REQUEST)
                .toCodedException()
        }
    }

    // el cambio de campos del destino, con UPDATE sobre su objeto y el motivo «IA aceptada»
    private suspend fun update(
        c: Context,
        changes: Map<String, Any?>
    ): RecordResponse {
        permissions.require(c.user, "Aceptar la sugerencia", Actions.UPDATE to c.target)
        return records.patch(c.target, targetId(c), changes, c.reason)
    }

    // el destino existe y quien acepta lo lee (§6.18 del modelo de dominio)
    private suspend fun targetId(c: Context): UUID {
        val id = c.targetId ?: throw notReadable(c.target)
        try {
            records.readable(c.target, id)
        } catch (_: NotReadable) {
            throw notReadable(c.target)
        }
        return id
    }

    private suspend fun readableId(
        objectName: String,
        value: String,
        key: String
    ): UUID {
        val id = Records.uuid(value) ?: throw invalidValue(key)
        try {
            records.readable(objectName, id)
        } catch (_: NotReadable) {
            throw invalidValue(key)
        }
        return id
    }

    companion object {
        const val ATTRIBUTES = "attributes"
        const val ACCEPT = "accept"
        const val TOPIC = "topic"
        const val ORGANIZATION = "organization"
        const val PERSON = "person"
        const val STAKEHOLDER = "stakeholder"
        const val CLAUSE = "agreement_clause"
        const val REJECTED = "rejected"

        fun fieldNotAllowed(key: String) =
            Finding("FIELD_NOT_ALLOWED", "La lista permitida no admite ese valor en esta sugerencia", "$ATTRIBUTES.$key", status = HttpStatus.BAD_REQUEST)
                .toCodedException()

        fun invalidValue(key: String) =
            Finding("INVALID_VALUE", "El valor no es del catálogo o no nombra un registro que se lea", "$ATTRIBUTES.$key", status = HttpStatus.BAD_REQUEST)
                .toCodedException()

        fun fieldRequired(key: String) =
            Finding("FIELD_REQUIRED", "Falta un valor obligatorio", "$ATTRIBUTES.$key", status = HttpStatus.BAD_REQUEST).toCodedException()

        fun notReadable(objectName: String) =
            Finding(
                "OBJECT_NOT_READABLE",
                "El registro de $objectName no existe o quien acepta no puede leerlo",
                "target_record_id",
                status = HttpStatus.FORBIDDEN
            ).toCodedException()
    }
}

// api.md §4.14: la aceptación honra Idempotency-Key, exige If-Match con el updatedAt de la sugerencia y X-Change-Reason
@RestController
@RequestMapping("/api/sgspe/ai-suggestions")
class SuggestionAcceptanceController(
    private val acceptance: SuggestionAcceptance,
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
            acceptance.accept(id, body, ChangeReason.fromHeader(reason), ifMatch)
        }
    }
}
