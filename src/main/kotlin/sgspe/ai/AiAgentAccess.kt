package sgspe.ai

import org.springframework.stereotype.Component
import wasichai.agent.AgentAccess
import wasichai.agent.AgentAccessPolicy
import wasichai.core.identity.AuthenticatedUser

// la AgentAccessPolicy de SGSPE (ASM-130, ai-architecture.md §8, WAS-ADR-0056): el asistente de wasichai solo en una organización
// con la IA activada y para una persona a quien AssistantUseCase se lo ofrece (CREATE sobre ai_interaction). GET /api/agent/status
// dice enabled=false a los demás, y AgentService.ask los rechaza con 403 antes de enviar nada. sin literales de rol. al ser el
// único bean de su tipo, el agentAccessPolicy de wasichai (ALLOW_ALL, @ConditionalOnMissingBean) no se crea
@Component
class AiAgentAccess(
    private val activation: AiActivation,
    private val assistant: AssistantUseCase
) : AgentAccessPolicy {
    override suspend fun check(caller: AuthenticatedUser): AgentAccess = decide(activation.organizationEnabled(), assistant.offered(caller))

    companion object {
        const val NOT_ENABLED = "La IA no está activada para su organización"
        const val NOT_OFFERED = "El asistente es para las personas con permiso de creación sobre ai_interaction"

        fun decide(
            enabled: Boolean,
            offered: Boolean
        ): AgentAccess =
            when {
                !enabled -> AgentAccess.Denied(NOT_ENABLED)
                !offered -> AgentAccess.Denied(NOT_OFFERED)
                else -> AgentAccess.Allowed
            }
    }
}
