package sgspe.ai

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F7ApiTest
import sgspe.common.IdempotencyKey
import sgspe.common.IfMatch
import tools.jackson.databind.JsonNode

// POST /api/sgspe/ai-suggestions/{id}/accept (ai-architecture.md §4.2 y §15, api.md §4.14) con la IA apagada: aceptar no depende del
// proveedor ni de la activación (§12.1). cada fila de la lista cerrada ejecuta su campo o comando con los valores revisados, como la
// persona y con su motivo, y lleva la sugerencia a accepted en la misma transacción; lo demás se rechaza con su código y su campo
class SuggestionAcceptanceApiTest : F7ApiTest() {
    private val cro by lazy { member("COMMUNITY_RELATIONS_OFFICER") }
    private val grievances by lazy { member("GRIEVANCE_MANAGER") }

    // una sugerencia propuesta de `kind` sobre `target`; un actor nuevo no tiene registro destino, solo su objeto (`newActor`)
    private fun suggestion(
        kind: String,
        target: Pair<String, String>?,
        value: String,
        newActor: String? = null
    ): String = aiSuggestion(testProject(), kind, target, mapOf("suggested_value" to value) + listOfNotNull(newActor?.let { "target_object" to it }))

    private fun accepted(response: Response): JsonNode {
        assertEquals(HttpStatus.OK, response.status, response.body)
        val body = tree(response.body)
        assertEquals("accepted", body["suggestion"]["state"].asString())
        assertEquals("\"${body["suggestion"]["updatedAt"].asString()}\"", response.headers.getFirst("ETag"))
        return body
    }

    private fun refused(
        response: Response,
        status: HttpStatus,
        code: String,
        field: String
    ) {
        assertEquals(status, response.status, response.body)
        assertEquals(code, code(response))
        assertEquals(listOf(field), problemFields(response.body))
    }

    private fun topic(): String = createRecord("topic", mapOf("code" to uniqueName("T-"), "name" to "Agua y riego", "active" to true))

    @Test
    fun `una clasificacion escribe el valor revisado con el origen IA aceptada en su motivo`() {
        val grievance = grievance(testProject())
        val id = suggestion("CLASIFICACION", "grievance" to grievance, """{"field":"category","value":"AGUA Y MEDIO AMBIENTE"}""")
        val body = accepted(acceptSuggestion(cro, id, mapOf("field" to "category", "value" to "EMPLEO"), "la categoría correcta es empleo"))
        assertEquals(grievance, body["result"]["id"].asString())
        assertEquals("EMPLEO", record("grievance", grievance)["attributes"]["category"].asString())
        val history = tree(send("GET", "/api/objects/grievance/records/$grievance/history", null, HttpStatus.OK)).values()
        assertTrue(history.any { it["reason"]?.asString() == "IA aceptada (ai_suggestion $id): la categoría correcta es empleo" }, history.toString())
        assertEquals("accepted", state("ai_suggestion", id))
    }

    @Test
    fun `el tono de una mencion y el tema de una pregunta`() {
        val mention = mediaMention(testProject())
        accepted(
            acceptSuggestion(
                cro,
                suggestion("CLASIFICACION", "media_mention" to mention, """{"field":"tone","value":"NEUTRO"}"""),
                mapOf(
                    "field" to "tone",
                    "value" to "POSITIVO"
                )
            )
        )
        assertEquals("POSITIVO", record("media_mention", mention)["attributes"]["tone"].asString())
        val question = createRecord("engagement_question", mapOf("engagement" to engagement(testProject()), "question_text" to "¿Cuándo llega el agua?"))
        val theme = topic()
        accepted(
            acceptSuggestion(
                cro,
                suggestion("CLASIFICACION", "engagement_question" to question, """{"field":"topic","value":"$theme"}"""),
                mapOf("field" to "topic", "value" to theme)
            )
        )
        assertEquals(theme, record("engagement_question", question)["attributes"]["topic"].asString())
    }

