package sgspe.ai

import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DuplicateKeyException
import org.springframework.r2dbc.core.DatabaseClient
import wasichai.test.WasichaiIntegrationTest
import java.util.UUID

// la migración sgspe_ai contra PostgreSQL de verdad (database-model.md §2.3 y §20, ai-architecture.md §13.2): knowledge_chunk
// con las columnas de su ficha y sin embedding, su único por posición, el índice GIN de texto completo en español y su
// historia de Flyway propia. escribe con SQL propio, como lo hará JOB-07 (plan 7.2c), en una organización inventada que borra
// al terminar; no aprovisiona ningún tenant
class KnowledgeChunkMigrationApiTest : WasichaiIntegrationTest() {
    @Autowired
    private lateinit var db: DatabaseClient

    private val organization = UUID.randomUUID()

    @AfterEach
    fun cleanUp() {
        runBlocking {
            db
                .sql("DELETE FROM ${KnowledgeMigration.TABLE} WHERE organization_id = :organization")
                .bind("organization", organization)
                .fetch()
                .rowsUpdated()
                .awaitSingle()
        }
    }

    private fun texts(
        sql: String,
        binds: Map<String, Any> = emptyMap()
    ): List<String> =
        runBlocking {
            binds.entries
                .fold(db.sql(sql)) { spec, (name, value) -> spec.bind(name, value) }
                .map { row, _ -> row.get(0, String::class.java)!! }
                .all()
                .collectList()
                .awaitSingle()
        }

    private fun insert(
        evidence: UUID,
        index: Int,
        content: String
    ) = runBlocking {
        db
            .sql(
                "INSERT INTO ${KnowledgeMigration.TABLE} (organization_id, evidence_id, chunk_index, classification, content) " +
                    "VALUES (:organization, :evidence, :index, 'INTERNA', :content)"
            ).bind("organization", organization)
            .bind("evidence", evidence)
            .bind("index", index)
            .bind("content", content)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    @Test
    fun `knowledge_chunk tiene las columnas de su ficha y no tiene embedding`() {
        val columns =
            texts(
                "SELECT (column_name || ':' || data_type || ':' || is_nullable)::text FROM information_schema.columns " +
                    "WHERE table_schema = 'sgspe' AND table_name = 'knowledge_chunk' ORDER BY ordinal_position"
            )
        assertEquals(
            listOf(
                "id:uuid:NO",
                "organization_id:uuid:NO",
                "project_id:uuid:YES",
                "evidence_id:uuid:NO",
                "chunk_index:integer:NO",
                "classification:text:NO",
                "content:text:NO",
                "created_at:timestamp with time zone:NO"
            ),
            columns
        )
    }

    @Test
    fun `el indice de texto completo es GIN en espanol y la migracion tiene su propia historia`() {
        val indexes = texts("SELECT indexdef::text FROM pg_indexes WHERE schemaname = 'sgspe' AND tablename = 'knowledge_chunk'")
        assertTrue(indexes.any { it.contains("USING gin") && it.contains("to_tsvector('spanish'::regconfig, content)") }, "$indexes")
        assertTrue(indexes.any { it.contains("(organization_id, project_id, classification)") }, "$indexes")
        assertTrue(indexes.any { it.contains("UNIQUE") && it.contains("(organization_id, evidence_id, chunk_index)") }, "$indexes")
        val history = texts("SELECT table_name::text FROM information_schema.tables WHERE table_name = 'flyway_history_sgspe_ai'")
        assertEquals(listOf("flyway_history_sgspe_ai"), history)
    }

    @Test
    fun `la busqueda en espanol encuentra por la raiz y una posicion repetida choca`() {
        val evidence = UUID.randomUUID()
        insert(evidence, 0, "La Comunidad Ejemplo pidió reparar los canales de riego antes de la siembra.")
        insert(evidence, 1, "Se acordó una visita de campo al puesto de salud.")
        val found =
            texts(
                "SELECT chunk_index::text FROM ${KnowledgeMigration.TABLE} WHERE organization_id = :organization " +
                    "AND to_tsvector('spanish', content) @@ websearch_to_tsquery('spanish', :q) " +
                    "ORDER BY ts_rank_cd(to_tsvector('spanish', content), websearch_to_tsquery('spanish', :q)) DESC",
                mapOf("organization" to organization, "q" to "canal de riego")
            )
        assertEquals(listOf("0"), found)
        assertThrows<DuplicateKeyException> { insert(evidence, 0, "Otro texto en la misma posición.") }
    }
}
