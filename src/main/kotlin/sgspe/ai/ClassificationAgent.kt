package sgspe.ai

import org.springframework.stereotype.Component
import sgspe.common.ModelCatalog
import sgspe.common.Values

// AI-08 (ai-architecture.md §4.1 y §4.2, RF-AIK-008, CON-031): un valor de catálogo, o un tema existente, para los campos de la
// lista permitida del registro: categoría, prioridad y tema de una QCS; tema de una pregunta; tono y tema de una mención. el tono
// es el de un texto, nunca la postura de un grupo de interés. una llamada con el prompt en uso de AI-08; se descarta lo que no es
// de un campo permitido, lo que no es de su catálogo, el mismo valor que ya tiene el registro, el segundo valor de un campo, una
// cita que no está en el texto y una confianza fuera de 0 a 1
@Component
class ClassificationAgent(
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
        val fields = FIELDS.getValue(source.objectName)
        val topics = if (TOPIC in fields) lookups.topics() else emptyList()
        budget.spend()
        val result =
            calls.call(
                ModelCall(
                    useCase = USE_CASE,
                    variables =
                        mapOf(
                            TEXT to Proposals.neutral(source.text),
                            FIELD_LIST to fieldList(source.objectName, fields),
                            TOPICS to ExtractionAgent.topicList(topics)
                        ),
                    names = names,
                    outputClass = ClassificationOutput::class.java,
                    inputReference = source.inputReference,
                    project = source.project,
                    restricted = source.restricted,
                    summary = { "${it.proposals.size} valores propuestos" }
                )
            )
        val text = result.inputs.getValue(TEXT)
        val topicIds = topics.map { it.id.toString() }.toSet()
        val seen = mutableSetOf<String>()
        val proposed = result.output.proposals
        val drafts =
            proposed.take(properties.limits.proposals).mapNotNull { proposal ->
                val field = proposal.field.trim().takeIf { it in fields } ?: return@mapNotNull null
                val value = proposal.value.trim()
                val valid = if (field == TOPIC) value in topicIds else value in catalog.enumOf(source.objectName, field)
                if (!valid || Values.text(source.current[field]) == value || field in seen) return@mapNotNull null
                val confidence = Proposals.confidence(proposal.confidence) ?: return@mapNotNull null
                if (!CitationCheck.found(proposal.quote, text)) return@mapNotNull null
                seen += field
                SuggestionDraft(
                    KIND,
                    source.objectName,
                    source.id,
                    Proposals.json(linkedMapOf("field" to field, "value" to value)),
                    result.table.stable(CitationCheck.normalize(proposal.quote)),
                    confidence,
                    result.model,
                    source.project,
                    result.interaction
                )
            }
        return RunProposals(suggestions = drafts, interactions = listOf(result.interaction), discarded = proposed.size - drafts.size)
    }

    // «- category: AGUA Y MEDIO AMBIENTE | EMPLEO | …» y «- topic: el id de un tema de la lista»
    private fun fieldList(
        objectName: String,
        fields: List<String>
    ): String =
        fields.joinToString("\n") { field ->
            if (field == TOPIC) "- $field: el id de un tema de la lista de temas" else "- $field: ${catalog.enumOf(objectName, field).joinToString(" | ")}"
        }

    companion object {
        const val USE_CASE = "AI-08"
        const val KIND = "CLASIFICACION"
        const val TEXT = "text"
        const val FIELD_LIST = "fields"
        const val TOPICS = "topics"
        const val TOPIC = "topic"

        // los campos de la lista permitida por objeto (ai-architecture.md §4.2, filas CLASIFICACION)
        val FIELDS: Map<String, List<String>> =
            mapOf(
                "grievance" to listOf("category", "priority", TOPIC),
                "inquiry" to listOf("category", "priority", TOPIC),
                "stakeholder_request" to listOf("category", "priority", TOPIC),
                "engagement_question" to listOf(TOPIC),
                "media_mention" to listOf("tone", TOPIC)
            )
    }
}
