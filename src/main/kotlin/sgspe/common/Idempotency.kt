package sgspe.common

import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Component
import tools.jackson.databind.SerializationFeature
import tools.jackson.databind.json.JsonMapper
import wasichai.core.common.FieldViolation
import wasichai.core.common.RetryLaterException
import wasichai.core.common.UnprocessableContentException
import wasichai.core.common.ValidationException
import wasichai.core.platform.ClusterLock
import wasichai.core.platform.ModuleMigration
import java.net.URI
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.util.UUID

// lo que responde un comando de /api/sgspe/**: el estado, el cuerpo (que se serializa con el JsonMapper de la aplicación),
// y si los tiene, el updatedAt del registro principal (va en ETag, entre comillas, como lo pide If-Match) y su ubicación
class CommandAnswer(
    val status: HttpStatus,
    val body: Any,
    val etag: Instant? = null,
    val location: String? = null
)

// la respuesta ya serializada: la de la primera petición, que un reenvío recibe byte por byte
data class StoredAnswer(
    val status: Int,
    val body: String,
    val etag: String?,
    val location: String?
)

// la cabecera Idempotency-Key con el contrato de wasichai (WAS-ADR-0058, decisión D3): de 1 a 128 caracteres ASCII
// imprimibles; el mismo método, la misma ruta y el mismo cuerpo (con sus claves ordenadas: el orden y los espacios no
// cuentan) son la misma petición
object IdempotencyKey {
    const val HEADER = "Idempotency-Key"
    const val REPLAYED = "Idempotent-Replayed"
    const val MAX_LENGTH = 128

    // tope en bytes UTF-8 de la respuesta que se guarda para los reenvíos (el CHECK idempotency_key_response_size de la
    // migración): un comando responde uno o dos registros, muy por debajo de esto
    const val MAX_RESPONSE_BYTES = 2 * 1024 * 1024
    val RETRY_AFTER: Duration = Duration.ofSeconds(1)

    // null si no vino; mal formada es un 400 sobre la cabecera
    fun parse(raw: String?): String? {
        if (raw == null) return null
        val valid = raw.length in 1..MAX_LENGTH && raw.isNotBlank() && raw.all { it.code in 0x20..0x7E }
        if (!valid) {
            throw ValidationException("Idempotency-Key mal formada", HEADER, "envíe de 1 a $MAX_LENGTH caracteres ASCII imprimibles")
        }
        return raw
    }

    // el cuerpo como lo leyó el controlador, con las claves ordenadas en todos los niveles
    fun canonical(
        json: JsonMapper,
        body: Any?
    ): String = json.writer().with(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).writeValueAsString(body)

