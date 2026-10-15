package sgspe.ai

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import sgspe.common.Criteria
import sgspe.common.Organizations
import sgspe.common.Records
import sgspe.common.StoredFiles
import sgspe.common.StoredRead
import sgspe.common.Values
import sgspe.evidence.EvidenceRules
import wasichai.core.common.WasichaiException
import wasichai.core.platform.ClusterLock
import java.time.Clock
import java.time.Instant
import java.util.UUID

// el texto de un archivo para JOB-07; NoText si no tiene (sin OCR en F7, ASM-132)
fun interface DocumentText {
    suspend fun text(
        bytes: ByteArray,
        contentType: String
    ): CorpusText
}

// DocumentText sobre TextExtraction de 7.2b (PDF con texto y DOCX, con PDFBox y POI): su 422 AI_NO_TEXT (un PDF escaneado, dañado
// o cifrado, otro formato) es «sin texto», y su 422 AI_INPUT_TOO_LARGE (una bomba de inflado de un DOCX) también, con una
// advertencia que solo nombra la clase del error: el archivo es inmutable (ASM-013) y leerlo otra vez da lo mismo. cualquier otro
// error es «no disponible»: la vuelta siguiente lo vuelve a intentar, sin tumbar la de su organización. la extracción bloquea:
// corre en Dispatchers.IO. un documento largo se indexa por su comienzo (truncate = true, hasta maxChars); no es un error. es el
// único archivo de 7.2c que llama a TextExtraction
@Component
class EvidenceText(
    private val extraction: TextExtraction,
    private val properties: KnowledgeProperties
) : DocumentText {
    private val log = LoggerFactory.getLogger(javaClass)

    override suspend fun text(
        bytes: ByteArray,
        contentType: String
    ): CorpusText =
        withContext(Dispatchers.IO) {
            try {
                CorpusText.Text(extraction.extract(bytes, contentType, maxChars = properties.maxChars, truncate = true))
            } catch (e: WasichaiException) {
                val codes = e.violations.map { it.message }
                when {
                    NO_TEXT in codes -> CorpusText.NoText
                    TOO_LARGE in codes -> {
                        log.warn("KnowledgeIndexJob: no se extrajo el texto de un archivo ({})", e.javaClass.name)
                        CorpusText.NoText
                    }
                    else -> {
                        log.warn("KnowledgeIndexJob: no se extrajo el texto de un archivo por ahora ({})", e.javaClass.name)
                        CorpusText.Unavailable
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.warn("KnowledgeIndexJob: no se extrajo el texto de un archivo por ahora ({})", e.javaClass.name)
                CorpusText.Unavailable
            }
        }

    companion object {
        const val NO_TEXT = "AI_NO_TEXT"
        const val TOO_LARGE = "AI_INPUT_TOO_LARGE"
    }
}

// las evidencias de una organización para JOB-07, con Records dentro de su asPlatform (WAS-ADR-0039): las cambiadas desde una
// hora (Criteria.updatedSince) o todas, las reemplazadas y el texto de un archivo
@Component
class KnowledgeEvidences(
    private val records: Records,
    private val files: StoredFiles,
    private val documents: DocumentText,
    private val properties: KnowledgeProperties
) : KnowledgeCorpus {
    override suspend fun changed(
        organizationId: UUID,
        since: Instant?
    ): List<IndexableEvidence> =
        records.all(EvidenceRules.EVIDENCE, criteria = listOfNotNull(since?.let { Criteria.updatedSince(it) })).map { evidence ->
            IndexableEvidence(
                UUID.fromString(evidence.id),
                Records.uuid(evidence.attributes[PROJECT]),
                Values.text(evidence.attributes[EvidenceRules.CLASSIFICATION]),
                Records.uuid(evidence.attributes[REPLACES]),
                Records.uuid(EvidenceRules.fileId(evidence.attributes[EvidenceRules.FILE]))
            )
        }

    override suspend fun replaced(organizationId: UUID): Set<UUID> =
        records
            .all(EvidenceRules.EVIDENCE, criteria = listOf(Criteria.filled(REPLACES)))
            .mapNotNull { Records.uuid(it.attributes[REPLACES]) }
            .toSet()

    override suspend fun text(
        organizationId: UUID,
        evidence: IndexableEvidence
    ): CorpusText {
        val file = evidence.file ?: return CorpusText.NoText
        return when (val read = files.read(organizationId, file, properties.maxBytes)) {
            is StoredRead.Found -> documents.text(read.file.bytes, read.file.contentType)
            StoredRead.TooLarge -> CorpusText.NoText
            StoredRead.Missing -> CorpusText.Unavailable
        }
    }

    companion object {
        private const val PROJECT = "project"
        private const val REPLACES = "replaces"
    }
}

// JOB-07 KnowledgeIndexJob (workflows.md, ai-architecture.md §13.2): una sola réplica por vuelta (ClusterLock.tryLock), solo
// las organizaciones con evidence que activaron la IA (sgspe.ai.enabled-organizations), cada una dentro de Records.asPlatform.
// escribe solo la tabla fija knowledge_chunk (KnowledgeChunks); nunca escribe registros de wasichai, nunca aplica transiciones
// y, en F7, nunca llama a un modelo, así que no deja ai_interaction. la vuelta periódica mira lo cambiado desde la anterior; la
// diaria, todo (y borra los fragmentos de las evidencias que ya no existen)
@Component
class KnowledgeIndexJob(
    corpus: KnowledgeEvidences,
    chunks: KnowledgeChunks,
    organizations: Organizations,
    ai: AiProperties,
    records: Records,
    private val locks: ClusterLock,
    clock: Clock,
    properties: KnowledgeProperties
) {
    private val sweep =
        KnowledgeSweep(
            corpus,
            chunks,
            { organizations.having(EvidenceRules.EVIDENCE) { ai.enabled(it) } },
            { organization, block -> records.asPlatform(organization, block) },
            clock,
            properties.chunkChars,
            properties.overlapChars,
            properties.maxChars
        )

    // lo hecho; null si otra réplica tenía el candado
    suspend fun runOnce(full: Boolean): KnowledgeTally? {
        val lease = locks.tryLock(LOCK) ?: return null
        return lease.use { sweep.run(full) }
    }

    companion object {
        const val LOCK = "sgspe.knowledge-index"
    }
}

// el reloj de JOB-07, en la zona de Lima; sgspe.jobs.enabled=false lo apaga (las pruebas llaman a runOnce)
@Component
@EnableScheduling
@ConditionalOnProperty(name = ["sgspe.jobs.enabled"], havingValue = "true", matchIfMissing = true)
class KnowledgeIndexSchedule(
    private val job: KnowledgeIndexJob
) {
    @Scheduled(cron = "\${sgspe.jobs.knowledge-index.cron:0 */30 * * * *}", zone = "America/Lima")
    suspend fun periodic() {
        job.runOnce(full = false)
    }

    @Scheduled(cron = "\${sgspe.jobs.knowledge-index.daily-cron:0 15 3 * * *}", zone = "America/Lima")
    suspend fun daily() {
        job.runOnce(full = true)
    }
}
