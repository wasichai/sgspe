package sgspe.ai

import com.embabel.agent.core.AgentPlatform
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Component
import wasichai.agent.AgentProperties
import wasichai.core.organization.OrganizationService

// si la IA se puede usar (ai-architecture.md §6.2 y §12.1, ASM-128): la organización de quien llama está en
// sgspe.ai.enabled-organizations por su slug, y hay proveedor: wasichai-agent encendido con su clave y la plataforma de agentes en
// el contexto. sin clave, EmbabelGate deja fuera a Embabel y la plataforma no existe; por eso llega por ObjectProvider, como
// AgentProperties (que falta con wasichai.agent.enabled=false)
@Component
class AiActivation(
    private val properties: AiProperties,
    private val organizations: OrganizationService,
    private val agent: ObjectProvider<AgentProperties>,
    private val platform: ObjectProvider<AgentPlatform>
) {
    suspend fun organizationEnabled(): Boolean = properties.enabled(organizations.current().slug)

    fun providerAvailable(): Boolean = providerAvailable(agent.ifAvailable, platform.ifAvailable != null)

    // 503 AI_UNAVAILABLE sobre organization
    suspend fun requireOrganization() {
        if (!organizationEnabled()) throw AiErrors.unavailable(AiErrors.ORGANIZATION)
    }

    // 503 AI_UNAVAILABLE sobre provider
    fun requireProvider() {
        if (!providerAvailable()) throw AiErrors.unavailable(AiErrors.PROVIDER)
    }

    companion object {
        fun providerAvailable(
            agent: AgentProperties?,
            platform: Boolean
        ): Boolean = agent != null && agent.available && platform
    }
}