    fun hash(
        method: String,
        path: String,
        canonicalBody: String
    ): String {
        val digest = MessageDigest.getInstance("SHA-256").digest("$method $path\n$canonicalBody".toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    // la respuesta que se guarda, si cabe en el tope. pasarlo es un error del comando (500): la transacción se revierte entera,
    // con el comando, y la clave queda libre
    fun bounded(body: String): String {
        val bytes = body.toByteArray(Charsets.UTF_8).size
        check(bytes <= MAX_RESPONSE_BYTES) { "La respuesta del comando ($bytes bytes) pasa el tope de $MAX_RESPONSE_BYTES bytes de Idempotency-Key" }
        return body
    }

    // la respuesta http de una respuesta guardada; un reenvío lo dice en Idempotent-Replayed
    fun entity(
        stored: StoredAnswer,
        replayed: Boolean
    ): ResponseEntity<ByteArray> {
        val builder = ResponseEntity.status(stored.status).contentType(MediaType.APPLICATION_JSON)
        stored.etag?.let { builder.header(HttpHeaders.ETAG, it) }
        stored.location?.let { builder.location(URI.create(it)) }
        if (replayed) builder.header(REPLAYED, "true")
        return builder.body(stored.body.toByteArray(Charsets.UTF_8))
    }
}

// las claves de los comandos de /api/sgspe/** en la tabla fija sgspe.idempotency_key (database-model.md §22, ASM-065), con el
// mismo contrato que la cabecera nativa del alta genérica (WAS-ADR-0058): sin clave, el comando corre como siempre; con clave,
// corre una sola vez por organización, usuario y clave dentro de wasichai.idempotency.ttl (el mismo plazo que la nativa). lo
// en curso es un candado de transacción que no espera (pg_try_advisory_xact_lock): una segunda petición mientras corre la
// primera recibe 409 con Retry-After, nunca un segundo comando. la clave, el comando y la respuesta se confirman o se revierten
// juntos: un comando que falla deja la clave libre. un reenvío con la misma petición devuelve la respuesta guardada sin correr
// nada (ni permisos, ni guardas, ni auditoría), y con otra petición es un 422. JOB-08 purga las vencidas.
// el candado se toma antes que los del comando (Transactions.locked) pero sin esperar, así que no puede interbloquearse con
// ellos; y si dos peticiones llegaran a pasar (un hash de 64 bits que colisiona no da eso: da un 409 espurio), la clave
// primaria rechaza el segundo INSERT y revierte su comando
@Component
class Idempotency(
    private val db: DatabaseClient,
    private val transactions: Transactions,
    private val callers: Callers,
    private val json: JsonMapper,
    @Value("\${wasichai.idempotency.ttl:24h}") private val ttl: Duration
) {
    suspend fun respond(
        rawKey: String?,
        method: String,
        path: String,
        body: Any?,
        command: suspend () -> CommandAnswer
    ): ResponseEntity<ByteArray> {
        val key = IdempotencyKey.parse(rawKey)
        if (key == null) return IdempotencyKey.entity(store(command()), replayed = false)
        val user = callers.require()
        val hash = IdempotencyKey.hash(method, path, IdempotencyKey.canonical(json, body))
        val (stored, replayed) = transactions.run { claimed(user.organizationId, user.userId, key, hash, command) }
        return IdempotencyKey.entity(stored, replayed)
    }

    private suspend fun claimed(
        organizationId: UUID,
        userId: UUID,
        key: String,
        hash: String,
        command: suspend () -> CommandAnswer
    ): Pair<StoredAnswer, Boolean> {
        // nunca espera: quien esperara retendría una conexión del pool mientras corre la primera (WAS-ADR-0058)
        val locked =
            db
                .sql("SELECT pg_try_advisory_xact_lock(:id) AS locked")
                .bind("id", lockId(organizationId, userId, key))
                .map { row, _ -> row.get("locked", Boolean::class.javaObjectType) == true }
                .one()
                .awaitSingle()
        if (!locked) {
            throw RetryLaterException(
                "Otra petición con esta Idempotency-Key todavía se está procesando: vuelva a enviarla en un momento",
                IdempotencyKey.RETRY_AFTER,
                listOf(FieldViolation(IdempotencyKey.HEADER, IN_FLIGHT))
            )
        }
        // vencida, la clave ya no existe aunque la purga no haya pasado: el plazo es exacto
        db
            .sql("DELETE FROM $TABLE WHERE $MATCH AND created_at < now() - make_interval(secs => :ttl)")
            .bind("organizationId", organizationId)
            .bind("userId", userId)
            .bind("key", key)
            .bind("ttl", ttl.toMillis() / 1000.0)
            .then()
            .awaitFirstOrNull()
        val previous =
            db
                .sql("SELECT request_sha256, response_status, response_body, response_etag, response_location FROM $TABLE WHERE $MATCH")
                .bind("organizationId", organizationId)
                .bind("userId", userId)
                .bind("key", key)
                .map { row, _ ->
                    row.get("request_sha256", String::class.java)!! to
                        StoredAnswer(
                            row.get("response_status", Int::class.javaObjectType)!!,
                            row.get("response_body", String::class.java)!!,
                            row.get("response_etag", String::class.java),
                            row.get("response_location", String::class.java)
                        )
                }.one()
                .awaitFirstOrNull()
        if (previous != null) {
            if (previous.first != hash) {
                throw UnprocessableContentException(
                    "Esta Idempotency-Key ya se usó con otra petición: use una clave nueva",
                    listOf(FieldViolation(IdempotencyKey.HEADER, REUSED))
                )
            }
            return previous.second to true
        }
        val stored = store(command())
        IdempotencyKey.bounded(stored.body)
        db
            .sql(
                "INSERT INTO $TABLE (organization_id, user_id, request_key, request_sha256, response_status, response_body, response_etag, " +
                    "response_location) VALUES (:organizationId, :userId, :key, :hash, :status, :body, :etag, :location)"
            ).bind("organizationId", organizationId)
            .bind("userId", userId)
            .bind("key", key)
            .bind("hash", hash)
            .bind("status", stored.status)
            .bind("body", stored.body)
            .let { if (stored.etag == null) it.bindNull("etag", String::class.java) else it.bind("etag", stored.etag) }
            .let { if (stored.location == null) it.bindNull("location", String::class.java) else it.bind("location", stored.location) }
            .then()
            .awaitFirstOrNull()
        return stored to false
    }

    private fun store(answer: CommandAnswer): StoredAnswer =
        StoredAnswer(answer.status.value(), json.writeValueAsString(answer.body), answer.etag?.let { "\"$it\"" }, answer.location)

    // borra las claves vencidas de todas las organizaciones (JOB-08); devuelve cuántas
    suspend fun purgeExpired(): Long =
        db
            .sql("DELETE FROM $TABLE WHERE created_at < now() - make_interval(secs => :ttl)")
            .bind("ttl", ttl.toMillis() / 1000.0)
            .fetch()
            .rowsUpdated()
            .awaitSingle()

    companion object {
        const val TABLE = "sgspe.idempotency_key"
        const val IN_FLIGHT = "IDEMPOTENCY_IN_FLIGHT"
        const val REUSED = "IDEMPOTENCY_KEY_REUSED"
        private const val MATCH = "organization_id = :organizationId AND user_id = :userId AND request_key = :key"

        // el candado de una clave: el mismo hash que usa ClusterLock, con un prefijo propio
        fun lockId(
            organizationId: UUID,
            userId: UUID,
            key: String
        ): Long = ClusterLock.lockId("sgspe.idempotency/$organizationId/$userId/$key")
    }
}

// la migración de las tablas fijas de sgspe.common (database-model.md §2.3, WAS-ADR-0026): su propia historia
// (flyway_history_sgspe_common) y un orden después de los módulos de wasichai, que van de 100 a 101
@Configuration(proxyBeanMethods = false)
class CommonMigration {
    @Bean
    fun sgspeCommonMigration(): ModuleMigration = ModuleMigration("sgspe_common", "classpath:db/sgspe/common", ORDER)

    companion object {
        const val ORDER = 200
    }
}
