package sgspe.common

import kotlinx.coroutines.withContext
import org.springframework.stereotype.Component
import wasichai.core.data.RecordRequest
import wasichai.core.data.RecordResponse
import wasichai.core.data.RecordService
import java.util.UUID

// el RecordService de wasichai para el código de sgspe (como Registros de caja-backend@dc9e391,
// src/main/kotlin/caja/comun/Registros.kt). RecordService comprueba los permisos de objeto y de campo de quien llama y
// valida cada escritura, así que aquí no se repite. cada escritura lleva la marca SgspeWrite, para que las guardas de F2
// distingan el código propio de la API genérica, y su motivo, que wasichai deja en audit_log.reason (ADR-0011)
@Component
class Records(
    private val records: RecordService
) {
    suspend fun get(
        objectName: String,
        id: UUID
    ): RecordResponse = records.get(objectName, id)

    suspend fun create(
        objectName: String,
        attributes: Map<String, Any?>,
        reason: String?
    ): RecordResponse = withContext(SgspeWrite) { records.create(objectName, RecordRequest(attributes), reason) }

    suspend fun update(
        objectName: String,
        id: UUID,
        attributes: Map<String, Any?>,
        reason: String?
    ): RecordResponse = withContext(SgspeWrite) { records.update(objectName, id, RecordRequest(attributes), reason) }
}
