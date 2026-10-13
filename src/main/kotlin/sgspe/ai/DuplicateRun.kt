package sgspe.ai

import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import wasichai.core.identity.AuthenticatedUser
import java.util.UUID

// AI-07 como caso de POST /api/sgspe/ai/runs (plan 7.2b, ai-architecture.md §4.1, api.md §4.14): sobre un proyecto o un registro de
// los objetos que compara, DuplicateDetection arma los pares, el modelo los compara y SuggestionWriter guarda, en una transacción
// como los demás casos, una ai_suggestion DUPLICADO por par en proposed (WF-K), como la persona y con la marca, con
// suggested_value {other_record_id, reasons[]}; un destino que la persona no lee o de otro proyecto, o una propuesta repetida, queda
// fuera. lo que exige de quien pide es lo de RunRequirements (una persona, CREATE sobre ai_interaction y ai_suggestion, READ sobre
// el objeto pedido y los nombres de person), antes que el proveedor, y decide también si GET /ai/status lo ofrece. sin pares no
// llama al modelo y no guarda nada (interaction null). nunca fusiona ni vincula: la aceptación de una persona decide (RQ-AI-015)
@Component
class DuplicateRun(
    requirements: RunRequirements,
    names: PersonNames,
    writer: SuggestionWriter,
    properties: AiProperties,
    private val detection: DuplicateDetection,
    private val json: JsonMapper
) : RequiredCase(requirements, names, writer, properties) {
    override val useCase: String = DuplicateDetection.USE_CASE
    override val objects: Set<String> = DuplicateCandidates.OBJECTS + DuplicateRecords.PROJECT
    override val outputs = listOf(SuggestionWriter.SUGGESTION)

    override suspend fun propose(
        target: RunTarget,
        names: NameDictionary,
        budget: RunBudget,
        user: AuthenticatedUser
    ): RunProposals {
        val findings = detection.detect(DuplicateTarget(target.objectName, target.recordId), names, budget)
        val interaction = findings.interaction ?: return RunProposals()
        return RunProposals(
            suggestions = findings.proposals.map { draft(it, findings.model, interaction) },
            interactions = listOf(interaction),
            discarded = findings.discarded
        )
    }

    private fun draft(
        proposal: DuplicateProposal,
        model: String,
        interaction: UUID
    ) = SuggestionDraft(
        kind = DuplicateDetection.KIND,
        targetObject = proposal.objectName,
        targetRecordId = proposal.targetRecordId,
        suggestedValue = proposal.suggestedValue(json),
        evidenceQuote = null,
        confidence = proposal.confidence,
        model = model,
        project = proposal.project,
        interaction = interaction
    )
}
