package sgspe.common

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import wasichai.core.metadata.MetadataService
import wasichai.core.metadata.ObjectDefinition
import java.util.UUID

// las definiciones de objeto de una organización, una vez por petición de /api (RequestMemo.rememberForRequest): una
// petición ve una sola versión del modelo aunque sus guardas, WriteView, TenantModel, el alcance y los permisos la pidan
// muchas veces. fuera de una petición, una vez dentro de un RequestMemo.within (un trabajo, una prueba) y, sin memo, cada
// vez. entre peticiones las guarda wasichai (wasichai.metadata.cache.ttl, ADR-066 de wasichai; plan corto de wasichai
// 1.1.0): en este nodo, un cambio del modelo se ve en la petición siguiente; en otro, a lo sumo el ttl después
// (security.md §9.7). una ausencia (NotFoundException) nunca se guarda
@Component
class ObjectDefinitions internal constructor(
    private val load: suspend (UUID, String) -> ObjectDefinition
) {
    @Autowired
    constructor(metadata: MetadataService) : this({ organizationId, name -> metadata.loadDefinition(organizationId, name) })

    private data class Key(
        val organizationId: UUID,
        val name: String
    )

    suspend fun current(
        organizationId: UUID,
        name: String
    ): ObjectDefinition = RequestMemo.rememberForRequest(Key(organizationId, name)) { load(organizationId, name) }
}
