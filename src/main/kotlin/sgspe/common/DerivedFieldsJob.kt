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

// la vuelta de JOB-02 sin candado ni base, para probarla con recalculadores falsos: qué organizaciones recorre, desde
// cuándo mira y cuándo da la vuelta por buena. `lastRun` solo avanza si ninguna organización falló; si una falla, la
// hora siguiente vuelve a mirar desde la última vuelta buena, pero nunca desde más atrás de MAX_WINDOW (un fallo
// permanente no deja crecer la ventana: la vuelta diaria cubre lo más antiguo)
class DerivedSweep(
    private val recalculators: List<DerivedRecalculator>,
    private val organizationsHaving: suspend (String) -> List<UUID>,
    private val asPlatform: suspend (UUID, suspend () -> Int) -> Int,
    private val clock: Clock
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Volatile
    var lastRun: Instant? = null
        private set

    // los registros escritos
    suspend fun run(full: Boolean): Int {
        val started = clock.instant()
        // el traslape cubre la diferencia entre el reloj de este proceso y el de la base, que es quien fecha updated_at
        val since = if (full) null else lastRun?.let { maxOf(it.minus(OVERLAP), started.minus(MAX_WINDOW)) }
        var written = 0
        var failed = false
        for (recalculator in recalculators) {
            val name = recalculator.javaClass.simpleName
            val organizations =
                try {
                    organizationsHaving(recalculator.sourceObject)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    failed = true
                    log.error("DerivedFieldsJob: {} no pudo listar las organizaciones de {}", name, recalculator.sourceObject, e)
                    continue
                }
            for (organization in organizations) {
                try {
                    written += asPlatform(organization) { recalculator.recalculate(organization, since) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    failed = true
                    log.error("DerivedFieldsJob: {} falló en la organización {}", name, organization, e)
                }
            }
        }
        if (!failed) lastRun = started
        return written
    }

    companion object {
        private val OVERLAP: Duration = Duration.ofMinutes(2)
        private val MAX_WINDOW: Duration = Duration.ofHours(25)
    }
}

// JOB-02 (workflows.md): corrige los derivados que un listener no pudo escribir. cada hora sobre lo cambiado desde la
// vuelta anterior y una vez al día sobre todo. una sola réplica por vuelta (ClusterLock.tryLock); una organización que
// falla no detiene a las demás. nunca aplica transiciones ni vincula registros (RN-WFL-001).
// la primera vuelta horaria de cada proceso no tiene vuelta anterior: es completa. si una organización falla, la vuelta
// no se da por hecha (lastRun no avanza) y la hora siguiente vuelve a mirar desde la última buena (DerivedSweep)
@Component
class DerivedFieldsJob(
    recalculators: List<DerivedRecalculator>,
    organizations: Organizations,
    records: Records,
    private val locks: ClusterLock,
    clock: Clock
) {
    private val sweep =
        DerivedSweep(
            recalculators,
            { organizations.having(it) },
            { organization, block -> records.asPlatform(organization, block) },
            clock
        )

    // los registros escritos; null si otra réplica tenía el candado
    suspend fun runOnce(full: Boolean): Int? {
        val lease = locks.tryLock(LOCK) ?: return null
        return lease.use { sweep.run(full) }
    }

    companion object {
        const val LOCK = "sgspe.derived-fields"

        // el trabajo que firma cada escritura de un recalculador (RQ-AUD-007, security.md §13.2): el motivo de un derivado
        // que escribe JOB-02 es «derivado: <regla> (JOB-02 DerivedFieldsJob)». cada DerivedRecalculator lo arma aquí
        const val NAME = "JOB-02 DerivedFieldsJob"

        fun reason(rule: String): String = "derivado: $rule ($NAME)"
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
