package sgspe.ai

import kotlinx.coroutines.reactor.asCoroutineContext
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import wasichai.core.identity.AuthenticatedUser
import wasichai.core.identity.JwtService
import java.util.UUID
import kotlin.coroutines.CoroutineContext

// una persona autenticada sin servidor de tokens: el contexto de seguridad de Reactor que llevaría su petición (como TestCallers de
// wasichai-agent 1.2.0). por defecto, el administrador de una organización cualquiera
object TestCallers {
    fun jwt(
        email: String = "persona@sgspe.test",
        roles: List<String> = listOf(AuthenticatedUser.ADMIN_ROLE),
        organizationId: UUID = UUID.randomUUID()
    ): Jwt =
        Jwt
            .withTokenValue("prueba")
            .header("alg", "none")
            .subject(UUID.randomUUID().toString())
            .claim(JwtService.CLAIM_ORGANIZATION, organizationId.toString())
            .claim(JwtService.CLAIM_EMAIL, email)
            .claim(JwtService.CLAIM_ROLES, roles)
            .build()

    fun context(jwt: Jwt = jwt()): CoroutineContext = ReactiveSecurityContextHolder.withAuthentication(JwtAuthenticationToken(jwt)).asCoroutineContext()
}
