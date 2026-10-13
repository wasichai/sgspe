package sgspe.ai

import kotlinx.coroutines.CancellationException
import org.slf4j.LoggerFactory
import sgspe.common.SweepTiming
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

// una evidencia como la lee JOB-07, como la plataforma: lo que se copia a sus fragmentos, lo que dice si se indexa y el id de su
// archivo (wasichai-files)
data class IndexableEvidence(
    val id: UUID,
    val project: UUID?,
    val classification: String?,
    val replaces: UUID?,
    val file: UUID? = null
)

// de dónde lee JOB-07 (ai-architecture.md §13.2): KnowledgeEvidences en la app (plan 7.2c, Tarea 9), un doble en las pruebas.
// corre dentro de Records.asPlatform de la organización
interface KnowledgeCorpus {
    // las evidencias creadas o cambiadas desde `since` (por updated_at); todas si es null
    suspend fun changed(
        organizationId: UUID,
        since: Instant?
    ): List<IndexableEvidence>

    // las que otra evidencia reemplaza (replaces): sus fragmentos dejan de recuperarse y se borran
    suspend fun replaced(organizationId: UUID): Set<UUID>

    // el texto de su archivo; null si no tiene (un PDF escaneado, una imagen, otro formato: sin OCR en F7, ASM-132)
    suspend fun text(
        organizationId: UUID,
        evidence: IndexableEvidence
    ): String?
}

// lo que hizo una vuelta: evidencias indexadas, resincronizadas (proyecto o clasificación), fragmentos borrados y evidencias
// sin texto
data class KnowledgeTally(
    val indexed: Int = 0,
    val resynced: Int = 0,
    val deleted: Long = 0,
    val withoutText: Int = 0
) {
    operator fun plus(other: KnowledgeTally): KnowledgeTally =
        KnowledgeTally(indexed + other.indexed, resynced + other.resynced, deleted + other.deleted, withoutText + other.withoutText)
}

// la vuelta de JOB-07 sin candado ni base, para probarla con dobles (como DerivedSweep de JOB-02). por organización: borra los
// fragmentos de las evidencias reemplazadas; en la vuelta completa, también los de las que ya no existen; indexa las evidencias
// cambiadas que no tienen fragmentos y resincroniza el proyecto y la clasificación de las que sí (el archivo de una evidencia es
// inmutable, ASM-013: su texto no cambia). una evidencia sin texto se recuerda en este proceso y no se vuelve a leer. una que
// falla no detiene a las demás, pero la organización cuenta como fallida: `lastRun` no avanza y la vuelta siguiente vuelve a mirar. nunca
// llama a un modelo, así que no deja ai_interaction (F7)
class KnowledgeSweep(
    private val corpus: KnowledgeCorpus,
    private val store: ChunkStore,
    private val organizations: suspend () -> List<UUID>,
    private val asPlatform: suspend (UUID, suspend () -> KnowledgeTally) -> KnowledgeTally,
    private val clock: Clock,
    private val chunkChars: Int = Segmenter.SIZE,
    private val overlapChars: Int = Segmenter.OVERLAP,
    private val maxChars: Int = MAX_CHARS
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // las evidencias sin texto que este proceso ya leyó, por organización
    private val withoutText = ConcurrentHashMap.newKeySet<Pair<UUID, UUID>>()

    @Volatile
    var lastRun: Instant? = null
        private set

    suspend fun run(full: Boolean): KnowledgeTally {
        val started = clock.instant()
        // sin vuelta anterior, completa; el traslape cubre la diferencia entre este reloj y el de la base, como en JOB-02
        val since = if (full) null else lastRun?.minus(OVERLAP)
        var tally = KnowledgeTally()
        var failed = false
        for (organization in organizations()) {
            try {
                tally += SweepTiming.timed(log, "KnowledgeIndexJob", organization) { asPlatform(organization) { organization(organization, since) } }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed = true
                log.error("KnowledgeIndexJob: falló la organización {}", organization, e)
            }
        }
        if (!failed) lastRun = started
        return tally
    }

    // una organización, dentro de su asPlatform. lanza al final si alguna evidencia falló
    suspend fun organization(
        organizationId: UUID,
        since: Instant?
    ): KnowledgeTally {
        val evidences = corpus.changed(organizationId, since)
        val replaced = corpus.replaced(organizationId)
        val indexed = store.indexed(organizationId)
        var deleted = store.delete(organizationId, replaced.filter { it in indexed })
        if (since == null) {
            // retain con una lista vacía borra todo el índice: un listado vacío pasajero no debe vaciarlo, así que solo se poda
            // con evidencias a la vista (una organización sin evidencias deja sus fragmentos hasta que haya alguna)
            if (evidences.isEmpty()) {
                if (indexed.isNotEmpty()) log.warn("KnowledgeIndexJob: la organización {} no lista evidencias; no se poda el índice", organizationId)
            } else {
                deleted += store.retain(organizationId, evidences.map { it.id }.filterNot { it in replaced })
            }
        }
        var tally = KnowledgeTally(deleted = deleted)
        val failures = mutableListOf<UUID>()
        for (evidence in evidences) {
            if (evidence.id in replaced) continue
            try {
                tally += evidence(organizationId, evidence, indexed[evidence.id])
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failures += evidence.id
                // solo el id: ni el título, ni el nombre del archivo, ni un pedazo del texto
                log.error("KnowledgeIndexJob: falló la evidencia {} de la organización {}", evidence.id, organizationId, e)
            }
        }
        check(failures.isEmpty()) { "KnowledgeIndexJob falló en ${failures.size} evidencias de la organización $organizationId" }
        return tally
    }

    private suspend fun evidence(
        organizationId: UUID,
        evidence: IndexableEvidence,
        current: IndexedEvidence?
    ): KnowledgeTally {
        // classification es obligatoria en el modelo: sin ella no hay con qué filtrar, y no se indexa
        val classification = evidence.classification ?: return KnowledgeTally()
        if (current != null) {
            if (current.projectId == evidence.project && current.classification == classification) return KnowledgeTally()
            store.resync(organizationId, evidence.id, evidence.project, classification)
            return KnowledgeTally(resynced = 1)
        }
        val key = organizationId to evidence.id
        if (key in withoutText) return KnowledgeTally()
        val text = corpus.text(organizationId, evidence)
        val bounded =
            text?.let {
                if (it.length > maxChars) log.warn("KnowledgeIndexJob: la evidencia {} pasa {} caracteres; se indexa el comienzo", evidence.id, maxChars)
                it.take(maxChars)
            }
        val segments = bounded?.let { Segmenter.segments(it, chunkChars, overlapChars) }.orEmpty()
        if (segments.isEmpty()) {
            withoutText += key
            return KnowledgeTally(withoutText = 1)
        }
        store.replace(organizationId, evidence.id, evidence.project, classification, segments)
        return KnowledgeTally(indexed = 1)
    }

    companion object {
        // el texto que se indexa de una evidencia: unos 330 fragmentos
        const val MAX_CHARS = 500_000
        private val OVERLAP: Duration = Duration.ofMinutes(2)
    }
}
