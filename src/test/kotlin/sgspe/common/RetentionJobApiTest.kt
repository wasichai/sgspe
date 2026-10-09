package sgspe.common

import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.r2dbc.core.DatabaseClient
import java.util.UUID

// JOB-08 (workflows.md, «Pruebas de trabajos»): borra solo las claves de idempotency_key más antiguas que el plazo, y una
// clave vencida vuelve a correr el comando aunque la purga no haya pasado
class RetentionJobApiTest : AssessmentCommandApiTest() {
    @Autowired
    private lateinit var job: RetentionJob

    @Autowired
    private lateinit var db: DatabaseClient

    private fun count(key: String): Long =
        runBlocking {
            db
                .sql("SELECT count(*) AS n FROM ${Idempotency.TABLE} WHERE request_key = :key")
                .bind("key", key)
                .map { row, _ -> row.get("n", java.lang.Long::class.java)!!.toLong() }
                .one()
                .awaitSingle()
        }

    private fun age(
        key: String,
        hours: Int
    ) = runBlocking {
        db
            .sql("UPDATE ${Idempotency.TABLE} SET created_at = now() - make_interval(hours => :hours) WHERE request_key = :key")
            .bind("hours", hours)
            .bind("key", key)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    @Test
    fun `la purga borra solo las claves vencidas`() {
        val (id, manager) = assessable()
        val old = UUID.randomUUID().toString()
        val fresh = UUID.randomUUID().toString()
        assertEquals(HttpStatus.CREATED, assess(id, manager, mapOf(IdempotencyKey.HEADER to old)).status)
        assertEquals(HttpStatus.CREATED, assess(id, manager, mapOf(IdempotencyKey.HEADER to fresh), date = "2026-06-12").status)
        age(old, 25)
        val purged = runBlocking { job.runOnce() }!!
        assertTrue(purged.getValue("idempotency_key") >= 1)
        assertEquals(0, count(old))
        assertEquals(1, count(fresh))
    }

    @Test
    fun `una clave vencida vuelve a evaluar aunque la purga no haya pasado`() {
        val (id, manager) = assessable()
        val key = UUID.randomUUID().toString()
        assertEquals(HttpStatus.CREATED, assess(id, manager, mapOf(IdempotencyKey.HEADER to key)).status)
        age(key, 25)
        val again = assess(id, manager, mapOf(IdempotencyKey.HEADER to key))
        assertEquals(HttpStatus.CREATED, again.status)
        assertEquals(null, again.headers.getFirst(IdempotencyKey.REPLAYED))
        assertEquals(2, assessments(id))
    }
}
