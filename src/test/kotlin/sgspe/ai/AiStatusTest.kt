package sgspe.ai

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import wasichai.core.identity.AuthenticatedUser
import java.util.UUID

// GET /api/sgspe/ai/status (api.md §4.14) y la oferta del asistente (ASM-130), sin base: el estado lo arman la activación, el
// proveedor y los casos que cada persona puede usar
class AiStatusTest {
    private val user = AuthenticatedUser(UUID.randomUUID(), UUID.randomUUID(), "persona@sgspe.test", emptyList())

    private fun offer(
        case: String,
        yes: Boolean
    ) = object : AiUseCaseOffer {
        override val useCase = case

        override suspend fun offered(user: AuthenticatedUser) = yes
    }

    @Test
    fun `una organizacion sin IA no ofrece casos aunque haya proveedor`() {
        assertEquals(AiStatusResponse(false, true, emptyList()), runBlocking { AiStatus.compose(false, true, user, listOf(offer("AI-05", true))) })
    }

    @Test
    fun `sin proveedor los casos se siguen diciendo`() {
        assertEquals(AiStatusResponse(true, false, listOf("AI-05")), runBlocking { AiStatus.compose(true, false, user, listOf(offer("AI-05", true))) })
    }

    @Test
    fun `solo los casos que ofrecen sus permisos, en orden`() {
        val offers = listOf(offer("AI-05", true), offer("AI-06", false), offer("AI-03", true))
        assertEquals(listOf("AI-03", "AI-05"), runBlocking { AiStatus.compose(true, true, user, offers) }.useCases)
    }

    @Test
    fun `el asistente es de las personas con CREATE sobre ai_interaction`() {
        assertNull(AssistantUseCase.decide(serviceAccount = false, canCreate = true))
        assertEquals(
            "PERSON_REQUIRED",
            AssistantUseCase
                .decide(serviceAccount = true, canCreate = true)!!
                .violations
                .single()
                .message
        )
        val missing = AssistantUseCase.decide(serviceAccount = false, canCreate = false)!!
        assertEquals(HttpStatus.FORBIDDEN, missing.status)
        assertTrue("ai_interaction" in missing.message.orEmpty(), missing.message)
    }
}
