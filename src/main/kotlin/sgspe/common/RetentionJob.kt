package sgspe.common

import kotlinx.coroutines.CancellationException
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import wasichai.core.platform.ClusterLock

// un paso de la retención (JOB-08): borra con SQL propio lo que una decisión registrada permite borrar, nunca registros de
// wasichai ni nada que referencie un registro (workflows.md, «Trabajos programados»). F3 trae el de idempotency_key; F8, el
// de los borradores de entregables
interface RetentionStep {
    val name: String

    // lo borrado
    suspend fun purge(): Long
}

// las claves de idempotencia vencidas, de todas las organizaciones en una sentencia (ASM-065): sin asPlatform ni
// TenantDirectory, porque la tabla fija no es de wasichai
@Component
class IdempotencyRetention(
    private val idempotency: Idempotency
) : RetentionStep {
    override val name: String = "idempotency_key"

    override suspend fun purge(): Long = idempotency.purgeExpired()
}

// la vuelta de JOB-08 sin candado, para probarla con pasos falsos: corre cada paso, uno que falla no detiene a los demás y
// al final lanza si alguno falló (como JOB-03), para que el fallo se vea
class RetentionSweep(
    private val steps: List<RetentionStep>
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun run(): Map<String, Long> {
        val purged = linkedMapOf<String, Long>()
        val failed = mutableListOf<String>()
        for (step in steps) {
            try {
                purged[step.name] = step.purge()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed += step.name
                log.error("RetentionJob: falló el paso {}", step.name, e)
            }
        }
        check(failed.isEmpty()) { "RetentionJob falló en ${failed.joinToString(", ")} (se purgó $purged)" }
        return purged
    }
}

// JOB-08 RetentionJob (workflows.md): una vez al día, una sola réplica (ClusterLock.tryLock), cada paso de retención
@Component
class RetentionJob(
    steps: List<RetentionStep>,
    private val locks: ClusterLock
) {
    private val sweep = RetentionSweep(steps)

    // lo purgado por paso; null si otra réplica tenía el candado
    suspend fun runOnce(): Map<String, Long>? {
        val lease = locks.tryLock(LOCK) ?: return null
        return lease.use { sweep.run() }
    }

    companion object {
        const val LOCK = "sgspe.retention"
    }
}

// el reloj de JOB-08, en la zona de Lima; sgspe.jobs.enabled=false lo apaga (las pruebas llaman a runOnce)
@Component
@EnableScheduling
@ConditionalOnProperty(name = ["sgspe.jobs.enabled"], havingValue = "true", matchIfMissing = true)
class RetentionSchedule(
    private val job: RetentionJob
) {
    @Scheduled(cron = "\${sgspe.jobs.retention.cron:0 15 4 * * *}", zone = "America/Lima")
    suspend fun daily() {
        job.runOnce()
    }
}
