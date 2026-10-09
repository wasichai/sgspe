package sgspe.common

import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Component

// las tareas que crea un trabajo programado (JOB-01 para el escalamiento, JOB-03 para el seguimiento): una por generator_key,
// que el modelo declara único (RF-WFL-004), así que repetir la corrida nunca duplica. corre dentro de Records.asPlatform de una
// organización. si otra réplica la creó entre la lectura y el alta, el único de la base la frena (DuplicateKeyException) y cuenta como hecha
@Component
class JobTasks(
    private val records: Records
) {
    /**
     * Crea la tarea de la clave `key` si no existe; true si la creó.
     *
     * Quien llama no debe tener una transacción abierta: si otra réplica gana la carrera, el insert falla con el único y en
     * PostgreSQL eso aborta la transacción en curso (todo lo que siga falla hasta el rollback). Si hace falta llamarla dentro
     * de una, envuélvala en un SAVEPOINT (transacción anidada) para que solo ese insert se deshaga.
     */
    suspend fun ensure(
        key: String,
        attributes: Map<String, Any?>,
        reason: String
    ): Boolean {
        if (records.count(TASK, filters = mapOf(GENERATOR_KEY to key)) > 0) return false
        return try {
            records.create(TASK, attributes + (GENERATOR_KEY to key), reason)
            true
        } catch (_: DuplicateKeyException) {
            // wasichai 0.6.0: RecordService.create no envuelve la violación del único; sube el DuplicateKeyException de Spring,
            // que GlobalExceptionHandler convierte en 409 (wasichai-core/.../common/GlobalExceptionHandler.kt)
            false
        }
    }

    companion object {
        const val TASK = "task"
        const val GENERATOR_KEY = "generator_key"
    }
}
