package sgspe.common

import wasichai.core.identity.AuthenticatedUser

// una cuenta de servicio, por su marca o por su correo de respaldo (WAS-ADR-0043): las decisiones que solo toma una persona
// (confirmar una alerta, decidir una sugerencia o un candidato de IA, pedir una corrida) la reconocen con esta sola regla
object ServiceAccounts {
    const val DOMAIN = "@service-accounts.invalid"

    fun isServiceAccount(user: AuthenticatedUser): Boolean = user.serviceAccount != null || user.email.endsWith(DOMAIN)
}
