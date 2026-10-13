package sgspe.ai

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import sgspe.common.Finding
import sgspe.common.toCodedException

// AI-03 (ai-architecture.md §4.1, §5 y §6.1, RF-AIK-003): los posibles compromisos de un acta, nunca un compromiso creado. una
// llamada con el prompt en uso de AI-03; cada propuesta se descarta si su cita no está literal en el acta seudonimizada, si su
// confianza no es de 0 a 1, si su texto está vacío o si repite un compromiso o un candidato del proyecto (o otra propuesta de la
// misma llamada). las válidas son commitment_candidate de la IA, sin responsable sugerido: lo elige quien acepta (§4.2). un
// candidato es de un proyecto, así que una evidencia del tenant no lleva AI-03 (400 PROJECT_REQUIRED antes de llamar)
@Component
class CommitmentAgent(
    private val calls: ModelCalls,
    private val lookups: RunLookups,
    private val properties: AiProperties
) {
    suspend fun run(
        source: RunSource,
        names: NameDictionary,
        budget: RunBudget
    ): RunProposals {
        val project = source.project ?: throw projectRequired()
        budget.spend()
        val result =
            calls.call(
                ModelCall(
                    useCase = USE_CASE,
                    variables = mapOf(TEXT to Proposals.neutral(source.text)),
                    names = names,
                    outputClass = CommitmentOutput::class.java,
                    inputReference = source.inputReference,
                    project = project,
                    restricted = source.restricted,
                    summary = { "${it.commitments.size} posibles compromisos propuestos" }
                )
            )
        val text = result.inputs.getValue(TEXT)
        val table = result.table
        val known = lookups.knownTexts(project).toMutableSet()
        val proposed = result.output.commitments
        val drafts =
            proposed.take(properties.limits.proposals).mapNotNull { commitment ->
                val confidence = Proposals.confidence(commitment.confidence) ?: return@mapNotNull null
                if (!CitationCheck.found(commitment.quote, text)) return@mapNotNull null
                val wording = commitment.text.trim().ifEmpty { return@mapNotNull null }
                if (!known.add(Proposals.folded(table.restore(wording)))) return@mapNotNull null
                CandidateDraft(
                    project = project,
                    proposedText = table.stable(wording),
                    evidenceQuote = table.stable(CitationCheck.normalize(commitment.quote)),
                    beneficiariesText =
                        commitment.beneficiaries
                            ?.trim()
                            ?.ifEmpty { null }
                            ?.let { table.stable(it) },
                    suggestedDueDate = Proposals.date(commitment.dueDate),
                    confidence = confidence,
                    interaction = result.interaction,
                    sourceEvidence = source.evidence,
                    engagement = source.engagement
                )
            }
        return RunProposals(candidates = drafts, interactions = listOf(result.interaction), discarded = proposed.size - drafts.size)
    }

    companion object {
        const val USE_CASE = "AI-03"
        const val TEXT = "text"

        fun projectRequired() =
            Finding(
                "PROJECT_REQUIRED",
                "Un candidato a compromiso es de un proyecto: la fuente no lo tiene",
                AiErrors.TARGET,
                status = HttpStatus.BAD_REQUEST
            ).toCodedException()
    }
}
