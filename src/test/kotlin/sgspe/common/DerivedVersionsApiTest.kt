package sgspe.common

import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.r2dbc.core.DatabaseClient
import sgspe.DomainApiTest
import wasichai.core.data.RecordWrite
import wasichai.core.data.RecordWriteGuard
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

// ADR-0027: el If-Match de la API genérica tolera solo los saltos exactos que dejan las escrituras de derivados
// (sgspe.derived_version), y wasichai lo compara en el propio UPDATE con la lista de versiones aceptadas. cada prueba fuerza
// su intercalado con llamadas en orden, sin depender de cuánto tarde un listener (el fallo E de la CI 1 de la fase 3.2)
class DerivedVersionsApiTest : DomainApiTest() {
    // la escritura de B en el peor momento: después de que IfMatchGuard comparó la de A y antes de su UPDATE. corre como una
    // guarda posterior, solo para la marca de esta prueba, y escribe con Records (otra escritura completa, que se confirma
    // sola porque la API genérica no abre transacción)
    @Order(GuardOrder.IF_MATCH + 1)
    class WriteBetweenGuardAndUpdate(
        private val records: Records
    ) : RecordWriteGuard {
        override suspend fun beforeWrite(change: RecordWrite) {
            if (WriteMark.present() || change.attributes?.get("alternative_names") != RACE) return
            records.patch(change.objectName, change.recordId!!, mapOf("alternative_names" to "Escrito por B"), "B entre la guarda y el UPDATE de A")
        }
    }

    @TestConfiguration
    class Race {
        @Bean
        fun writeBetweenGuardAndUpdate(records: Records) = WriteBetweenGuardAndUpdate(records)
    }

    @Autowired
    private lateinit var records: Records

    @Autowired
    private lateinit var db: DatabaseClient

    @Autowired
    private lateinit var job: RetentionJob

    private fun organization(): UUID = UUID.fromString(me()["organizationId"].asString())

    private fun concession(): String =
        createWith(
            "mining_concession",
            sample("mining_concession"),
            mapOf("boundary" to square(-78.6, -7.9, 0.01, "MultiPolygon"))
        )

    // una escritura de solo derivados, como la de JOB-02: con la marca, condicionada a lo leído y con su salto
    private fun derive(
        id: String,
        area: String
    ): Instant =
        runBlocking {
            records.asPlatform(organization()) {
                records.patch("mining_concession", UUID.fromString(id), mapOf("calculated_area_ha" to BigDecimal(area)), "derivado: área de la prueba")
            }
        }.updatedAt!!

    private fun jumps(id: String): List<DerivedJump> =
        runBlocking {
            db
                .sql("SELECT from_updated_at, to_updated_at FROM ${DerivedVersions.TABLE} WHERE record_id = :id ORDER BY from_updated_at")
                .bind("id", UUID.fromString(id))
                .map { row, _ -> DerivedJump(row.get("from_updated_at", Instant::class.java)!!, row.get("to_updated_at", Instant::class.java)!!) }
                .all()
                .collectList()
                .awaitSingle()
        }

    private fun updatedAt(id: String): Instant = Instant.parse(record("mining_concession", id)["updatedAt"].asString())

    @Test
    fun `una escritura de derivados deja su salto exacto y el If-Match anterior sigue valiendo`() {
        val id = concession()
        val read = updatedAt(id)
        val derived = derive(id, "12.3456")
        assertEquals(derived, updatedAt(id))
        assertTrue(DerivedJump(read, derived) in jumps(id), jumps(id).toString())
        val response = edit("mining_concession", id, mapOf("name" to "Concesión de A"), headers = mapOf(IfMatch.HEADER to "\"$read\""))
        assertEquals(HttpStatus.OK, response.status, response.body)
        assertEquals("Concesión de A", record("mining_concession", id)["attributes"]["name"].asString())
    }

