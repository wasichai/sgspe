package sgspe.ai

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitSingle
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Component
import sgspe.common.Transactions
import java.util.UUID

// lo que una recuperación admite antes de releer las evidencias (ai-architecture.md §13.3, pasos 1 a 3): el tenant de quien
// pregunta, el proyecto de la pregunta (y los fragmentos del tenant, sin proyecto) y las clasificaciones que no puede descargar
data class ChunkScope(
    val organizationId: UUID,
    val project: UUID,
    val excluded: Set<String>
)

// un fragmento que pasó los filtros de la base, con su relevancia (ts_rank_cd)
data class ChunkHit(
    val evidenceId: UUID,
    val chunkIndex: Int,
    val projectId: UUID?,
    val classification: String,
    val content: String,
    val rank: Double
)

// cómo quedó indexada una evidencia: el proyecto y la clasificación que se copiaron a sus fragmentos
data class IndexedEvidence(
    val evidenceId: UUID,
    val projectId: UUID?,
    val classification: String
)

// lo que JOB-07 escribe en knowledge_chunk; KnowledgeChunks en la app, un doble en las pruebas unitarias
interface ChunkStore {
    suspend fun indexed(organizationId: UUID): Map<UUID, IndexedEvidence>

    suspend fun replace(
        organizationId: UUID,
        evidenceId: UUID,
        projectId: UUID?,
        classification: String,
        contents: List<String>
    )

    suspend fun resync(
        organizationId: UUID,
        evidenceId: UUID,
        projectId: UUID?,
        classification: String
    ): Long

    suspend fun delete(
        organizationId: UUID,
        evidenceIds: Collection<UUID>
    ): Long

    // borra los fragmentos de toda evidencia que no esté en `evidenceIds`: las que ya no existen
    suspend fun retain(
        organizationId: UUID,
        evidenceIds: Collection<UUID>
    ): Long
}

