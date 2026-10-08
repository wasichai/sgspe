package sgspe.common

import org.springframework.stereotype.Component
import wasichai.core.common.Actions
import wasichai.core.common.ForbiddenException
import wasichai.core.identity.AuthenticatedUser
import wasichai.core.identity.CurrentUser
import wasichai.core.metadata.MetadataService

// los permisos de objeto y las acciones declaradas de wasichai comprobados antes de la primera escritura, diciendo lo
// que falta (api.md §3.9, como Permisos de caja-backend@dc9e391). después RecordService los vuelve a comprobar en cada
// escritura. nunca compara nombres de rol (RQ-SEC-005)
@Component
class Permissions(
    private val currentUser: CurrentUser,
    private val metadata: MetadataService
) {
    // 403 si falta alguna (acción a objeto): «<what> exige <lo que falta>»
    suspend fun require(
        user: AuthenticatedUser,
        what: String,
        vararg grants: Pair<String, String>
    ) {
        val missing = grants.filterNot { (action, objectName) -> has(user, action, objectName) }
        if (missing.isNotEmpty()) {
            throw ForbiddenException("$what exige ${missing.joinToString(" y ") { (action, objectName) -> "${label(action)} sobre $objectName" }}")
        }
    }

    suspend fun has(
        user: AuthenticatedUser,
        action: String,
        objectName: String
    ): Boolean =
        try {
            currentUser.requirePermission(user, action, metadata.loadDefinition(user.organizationId, objectName).obj.id)
            true
        } catch (_: ForbiddenException) {
            false
        }

    private fun label(action: String): String =
        when (action) {
            Actions.READ -> "permiso de lectura"
            Actions.CREATE -> "permiso de creación"
            Actions.UPDATE -> "permiso de edición"
            Actions.DELETE -> "permiso de borrado"
            else -> "la acción $action"
        }
}
