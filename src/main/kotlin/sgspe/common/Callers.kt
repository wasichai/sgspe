package sgspe.common

import org.springframework.stereotype.Component
import wasichai.core.common.UnauthorizedException
import wasichai.core.identity.AuthenticatedUser
import wasichai.core.identity.CurrentUser

// quién llama: la persona o la cuenta de servicio del token, o null fuera de una petición (un trabajo dentro de
// RecordService.asPlatform, una automatización)
@Component
class Callers(
    private val currentUser: CurrentUser
) {
    suspend fun userOrNull(): AuthenticatedUser? =
        try {
            currentUser.require()
        } catch (_: UnauthorizedException) {
            null
        }

    suspend fun require(): AuthenticatedUser = currentUser.require()
}