    @Test
    fun `el lugar y el tema de una interaccion`() {
        val engagement = engagement(testProject())
        val community = fixture("community").id
        accepted(
            acceptSuggestion(
                cro,
                suggestion("EXTRACCION", "engagement" to engagement, """{"item":"place","field":"community","value":"$community"}"""),
                mapOf("item" to "place", "field" to "community", "value" to community)
            )
        )
        assertEquals(community, record("engagement", engagement)["attributes"]["community"].asString())
        val theme = topic()
        accepted(
            acceptSuggestion(
                cro,
                suggestion("EXTRACCION", "engagement" to engagement, """{"item":"topic","topic":"$theme"}"""),
                mapOf(
                    "item" to "topic",
                    "topic" to theme
                )
            )
        )
        val related = tree(send("GET", "/api/objects/engagement/records/$engagement/related/engagement_topic", null, HttpStatus.OK))["content"].values()
        assertEquals(listOf(theme), related.map { it["id"].asString() })
    }

    @Test
    fun `la fecha de un acuerdo y una clausula nueva`() {
        val agreement = agreement(testProject())
        accepted(
            acceptSuggestion(
                cro,
                suggestion("EXTRACCION", "agreement" to agreement, """{"item":"date","field":"expiry_date","value":"2027-03-31"}"""),
                mapOf("item" to "date", "field" to "expiry_date", "value" to "2027-03-31")
            )
        )
        assertEquals("2027-03-31", record("agreement", agreement)["attributes"]["expiry_date"].asString())
        val clause =
            accepted(
                acceptSuggestion(
                    cro,
                    suggestion("EXTRACCION", "agreement" to agreement, """{"item":"clause","clause_number":"5","clause_text":"x","creates_commitment":true}"""),
                    mapOf("item" to "clause", "clause_number" to "5", "clause_text" to "El proyecto contratará un vigía comunal", "creates_commitment" to true)
                )
            )["result"]
        assertEquals(agreement, clause["attributes"]["agreement"].asString())
        assertEquals("El proyecto contratará un vigía comunal", clause["attributes"]["clause_text"].asString())
    }

    @Test
    fun `una organizacion nueva con su grupo de interes, y una existente que no cambia`() {
        val created =
            accepted(
                acceptSuggestion(
                    cro,
                    suggestion("EXTRACCION", null, """{"item":"actor","kind":"organization","name":"Junta Ejemplo"}""", "organization"),
                    mapOf(
                        "item" to "actor",
                        "kind" to "organization",
                        "name" to "Junta de Usuarios Ejemplo",
                        "organization_type" to "COMITE DE REGANTES",
                        "stakeholder" to mapOf("stakeholder_type" to "ORGANIZACION SOCIAL LOCAL")
                    )
                )
            )
        assertEquals("Junta de Usuarios Ejemplo", created["result"]["attributes"]["name"].asString())
        assertEquals(created["result"]["id"].asString(), created["stakeholder"]["attributes"]["organization"].asString())
        assertEquals(testProject(), created["stakeholder"]["attributes"]["project"].asString())
        val existing = organization("Asociación Ejemplo")
        val kept =
            accepted(
                acceptSuggestion(
                    cro,
                    suggestion("EXTRACCION", "organization" to existing, """{"item":"actor","kind":"organization","name":"Asociación Ejemplo"}"""),
                    mapOf("item" to "actor", "kind" to "organization", "name" to "Otro nombre", "organization_type" to "ONG")
                )
            )
        assertEquals(existing, kept["result"]["id"].asString())
        assertEquals("Asociación Ejemplo", record("organization", existing)["attributes"]["name"].asString())
        assertFalse(kept.has("stakeholder"))
    }

    @Test
    fun `una persona nueva la registra quien escribe sus nombres, y quien no crea personas no la acepta`() {
        fun personSuggestion() = suggestion("EXTRACCION", null, """{"item":"actor","kind":"person","name":"[PERSONA]"}""", "person")
        val names = mapOf("item" to "actor", "kind" to "person", "given_names" to "Rosa", "surnames" to "Ejemplo", "sex" to "FEMENINO")
        val created = accepted(acceptSuggestion(cro, personSuggestion(), names))["result"]
        assertEquals("Rosa", record("person", created["id"].asString())["attributes"]["given_names"].asString())
        // SOCIAL_RISK_MANAGER acepta sugerencias pero no crea personas
        val risks = member("SOCIAL_RISK_MANAGER")
        assertEquals(HttpStatus.FORBIDDEN, acceptSuggestion(risks, personSuggestion(), names).status)
    }

