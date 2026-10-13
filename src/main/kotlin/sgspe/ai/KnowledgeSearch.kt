package sgspe.ai

import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component
import sgspe.common.Criteria
import sgspe.common.Permissions
import sgspe.common.Records
import sgspe.evidence.EvidenceRules
import wasichai.core.common.ForbiddenException
import wasichai.core.common.NotFoundException
import wasichai.core.common.ValidationException
import wasichai.core.identity.AuthenticatedUser
import java.util.UUID

// los límites del RAG de F7 (ai-architecture.md §13, decisiones 3 y 6 del plan 7.2c): el tamaño y el solape de los fragmentos,
// el texto que JOB-07 indexa de una evidencia y los bytes del archivo que lee, cuántos fragmentos pide la búsqueda a la base
// antes de releer las evidencias y cuántos llegan al modelo. se validan al arrancar: una configuración imposible falla de
// inmediato, no en cada vuelta de JOB-07 (el solape, menor que la mitad del fragmento, como lo exige Segmenter)
@ConfigurationProperties("sgspe.ai.knowledge")
data class KnowledgeProperties(
    val chunkChars: Int = Segmenter.SIZE,
    val overlapChars: Int = Segmenter.OVERLAP,
    val maxChars: Int = KnowledgeSweep.MAX_CHARS,
    val maxBytes: Long = 20L * 1024 * 1024,
    val candidates: Int = 25,
    val results: Int = 5
) {
    init {
        require(chunkChars > 0) { "sgspe.ai.knowledge.chunk-chars debe ser mayor que 0 ($chunkChars)" }
        require(overlapChars >= 0 && overlapChars < chunkChars / 2) {
            "sgspe.ai.knowledge.overlap-chars debe estar entre 0 y la mitad de chunk-chars ($overlapChars, $chunkChars)"
        }
        require(maxChars >= chunkChars) { "sgspe.ai.knowledge.max-chars no puede ser menor que chunk-chars ($maxChars, $chunkChars)" }
        require(maxBytes > 0) { "sgspe.ai.knowledge.max-bytes debe ser mayor que 0 ($maxBytes)" }
        require(results >= 1) { "sgspe.ai.knowledge.results debe ser al menos 1 ($results)" }
        require(candidates >= results) { "sgspe.ai.knowledge.candidates no puede ser menor que results ($candidates, $results)" }
    }
}

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(KnowledgeProperties::class)
class KnowledgeConfiguration

// un fragmento que pasó los cuatro filtros, en el orden de la base: lo que llega al modelo y lo que una cita nombra
data class KnowledgeFragment(
    val evidenceId: UUID,
    val chunkIndex: Int,
    val content: String
)

// el cuarto filtro, puro (ai-architecture.md §13.3, paso 4): un fragmento pasa si su evidencia volvió al releerla como la persona;
// si, con la clasificación que la persona lee hoy, puede descargarla (falla cerrado: sin clasificación legible es restringida,
// EvidenceRules.downloadRestricted); si sigue en el proyecto de la pregunta o en el tenant; y si ninguna evidencia que la persona
// lee la reemplaza. los primeros `limit`
object KnowledgeFilter {
    fun admit(
        hits: List<ChunkHit>,
        evidences: Map<UUID, Map<String, Any?>>,
        project: UUID,
        downloadsRestricted: Boolean,
        replaced: Set<UUID>,
        limit: Int
    ): List<KnowledgeFragment> =
        hits
            .asSequence()
            .filter { hit ->
                val attributes = evidences[hit.evidenceId] ?: return@filter false
                val evidenceProject = Records.uuid(attributes[PROJECT])
                (downloadsRestricted || !EvidenceRules.downloadRestricted(attributes)) &&
                    (evidenceProject == null || evidenceProject == project) &&
                    hit.evidenceId !in replaced
            }.take(limit)
            .map { KnowledgeFragment(it.evidenceId, it.chunkIndex, it.content) }
            .toList()

    // las clasificaciones que no se admiten antes de releer: las restringidas, a quien no tiene DOWNLOAD_RESTRICTED (RF-DOC-003)
    fun excluded(downloadsRestricted: Boolean): Set<String> = if (downloadsRestricted) emptySet() else EvidenceRules.RESTRICTED

    private const val PROJECT = "project"
}

// la recuperación con permisos de AI-06 (ai-architecture.md §13.3, especificación de F7 §3.5), en este orden: el tenant de quien
// pregunta; el proyecto, que tiene que leer (ProjectReadScope; si no, 404), más los documentos del tenant; las clasificaciones
// que puede descargar; la búsqueda de texto completo; y la relectura de las evidencias candidatas con Records, como la persona,
// con lo que el permiso READ, el alcance y la clasificación de hoy deciden. siempre como la persona, nunca como la plataforma
@Component
class KnowledgeSearch(
    private val records: Records,
    private val permissions: Permissions,
    private val chunks: KnowledgeChunks,
    private val properties: KnowledgeProperties
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun search(
        user: AuthenticatedUser,
        project: UUID,
        question: String
    ): List<KnowledgeFragment> {
        records.find(PROJECT, project) ?: throw NotFoundException("No existe el proyecto $project o no puede leerlo")
        val downloadsRestricted = permissions.has(user, EvidenceRules.DOWNLOAD_RESTRICTED, EvidenceRules.EVIDENCE)
        val hits = chunks.search(ChunkScope(user.organizationId, project, KnowledgeFilter.excluded(downloadsRestricted)), question, properties.candidates)
        if (hits.isEmpty()) return emptyList()
        val ids = hits.map { it.evidenceId }.distinct()
        val evidences =
            try {
                records.all(EvidenceRules.EVIDENCE, ids = ids).associate { UUID.fromString(it.id) to it.attributes }
            } catch (_: ForbiddenException) {
                return emptyList()
            }
        return KnowledgeFilter.admit(hits, evidences, project, downloadsRestricted, replaced(ids), properties.results)
    }

    // las candidatas que reemplaza otra evidencia que la persona lee. si no lee `replaces`, no se sabe: JOB-07 borra esos
    // fragmentos en su vuelta siguiente
    private suspend fun replaced(ids: List<UUID>): Set<UUID> =
        try {
            records
                .all(EvidenceRules.EVIDENCE, criteria = listOf(Criteria.anyOf(REPLACES, ids)))
                .mapNotNull { Records.uuid(it.attributes[REPLACES]) }
                .toSet()
        } catch (e: ValidationException) {
            // solo la clase: el mensaje podría nombrar el campo o el valor
            log.debug("KnowledgeSearch: no se leyó replaces de las evidencias candidatas ({})", e.javaClass.name)
            emptySet()
        }

    companion object {
        private const val PROJECT = "project"
        private const val REPLACES = "replaces"
    }
}
