package sgspe.ai

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus

// lo que exige una corrida antes de llamar al modelo (api.md §4.14, ai-architecture.md §12.2): una persona, CREATE sobre
// ai_interaction y las salidas, READ sobre la fuente y los nombres de person; el primero que falta decide el 403
class RunRequirementsTest {
    private fun decide(
        serviceAccount: Boolean = false,
        createsInteraction: Boolean = true,
        missingOutputs: List<String> = emptyList(),
        readsSource: Boolean = true,
        readsNames: Boolean = true
    ) = RunRequirements.decide(serviceAccount, createsInteraction, missingOutputs, readsSource, readsNames)

    @Test
    fun `quien cumple todo pide la corrida`() {
        assertNull(decide())
    }

    @Test
    fun `una cuenta de servicio es PERSON_REQUIRED aunque tenga los permisos`() {
        val refused = decide(serviceAccount = true)!!
        assertEquals(HttpStatus.FORBIDDEN, refused.status)
        assertEquals("PERSON_REQUIRED", refused.violations.single().message)
    }

    @Test
    fun `cada permiso que falta es un 403 que lo nombra`() {
        assertTrue("ai_interaction" in decide(createsInteraction = false)!!.message.orEmpty())
        assertTrue("commitment_candidate" in decide(missingOutputs = listOf("commitment_candidate"))!!.message.orEmpty())
        assertTrue("fuente" in decide(readsSource = false)!!.message.orEmpty())
    }

    @Test
    fun `quien no lee los nombres de person no pide corridas`() {
        val refused = decide(readsNames = false)!!
        assertEquals(HttpStatus.FORBIDDEN, refused.status)
        assertEquals("person.given_names" to "FIELD_NOT_READABLE", refused.violations.single().let { it.field to it.message })
    }
}