    @Test
    fun `el borrador de acta escribe minutes y devuelve la interaccion`() {
        val engagement = engagement(testProject())
        val body =
            accepted(
                acceptSuggestion(
                    cro,
                    suggestion("BORRADOR DE ACTA", "engagement" to engagement, "## Acta\n\n[PERSONA] pidió el canal."),
                    mapOf("minutes" to "## Acta\n\nRosa Ejemplo pidió el canal.")
                )
            )
        assertEquals("## Acta\n\nRosa Ejemplo pidió el canal.", body["result"]["attributes"]["minutes"].asString())
    }

    @Test
    fun `un duplicado de queja con respuesta la rechaza como duplicado, y sin respuesta solo la vincula`() {
        val kept = grievance(testProject())
        val repeated = grievance(testProject())
        val id = suggestion("DUPLICADO", "grievance" to repeated, """{"other_record_id":"$kept","reasons":["Mismo anexo y misma fecha"]}""")
        val body = accepted(acceptSuggestion(grievances, id, mapOf("other_record_id" to kept, "response" to "Su pedido se atiende en la otra queja.")))
        assertEquals("rejected", body["result"]["state"].asString())
        val attributes = record("grievance", repeated)["attributes"]
        assertEquals(kept, attributes["duplicate_of"].asString())
        assertEquals("Su pedido se atiende en la otra queja.", attributes["response"].asString())
        val linked = grievance(testProject())
        accepted(
            acceptSuggestion(grievances, suggestion("DUPLICADO", "grievance" to linked, """{"other_record_id":"$kept"}"""), mapOf("other_record_id" to kept))
        )
        assertEquals("received", state("grievance", linked))
        assertEquals(kept, record("grievance", linked)["attributes"]["duplicate_of"].asString())
    }

    @Test
    fun `un duplicado de organizacion agrega el nombre y uno de compromiso solo registra la decision`() {
        val kept = organization("Junta Ejemplo")
        val other = organization("Junta de Regantes Ejemplo")
        accepted(acceptSuggestion(cro, suggestion("DUPLICADO", "organization" to kept, """{"other_record_id":"$other"}"""), mapOf("other_record_id" to other)))
        assertEquals("Junta de Regantes Ejemplo", record("organization", kept)["attributes"]["alternative_names"].asString())
        val first = commitment(testProject())
        val second = commitment(testProject())
        val before = record("commitment", second)["attributes"]
        val body =
            accepted(
                acceptSuggestion(
                    cro,
                    suggestion("DUPLICADO", "commitment" to second, """{"other_record_id":"$first"}"""),
                    mapOf("other_record_id" to first)
                )
            )
        assertTrue(body["result"].isNull)
        assertEquals(before, record("commitment", second)["attributes"])
    }

    @Test
    fun `cada rechazo nombra su codigo y su campo`() {
        val grievance = grievance(testProject())
        val id = suggestion("CLASIFICACION", "grievance" to grievance, """{"field":"category","value":"EMPLEO"}""")
        val path = "/api/sgspe/ai-suggestions/$id/accept"
        val body = mapOf("attributes" to mapOf("field" to "category", "value" to "EMPLEO"))
        assertEquals(HttpStatus.PRECONDITION_REQUIRED, exchange("POST", path, body, cro.token, withReason()).status)
        refusedNoReason(id)
        val unknown = acceptSuggestion(cro, id, mapOf("field" to "category", "value" to "EMPLEO", "extra" to 1))
        assertEquals(HttpStatus.BAD_REQUEST, unknown.status, unknown.body)
        assertEquals(listOf("attributes.extra"), problemFields(unknown.body))
        refused(
            acceptSuggestion(cro, id, mapOf("field" to "workflow_state", "value" to "closed")),
            HttpStatus.BAD_REQUEST,
            "FIELD_NOT_ALLOWED",
            "attributes.field"
        )
        refused(acceptSuggestion(cro, id, mapOf("field" to "category", "value" to "OTRA COSA")), HttpStatus.BAD_REQUEST, "INVALID_VALUE", "attributes.value")
        val stale = exchange("POST", path, body, cro.token, mapOf(IfMatch.HEADER to "\"2020-01-01T00:00:00Z\"") + withReason())
        assertEquals(HttpStatus.PRECONDITION_FAILED, stale.status, stale.body)
        move("ai_suggestion", id, "reject", cro.token, "no corresponde")
        refused(acceptSuggestion(cro, id, mapOf("field" to "category", "value" to "EMPLEO")), HttpStatus.CONFLICT, "STATE_MISMATCH", "state")
    }

