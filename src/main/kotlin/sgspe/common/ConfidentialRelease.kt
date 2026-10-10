package sgspe.common

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

// la liberación del alcance de las QCS confidenciales para una regla del sistema (RN-GRV-001, decisión 4 del plan 4.2a).
// wasichai no deja ser la plataforma dentro de una petición (RecordService.asPlatform lanza) ni aplicar una transición sin
// usuario, así que RequestClosureListener cierra la solicitud como quien cierra el compromiso, dentro de `within`: mientras
// dura el bloque, ConfidentialAttentionScope admite además los registros de `objectName` cuyo `field` vale `value` (las
// solicitudes de ese compromiso), y nada más. es un elemento del contexto de la corrutina, como SgspeWrite: un cliente http
// no puede ponerlo, y ArchitectureTest vigila que solo RequestClosureListener lo abra
class ConfidentialRelease private constructor(
    val objectName: String,
    val field: String,
    val value: UUID
) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<ConfidentialRelease> {
        suspend fun <T> within(
            objectName: String,
            field: String,
            value: UUID,
            block: suspend () -> T
        ): T = withContext(ConfidentialRelease(objectName, field, value)) { block() }

        // la liberación en curso sobre `objectName`, o null
        suspend fun on(objectName: String): ConfidentialRelease? = currentCoroutineContext()[Key]?.takeIf { it.objectName == objectName }
    }

    override fun toString() = "ConfidentialRelease($objectName.$field=$value)"
}
