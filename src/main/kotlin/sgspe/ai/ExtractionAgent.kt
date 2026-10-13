package sgspe.ai

import org.springframework.stereotype.Component
import sgspe.common.ModelCatalog
import java.util.UUID

// AI-01 (ai-architecture.md §4.1 y §6.1, RF-AIK-001): extrae de un documento actores, temas, lugares, fechas y cláusulas. una
// llamada con el prompt en uso de AI-01 y, después, la validación determinista de cada hallazgo, que lo descarta si su cita no está
// literal en la fuente seudonimizada (CitationCheck), si su confianza no es de 0 a 1, si su valor no es del catálogo o si su
// destino no existe o no se lee. cada hallazgo válido es una ai_suggestion EXTRACCION con la forma de su fila de §4.2 y marcadores
// estables. los compromisos no salen de aquí: los propone CommitmentAgent (AI-03) en la misma corrida
@Component
class ExtractionAgent(
    private val calls: ModelCalls,
    private val lookups: RunLookups,
    private val catalog: ModelCatalog,
    private val properties: AiProperties
) {
    suspend fun run(
        source: RunSource,
        names: NameDictionary,
        budget: RunBudget
    ): RunProposals {
        val topics = lookups.topics()
        budget.spend()
        val result =
            calls.call(
                ModelCall(
                    useCase = USE_CASE,
                    variables = mapOf(TEXT to Proposals.neutral(source.text), TOPICS to topicList(topics)),
                    names = names,
                    outputClass = ExtractionOutput::class.java,
                    inputReference = source.inputReference,
                    project = source.project,
                    restricted = source.restricted,
                    summary = { "${it.items.size} hallazgos propuestos" }
                )
            )
        val text = result.inputs.getValue(TEXT)
        val known = topics.map { it.id }.toSet()
        val items = result.output.items
        val drafts =
            items
                .take(properties.limits.proposals)
                .mapNotNull { draft(it, result, text, source, known) }
                .distinctBy { Triple(it.targetObject, it.targetRecordId, it.suggestedValue) }
        return RunProposals(suggestions = drafts, interactions = listOf(result.interaction), discarded = items.size - drafts.size)
    }

    private suspend fun draft(
        item: ExtractedItem,
        result: ModelCallResult<ExtractionOutput>,
        text: String,
        source: RunSource,
        topics: Set<UUID>
    ): SuggestionDraft? {
        val confidence = Proposals.confidence(item.confidence) ?: return null
        if (!CitationCheck.found(item.quote, text)) return null
        val table = result.table

        fun suggestion(
            target: String,
            id: UUID?,
            value: Map<String, Any?>
        ) = SuggestionDraft(
            EXTRACCION,
            target,
            id,
            Proposals.json(value),
            table.stable(CitationCheck.normalize(item.quote)),
            confidence,
            result.model,
            source.project,
            result.interaction
        )
        return when (item.item) {
            ACTOR -> actor(item, table)?.let { (target, id, value) -> suggestion(target, id, value) }
            TOPIC -> {
                val engagement = source.engagement ?: return null
                val topic =
                    item.topic
                        ?.trim()
                        ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                        ?.takeIf { it in topics } ?: return null
                suggestion(ENGAGEMENT, engagement, linkedMapOf("item" to TOPIC, "topic" to topic.toString()))
            }
            PLACE -> {
                val engagement = source.engagement ?: return null
                val field = item.field?.takeIf { it in PLACE_FIELDS } ?: return null
                val place = lookups.place(field, table.restore(item.name ?: return null)) ?: return null
                suggestion(ENGAGEMENT, engagement, linkedMapOf("item" to PLACE, "field" to field, "value" to place.toString()))
            }
            DATE -> {
                val agreement = source.agreement ?: return null
                val field = item.field?.takeIf { it in DATE_FIELDS } ?: return null
                val date = Proposals.date(item.value) ?: return null
                suggestion(AGREEMENT, agreement, linkedMapOf("item" to DATE, "field" to field, "value" to date.toString()))
            }
            CLAUSE -> {
                val agreement = source.agreement ?: return null
                val number = item.clauseNumber?.trim()?.takeIf { it.isNotEmpty() && it.length <= MAX_CLAUSE_NUMBER } ?: return null
                val clause = item.clauseText?.trim()?.ifEmpty { null } ?: return null
                suggestion(
                    AGREEMENT,
                    agreement,
                    linkedMapOf(
                        "item" to CLAUSE,
                        "clause_number" to table.stable(number),
                        "clause_text" to table.stable(clause),
                        "creates_commitment" to (item.createsCommitment ?: false)
                    )
                )
            }
            else -> null
        }
    }

    // un actor: una organización (la existente que se lee con ese nombre exacto, o una nueva) o una persona. de una persona solo
    // se guarda el marcador: [PERSONA:<id>] si es una registrada (y entonces es el destino), [PERSONA] si no (§12.2)
    private suspend fun actor(
        item: ExtractedItem,
        table: PseudonymTable
    ): Triple<String, UUID?, Map<String, Any?>>? {
        val stakeholderType = enumOrNull("stakeholder", "stakeholder_type", item.stakeholderType) ?: return null
        val stakeholder = stakeholderType.value?.let { mapOf("stakeholder_type" to it) }
        val name = item.name?.trim()?.takeIf { it.isNotEmpty() && it.length <= MAX_NAME } ?: return null
        return when (item.kind) {
            ORGANIZATION -> {
                val type = enumOrNull(ORGANIZATION, "organization_type", item.organizationType) ?: return null
                val existing = lookups.organization(table.restore(name))
                Triple(
                    ORGANIZATION,
                    existing,
                    linkedMapOf(
                        "item" to ACTOR,
                        "kind" to ORGANIZATION,
                        "name" to table.stable(name),
                        "organization_type" to type.value,
                        "stakeholder" to stakeholder
                    )
                )
            }
            PERSON -> {
                val person = Proposals.personReference(table.stable(name))
                Triple(
                    PERSON,
                    person,
                    linkedMapOf(
                        "item" to ACTOR,
                        "kind" to PERSON,
                        "name" to (person?.let { "[PERSONA:$it]" } ?: Marker.PERSONA.reserved),
                        "stakeholder" to stakeholder
                    )
                )
            }
            else -> null
        }
    }

    // un valor de catálogo opcional: ausente es Choice(null); fuera del catálogo, null (la propuesta se descarta)
    private fun enumOrNull(
        objectName: String,
        field: String,
        value: String?
    ): Choice? {
        val wanted = value?.trim()?.ifEmpty { null } ?: return Choice(null)
        return if (wanted in catalog.enumOf(objectName, field)) Choice(wanted) else null
    }

    private data class Choice(
        val value: String?
    )

    companion object {
        const val USE_CASE = "AI-01"
        const val EXTRACCION = "EXTRACCION"
        const val TEXT = "text"
        const val TOPICS = "topics"
        const val ACTOR = "actor"
        const val TOPIC = "topic"
        const val PLACE = "place"
        const val DATE = "date"
        const val CLAUSE = "clause"
        const val ORGANIZATION = "organization"
        const val PERSON = "person"
        const val ENGAGEMENT = "engagement"
        const val AGREEMENT = "agreement"
        const val MAX_NAME = 300
        const val MAX_CLAUSE_NUMBER = 20
        val PLACE_FIELDS = setOf("locality", "community")
        val DATE_FIELDS = setOf("signed_date", "effective_date", "expiry_date")

        // la lista de temas que recibe el modelo: «- <id>: <nombre>», o «(ninguno)»
        fun topicList(topics: List<TopicOption>): String = topics.joinToString("\n") { "- ${it.id}: ${it.name}" }.ifEmpty { "(ninguno)" }
    }
}
