package sgspe.ai

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import sgspe.common.Finding
import sgspe.common.Permissions
import sgspe.common.Readability
import sgspe.common.ServiceAccounts
import sgspe.common.toCodedException
import wasichai.core.common.Actions
import wasichai.core.common.ForbiddenException
import wasichai.core.common.NotFoundException
import wasichai.core.common.WasichaiException
import wasichai.core.data.RecordResponse
import wasichai.core.identity.AuthenticatedUser
import wasichai.core.metadata.MetadataService
import java.util.UUID

// lo que devuelve un caso: la ai_interaction de su primera llamada (null si no llamó al modelo), lo que guardó, como lo devuelve la
// API genérica, las ai_interaction de todas sus llamadas y cuántas propuestas descartó la validación (solo el conteo, para el log)
data class RunResult(
    val interaction: UUID?,
    val suggestions: List<RecordResponse>,
    val candidates: List<RecordResponse>,
    val interactions: List<UUID> = listOfNotNull(interaction),
    val discarded: Int = 0
)

// un caso de POST /api/sgspe/ai/runs (especificación de F7 §3.4), el punto de extensión de AiRuns: un bean por caso, elegido por
// `useCase`, con los objetos sobre los que se pide. AiRuns comprueba el cuerpo, la activación y `require` (antes que el proveedor:
// un 403 no depende de él) y llama a `run`. 7.2b registra AI-01, AI-02, AI-03, AI-04 y AI-08; 7.2c suma AI-07 sin tocar AiRuns
interface AiRunCase {
    val useCase: String
    val objects: Set<String>

    // lo que el caso exige de quien pide antes de llamar al modelo (403); por defecto, nada: el caso lo comprueba en `run`
    suspend fun require(
        user: AuthenticatedUser,
        target: RunTarget
    ) {}

    suspend fun run(
        user: AuthenticatedUser,
        target: RunTarget
    ): RunResult
}

// lo que exige una corrida antes de llamar al modelo (api.md §4.14, ai-architecture.md §9.1 y §12.2): una persona, CREATE sobre
// ai_interaction y sobre los objetos de salida, READ sobre la fuente y leer los nombres de person, que la seudonimización necesita.
// sin ellos, 403 antes de llamar. nunca un nombre de rol: decide el permiso (§2.2.1)
@Component
class RunRequirements(
    private val permissions: Permissions,
    private val metadata: MetadataService
) {
    suspend fun check(
        user: AuthenticatedUser,
        outputs: List<String>,
        source: String
    ): WasichaiException? {
        if (ServiceAccounts.isServiceAccount(user)) return AiErrors.personRequired()
        val names = Readability(metadata)
        return decide(
            serviceAccount = false,
            createsInteraction = has(user, Actions.CREATE, Interactions.OBJECT),
            missingOutputs = outputs.filterNot { has(user, Actions.CREATE, it) },
            readsSource = has(user, Actions.READ, source),
            readsNames = names.canRead("person.given_names") && names.canRead("person.surnames")
        )
    }

    // un objeto que el tenant todavía no tiene (sin el modelo de F7) no da permiso
    private suspend fun has(
        user: AuthenticatedUser,
        action: String,
        objectName: String
    ): Boolean =
        try {
            permissions.has(user, action, objectName)
        } catch (_: NotFoundException) {
            false
        }

    companion object {
        fun decide(
            serviceAccount: Boolean,
            createsInteraction: Boolean,
            missingOutputs: List<String>,
            readsSource: Boolean,
            readsNames: Boolean
        ): WasichaiException? =
            when {
                serviceAccount -> AiErrors.personRequired()
                !createsInteraction -> ForbiddenException("La IA exige permiso de creación sobre ai_interaction")
                missingOutputs.isNotEmpty() -> ForbiddenException("Este caso de IA exige permiso de creación sobre ${missingOutputs.joinToString(" y ")}")
                !readsSource -> ForbiddenException("Este caso de IA exige permiso de lectura sobre la fuente")
                !readsNames ->
                    Finding(
                        "FIELD_NOT_READABLE",
                        "La seudonimización necesita leer los nombres de person: la corrida la pide quien puede leerlos",
                        "person.given_names",
                        status = HttpStatus.FORBIDDEN
                    ).toCodedException()
                else -> null
            }
    }
}

// lo común de los casos de 7.2b: sus requisitos (RunRequirements) sobre sus objetos de salida, que también deciden si GET
// /ai/status lo ofrece (sobre alguno de sus objetos); la corrida, con el diccionario de nombres de quien pide (PersonNames) y el tope
// de llamadas (sgspe.ai.limits.actions), y lo validado guardado en una transacción (SuggestionWriter)
abstract class RequiredCase(
    private val requirements: RunRequirements,
    private val names: PersonNames,
    private val writer: SuggestionWriter,
    private val properties: AiProperties
) : AiRunCase,
    AiUseCaseOffer {
    abstract val outputs: List<String>

    override suspend fun offered(user: AuthenticatedUser): Boolean = objects.any { requirements.check(user, outputs, it) == null }

    override suspend fun require(
        user: AuthenticatedUser,
        target: RunTarget
    ) {
        requirements.check(user, outputs, target.objectName)?.let { throw it }
    }

    override suspend fun run(
        user: AuthenticatedUser,
        target: RunTarget
    ): RunResult {
        val proposals = propose(target, names.readable(), RunBudget(properties.limits.actions), user)
        val written = writer.write(useCase, proposals)
        return RunResult(proposals.interactions.firstOrNull(), written.suggestions, written.candidates, proposals.interactions, proposals.discarded)
    }

    protected abstract suspend fun propose(
        target: RunTarget,
        names: NameDictionary,
        budget: RunBudget,
        user: AuthenticatedUser
    ): RunProposals
}

