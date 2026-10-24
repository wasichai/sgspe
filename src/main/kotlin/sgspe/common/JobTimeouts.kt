package sgspe.common

import kotlinx.coroutines.withTimeoutOrNull
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import java.time.Duration
import kotlin.time.toKotlinDuration

// sgspe.jobs.* (issue #164, architecture.md §8.1): los plazos de los trabajos de fondo. read-timeout acota una lectura de un
// sondeo (las organizaciones y los pedidos pendientes de JOB-05 y JOB-09); run-timeout, la vuelta entera de cada trabajo,
// menor que la hora de los cron horarios para que la vuelta siguiente encuentre libre el candado. el resto de sgspe.jobs
// (enabled y los cron) lo leen los relojes con @Scheduled y @ConditionalOnProperty. un valor fuera de rango detiene el arranque
@ConfigurationProperties("sgspe.jobs")
data class JobsProperties(
    val readTimeout: Duration = Duration.ofMinutes(2),
    val runTimeout: Duration = Duration.ofMinutes(50)
) {
    init {
        require(!readTimeout.isNegative && !readTimeout.isZero) { "sgspe.jobs.read-timeout debe ser positivo" }
        require(runTimeout >= Duration.ofMinutes(1)) { "sgspe.jobs.run-timeout debe ser de al menos 1 minuto" }
        require(readTimeout < runTimeout) { "sgspe.jobs.read-timeout debe ser menor que sgspe.jobs.run-timeout" }
    }
}

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(JobsProperties::class)
class JobsConfiguration

// un paso de un trabajo que pasó su plazo: un error común y no una cancelación, para que el catch por organización de cada
// trabajo lo registre (con la clase, sin mensaje) y la vuelta siga con la organización siguiente
class JobTimeoutException(
    val step: String,
    val limit: Duration
) : Exception("$step pasó el plazo de $limit")

object JobTimeouts {
    private val defaultLog = LoggerFactory.getLogger(JobTimeouts::class.java)

    // una lectura acotada: pasado el plazo, cancela el bloque y lanza JobTimeoutException. el plazo de un bloque interior
    // (withTimeout de un cliente) sale tal cual: withTimeoutOrNull solo devuelve null por el suyo
    suspend fun <T> read(
        step: String,
        limit: Duration,
        block: suspend () -> T
    ): T = (withTimeoutOrNull(limit.toKotlinDuration()) { Done(block()) } ?: throw JobTimeoutException(step, limit)).value

    // la vuelta entera de un trabajo, desde su reloj: pasado el plazo, la cancela (el candado del trabajo se suelta con su
    // use) y deja una línea de log, sin datos de registros. devuelve null si se cortó; otro error sale tal cual
    suspend fun <T> run(
        log: Logger = defaultLog,
        job: String,
        limit: Duration,
        block: suspend () -> T
    ): T? =
        try {
            read(RUN, limit, block)
        } catch (e: JobTimeoutException) {
            log.error("{}: {} pasó el plazo de {} y la vuelta se cortó", job, e.step, e.limit)
            null
        }

    private const val RUN = "la vuelta"

    private class Done<T>(
        val value: T
    )
}
