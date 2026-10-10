package sgspe.common

import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Component
import java.time.LocalDate

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

        // las tareas automáticas de una QCS confidencial (ASM-102, D-1 del 2026-10-10): las de escalamiento de los niveles 1 y 2
        // (JOB-01) y la de acuse de una queja (ConfidentialTasksListener, en lugar de AUT-02). su clave lleva la huella del
        // registro (16 dígitos hexadecimales, ConfidentialReaders.fingerprint) en lugar de su id, y la tarea no se vincula a la
        // QCS. son las únicas tareas sin origen que admite ExactlyOneGuard, y las únicas claves que el código de sgspe con la
        // marca escribe como quien llama (PlatformOnlyFields); fuera de eso, generator_key solo la escribe la plataforma
        private val CONFIDENTIAL_ESCALATION =
            Regex("escalation:(${Attention.OBJECTS.joinToString("|")}):[0-9a-f]{16}:\\d{4}-\\d{2}-\\d{2}:[12]")
        private val CONFIDENTIAL_ACK = Regex("ack:${Attention.GRIEVANCE}:[0-9a-f]{16}")

        // la de escalamiento del nivel `level` (1 o 2) de una QCS confidencial
        fun confidentialEscalationKey(
            objectName: String,
            fingerprint: String,
            due: LocalDate,
            level: Int
        ): String = "escalation:$objectName:$fingerprint:$due:$level"

        // la de acuse de una queja confidencial: solo la huella
        fun confidentialAckKey(fingerprint: String): String = "ack:${Attention.GRIEVANCE}:$fingerprint"

        fun isConfidentialTask(key: String?): Boolean = key != null && (CONFIDENTIAL_ESCALATION.matches(key) || CONFIDENTIAL_ACK.matches(key))
    }
}
