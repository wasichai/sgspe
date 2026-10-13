package sgspe.ai

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import sgspe.common.Finding
import sgspe.common.NotReadable
import sgspe.common.Records
import sgspe.common.Transactions
import sgspe.common.Values
import sgspe.common.readable
import sgspe.common.toCodedException
import sgspe.scope.ProjectScope
import sgspe.scope.linkedProject
import wasichai.core.data.RecordResponse

// lo que guardó una corrida: las ai_suggestion y los commitment_candidate creados, como los devuelve la API genérica
data class Written(
    val suggestions: List<RecordResponse>,
    val candidates: List<RecordResponse>
)

// el escritor de sugerencias de sgspe.ai (ai-architecture.md §9.1, especificación de F7 §3.4): guarda lo que ya validaron los
// agentes con Records (RecordService con la marca SgspeWrite) como la persona que pidió la corrida, así que las guardas de los dos
// objetos se aplican (CandidateLinkRule y ProjectLinkGuard: «mismo proyecto», también para su ai_interaction). antes de cada
// sugerencia vuelve a leer el destino como la persona (§6.18 del modelo de dominio): uno que no existe o no se lee, o que es de
// otro proyecto que la sugerencia, no se guarda; y una sugerencia que repite una propuesta igual (mismo tipo, destino y valor) no se
// guarda otra vez. `write` guarda una corrida entera en una transacción y deja fuera esas; `suggestion` guarda una sola (la usa
// AI-07, plan 7.2c) y las rechaza: 403 OBJECT_NOT_READABLE o 409 PROJECT_MISMATCH sobre target_record_id, y la repetida vuelve tal
// como está. nunca escribe un dato de dominio: solo los dos objetos de salida
@Component
class SuggestionWriter(
    private val records: Records,
    private val transactions: Transactions
) {
    suspend fun write(
        useCase: String,
        proposals: RunProposals
    ): Written =
        transactions.run {
            val reason = "propuesta de la IA ($useCase)"
            Written(
                proposals.suggestions.mapNotNull { draft ->
                    if (verdict(draft) == Verdict.NEW) records.create(SUGGESTION, attributes(draft), reason) else null
                },
                proposals.candidates.map { records.create(CANDIDATE, attributes(it), reason) }
            )
        }

    suspend fun suggestion(draft: SuggestionDraft): RecordResponse =
        when (verdict(draft)) {
            Verdict.NEW -> records.create(SUGGESTION, attributes(draft), "propuesta de la IA (${draft.kind})")
            Verdict.REPEATED -> repeated(draft)!!
            Verdict.UNREADABLE ->
                throw Finding(
                    "OBJECT_NOT_READABLE",
                    "El registro destino no existe o quien pide la corrida no puede leerlo",
                    TARGET,
                    status = HttpStatus.FORBIDDEN
                ).toCodedException()
            Verdict.OTHER_PROJECT -> throw Finding("PROJECT_MISMATCH", "El registro destino es de otro proyecto que la sugerencia", TARGET).toCodedException()
        }

    private enum class Verdict { NEW, REPEATED, UNREADABLE, OTHER_PROJECT }

    private suspend fun verdict(draft: SuggestionDraft): Verdict {
        val id = draft.targetRecordId
        if (id != null) {
            val owner =
                try {
                    when {
                        draft.targetObject == PROJECT -> records.readable(PROJECT, id).let { id }
                        ProjectScope.covers(draft.targetObject) -> records.linkedProject(draft.targetObject, id)
                        else -> records.readable(draft.targetObject, id).let { null }
                    }
                } catch (_: NotReadable) {
                    return Verdict.UNREADABLE
                }
            if (draft.project != null && owner != null && owner != draft.project) return Verdict.OTHER_PROJECT
        }
        return if (repeated(draft) != null) Verdict.REPEATED else Verdict.NEW
    }

    // la sugerencia propuesta con el mismo tipo, destino y valor que ya espera decisión, o null
    private suspend fun repeated(draft: SuggestionDraft): RecordResponse? {
        val filters =
            listOfNotNull(
                "suggestion_kind" to draft.kind,
                "target_object" to draft.targetObject,
                draft.targetRecordId?.let { "target_record_id" to it.toString() },
                draft.project?.let { "project" to it.toString() }
            ).toMap()
        return records.all(SUGGESTION, filters).firstOrNull { it.state == PROPOSED && Values.text(it.attributes["suggested_value"]) == draft.suggestedValue }
    }

    companion object {
        const val SUGGESTION = "ai_suggestion"
        const val CANDIDATE = "commitment_candidate"
        const val PROJECT = "project"
        const val PROPOSED = "proposed"
        const val TARGET = "target_record_id"

        fun attributes(draft: SuggestionDraft): Map<String, Any?> =
            mapOf(
                "suggestion_kind" to draft.kind,
                "target_object" to draft.targetObject,
                "target_record_id" to draft.targetRecordId?.toString(),
                "suggested_value" to draft.suggestedValue,
                "evidence_quote" to draft.evidenceQuote,
                "confidence" to draft.confidence,
                "model" to draft.model,
                "ai_interaction" to draft.interaction.toString(),
                PROJECT to draft.project?.toString()
            ).filterValues { it != null }

        fun attributes(draft: CandidateDraft): Map<String, Any?> =
            mapOf(
                PROJECT to draft.project.toString(),
                "proposed_text" to draft.proposedText,
                "evidence_quote" to draft.evidenceQuote,
                "beneficiaries_text" to draft.beneficiariesText,
                "suggested_due_date" to draft.suggestedDueDate?.toString(),
                "confidence" to draft.confidence,
                "ai_generated" to true,
                "ai_interaction" to draft.interaction.toString(),
                "source_evidence" to draft.sourceEvidence?.toString(),
                "engagement" to draft.engagement?.toString()
            ).filterValues { it != null }
    }
}
