package sgspe.ai

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ServerWebExchange
import sgspe.common.Callers
import sgspe.common.Permissions
import sgspe.common.Readability
import sgspe.common.StrictBody
import sgspe.common.StrictQuery
import sgspe.evidence.EvidenceRules
import wasichai.core.common.Actions
import wasichai.core.common.ForbiddenException
import wasichai.core.common.NotFoundException
import wasichai.core.common.ValidationException
import wasichai.core.common.WasichaiException
import wasichai.core.identity.AuthenticatedUser
import wasichai.core.metadata.MetadataService
import java.util.UUID

// si quien llama lee los nombres de person (given_names y surnames, restringidos por security.md §7.9): la seudonimización de los
// casos que envían texto los necesita, porque dentro de una petición el diccionario sale de lo que la persona lee
// (ai-architecture.md §12.2). con la definición filtrada por sus permisos (Readability), sin leer ningún registro
@Component
class NameAccess(
    private val metadata: MetadataService
) {
    suspend fun readable(): Boolean = Readability(metadata).missing(FIELDS).isEmpty()

    companion object {
        val FIELDS = listOf("person.given_names", "person.surnames")
    }
}

// AI-06, la pregunta a los documentos (api.md §4.14, ai-architecture.md §13.3): una persona (no una cuenta de servicio) con CREATE
// sobre ai_interaction, READ sobre evidence y la lectura de los nombres de person. ningún nombre de rol: decide el permiso
@Component
class KnowledgeUseCase(
    private val permissions: Permissions,
    private val names: NameAccess
) : AiUseCaseOffer {
    override val useCase: String = KnowledgeAnswers.USE_CASE

    override suspend fun offered(user: AuthenticatedUser): Boolean = check(user) == null

    // 403 si no puede preguntar: PERSON_REQUIRED a una cuenta de servicio, o lo que le falta
    suspend fun require(user: AuthenticatedUser) {
        check(user)?.let { throw it }
    }

    private suspend fun check(user: AuthenticatedUser): WasichaiException? =
        decide(
            user.serviceAccount != null,
            has(user, Actions.CREATE, Interactions.OBJECT),
            has(user, Actions.READ, EvidenceRules.EVIDENCE),
            names.readable()
        )

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
            canCreateInteraction: Boolean,
            canReadEvidence: Boolean,
            readsNames: Boolean
        ): WasichaiException? =
            when {
                serviceAccount -> AiErrors.personRequired()
                !canCreateInteraction -> ForbiddenException("La pregunta a los documentos exige permiso de creación sobre ai_interaction")
                !canReadEvidence -> ForbiddenException("La pregunta a los documentos exige permiso de lectura sobre evidence")
                !readsNames -> ForbiddenException("La pregunta a los documentos exige leer los nombres de person: la seudonimización los necesita")
                else -> null
            }
    }
}

// POST /api/sgspe/ai/knowledge/questions (api.md §4.14, ai-architecture.md §13.3): AI-06 sobre los documentos del proyecto que
// quien pregunta puede leer. en este orden (decisión 8 del plan 7.2c): el cuerpo (400) y el largo de la pregunta (422
// AI_INPUT_TOO_LARGE), la activación de la organización (503 organization), la persona y sus permisos (403), el proveedor (503
// provider), el proyecto (404 si no lo lee) y la recuperación con permisos. sin fragmento que pase los filtros responde que no hay
// evidencia, sin llamar al modelo y sin ai_interaction; con fragmentos, la respuesta con citas de KnowledgeAnswers. el log solo
// lleva el id de la ai_interaction
@RestController
@RequestMapping("/api/sgspe/ai/knowledge/questions")
class KnowledgeQuestions(
    private val callers: Callers,
    private val activation: AiActivation,
    private val knowledge: KnowledgeUseCase,
    private val search: KnowledgeSearch,
    private val names: PersonNames,
    private val answers: KnowledgeAnswers,
    private val properties: AiProperties
) {
    @PostMapping
    suspend fun ask(
        @RequestBody body: Map<String, Any?>,
        exchange: ServerWebExchange
    ): KnowledgeResponse {
        StrictQuery.requireOnly(exchange.request.queryParams, emptySet())
        val (question, project) = parse(body, properties.limits.questionChars)
        val user = callers.require()
        activation.requireOrganization()
        knowledge.require(user)
        activation.requireProvider()
        val fragments = search.search(user, project, question)
        if (fragments.isEmpty()) return KnowledgeAnswers.noEvidence()
        val response = answers.answer(question, project, fragments, names.readable())
        LOG.info("AI-06 respondió; ai_interaction {}", response.interaction)
        return response
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(KnowledgeQuestions::class.java)
        const val QUESTION = "question"
        const val PROJECT = "project"

        // 400 si falta algo, no es del tipo, está en blanco o el cuerpo trae otra propiedad; 422 AI_INPUT_TOO_LARGE si la pregunta
        // pasa el tope (el mismo de assistant/questions, sgspe.ai.limits.question-chars)
        fun parse(
            body: Map<String, Any?>,
            limit: Int
        ): Pair<String, UUID> {
            StrictBody.requireOnly(body, setOf(QUESTION, PROJECT))
            val raw = body[QUESTION]
            if (raw != null && raw !is String) throw ValidationException("Cuerpo inválido", QUESTION, "debe ser un texto")
            val question = raw?.trim().orEmpty()
            if (question.isEmpty()) throw ValidationException("Cuerpo inválido", QUESTION, "es obligatoria")
            val project =
                (body[PROJECT] as? String)?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                    ?: throw ValidationException("Cuerpo inválido", PROJECT, "es obligatorio: el id de un proyecto")
            if (question.length > limit) throw AiErrors.inputTooLarge(QUESTION, limit)
            return question to project
        }
    }
}
