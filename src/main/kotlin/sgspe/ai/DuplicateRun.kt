package sgspe.ai

import org.springframework.stereotype.Component
import sgspe.common.Permissions
import sgspe.common.ServiceAccounts
import tools.jackson.databind.json.JsonMapper
import wasichai.core.common.Actions
import wasichai.core.common.ForbiddenException
import wasichai.core.common.NotFoundException
import wasichai.core.common.WasichaiException
import wasichai.core.identity.AuthenticatedUser

// AI-07, los posibles duplicados (ai-architecture.md §4.1, api.md §4.14): una persona (no una cuenta de servicio) con CREATE sobre
// ai_interaction y sobre ai_suggestion y la lectura de los nombres de person. ningún nombre de rol: decide el permiso
@Component
class DuplicateUseCase(
    private val permissions: Permissions,
    private val names: NameAccess
) : AiUseCaseOffer {
    override val useCase: String = DuplicateDetection.USE_CASE

    override suspend fun offered(user: AuthenticatedUser): Boolean = check(user) == null

    // 403 si no puede pedirlo: PERSON_REQUIRED a una cuenta de servicio, o lo que le falta
    suspend fun require(user: AuthenticatedUser) {
        check(user)?.let { throw it }
    }

    private suspend fun check(user: AuthenticatedUser): WasichaiException? =
        decide(
            ServiceAccounts.isServiceAccount(user),
            has(user, Interactions.OBJECT) && has(user, DuplicateDetection.SUGGESTION),
            names.readable()
        )

    // un objeto que el tenant todavía no tiene (sin el modelo de F7) no da permiso
    private suspend fun has(
        user: AuthenticatedUser,
        objectName: String
    ): Boolean =
        try {
            permissions.has(user, Actions.CREATE, objectName)
        } catch (_: NotFoundException) {
            false
        }

    companion object {
        fun decide(
            serviceAccount: Boolean,
            canCreate: Boolean,
            readsNames: Boolean
        ): WasichaiException? =
            when {
                serviceAccount -> AiErrors.personRequired()
                !canCreate -> ForbiddenException("Buscar duplicados exige permiso de creación sobre ai_interaction y ai_suggestion")
                !readsNames -> ForbiddenException("Buscar duplicados exige leer los nombres de person: la seudonimización los necesita")
                else -> null
            }
    }
}

// AI-07 como caso de POST /api/sgspe/ai/runs (plan 7.2b): sobre un proyecto o un registro de los objetos que compara,
// DuplicateDetection arma los pares, el modelo los compara y SuggestionWriter guarda una ai_suggestion DUPLICADO por par en proposed
// (WF-K), como la persona y con la marca, con suggested_value {other_record_id, reasons[]}. lo que exige de quien pide va en
// `require`, antes que el proveedor (AiRuns). sin pares no llama al modelo y no guarda nada (interaction null). nunca fusiona ni
// vincula: la aceptación de una persona decide (RQ-AI-015)
@Component
class DuplicateRun(
    private val detection: DuplicateDetection,
    private val writer: SuggestionWriter,
    private val offer: DuplicateUseCase,
    private val json: JsonMapper
) : AiRunCase {
    override val useCase: String = DuplicateDetection.USE_CASE

    override val objects: Set<String> = DuplicateCandidates.OBJECTS + DuplicateRecords.PROJECT

    override suspend fun require(
        user: AuthenticatedUser,
        target: RunTarget
    ) {
        offer.require(user)
    }

    override suspend fun run(
        user: AuthenticatedUser,
        target: RunTarget
    ): RunResult {
        val findings = detection.detect(DuplicateTarget(target.objectName, target.recordId))
        val interaction = findings.interaction ?: return RunResult(null, emptyList(), emptyList())
        val written =
            findings.proposals.map { proposal ->
                writer.suggestion(
                    SuggestionDraft(
                        kind = DuplicateDetection.KIND,
                        targetObject = proposal.objectName,
                        targetRecordId = proposal.targetRecordId,
                        suggestedValue = proposal.suggestedValue(json),
                        evidenceQuote = null,
                        confidence = proposal.confidence,
                        model = findings.model,
                        project = proposal.project,
                        interaction = interaction
                    )
                )
            }
        return RunResult(interaction, written, emptyList())
    }
}
