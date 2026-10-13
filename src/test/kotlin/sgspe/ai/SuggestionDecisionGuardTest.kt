package sgspe.ai

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.http.HttpStatus
import sgspe.common.Callers
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordWrite
import wasichai.core.identity.AuthenticatedUser
import java.util.UUID

// WF-K de ai_suggestion lo decide una persona (RQ-AI-003): una cuenta de servicio no acepta, rechaza ni marca duplicada (decisión 9
// del plan 7.2b)
class SuggestionDecisionGuardTest {
    @Test
    fun `una persona decide`() {
        assertTrue(
            SuggestionDecisionGuard.decide(AuthenticatedUser(UUID.randomUUID(), UUID.randomUUID(), "smg@sgspe.test", listOf("SOCIAL_MANAGER"))).isEmpty()
        )
    }

    @Test
    fun `una cuenta de servicio no, por su marca o por su correo de respaldo`() {
        val marked = AuthenticatedUser(UUID.randomUUID(), UUID.randomUUID(), "integracion@sgspe.test", listOf("SOCIAL_MANAGER"), "integracion")
        val backup = AuthenticatedUser(UUID.randomUUID(), UUID.randomUUID(), "${UUID.randomUUID()}@service-accounts.invalid", listOf("SOCIAL_MANAGER"))
        for (user in listOf(marked, backup)) {
            val finding = SuggestionDecisionGuard.decide(user).single()
            assertEquals("PERSON_REQUIRED" to HttpStatus.FORBIDDEN, finding.code to finding.status)
        }
    }

    @Test
    fun `sin usuario no se juzga`() {
        assertTrue(SuggestionDecisionGuard.decide(null).isEmpty())
    }

    @Test
    fun `una escritura de la plataforma no se juzga aunque la peticion sea de una cuenta de servicio`() {
        val service = AuthenticatedUser(UUID.randomUUID(), UUID.randomUUID(), "integracion@sgspe.test", listOf("SOCIAL_MANAGER"), "integracion")
        val guard = SuggestionDecisionGuard(mock(Callers::class.java) { service })
        val change =
            RecordWrite(
                UUID.randomUUID(),
                null,
                UUID.randomUUID(),
                SuggestionWriter.SUGGESTION,
                UUID.randomUUID(),
                RecordChangeKind.TRANSITIONED,
                null,
                null,
                "reject"
            )
        assertTrue(runBlocking { guard.findings(change, dryRun = false) }.isEmpty())
        val asService = change.copy(userId = service.userId)
        assertEquals("PERSON_REQUIRED", runBlocking { guard.findings(asService, dryRun = false) }.single().code)
    }
}