    @Test
    fun `dos derivados seguidos tambien se toleran`() {
        val id = concession()
        val read = updatedAt(id)
        derive(id, "12.3456")
        derive(id, "13.5791")
        val response = edit("mining_concession", id, mapOf("name" to "Concesión de A"), headers = mapOf(IfMatch.HEADER to "\"$read\""))
        assertEquals(HttpStatus.OK, response.status, response.body)
    }

    // el fallo E: derivado, luego B, luego A con la versión previa al derivado. con la heurística de la auditoría, la
    // entrada de B podía pasar por la versión leída; con los saltos, B no es uno y A recibe 412 sin pisarlo
    @Test
    fun `derivado, luego la escritura de B, luego A con la version previa al derivado es 412`() {
        val id = concession()
        val read = updatedAt(id)
        derive(id, "12.3456")
        assertEquals(HttpStatus.OK, edit("mining_concession", id, mapOf("name" to "Concesión de B")).status)
        val stale = edit("mining_concession", id, mapOf("name" to "Concesión de A"), headers = mapOf(IfMatch.HEADER to "\"$read\""))
        assertEquals(HttpStatus.PRECONDITION_FAILED, stale.status, stale.body)
        assertEquals("Concesión de B", record("mining_concession", id)["attributes"]["name"].asString())
        // y con la versión que dejó el derivado, igual: lo de B vino después
        val afterDerived = jumps(id).first { it.from == read }.to
        val again = edit("mining_concession", id, mapOf("name" to "Concesión de A"), headers = mapOf(IfMatch.HEADER to "\"$afterDerived\""))
        assertEquals(HttpStatus.PRECONDITION_FAILED, again.status, again.body)
    }

    // B escribe después de que la guarda de sgspe dio por buena la versión de A y antes de su UPDATE: wasichai compara en el
    // propio UPDATE con la lista que dejó IfMatchFilter y responde su 412; nada se pisa. antes de ADR-0027 el filtro quitaba
    // la cabecera y esta ventana daba 200
    @Test
    fun `una escritura de otro entre la guarda y el UPDATE no se pierde`() {
        val locality = createRecord("locality", mapOf("name" to "Caserío Ejemplo", "locality_type" to "CASERIO"))
        val read = record("locality", locality)["updatedAt"].asString()
        val stale = edit("locality", locality, mapOf("alternative_names" to RACE), headers = mapOf(IfMatch.HEADER to "\"$read\""))
        assertEquals(HttpStatus.PRECONDITION_FAILED, stale.status, stale.body)
        assertEquals("If-Match", problemFields(stale.body).single())
        assertEquals("Escrito por B", record("locality", locality)["attributes"]["alternative_names"].asString())
    }

    // JOB-08 borra los saltos más viejos que el plazo (sgspe.concurrency.derived-versions-retention, 7 días): una versión
    // leída hace más que eso ya no se sigue por derivados y es 412 (falla cerrado: el portal recarga)
    @Test
    fun `un salto vencido no cuenta y JOB-08 lo borra`() {
        val id = concession()
        val read = updatedAt(id)
        derive(id, "12.3456")
        runBlocking {
            db
                .sql("UPDATE ${DerivedVersions.TABLE} SET created_at = now() - make_interval(days => 8) WHERE record_id = :id")
                .bind("id", UUID.fromString(id))
                .fetch()
                .rowsUpdated()
                .awaitSingle()
        }
        val stale = edit("mining_concession", id, mapOf("name" to "Concesión de A"), headers = mapOf(IfMatch.HEADER to "\"$read\""))
        assertEquals(HttpStatus.PRECONDITION_FAILED, stale.status, stale.body)
        val purged = runBlocking { job.runOnce() }!!
        assertTrue(purged.getValue(DerivedVersionsRetention.NAME) >= 1)
        assertTrue(jumps(id).none { it.from == read }, jumps(id).toString())
    }

    companion object {
        const val RACE = "MARCA-CARRERA-DE-LA-PRUEBA"
    }
}