    // quien acepta corrige el valor, no el campo: el campo es el que propuso la IA (§4.2, RQ-AUD-012)
    @Test
    fun `una clasificacion no cambia de campo`() {
        val grievance = grievance(testProject())
        val id = suggestion("CLASIFICACION", "grievance" to grievance, """{"field":"category","value":"EMPLEO"}""")
        refused(acceptSuggestion(cro, id, mapOf("field" to "priority", "value" to "ALTA")), HttpStatus.BAD_REQUEST, "FIELD_NOT_ALLOWED", "attributes.field")
        assertEquals("proposed", state("ai_suggestion", id))
        // sin field, el guardado
        accepted(acceptSuggestion(cro, id, mapOf("value" to "TIERRAS")))
        assertEquals("TIERRAS", record("grievance", grievance)["attributes"]["category"].asString())
    }

    @Test
    fun `un valor compuesto se rechaza y el grupo de interes pide su tipo antes de escribir`() {
        val engagement = engagement(testProject())
        val draft = suggestion("BORRADOR DE ACTA", "engagement" to engagement, "## Acta")
        refused(acceptSuggestion(cro, draft, mapOf("minutes" to listOf("## Acta"))), HttpStatus.BAD_REQUEST, "INVALID_VALUE", "attributes.minutes")
        val name = uniqueName("Junta Sin Tipo ")
        val actor = suggestion("EXTRACCION", null, """{"item":"actor","kind":"organization","name":"Junta"}""", "organization")
        val organization = mapOf("item" to "actor", "kind" to "organization", "name" to name)
        refused(
            acceptSuggestion(cro, actor, organization + ("stakeholder" to emptyMap<String, Any?>())),
            HttpStatus.BAD_REQUEST,
            "FIELD_REQUIRED",
            "attributes.stakeholder.stakeholder_type"
        )
        refused(
            acceptSuggestion(cro, actor, organization + ("stakeholder" to mapOf("stakeholder_type" to "[PERSONA]"))),
            HttpStatus.BAD_REQUEST,
            "PSEUDONYM_NOT_ALLOWED",
            "attributes.stakeholder.stakeholder_type"
        )
        val found = send("GET", "/api/objects/organization/records?name=${java.net.URLEncoder.encode(name, Charsets.UTF_8)}", null, HttpStatus.OK)
        assertEquals(0, tree(found)["content"].size())
        assertEquals("proposed", state("ai_suggestion", actor))
    }

    @Test
    fun `un duplicado de queja de otro proyecto no se acepta`() {
        val other = project()
        val manager = member("GRIEVANCE_MANAGER")
        assign(manager, other, "GRIEVANCE_MANAGER")
        val elsewhere = grievance(other)
        val id = suggestion("DUPLICADO", "grievance" to grievance(testProject()), """{"other_record_id":"$elsewhere"}""")
        refused(acceptSuggestion(manager, id, mapOf("other_record_id" to elsewhere)), HttpStatus.CONFLICT, "PROJECT_MISMATCH", "attributes.other_record_id")
        assertEquals("proposed", state("ai_suggestion", id))
    }

