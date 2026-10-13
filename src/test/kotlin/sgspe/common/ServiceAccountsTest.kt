package sgspe.common

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import wasichai.core.identity.AuthenticatedUser
import java.util.UUID

// una cuenta de servicio se reconoce por su marca o por su correo de respaldo (WAS-ADR-0043): una sola regla para las
// decisiones que solo toma una persona (alertas, sugerencias y candidatos de IA, corridas)
class ServiceAccountsTest {
    private fun user(
        email: String,
        service: String? = null
    ) = AuthenticatedUser(UUID.randomUUID(), UUID.randomUUID(), email, listOf("SOCIAL_MANAGER"), service)

    @Test
    fun `una persona no es cuenta de servicio`() {
        assertFalse(ServiceAccounts.isServiceAccount(user("smg@sgspe.test")))
    }

    @Test
    fun `una cuenta de servicio, por su marca o por su correo de respaldo`() {
        assertTrue(ServiceAccounts.isServiceAccount(user("integracion@sgspe.test", "integracion")))
        assertTrue(ServiceAccounts.isServiceAccount(user("${UUID.randomUUID()}${ServiceAccounts.DOMAIN}")))
        assertTrue(ServiceAccounts.DOMAIN == "@service-accounts.invalid")
    }
}
