package sgspe.ai

import org.springframework.stereotype.Component
import sgspe.common.Permissions
import sgspe.common.ServiceAccounts
import wasichai.core.common.Actions
import wasichai.core.common.ForbiddenException
import wasichai.core.common.NotFoundException
import wasichai.core.common.WasichaiException
import wasichai.core.identity.AuthenticatedUser

// un caso de IA que el portal puede ofrecer (useCases de GET /api/sgspe/ai/status): cada caso dice, con los permisos de quien
// mira, si lo ofrece. 7.2a registra AI-05; 7.2b y 7.2c suman un bean por caso. ningún nombre de rol: decide el permiso
// (especificación de F7 §2.2.1, decisión 9 del plan 7.2a)
interface AiUseCaseOffer {
    val useCase: String

    suspend fun offered(user: AuthenticatedUser): Boolean
}

// AI-05, el asistente (ASM-130, ai-architecture.md §8): toda persona con CREATE sobre ai_interaction; una cuenta de servicio, no.
// en un tenant que todavía no tiene el modelo de F7, ai_interaction no existe y el asistente no se ofrece
@Component
class AssistantUseCase(
    private val permissions: Permissions
) : AiUseCaseOffer {
    override val useCase: String = USE_CASE

    override suspend fun offered(user: AuthenticatedUser): Boolean = decide(ServiceAccounts.isServiceAccount(user), canCreateInteraction(user)) == null

    // 403 si no puede preguntar: PERSON_REQUIRED a una cuenta de servicio, o el permiso que le falta
    suspend fun require(user: AuthenticatedUser) {
        decide(ServiceAccounts.isServiceAccount(user), canCreateInteraction(user))?.let { throw it }
    }

    private suspend fun canCreateInteraction(user: AuthenticatedUser): Boolean =
        try {
            permissions.has(user, Actions.CREATE, Interactions.OBJECT)
        } catch (_: NotFoundException) {
            false
        }

    companion object {
        const val USE_CASE = "AI-05"

        fun decide(
            serviceAccount: Boolean,
            canCreate: Boolean
        ): WasichaiException? =
            when {
                serviceAccount -> AiErrors.personRequired()
                !canCreate -> ForbiddenException("El asistente exige permiso de creación sobre ai_interaction")
                else -> null
            }
    }
}
