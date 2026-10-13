package sgspe.ai

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import wasichai.agent.AgentAccess

// la AgentAccessPolicy de SGSPE (ASM-130, ai-architecture.md §8): organización activada y persona a quien se le ofrece el
// asistente; si no, Denied con la razón
class AiAgentAccessTest {
    @Test
    fun `sin IA en la organizacion o sin la oferta, el asistente se niega con su razon`() {
        assertEquals(AgentAccess.Allowed, AiAgentAccess.decide(enabled = true, offered = true))
        assertEquals(AgentAccess.Denied(AiAgentAccess.NOT_ENABLED), AiAgentAccess.decide(enabled = false, offered = true))
        assertEquals(AgentAccess.Denied(AiAgentAccess.NOT_ENABLED), AiAgentAccess.decide(enabled = false, offered = false))
        assertEquals(AgentAccess.Denied(AiAgentAccess.NOT_OFFERED), AiAgentAccess.decide(enabled = true, offered = false))
    }
}
