package sgspe.ai

// las filas de la lista permitida (ai-architecture.md §4.2): cada una fija el tipo, el objeto destino y el único campo o comando
// que la aceptación ejecuta. desde F8 (plan 8.2c), BORRADOR DE SECCION y RESUMEN EJECUTIVO se adoptan en una sección de entregable
enum class AcceptRow {
    CLASSIFICATION,
    TOPIC,
    PLACE,
    DATE,
    CLAUSE,
    ORGANIZATION,
    PERSON,
    MINUTES,
    GRIEVANCE_DUPLICATE,
    ORGANIZATION_DUPLICATE,
    DECISION_DUPLICATE,
    SECTION,
    SUMMARY_SECTION
}

// la lista permitida, cerrada y en Kotlin puro (P11): la fila de una sugerencia guardada sale de su tipo, su objeto destino y la
// forma de su suggested_value; todo par de tipo, objeto y campo que no esté aquí no tiene fila y no se acepta. ningún campo derivado,
// ni workflow_state, ni un objeto apiOnly salvo el candidato, que tiene su propio comando
object AllowList {
    val CLASSIFICATION_FIELDS: Map<String, Set<String>> =
        mapOf(
            "grievance" to setOf("category", "priority", "topic"),
            "inquiry" to setOf("category", "priority", "topic"),
            "stakeholder_request" to setOf("category", "priority", "topic"),
            "engagement_question" to setOf("topic"),
            "media_mention" to setOf("tone", "topic")
        )
    val PLACE_FIELDS = setOf("locality", "community")
    val DATE_FIELDS = setOf("signed_date", "effective_date", "expiry_date")

    // DUPLICADO sin vínculo de duplicado: solo se registra la decisión (§4.2, RQ-AI-015)
    val DECISION_ONLY = setOf("person", "stakeholder", "commitment", "inquiry", "stakeholder_request")

    // el Markdown de los tipos que no guardan JSON
    val MARKDOWN_KINDS = setOf("BORRADOR DE ACTA", "RESUMEN EJECUTIVO", "BORRADOR DE SECCION")

    fun rowOf(
        kind: String?,
        targetObject: String?,
        suggestedValue: String?
    ): AcceptRow? {
        if (kind == "BORRADOR DE ACTA") return AcceptRow.MINUTES.takeIf { targetObject == "engagement" }
        // F8 (plan 8.2c): un borrador de sección se adopta en su sección; un resumen ejecutivo se copia a una sección del proyecto
        if (kind == "BORRADOR DE SECCION") return AcceptRow.SECTION.takeIf { targetObject == "deliverable_section" }
        if (kind == "RESUMEN EJECUTIVO") return AcceptRow.SUMMARY_SECTION.takeIf { targetObject == "project" }
        if (kind in MARKDOWN_KINDS) return null
        val value = Proposals.parse(suggestedValue) ?: return null
        return when (kind) {
            "CLASIFICACION" -> AcceptRow.CLASSIFICATION.takeIf { value["field"] in CLASSIFICATION_FIELDS[targetObject].orEmpty() }
            "EXTRACCION" -> extraction(targetObject, value)
            "DUPLICADO" ->
                when (targetObject) {
                    "grievance" -> AcceptRow.GRIEVANCE_DUPLICATE
                    "organization" -> AcceptRow.ORGANIZATION_DUPLICATE
                    in DECISION_ONLY -> AcceptRow.DECISION_DUPLICATE
                    else -> null
                }
            else -> null
        }
    }

    private fun extraction(
        targetObject: String?,
        value: Map<String, Any?>
    ): AcceptRow? =
        when (value["item"]) {
            "actor" ->
                when {
                    value["kind"] == "organization" && targetObject == "organization" -> AcceptRow.ORGANIZATION
                    value["kind"] == "person" && targetObject == "person" -> AcceptRow.PERSON
                    else -> null
                }
            "topic" -> AcceptRow.TOPIC.takeIf { targetObject == "engagement" }
            "place" -> AcceptRow.PLACE.takeIf { targetObject == "engagement" && value["field"] in PLACE_FIELDS }
            "date" -> AcceptRow.DATE.takeIf { targetObject == "agreement" && value["field"] in DATE_FIELDS }
            "clause" -> AcceptRow.CLAUSE.takeIf { targetObject == "agreement" }
            else -> null
        }

    // las claves que admite el cuerpo de la aceptación en cada fila: la forma de suggested_value con los valores revisados; un actor
    // persona nuevo lleva los nombres que escribe quien acepta en lugar de `name` (api.md §4.14)
    val BODY_KEYS: Map<AcceptRow, Set<String>> =
        mapOf(
            AcceptRow.CLASSIFICATION to setOf("field", "value"),
            AcceptRow.TOPIC to setOf("item", "topic"),
            AcceptRow.PLACE to setOf("item", "field", "value"),
            AcceptRow.DATE to setOf("item", "field", "value"),
            AcceptRow.CLAUSE to setOf("item", "clause_number", "clause_text", "creates_commitment"),
            AcceptRow.ORGANIZATION to setOf("item", "kind", "name", "organization_type", "locality", "stakeholder"),
            AcceptRow.PERSON to setOf("item", "kind", "given_names", "surnames", "sex", "residence_locality", "stakeholder"),
            AcceptRow.MINUTES to setOf("minutes"),
            AcceptRow.GRIEVANCE_DUPLICATE to setOf("other_record_id", "response"),
            AcceptRow.ORGANIZATION_DUPLICATE to setOf("other_record_id"),
            AcceptRow.DECISION_DUPLICATE to setOf("other_record_id"),
            AcceptRow.SECTION to setOf("content"),
            AcceptRow.SUMMARY_SECTION to setOf("section", "content")
        )

    // las filas que adoptan un texto en una sección: DeliverableSections.adopt convierte los marcadores en lugar de rechazarlos
    val ADOPTIONS: Set<AcceptRow> = setOf(AcceptRow.SECTION, AcceptRow.SUMMARY_SECTION)

    // `item` y `kind`, si vienen, son los de la fila: la clave que cambiaría de fila, o null
    fun misplaced(
        row: AcceptRow,
        attributes: Map<String, Any?>
    ): String? {
        val (item, kind) =
            when (row) {
                AcceptRow.TOPIC -> "topic" to null
                AcceptRow.PLACE -> "place" to null
                AcceptRow.DATE -> "date" to null
                AcceptRow.CLAUSE -> "clause" to null
                AcceptRow.ORGANIZATION -> "actor" to "organization"
                AcceptRow.PERSON -> "actor" to "person"
                else -> return null
            }
        return when {
            "item" in attributes && attributes["item"] != item -> "item"
            "kind" in attributes && attributes["kind"] != kind -> "kind"
            else -> null
        }
    }
}
