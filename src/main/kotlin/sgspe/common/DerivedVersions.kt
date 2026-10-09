package sgspe.common

import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import org.springframework.beans.factory.annotation.Value
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.util.UUID
import wasichai.core.common.PreconditionFailedException as StaleWrite

// un salto de versión de una escritura de solo derivados (ADR-0027): el registro pasó de `from` a `to` y entre los dos no
// se escribió nada más, porque el UPDATE se condicionó a `from` [WAS-ADR-0051]
data class DerivedJump(
    val from: Instant,
    val to: Instant
)

// las versiones que valen como If-Match de quien leyó `expected`: ella y las que la siguieron por saltos de derivados, en
// orden. de una versión sale a lo sumo un salto (una sola escritura condicionada a ella puede ganar, y la clave primaria de
// derived_version lo asegura), así que la cadena es única y recorrerla hacia adelante desde lo leído equivale a recorrerla
// hacia atrás desde lo vigente. no mira horas: una escritura que no es un salto (la de otra persona, un cambio de
// geometría) corta la cadena aunque caiga a un milisegundo. `max` acota la vuelta: lo que queda fuera es 412 (falla cerrado)
object DerivedChain {
    const val MAX = 500

    fun from(
        expected: Instant,
        jumps: Collection<DerivedJump>,
        max: Int = MAX
    ): List<Instant> {
        val next = jumps.associate { it.from to it.to }
        val chain = mutableListOf(expected)
        val seen = mutableSetOf(expected)
        var version = expected
        while (chain.size <= max) {
            version = next[version] ?: break
            // un ciclo no puede darse con un reloj que avanza (clock_timestamp()); si se diera, la vuelta termina
            if (!seen.add(version)) break
            chain += version
        }
        return chain
    }
}

// la escritura de solo derivados condicionada a la versión que leyó (ADR-0027): si gana, deja su salto `leída -> escrita`;
// si otra escritura ganó entre la lectura y el UPDATE (wasichai responde su 412 y no escribe nada), vuelve a leer y lo
// intenta de nuevo sobre lo nuevo, así nunca pisa lo que otro acaba de escribir. tras ATTEMPTS pérdidas escribe sin
// condición, como antes, y no deja salto: quien tenga una versión anterior recibe 412 y recarga. sin versión leída (no
// debería pasar) tampoco hay de dónde condicionar
object DerivedWrite {
    const val ATTEMPTS = 3

    suspend fun <S, R> conditioned(
        stored: suspend () -> S,
        versionOf: (S) -> Instant?,
        write: suspend (S, Instant?) -> R,
        writtenVersion: (R) -> Instant?,
        record: suspend (DerivedJump) -> Unit
    ): R {
        repeat(ATTEMPTS) {
            val current = stored()
            val from = versionOf(current) ?: return write(current, null)
            val written =
                try {
                    write(current, from)
                } catch (_: StaleWrite) {
                    return@repeat
                }
            writtenVersion(written)?.let { to -> record(DerivedJump(from, to)) }
            return written
        }
        return write(stored(), null)
    }
}

// las versiones que acepta el If-Match de quien llama sobre un registro: la que leyó y las que la siguieron por derivados
interface VersionChains {
    suspend fun accepted(
        objectName: String,
        recordId: UUID,
        expected: Instant
    ): List<Instant>
}

// los saltos de las escrituras de derivados en la tabla fija sgspe.derived_version (database-model.md §22, ADR-0027): los
// escribe Records.patch cuando lo que cambia son solo derivados, en la transacción de quien escribe si la hay; los leen
// IfMatchFilter y Versions con la organización de quien llama. fuera de una transacción el salto se inserta justo después
// del UPDATE ya confirmado: en ese instante una comparación no lo ve y responde 412, nunca deja pasar una escritura perdida.
// un salto más viejo que el plazo no cuenta aunque la purga no haya pasado, y JOB-08 lo borra
@Component
class DerivedVersions(
    private val db: DatabaseClient,
    private val callers: Callers,
    @Value("\${sgspe.concurrency.derived-versions-retention:7d}") private val retention: Duration
) : VersionChains {
    suspend fun record(
        organizationId: UUID,
        objectName: String,
        recordId: UUID,
        jump: DerivedJump
    ) {
        db
            .sql(
                "INSERT INTO $TABLE (organization_id, object_name, record_id, from_updated_at, to_updated_at) " +
                    "VALUES (:organizationId, :objectName, :recordId, :from, :to)"
            ).bind("organizationId", organizationId)
            .bind("objectName", objectName)
            .bind("recordId", recordId)
            .bind("from", jump.from)
            .bind("to", jump.to)
            .then()
            .awaitFirstOrNull()
    }

    // sin persona que llama (no debería pasar en una petición) no hay organización con qué leer: solo lo leído
    override suspend fun accepted(
        objectName: String,
        recordId: UUID,
        expected: Instant
    ): List<Instant> {
        val organizationId = callers.userOrNull()?.organizationId ?: return listOf(expected)
        val jumps =
            db
                .sql(
                    "SELECT from_updated_at, to_updated_at FROM $TABLE WHERE organization_id = :organizationId AND object_name = :objectName " +
                        "AND record_id = :recordId AND from_updated_at >= :expected AND created_at >= now() - make_interval(secs => :retention) " +
                        "ORDER BY from_updated_at LIMIT ${DerivedChain.MAX + 1}"
                ).bind("organizationId", organizationId)
                .bind("objectName", objectName)
                .bind("recordId", recordId)
                .bind("expected", expected)
                .bind("retention", retention.toMillis() / 1000.0)
                .map { row, _ -> DerivedJump(row.get("from_updated_at", Instant::class.java)!!, row.get("to_updated_at", Instant::class.java)!!) }
                .all()
                .collectList()
                .awaitSingle()
        return DerivedChain.from(expected, jumps)
    }

    // borra los saltos vencidos de todas las organizaciones (JOB-08) en lotes acotados, como Idempotency.purgeExpired
    suspend fun purgeExpired(): Long {
        var total = 0L
        while (true) {
            val deleted =
                db
                    .sql(
                        "DELETE FROM $TABLE WHERE ctid IN (SELECT ctid FROM $TABLE " +
                            "WHERE created_at < now() - make_interval(secs => :retention) LIMIT $PURGE_BATCH)"
                    ).bind("retention", retention.toMillis() / 1000.0)
                    .fetch()
                    .rowsUpdated()
                    .awaitSingle()
            total += deleted
            if (deleted < PURGE_BATCH) return total
        }
    }

    companion object {
        const val TABLE = "sgspe.derived_version"
        private const val PURGE_BATCH = 5000
    }
}

// el paso de JOB-08 para derived_version: un salto vencido ya no sirve a ninguna lectura plausible (ADR-0027, ASM-091)
@Component
class DerivedVersionsRetention(
    private val versions: DerivedVersions
) : RetentionStep {
    override val name: String = NAME

    override suspend fun purge(): Long = versions.purgeExpired()

    companion object {
        const val NAME = "derived_version"
    }
}
