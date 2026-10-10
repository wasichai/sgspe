package sgspe.common

import org.slf4j.Logger
import java.util.UUID

// la duración de cada organización en un barrido de un trabajo (especificación 4.4 §3.10): los trabajos recorren los tenants
// en serie (Organizations.having), y con muchos clientes un barrido podría no caber en su intervalo. una línea de log por
// organización, con su id y los milisegundos, sin nombres ni datos de registros; también si la organización falla
object SweepTiming {
    private const val NANOS_PER_MILLI = 1_000_000L

    suspend fun <T> timed(
        log: Logger,
        job: String,
        organization: UUID,
        nanoTime: () -> Long = System::nanoTime,
        block: suspend () -> T
    ): T {
        val start = nanoTime()
        try {
            return block()
        } finally {
            log.info("{}: organización {} en {} ms", job, organization, (nanoTime() - start) / NANOS_PER_MILLI)
        }
    }
}
