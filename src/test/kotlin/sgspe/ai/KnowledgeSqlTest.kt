package sgspe.ai

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// las sentencias de knowledge_chunk (ai-architecture.md §13.2 y §13.3, decisión 2 del plan 7.2c): toda una nombra su tenant, los
// valores van enlazados y la búsqueda usa la expresión del índice GIN, websearch_to_tsquery y ts_rank_cd
class KnowledgeSqlTest {
    @Test
    fun `toda sentencia filtra por la organizacion y nombra solo la tabla de la migracion`() {
        for (sql in KnowledgeSql.ALL) {
            assertTrue(":organization" in sql, sql)
            assertTrue(KnowledgeMigration.TABLE in sql, sql)
            assertFalse("$" in sql, "una sentencia con algo sin enlazar: $sql")
            assertFalse("'" in sql.replace("'spanish'", ""), "un literal que no es la configuración de texto: $sql")
        }
        assertTrue("organization_id = :organization" in KnowledgeSql.SEARCH)
        assertTrue("organization_id = :organization AND evidence_id = :evidence" in KnowledgeSql.RESYNC)
    }

    @Test
    fun `la busqueda usa el indice de texto completo en espanol y ordena por relevancia`() {
        val sql = KnowledgeSql.SEARCH
        assertTrue("websearch_to_tsquery('spanish', :query)" in sql, sql)
        assertTrue("to_tsvector('spanish', c.content) @@ q.query" in sql, sql)
        assertTrue("ts_rank_cd(to_tsvector('spanish', c.content), q.query)" in sql, sql)
        assertTrue("ORDER BY rank DESC, c.evidence_id, c.chunk_index" in sql, sql)
        assertTrue("LIMIT :limit" in sql, sql)
    }

    @Test
    fun `la busqueda admite el proyecto de la pregunta y los fragmentos del tenant, sin las clasificaciones excluidas`() {
        val sql = KnowledgeSql.SEARCH
        assertTrue("(c.project_id = :project OR c.project_id IS NULL)" in sql, sql)
        assertTrue("NOT (c.classification = ANY(CAST(:excluded AS text[])))" in sql, sql)
    }

    @Test
    fun `el alta numera los fragmentos desde cero en el orden dado`() {
        assertTrue("CAST(t.position AS integer) - 1" in KnowledgeSql.INSERT)
        assertTrue("unnest(CAST(:contents AS text[])) WITH ORDINALITY" in KnowledgeSql.INSERT)
        assertEquals(6, KnowledgeSql.ALL.size)
    }
}