// la única puerta de SQL de knowledge_chunk (ai-architecture.md §13.2 y §13.3, decisión 2 del plan 7.2c): toda sentencia nombra
// su organization_id, los valores van enlazados y el nombre de la tabla es la constante de la migración. ArchitectureTest vigila
// que solo este archivo use DatabaseClient en sgspe.ai y nombre la tabla. JOB-07 escribe; KnowledgeSearch lee
@Component
class KnowledgeChunks(
    private val db: DatabaseClient,
    private val transactions: Transactions
) : ChunkStore {
    override suspend fun indexed(organizationId: UUID): Map<UUID, IndexedEvidence> =
        db
            .sql(KnowledgeSql.INDEXED)
            .bind("organization", organizationId)
            .map { row, _ ->
                IndexedEvidence(
                    row.get("evidence_id", UUID::class.java)!!,
                    row.get("project_id", UUID::class.java),
                    row.get("classification", String::class.java)!!
                )
            }.all()
            .asFlow()
            .toList()
            .associateBy { it.evidenceId }

    // los fragmentos de la evidencia, de cero, en una transacción: los anteriores se borran y los nuevos quedan en orden
    override suspend fun replace(
        organizationId: UUID,
        evidenceId: UUID,
        projectId: UUID?,
        classification: String,
        contents: List<String>
    ) {
        transactions.run {
            delete(organizationId, listOf(evidenceId))
            if (contents.isNotEmpty()) {
                db
                    .sql(KnowledgeSql.INSERT)
                    .bind("organization", organizationId)
                    .let { if (projectId == null) it.bindNull("project", UUID::class.java) else it.bind("project", projectId) }
                    .bind("evidence", evidenceId)
                    .bind("classification", classification)
                    .bind("contents", contents.toTypedArray())
                    .fetch()
                    .rowsUpdated()
                    .awaitSingle()
            }
        }
    }

    override suspend fun resync(
        organizationId: UUID,
        evidenceId: UUID,
        projectId: UUID?,
        classification: String
    ): Long =
        db
            .sql(KnowledgeSql.RESYNC)
            .bind("organization", organizationId)
            .bind("evidence", evidenceId)
            .let { if (projectId == null) it.bindNull("project", UUID::class.java) else it.bind("project", projectId) }
            .bind("classification", classification)
            .fetch()
            .rowsUpdated()
            .awaitSingle()

    override suspend fun delete(
        organizationId: UUID,
        evidenceIds: Collection<UUID>
    ): Long {
        if (evidenceIds.isEmpty()) return 0
        return db
            .sql(KnowledgeSql.DELETE)
            .bind("organization", organizationId)
            .bind("evidences", evidenceIds.toTypedArray())
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    override suspend fun retain(
        organizationId: UUID,
        evidenceIds: Collection<UUID>
    ): Long =
        db
            .sql(KnowledgeSql.RETAIN)
            .bind("organization", organizationId)
            .bind("evidences", evidenceIds.toTypedArray())
            .fetch()
            .rowsUpdated()
            .awaitSingle()

    // los `limit` fragmentos más relevantes para `query` dentro de `scope`, por ts_rank_cd y en orden estable. una consulta sin
    // palabras que buscar (solo signos o palabras vacías) no encuentra nada
    suspend fun search(
        scope: ChunkScope,
        query: String,
        limit: Int
    ): List<ChunkHit> {
        if (query.isBlank() || limit <= 0) return emptyList()
        return db
            .sql(KnowledgeSql.SEARCH)
            .bind("query", query)
            .bind("organization", scope.organizationId)
            .bind("project", scope.project)
            .bind("excluded", scope.excluded.sorted().toTypedArray())
            .bind("limit", limit)
            .map { row, _ ->
                ChunkHit(
                    row.get("evidence_id", UUID::class.java)!!,
                    row.get("chunk_index", Integer::class.java)!!.toInt(),
                    row.get("project_id", UUID::class.java),
                    row.get("classification", String::class.java)!!,
                    row.get("content", String::class.java)!!,
                    row.get("rank", java.lang.Float::class.java)?.toDouble() ?: 0.0
                )
            }.all()
            .asFlow()
            .toList()
    }
}

// las sentencias de KnowledgeChunks, constantes y sin nada interpolado salvo el nombre fijo de la tabla: todo valor es un
// parámetro enlazado (:organization, :evidence…). la búsqueda usa la misma expresión que el índice GIN de la migración
// (to_tsvector('spanish', content)) para que el índice sirva
object KnowledgeSql {
    private const val TABLE = KnowledgeMigration.TABLE

    const val INDEXED =
        "SELECT DISTINCT ON (evidence_id) evidence_id, project_id, classification FROM $TABLE " +
            "WHERE organization_id = :organization ORDER BY evidence_id, chunk_index"

    const val INSERT =
        "INSERT INTO $TABLE (organization_id, project_id, evidence_id, chunk_index, classification, content) " +
            "SELECT :organization, CAST(:project AS uuid), :evidence, CAST(t.position AS integer) - 1, :classification, t.content " +
            "FROM unnest(CAST(:contents AS text[])) WITH ORDINALITY AS t(content, position)"

    const val RESYNC =
        "UPDATE $TABLE SET project_id = CAST(:project AS uuid), classification = :classification " +
            "WHERE organization_id = :organization AND evidence_id = :evidence"

    const val DELETE = "DELETE FROM $TABLE WHERE organization_id = :organization AND evidence_id = ANY(CAST(:evidences AS uuid[]))"

    const val RETAIN = "DELETE FROM $TABLE WHERE organization_id = :organization AND NOT (evidence_id = ANY(CAST(:evidences AS uuid[])))"

    const val SEARCH =
        "SELECT c.evidence_id, c.chunk_index, c.project_id, c.classification, c.content, " +
            "ts_rank_cd(to_tsvector('spanish', c.content), q.query) AS rank " +
            "FROM $TABLE c, websearch_to_tsquery('spanish', :query) AS q(query) " +
            "WHERE c.organization_id = :organization " +
            "AND (c.project_id = :project OR c.project_id IS NULL) " +
            "AND NOT (c.classification = ANY(CAST(:excluded AS text[]))) " +
            "AND to_tsvector('spanish', c.content) @@ q.query " +
            "ORDER BY rank DESC, c.evidence_id, c.chunk_index " +
            "LIMIT :limit"

    val ALL = listOf(INDEXED, INSERT, RESYNC, DELETE, RETAIN, SEARCH)
}
