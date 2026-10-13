package sgspe.ai

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import sgspe.common.CommonMigration
import wasichai.core.platform.ModuleMigration

// la migración de la tabla fija de sgspe.ai (database-model.md §2.3, WAS-ADR-0026): knowledge_chunk, con su propia historia
// (flyway_history_sgspe_ai) y un orden después de sgspe_common; las dos crean el esquema sgspe si falta. solo sgspe.ai lee y
// escribe la tabla, con DatabaseClient y SQL propio que filtra por organization_id (JOB-07 y AI-06, plan 7.2c)
@Configuration(proxyBeanMethods = false)
class KnowledgeMigration {
    @Bean
    fun sgspeAiMigration(): ModuleMigration = ModuleMigration(NAME, LOCATION, ORDER)

    companion object {
        const val NAME = "sgspe_ai"
        const val LOCATION = "classpath:db/sgspe/ai"
        const val ORDER = CommonMigration.ORDER + 1
        const val TABLE = "sgspe.knowledge_chunk"
    }
}
