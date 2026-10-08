package sgspe.common

import kotlinx.coroutines.CancellationException
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import wasichai.core.platform.ClusterLock
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

// un recalculador de campos derivados de un paquete (domain-model §7.4): calcula desde cero a partir de sus fuentes,
// nunca por incremento, y escribe solo si el valor cambió (un valor igual no toca updatedAt ni deja historia), con la
// marca y el motivo «derivado: <regla>». corre dentro de RecordService.asPlatform de una organización; cada registro que
// cambia lo escribe dentro de Transactions.locked("<objeto>:<id>"). since: los registros fuente cambiados desde ese
// instante; null, todos. devuelve los registros que escribió; si algún registro falla lo intenta con los demás y al
// final lanza, para que el trabajo no dé la vuelta por buena
interface DerivedRecalculator {
    // el objeto cuya presencia dice que la organización tiene el modelo que este recalculador necesita
    val sourceObject: String

    suspend fun recalculate(
        organizationId: UUID,
        since: Instant?
    ): Int
}

// JOB-02 (workflows.md): corrige los derivados que un listener no pudo escribir. cada hora sobre lo cambiado desde la
// vuelta anterior y una vez al día sobre todo. una sola réplica por vuelta (ClusterLock.tryLock); una organización que
// falla no detiene a las demás. nunca aplica transiciones ni vincula registros (RN-WFL-001).
// la primera vuelta horaria de cada proceso no tiene vuelta anterior: es completa. si una organización falla, la vuelta
// no se da por hecha (lastRun no avanza) y la hora siguiente vuelve a mirar desde la última buena
@Component
class DerivedFieldsJob(
    private val recalculators: List<DerivedRecalculator>,
    private val organizations: Organizations,
    private val records: Records,
    private val locks: ClusterLock,
    private val clock: Clock
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Volatile
    private var lastRun: Instant? = null

    // los registros escritos; null si otra réplica tenía el candado
    suspend fun runOnce(full: Boolean): Int? {
        val started = clock.instant()
        // el traslape cubre la diferencia entre el reloj de este proceso y el de la base, que es quien fecha updated_at
        val since = if (full) null else lastRun?.minus(OVERLAP)
        val lease = locks.tryLock(LOCK) ?: return null
        return lease.use {
            var written = 0
            var failed = false
            for (recalculator in recalculators) {
                for (organization in organizations.having(recalculator.sourceObject)) {
                    try {
                        written += records.asPlatform(organization) { recalculator.recalculate(organization, since) }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        failed = true
                        log.error("DerivedFieldsJob: {} falló en la organización {}: {}", recalculator.javaClass.simpleName, organization, e.message)
                    }
                }
            }
            if (!failed) lastRun = started
            written
        }
    }

    companion object {
        const val LOCK = "sgspe.derived-fields"
        private val OVERLAP: Duration = Duration.ofMinutes(2)
    }
}

// los relojes de los trabajos, en la zona de Lima. sgspe.jobs.enabled=false los apaga (las pruebas lo fijan en las dos
// tareas de Gradle y llaman a runOnce); DerivedFieldsJob existe aunque estén apagados
@Component
@EnableScheduling
@ConditionalOnProperty(name = ["sgspe.jobs.enabled"], havingValue = "true", matchIfMissing = true)
class DerivedFieldsSchedule(
    private val job: DerivedFieldsJob
) {
    @Scheduled(cron = "\${sgspe.jobs.derived-fields.hourly-cron:0 15 * * * *}", zone = "America/Lima")
    suspend fun hourly() {
        job.runOnce(full = false)
    }

    @Scheduled(cron = "\${sgspe.jobs.derived-fields.daily-cron:0 45 3 * * *}", zone = "America/Lima")
    suspend fun daily() {
        job.runOnce(full = true)
    }
}
