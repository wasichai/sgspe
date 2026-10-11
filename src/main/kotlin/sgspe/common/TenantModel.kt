package sgspe.common

import org.springframework.stereotype.Component
import wasichai.core.common.NotFoundException
import java.util.UUID

// los objetos que una organización tiene aprovisionados en wasichai, dentro de una petición (Organizations solo vale en los
// trabajos de fondo, y ModelCatalog es el modelo del jar, el mismo para todos los tenants). un tenant sin el modelo de F4
// sigue con los objetos de F3: las reglas y los listeners de F3 que también miran las QCS preguntan aquí antes de leerlas.
// la definición se carga por organización, sin mirar los permisos de quien llama (ObjectDefinitions.current); su ausencia
// es el NotFoundException de wasichai, que solo se atrapa aquí y solo en esa llamada (como DependentsDeleteGuard). la
// respuesta, también el «no está», se memoriza solo por petición: el objeto que trae una subida de fase se ve en la siguiente
@Component
class TenantModel(
    private val definitions: ObjectDefinitions
) {
    suspend fun has(
        organizationId: UUID,
        objectName: String
    ): Boolean =
        RequestMemo.rememberForRequest(MEMO to (organizationId to objectName)) {
            try {
                definitions.current(organizationId, objectName)
                true
            } catch (_: NotFoundException) {
                false
            }
        }

    // los de `objectNames` que la organización tiene, en su orden
    suspend fun present(
        organizationId: UUID,
        objectNames: Collection<String>
    ): List<String> = objectNames.filter { has(organizationId, it) }

    // si la organización tiene el campo (un campo nuevo de una fase, como community.code de F6): la definición de la petición,
    // sin mirar los permisos de quien llama
    suspend fun hasField(
        organizationId: UUID,
        objectName: String,
        field: String
    ): Boolean = has(organizationId, objectName) && definitions.current(organizationId, objectName).fields.any { it.name == field }

    companion object {
        private const val MEMO = "tenant-model"
    }
}
