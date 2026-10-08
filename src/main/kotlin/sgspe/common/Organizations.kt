package sgspe.common

import org.springframework.stereotype.Component
import wasichai.core.metadata.CustomObjectRepository
import java.util.UUID

// las organizaciones que tienen un objeto, para los trabajos de fondo. wasichai no tiene una forma soportada de listar
// los tenants (UPS-17): como caja (BuzonStore), se usa findAllOrganizations, la lectura de la plataforma sin tenant que
// wasichai reserva para su trabajo de arranque. es una excepción vigilada a RNF-MNT-005 (architecture.md §8.1), aislada
// aquí: ArchitectureTest rechaza ese nombre en cualquier otro archivo. nunca se llama desde una petición
@Component
class Organizations(
    private val objects: CustomObjectRepository
) {
    suspend fun having(objectName: String): List<UUID> =
        objects
            .findAllOrganizations()
            .filter { it.name == objectName }
            .map { it.organizationId }
            .distinct()
}
