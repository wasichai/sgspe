package sgspe.common

import org.springframework.stereotype.Component
import wasichai.core.common.NotFoundException
import wasichai.core.metadata.MetadataService
import java.util.UUID

// los objetos que una organización tiene aprovisionados en wasichai, dentro de una petición (Organizations solo vale en los
// trabajos de fondo, y ModelCatalog es el modelo del jar, el mismo para todos los tenants). un tenant sin el modelo de F4
// sigue con los objetos de F3: las reglas y los listeners de F3 que también miran las QCS preguntan aquí antes de leerlas.
// la definición se carga por organización, sin mirar los permisos de quien llama; su ausencia es el NotFoundException de
// MetadataService.loadDefinition, que solo se atrapa aquí y solo en esa llamada (como DependentsDeleteGuard)
@Component
class TenantModel(
    private val metadata: MetadataService
) {
    suspend fun has(
        organizationId: UUID,
        objectName: String
    ): Boolean =
        try {
            metadata.loadDefinition(organizationId, objectName)
            true
        } catch (_: NotFoundException) {
            false
        }

    // los de `objectNames` que la organización tiene, en su orden
    suspend fun present(
        organizationId: UUID,
        objectNames: Collection<String>
    ): List<String> = objectNames.filter { has(organizationId, it) }
}
