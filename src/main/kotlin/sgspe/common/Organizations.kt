package sgspe.common

import org.springframework.stereotype.Component
import wasichai.core.platform.TenantDirectory
import java.util.UUID

// las organizaciones que tienen un objeto, para los trabajos de fondo. es la forma soportada de wasichai
// (TenantDirectory.organizationsWithObject, WAS-ADR-0057, UPS-17): una instantánea ordenada por id que solo se puede pedir
// desde un trabajo de fondo, nunca desde una petición. ArchitectureTest rechaza findAllOrganizations en todo el código y
// TenantDirectory fuera de este archivo
@Component
class Organizations(
    private val tenants: TenantDirectory
) {
    suspend fun having(objectName: String): List<UUID> = tenants.organizationsWithObject(objectName).map { it.id }
}
