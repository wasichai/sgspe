package sgspe.ai

import org.springframework.stereotype.Component

// AI-02 y AI-04 (ai-architecture.md §4.1 y §4.2, RF-AIK-002, RF-AIK-004): un texto Markdown sin HTML que una persona copia o edita.
// una llamada con el prompt en uso del caso; el borrador vacío se descarta y las etiquetas HTML se quitan. AI-02 es una
// ai_suggestion BORRADOR DE ACTA con destino la interacción; AI-04, una RESUMEN EJECUTIVO con destino el proyecto, que en F7 solo
// informa y no se acepta. no llevan cita: no extraen un dato, redactan
@Component
class DraftAgents(
    private val calls: ModelCalls
) {
    suspend fun minutes(
        source: RunSource,
        names: NameDictionary,
        budget: RunBudget
    ): RunProposals = draft(MINUTES_CASE, MINUTES, "engagement", source, names, budget)

    suspend fun summary(
        source: RunSource,
        names: NameDictionary,
        budget: RunBudget
    ): RunProposals = draft(SUMMARY_CASE, SUMMARY, "project", source, names, budget)

    private suspend fun draft(
        useCase: String,
        kind: String,
        target: String,
        source: RunSource,
        names: NameDictionary,
        budget: RunBudget
    ): RunProposals {
        budget.spend()
        val result =
            calls.call(
                ModelCall(
                    useCase = useCase,
                    variables = mapOf(TEXT to Proposals.neutral(source.text)),
                    names = names,
                    outputClass = DraftText::class.java,
                    inputReference = source.inputReference,
                    project = source.project,
                    restricted = source.restricted,
                    summary = { if (it.markdown.isBlank()) "Borrador vacío" else "Un borrador de ${it.markdown.length} caracteres" }
                )
            )
        val markdown = Proposals.markdown(result.output.markdown) ?: return RunProposals(interactions = listOf(result.interaction), discarded = 1)
        val suggestion =
            SuggestionDraft(
                kind,
                target,
                source.id,
                result.table.stable(markdown),
                null,
                Proposals.confidence(result.output.confidence),
                result.model,
                source.project,
                result.interaction
            )
        return RunProposals(suggestions = listOf(suggestion), interactions = listOf(result.interaction))
    }

    companion object {
        const val MINUTES_CASE = "AI-02"
        const val SUMMARY_CASE = "AI-04"
        const val MINUTES = "BORRADOR DE ACTA"
        const val SUMMARY = "RESUMEN EJECUTIVO"
        const val TEXT = "text"
    }
}