// AI-01 sobre una evidencia o la minuta de una interacción: los hallazgos y, si quien pide crea candidatos y la fuente es de un
// proyecto, los posibles compromisos de AI-03 en la misma corrida (dos llamadas)
@Component
class ExtractionCase(
    requirements: RunRequirements,
    names: PersonNames,
    writer: SuggestionWriter,
    properties: AiProperties,
    private val sources: RunSources,
    private val extraction: ExtractionAgent,
    private val commitments: CommitmentAgent,
    private val permissions: Permissions
) : RequiredCase(requirements, names, writer, properties) {
    override val useCase = ExtractionAgent.USE_CASE
    override val objects = setOf(RunSources.EVIDENCE, RunSources.ENGAGEMENT)
    override val outputs = listOf(SuggestionWriter.SUGGESTION)

    override suspend fun propose(
        target: RunTarget,
        names: NameDictionary,
        budget: RunBudget,
        user: AuthenticatedUser
    ): RunProposals {
        val source = if (target.objectName == RunSources.EVIDENCE) sources.evidenceText(target.recordId) else sources.minutes(target.recordId)
        val found = extraction.run(source, names, budget)
        val withCandidates = source.project != null && permissions.has(user, Actions.CREATE, SuggestionWriter.CANDIDATE)
        return if (withCandidates) found + commitments.run(source, names, budget) else found
    }
}

// AI-02: el borrador del acta de una interacción
@Component
class MinutesCase(
    requirements: RunRequirements,
    names: PersonNames,
    writer: SuggestionWriter,
    properties: AiProperties,
    private val sources: RunSources,
    private val drafts: DraftAgents
) : RequiredCase(requirements, names, writer, properties) {
    override val useCase = DraftAgents.MINUTES_CASE
    override val objects = setOf(RunSources.ENGAGEMENT)
    override val outputs = listOf(SuggestionWriter.SUGGESTION)

    override suspend fun propose(
        target: RunTarget,
        names: NameDictionary,
        budget: RunBudget,
        user: AuthenticatedUser
    ): RunProposals = drafts.minutes(sources.engagementNotes(target.recordId), names, budget)
}

// AI-03 sobre un acta: una evidencia o la minuta de una interacción
@Component
class CommitmentCase(
    requirements: RunRequirements,
    names: PersonNames,
    writer: SuggestionWriter,
    properties: AiProperties,
    private val sources: RunSources,
    private val commitments: CommitmentAgent
) : RequiredCase(requirements, names, writer, properties) {
    override val useCase = CommitmentAgent.USE_CASE
    override val objects = setOf(RunSources.EVIDENCE, RunSources.ENGAGEMENT)
    override val outputs = listOf(SuggestionWriter.CANDIDATE)

    override suspend fun propose(
        target: RunTarget,
        names: NameDictionary,
        budget: RunBudget,
        user: AuthenticatedUser
    ): RunProposals {
        val source = if (target.objectName == RunSources.EVIDENCE) sources.evidenceText(target.recordId) else sources.minutes(target.recordId)
        return commitments.run(source, names, budget)
    }
}

// AI-04, solo el resumen ejecutivo de un proyecto (F7); los borradores de sección llegan en F8
@Component
class SummaryCase(
    requirements: RunRequirements,
    names: PersonNames,
    writer: SuggestionWriter,
    properties: AiProperties,
    private val sources: RunSources,
    private val drafts: DraftAgents
) : RequiredCase(requirements, names, writer, properties) {
    override val useCase = DraftAgents.SUMMARY_CASE
    override val objects = setOf(RunSources.PROJECT)
    override val outputs = listOf(SuggestionWriter.SUGGESTION)

    override suspend fun propose(
        target: RunTarget,
        names: NameDictionary,
        budget: RunBudget,
        user: AuthenticatedUser
    ): RunProposals = drafts.summary(sources.projectSummary(target.recordId), names, budget)
}

// AI-08 sobre una QCS, una pregunta o una mención
@Component
class ClassificationCase(
    requirements: RunRequirements,
    names: PersonNames,
    writer: SuggestionWriter,
    properties: AiProperties,
    private val sources: RunSources,
    private val classification: ClassificationAgent
) : RequiredCase(requirements, names, writer, properties) {
    override val useCase = ClassificationAgent.USE_CASE
    override val objects = ClassificationAgent.FIELDS.keys
    override val outputs = listOf(SuggestionWriter.SUGGESTION)

    override suspend fun propose(
        target: RunTarget,
        names: NameDictionary,
        budget: RunBudget,
        user: AuthenticatedUser
    ): RunProposals = classification.run(sources.classification(target.objectName, target.recordId), names, budget)
}
