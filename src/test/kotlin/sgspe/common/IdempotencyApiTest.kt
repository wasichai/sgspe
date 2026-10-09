package sgspe.common

import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.transaction.reactive.TransactionalOperator
import org.springframework.transaction.reactive.executeAndAwait
import java.util.UUID

// Idempotency-Key en los comandos de /api/sgspe/** con el contrato de la cabecera nativa (decisión D3, WAS-ADR-0058, api.md
// §3.5), probado con las evaluaciones (F2), que la honran desde F3: un reenvío recibe los mismos bytes con
// Idempotent-Replayed y no evalúa otra vez; otra petición con la misma clave es un 422; una en curso, un 409 con Retry-After; y
// un comando que falló deja la clave libre
class IdempotencyApiTest : AssessmentCommandApiTest() {
    @Autowired
    private lateinit var db: DatabaseClient

    @Autowired
    private lateinit var operator: TransactionalOperator

    @Test
    fun `un reenvio con la misma clave devuelve los mismos bytes y no evalua otra vez`() {
        val (id, manager) = assessable()
        val key = withKey()
        val first = assess(id, manager, key)
        assertEquals(HttpStatus.CREATED, first.status, first.body)
        assertNull(first.headers.getFirst(IdempotencyKey.REPLAYED))
        val again = assess(id, manager, key)
        assertEquals(HttpStatus.CREATED, again.status)
        assertEquals(first.body, again.body)
        assertEquals("true", again.headers.getFirst(IdempotencyKey.REPLAYED))
        assertEquals(first.headers.getFirst(HttpHeaders.ETAG), again.headers.getFirst(HttpHeaders.ETAG))
        assertEquals(1, assessments(id))
    }

    @Test
    fun `la misma clave con otro cuerpo es un 422 que la nombra, y otra persona con la misma clave es otra clave`() {
        val (id, manager) = assessable()
        val key = withKey()
        assertEquals(HttpStatus.CREATED, assess(id, manager, key).status)
        val other = assess(id, manager, key, date = "2026-06-12")
        assertEquals(HttpStatus.UNPROCESSABLE_CONTENT, other.status, other.body)
        assertEquals(IdempotencyKey.HEADER, tree(other.body)["errors"][0]["field"].asString())
        assertEquals(Idempotency.REUSED, code(other))
        val colleague = member("STAKEHOLDER_MANAGER", testProject())
        assertEquals(HttpStatus.CREATED, assess(id, colleague, key, date = "2026-06-12").status)
    }

    @Test
    fun `una clave mal formada es un 400 y un comando que falla deja la clave libre`() {
        val (id, manager) = assessable()
        assertEquals(HttpStatus.BAD_REQUEST, assess(id, manager, mapOf(IdempotencyKey.HEADER to "x".repeat(129))).status)
        val key = withKey()
        assertEquals(HttpStatus.BAD_REQUEST, assess(id, manager, key, value = 9).status)
        assertEquals(HttpStatus.CREATED, assess(id, manager, key).status)
        assertEquals(1, assessments(id))
    }

    @Test
    fun `una peticion con la clave en curso recibe 409 con Retry-After`() {
        val (id, manager) = assessable()
        val key = UUID.randomUUID().toString()
        val me = me(manager.token)
        val organization = UUID.fromString(me["organizationId"].asString())
        val user = UUID.fromString(me["userId"].asString())
        // otra transacción tiene el candado de la clave mientras llega la petición
        runBlocking {
            operator.executeAndAwait {
                db
                    .sql("SELECT pg_advisory_xact_lock(:id)")
                    .bind("id", Idempotency.lockId(organization, user, key))
                    .fetch()
                    .rowsUpdated()
                    .awaitSingle()
                val busy = assess(id, manager, mapOf(IdempotencyKey.HEADER to key))
                assertEquals(HttpStatus.CONFLICT, busy.status, busy.body)
                assertEquals("1", busy.headers.getFirst(HttpHeaders.RETRY_AFTER))
                assertEquals(Idempotency.IN_FLIGHT, code(busy))
            }
        }
        assertEquals(HttpStatus.CREATED, assess(id, manager, mapOf(IdempotencyKey.HEADER to key)).status)
    }
}
