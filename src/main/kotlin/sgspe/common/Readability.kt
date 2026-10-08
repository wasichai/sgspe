package sgspe.common

import wasichai.core.common.ForbiddenException
import wasichai.core.common.NotFoundException
import wasichai.core.metadata.MetadataService

// qué objetos y campos puede leer quien llama: la definición de cada objeto filtrada por sus permisos
// (MetadataService.definitionOf, la misma vista que usan la interfaz y el agente, dashboards.md §8). sin READ sobre el
// objeto, ningún campo suyo es legible. se consulta una vez por objeto en cada pedido
class Readability(
    private val metadata: MetadataService
) {
    private val fields = mutableMapOf<String, Set<String>?>()

    private suspend fun fieldsOf(objectName: String): Set<String>? {
        if (objectName in fields) return fields[objectName]
        val readable =
            try {
                metadata
                    .definitionOf(objectName)
                    .fields
                    .map { it.name }
                    .toSet()
            } catch (_: ForbiddenException) {
                null
            } catch (_: NotFoundException) {
                null
            }
        fields[objectName] = readable
        return readable
    }

    suspend fun canRead(path: String): Boolean {
        val objectName = path.substringBefore('.')
        val field = path.substringAfter('.', "")
        val readable = fieldsOf(objectName) ?: return false
        return field.isEmpty() || field in readable
    }

    suspend fun missing(requires: List<String>): List<String> = requires.filterNot { canRead(it) }

    suspend fun objectMissing(path: String): Boolean = fieldsOf(path.substringBefore('.')) == null
}
