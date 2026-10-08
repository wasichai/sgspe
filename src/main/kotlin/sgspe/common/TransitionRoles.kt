package sgspe.common

import org.springframework.http.HttpStatus
import wasichai.core.identity.AuthenticatedUser

// los roles de las transiciones que un comando aplica en nombre de quien llama, de model/workflows (ModelCatalog.transition).
// un endpoint que escribe varias transiciones en una transacción las comprueba todas antes de la primera escritura: la
// transición de wasichai ya las exige, pero fallar a medias dejaría la respuesta a un paso de la primera. el ADMIN pasa
object TransitionRoles {
    fun require(
        catalog: ModelCatalog,
        user: AuthenticatedUser,
        objectName: String,
        transitions: List<String>
    ) {
        if (user.isAdmin) return
        for (name in transitions) {
            val roles = catalog.transition(objectName, name)?.roles.orEmpty()
            if (roles.none { it in user.roles }) {
                throw Finding(
                    "ROLE_REQUIRED",
                    "La transición $name exige uno de los roles ${roles.joinToString(", ")}",
                    status = HttpStatus.FORBIDDEN
                ).toCodedException()
            }
        }
    }
}
