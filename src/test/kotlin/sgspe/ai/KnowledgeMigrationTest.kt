package sgspe.ai

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import sgspe.common.CommonMigration
import wasichai.core.platform.ModuleMigration

// la migración de la tabla fija de sgspe.ai (database-model.md §2.3, WAS-ADR-0026), sin base: su nombre, su ruta, su
// historia y su orden después de sgspe_common. que la tabla sea la de su ficha lo prueba model/test_model.py
// (FixedTablesTests), y contra PostgreSQL, KnowledgeChunkMigrationApiTest
class KnowledgeMigrationTest {
    @Test
    fun `sgspe_ai migra su tabla fija despues de sgspe_common, con su propia historia`() {
        val migration = KnowledgeMigration().sgspeAiMigration()
        assertEquals(ModuleMigration("sgspe_ai", "classpath:db/sgspe/ai", CommonMigration.ORDER + 1), migration)
        assertEquals("flyway_history_sgspe_ai", migration.historyTable)
    }

    @Test
    fun `su primera version crea knowledge_chunk en el esquema sgspe`() {
        val sql = KnowledgeMigrationTest::class.java.getResource("/db/sgspe/ai/V1__knowledge_chunk.sql")?.readText()
        assertNotNull(sql)
        assertTrue(sql!!.contains("CREATE TABLE ${KnowledgeMigration.TABLE} ("), sql)
    }
}