    // Idempotency-Key: el reenvío devuelve la misma respuesta, marcada como reenvío, y escribe una sola vez
    @Test
    fun `un reenvio con la misma clave no vuelve a escribir`() {
        val grievance = grievance(testProject())
        val id = suggestion("CLASIFICACION", "grievance" to grievance, """{"field":"category","value":"EMPLEO"}""")
        val headers =
            mapOf(IfMatch.HEADER to "\"${record("ai_suggestion", id)["updatedAt"].asString()}\"", IdempotencyKey.HEADER to uniqueName("acepta-")) +
                withReason("revisada una vez")
        val body = mapOf("attributes" to mapOf("field" to "category", "value" to "EMPLEO"))
        val path = "/api/sgspe/ai-suggestions/$id/accept"
        val first = exchange("POST", path, body, cro.token, headers)
        accepted(first)
        val second = exchange("POST", path, body, cro.token, headers)
        assertEquals(HttpStatus.OK, second.status, second.body)
        assertEquals(tree(first.body), tree(second.body))
        assertEquals("true", second.headers.getFirst(IdempotencyKey.REPLAYED))
        val history = tree(send("GET", "/api/objects/grievance/records/$grievance/history", null, HttpStatus.OK)).values()
        assertEquals(1, history.count { it["reason"]?.asString() == "IA aceptada (ai_suggestion $id): revisada una vez" }, history.toString())
    }

    private fun refusedNoReason(id: String) {
        val response = acceptSuggestion(cro, id, mapOf("field" to "category", "value" to "EMPLEO"), reason = null)
        assertEquals(HttpStatus.BAD_REQUEST, response.status, response.body)
        assertEquals(listOf("reason"), problemFields(response.body))
    }

    @Test
    fun `un marcador no llega a un registro de dominio y un resumen ejecutivo no se acepta`() {
        val engagement = engagement(testProject())
        val draft = suggestion("BORRADOR DE ACTA", "engagement" to engagement, "## Acta\n\n[PERSONA] pidió el canal.")
        refused(
            acceptSuggestion(cro, draft, mapOf("minutes" to "## Acta\n\n[PERSONA] pidió el canal.")),
            HttpStatus.BAD_REQUEST,
            "PSEUDONYM_NOT_ALLOWED",
            "attributes.minutes"
        )
        // tampoco escondido en una lista
        refused(
            acceptSuggestion(cro, draft, mapOf("minutes" to listOf("[PERSONA] pidió el canal."))),
            HttpStatus.BAD_REQUEST,
            "PSEUDONYM_NOT_ALLOWED",
            "attributes.minutes"
        )
        assertEquals("proposed", state("ai_suggestion", draft))
        val summary = suggestion("RESUMEN EJECUTIVO", "project" to testProject(), "## Situación general")
        refused(acceptSuggestion(cro, summary, emptyMap()), HttpStatus.CONFLICT, "SUGGESTION_NOT_ACCEPTABLE", "suggestion_kind")
        assertEquals("proposed", state("ai_suggestion", summary))
    }

    @Test
    fun `una cuenta de servicio no acepta aunque tenga el rol`() {
        val service = serviceAccount("COMMUNITY_RELATIONS_OFFICER")
        val id = suggestion("CLASIFICACION", "grievance" to grievance(testProject()), """{"field":"category","value":"EMPLEO"}""")
        val response = acceptSuggestion(service, id, mapOf("field" to "category", "value" to "EMPLEO"))
        assertEquals(HttpStatus.FORBIDDEN, response.status, response.body)
        assertEquals("PERSON_REQUIRED", code(response))
        assertEquals("proposed", state("ai_suggestion", id))
    }

    // una sola transacción (§4.2, §15): la organización ya creada se revierte si el grupo de interés no pasa, y la sugerencia sigue
    // propuesta
    @Test
    fun `si algo falla despues de escribir nada queda escrito`() {
        val name = uniqueName("Junta Revertida ")
        val id = suggestion("EXTRACCION", null, """{"item":"actor","kind":"organization","name":"Junta"}""", "organization")
        val response =
            acceptSuggestion(
                cro,
                id,
                mapOf(
                    "item" to "actor",
                    "kind" to "organization",
                    "name" to name,
                    "stakeholder" to mapOf("stakeholder_type" to "NO EXISTE")
                )
            )
        refused(response, HttpStatus.BAD_REQUEST, "INVALID_VALUE", "attributes.stakeholder.stakeholder_type")
        assertEquals(
            0,
            tree(
                send("GET", "/api/objects/organization/records?name=${java.net.URLEncoder.encode(name, Charsets.UTF_8)}", null, HttpStatus.OK)
            )["content"].size()
        )
        assertEquals("proposed", state("ai_suggestion", id))
    }
}
