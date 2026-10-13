package sgspe.ai

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import wasichai.agent.AgentProperties

// la activación de la IA (ASM-128, ai-architecture.md §12.1) y sus errores (api.md §3.3), sin base ni contexto
class AiActivationTest {
    @Test
    fun `sin clave, con el modulo apagado o sin plataforma no hay proveedor`() {
        assertFalse(AiActivation.providerAvailable(null, true))
        assertFalse(AiActivation.providerAvailable(AgentProperties(apiKey = ""), true))
        assertFalse(AiActivation.providerAvailable(AgentProperties(enabled = false, apiKey = "clave-de-prueba"), true))
        assertFalse(AiActivation.providerAvailable(AgentProperties(apiKey = "clave-de-prueba"), false))
        assertTrue(AiActivation.providerAvailable(AgentProperties(apiKey = "clave-de-prueba"), true))
    }

    @Test
    fun `la IA viene apagada y se activa por slug`() {
        assertFalse(AiProperties().enabled("demo"))
        val properties = AiProperties(enabledOrganizations = listOf("demo", " otra ", ""))
        assertTrue(properties.enabled("demo"))
        assertTrue(properties.enabled("otra"))
        assertFalse(properties.enabled("tercera"))
        assertFalse(properties.enabled(""))
        assertFalse(properties.enabled(null))
    }

    @Test
    fun `cada error lleva su estado, su codigo y su campo`() {
        val organization = AiErrors.unavailable(AiErrors.ORGANIZATION)
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, organization.status)
        assertEquals("organization" to "AI_UNAVAILABLE", organization.violations.single().let { it.field to it.message })
        assertEquals(
            "provider" to "AI_UNAVAILABLE",
            AiErrors
                .unavailable(AiErrors.PROVIDER)
                .violations
                .single()
                .let { it.field to it.message }
        )
        val tooLarge = AiErrors.inputTooLarge("question", 4_000)
        assertEquals(422, tooLarge.status.value())
        assertEquals("question" to "AI_INPUT_TOO_LARGE", tooLarge.violations.single().let { it.field to it.message })
        assertEquals(HttpStatus.BAD_GATEWAY, AiErrors.truncated().status)
        assertEquals(
            "useCase" to "AI_RUN_TRUNCATED",
            AiErrors
                .truncated()
                .violations
                .single()
                .let { it.field to it.message }
        )
        assertEquals(HttpStatus.FORBIDDEN, AiErrors.personRequired().status)
        assertEquals(
            "PERSON_REQUIRED",
            AiErrors
                .personRequired()
                .violations
                .single()
                .message
        )
    }
}
