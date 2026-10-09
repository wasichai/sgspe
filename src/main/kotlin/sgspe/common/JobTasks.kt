package sgspe.common

import org.springframework.stereotype.Component
import wasichai.core.common.ConflictException

// las tareas que crea un trabajo programado (JOB-01 para el escalamiento, JOB-03 para el seguimiento): una por generator_key,
// que el modelo declara único (RF-WFL-004), así que repetir la corrida nunca duplica. corre dentro de Records.asPlatform de una
// organización. si otra réplica la creó entre la lectura y el alta, el único de la base la frena (409) y cuenta como hecha
@Component
class JobTasks(
    private val records: Records
) {
    // true si la creó
    suspend fun ensure(
        key: String,
        attributes: Map<String, Any?>,
        reason: String
    ): Boolean {
        if (records.count(TASK, filters = mapOf(GENERATOR_KEY to key)) > 0) return false
        return try {
            records.create(TASK, attributes + (GENERATOR_KEY to key), reason)
            true
        } catch (_: ConflictException) {
            false
        }
    }

    companion object {
        const val TASK = "task"
        const val GENERATOR_KEY = "generator_key"
    }
}
