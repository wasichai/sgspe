package sgspe.deliverable

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.core.io.buffer.DefaultDataBufferFactory
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import sgspe.common.Organizations
import sgspe.common.Records
import sgspe.common.StoredFiles
import sgspe.common.StoredRead
import sgspe.common.SweepTiming
import sgspe.privacy.CanonicalJson
import sgspe.privacy.Disclosure
import sgspe.privacy.ReportDisclosure
import tools.jackson.databind.json.JsonMapper
import wasichai.core.data.RecordResponse
import wasichai.core.platform.ClusterLock
import wasichai.files.FileService
import wasichai.files.FileUpload
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

// el último paso de JOB-09 (ADR-0031): sube la especificación y el PDF y los adjunta con LISTO en una sola escritura de la
// plataforma, como DraftFiles (UPS-31). es un puerto para que las pruebas puedan cambiarlo
fun interface DeckFiles {
    suspend fun attach(
        organizationId: UUID,
        presentationId: UUID,
        files: List<DraftFile>,
        fields: Map<String, Any?>
    )
}

@Component
class PlatformDeckFiles(
    private val records: Records,
    private val files: FileService
) : DeckFiles {
    override suspend fun attach(
        organizationId: UUID,
        presentationId: UUID,
        files: List<DraftFile>,
        fields: Map<String, Any?>
    ) {
        records.asPlatform(organizationId, Presentations.JOB_SOURCE) {
            val ids =
                files.associate { file ->
                    val upload = FileUpload(file.fileName, file.contentType, Flux.just(DefaultDataBufferFactory.sharedInstance.wrap(file.bytes)))
                    file.field to
                        this.files
                            .stage(Presentations.OBJECT, file.field, upload)
                            .id
                            .toString()
                }
            records.patch(Presentations.OBJECT, presentationId, fields + ids, Presentations.JOB_REASON)
        }
    }
}

// lo pendiente de JOB-09: una presentación EN COLA, o EN PROCESO desde hace más de stale-after (otra corrida la tiene o se cayó);
// lo mismo para su MP4 (ASM-148), con su estado y su momento
object DeckQueue {
    fun pending(
        attributes: Map<String, Any?>,
        now: Instant,
        staleAfter: Duration
    ): Boolean = pending(attributes[Presentations.STATUS], attributes[Presentations.GENERATED_AT], now, staleAfter)

    fun videoPending(
        attributes: Map<String, Any?>,
        now: Instant,
        staleAfter: Duration
    ): Boolean = pending(attributes[Presentations.VIDEO_STATUS], attributes[Presentations.VIDEO_GENERATED_AT], now, staleAfter)

    private fun pending(
        status: Any?,
        generatedAt: Any?,
        now: Instant,
        staleAfter: Duration
    ): Boolean =
        when (status?.toString()) {
            Presentations.QUEUED -> true
            Presentations.RUNNING -> Deliverables.instant(generatedAt)?.plus(staleAfter)?.isBefore(now) ?: true
            else -> false
        }
}

// una presentación de punta a punta (deliverables.md §16.3 y §16.7): EN PROCESO con generated_at; lee la versión y su base,
// el modelo congelado de la base con su SHA-256 comprobado (el de la presentación y el del archivo); aplica el nivel; lee las
// fotos y el logo por su SHA-256; planifica con DeckPlanner; compone con sgspe-slides; verifica el PDF (DeckChecks) y sube la
// especificación y el PDF con LISTO en una sola escritura. todo error deja FALLIDO con su código, nunca con texto. lee y
// escribe como la plataforma; los logs llevan ids, códigos y el id de la corrida. el trabajo bloqueante, en Dispatchers.IO
@Component
class DeckGeneration(
    private val records: Records,
    private val renderer: SlidesRenderer,
    private val files: DeckFiles,
    private val stored: StoredFiles,
    private val properties: DeliverableProperties,
    private val clock: Clock
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun run(
        organizationId: UUID,
        presentationId: UUID
    ) {
        val correlation = "job-09-${UUID.randomUUID()}"
        val attemptAt = clock.instant()
        try {
            val prepared =
                platform(organizationId) {
                    val presentation = records.get(Presentations.OBJECT, presentationId)
                    records.patch(
                        Presentations.OBJECT,
                        presentationId,
                        mapOf(Presentations.STATUS to Presentations.RUNNING, Presentations.GENERATED_AT to attemptAt.toString(), Presentations.FAILURE to null),
                        Presentations.JOB_REASON
                    )
                    prepare(organizationId, presentation)
                }
            val spec = withContext(Dispatchers.IO) { DeckPlanner.plan(prepared) }
            val bytes = spec.bytes()
            val rendered = renderer.render(bytes, correlation)
            val problems = withContext(Dispatchers.IO) { DeckChecks.problems(rendered.pdf, spec) }
            if (problems.isNotEmpty()) {
                log.warn("JOB-09: la presentación {} no pasó la verificación: {} (corrida {})", presentationId, problems, correlation)
                throw DraftFailure(Presentations.DECK_CHECK_FAILED)
            }
            val name = "${spec.meta.code}-${spec.meta.audience.lowercase()}-${spec.meta.level.lowercase()}"
            files.attach(
                organizationId,
                presentationId,
                listOf(
                    DraftFile(Presentations.SPEC, "$name.json", Presentations.SPEC_TYPE, bytes),
                    DraftFile(Presentations.PDF, "$name.pdf", Deliverables.PDF_TYPE, rendered.pdf)
                ),
                mapOf(
                    Presentations.STATUS to Presentations.READY,
                    Presentations.SPEC_SHA256 to CanonicalJson.sha256(bytes),
                    Presentations.PDF_SHA256 to CanonicalJson.sha256(rendered.pdf),
                    Presentations.SLIDES to spec.slides.size,
                    Presentations.DESIGN to (rendered.design ?: DeckDesign.VERSION),
                    Presentations.RENDERER to rendered.renderer
                )
            )
            log.info("JOB-09: presentación {} LISTO con {} láminas (corrida {})", presentationId, spec.slides.size, correlation)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failed(organizationId, presentationId, e, correlation)
        } catch (e: StackOverflowError) {
            failed(organizationId, presentationId, e, correlation)
        } catch (e: OutOfMemoryError) {
            failed(organizationId, presentationId, e, correlation)
        }
    }

    private suspend fun failed(
        organizationId: UUID,
        presentationId: UUID,
        e: Throwable,
        correlation: String
    ) {
        val code =
            when (e) {
                is DraftFailure -> e.code
                is DeckPlanner.NotAvailable -> e.code
                is SlidesUnavailable -> Presentations.SLIDES_UNAVAILABLE
                is SlideFailed -> e.code
                is PdfTooLarge -> Presentations.FILE_TOO_LARGE
                else -> Presentations.GENERATION_FAILED
            }
        if (code == Presentations.GENERATION_FAILED) {
            log.error("JOB-09: falló la presentación {} ({}, corrida {})", presentationId, e.javaClass.name, correlation)
            log.debug("JOB-09: traza de la presentación {}", presentationId, e)
        } else {
            log.warn("JOB-09: presentación {} FALLIDO con {} (corrida {})", presentationId, code, correlation)
        }
        platform(organizationId) {
            records.patch(
                Presentations.OBJECT,
                presentationId,
                mapOf(Presentations.STATUS to Presentations.FAILED, Presentations.FAILURE to code),
                Presentations.JOB_REASON
            )
        }
    }

    // la entrada del planificador: el modelo de la versión base comprobado por su SHA-256, ya divulgado para el nivel pedido,
    // con las fotos que el nivel dejó y el logo
    private suspend fun prepare(
        organizationId: UUID,
        presentation: RecordResponse
    ): DeckPlanner.Input {
        val a = presentation.attributes
        val level = Disclosure.valueOf(a[Presentations.LEVEL].toString())
        val audience = Audience.of(a[Presentations.AUDIENCE]?.toString()) ?: throw DraftFailure(Presentations.GENERATION_FAILED)
        val version = records.get(Deliverables.VERSION, UUID.fromString(a[Presentations.VERSION].toString()))
        val base = Records.uuid(version.attributes[Deliverables.DERIVED_FROM])?.let { records.get(Deliverables.VERSION, it) } ?: version
        val descriptor = base.attributes[Deliverables.MODEL] as? Map<*, *> ?: throw DraftFailure(Presentations.MODEL_MISMATCH)
        val expected = a[Presentations.MODEL_SHA256]?.toString()
        if (expected == null || descriptor["sha256"]?.toString() != expected) throw DraftFailure(Presentations.MODEL_MISMATCH)
        val model = CanonicalJson.read(read(organizationId, descriptor, Presentations.MODEL_MISMATCH))
        val leveled = ReportDisclosure.apply(model, level)
        val photos =
            leveled.photos.mapValues { (_, ref) ->
                read(organizationId, mapOf("id" to ref.file, "sha256" to ref.sha256), Presentations.GENERATION_FAILED)
            }
        val logo =
            model.meta.brand?.let { brand ->
                brand.logoFile?.let { read(organizationId, mapOf("id" to it, "sha256" to brand.logoSha256), Presentations.GENERATION_FAILED) }
            }
        return DeckPlanner.Input(leveled, level, audience, photos, logo)
    }

    // los bytes de un archivo como la plataforma, con su SHA-256 igual al del descriptor
    private suspend fun read(
        organizationId: UUID,
        descriptor: Map<*, *>,
        failure: String
    ): ByteArray {
        val id = descriptor["id"]?.toString()?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: throw DraftFailure(failure)
        val read = stored.read(organizationId, id, properties.maxFileBytes)
        val bytes = (read as? StoredRead.Found)?.file?.bytes ?: throw DraftFailure(failure)
        if (CanonicalJson.sha256(bytes) != descriptor["sha256"]?.toString()) throw DraftFailure(failure)
        return bytes
    }

    private suspend fun <T> platform(
        organizationId: UUID,
        block: suspend () -> T
    ): T = records.asPlatform(organizationId, Presentations.JOB_SOURCE) { block() }
}

// el MP4 de una presentación de punta a punta (ASM-148, deliverables.md §16.2), la segunda tarea de JOB-09: EN PROCESO con
// video_generated_at; lee la especificación guardada de la presentación LISTO, con su SHA-256 comprobado contra spec_sha256 (el
// video cuenta lo mismo que el PDF, sin volver a planificar); la compone con sgspe-slides (POST /video); verifica el MP4
// (DeckVideoChecks) y lo sube con LISTO en una sola escritura. todo error deja el video FALLIDO con su código, nunca con texto,
// y el PDF como estaba. lee y escribe como la plataforma; los logs llevan ids, códigos y el id de la corrida
@Component
class DeckVideoGeneration(
    private val records: Records,
    private val renderer: SlidesVideoRenderer,
    private val files: DeckFiles,
    private val stored: StoredFiles,
    private val properties: DeliverableProperties,
    private val clock: Clock
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val json = JsonMapper.builder().build()

    suspend fun run(
        organizationId: UUID,
        presentationId: UUID
    ) {
        val correlation = "job-09-${UUID.randomUUID()}"
        try {
            val a =
                platform(organizationId) {
                    val presentation = records.get(Presentations.OBJECT, presentationId)
                    records.patch(
                        Presentations.OBJECT,
                        presentationId,
                        mapOf(
                            Presentations.VIDEO_STATUS to Presentations.RUNNING,
                            Presentations.VIDEO_GENERATED_AT to clock.instant().toString(),
                            Presentations.VIDEO_FAILURE to null
                        ),
                        Presentations.VIDEO_REASON
                    )
                    presentation.attributes
                }
            if (a[Presentations.STATUS]?.toString() != Presentations.READY) throw DraftFailure(Presentations.SPEC_MISMATCH)
            val spec = spec(organizationId, a)
            val slides = (a[Presentations.SLIDES] as? Number)?.toInt() ?: throw DraftFailure(Presentations.SPEC_MISMATCH)
            val rendered = renderer.video(spec, correlation)
            val problems = DeckVideoChecks.problems(rendered.mp4, slides)
            if (problems.isNotEmpty()) {
                log.warn("JOB-09: el video de la presentación {} no pasó la verificación: {} (corrida {})", presentationId, problems, correlation)
                throw DraftFailure(Presentations.DECK_CHECK_FAILED)
            }
            files.attach(
                organizationId,
                presentationId,
                listOf(DraftFile(Presentations.VIDEO, "${name(spec)}.mp4", Presentations.VIDEO_TYPE, rendered.mp4)),
                mapOf(Presentations.VIDEO_STATUS to Presentations.READY, Presentations.VIDEO_SHA256 to CanonicalJson.sha256(rendered.mp4))
            )
            log.info("JOB-09: video de la presentación {} LISTO con {} láminas (corrida {})", presentationId, slides, correlation)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failed(organizationId, presentationId, e, correlation)
        } catch (e: StackOverflowError) {
            failed(organizationId, presentationId, e, correlation)
        } catch (e: OutOfMemoryError) {
            failed(organizationId, presentationId, e, correlation)
        }
    }

    // la especificación guardada, como la plataforma, con su SHA-256 igual al de la presentación y al de su descriptor
    private suspend fun spec(
        organizationId: UUID,
        a: Map<String, Any?>
    ): ByteArray {
        val descriptor = a[Presentations.SPEC] as? Map<*, *> ?: throw DraftFailure(Presentations.SPEC_MISMATCH)
        val expected = a[Presentations.SPEC_SHA256]?.toString() ?: throw DraftFailure(Presentations.SPEC_MISMATCH)
        val id = descriptor["id"]?.toString()?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: throw DraftFailure(Presentations.SPEC_MISMATCH)
        val read = stored.read(organizationId, id, properties.maxFileBytes)
        val bytes = (read as? StoredRead.Found)?.file?.bytes ?: throw DraftFailure(Presentations.SPEC_MISMATCH)
        if (withContext(Dispatchers.IO) { CanonicalJson.sha256(bytes) } != expected) throw DraftFailure(Presentations.SPEC_MISMATCH)
        return bytes
    }

    // el nombre del archivo, como el del PDF: <código>-<público>-<nivel>
    private fun name(spec: ByteArray): String {
        val meta = json.readTree(spec).get("meta")
        return "${meta.get("code").asString()}-${meta.get("audience").asString().lowercase()}-${meta.get("level").asString().lowercase()}"
    }

    private suspend fun failed(
        organizationId: UUID,
        presentationId: UUID,
        e: Throwable,
        correlation: String
    ) {
        val code =
            when (e) {
                is DraftFailure -> e.code
                is SlidesUnavailable -> Presentations.SLIDES_UNAVAILABLE
                is SlideFailed -> e.code
                is PdfTooLarge -> Presentations.FILE_TOO_LARGE
                else -> Presentations.GENERATION_FAILED
            }
        if (code == Presentations.GENERATION_FAILED) {
            log.error("JOB-09: falló el video de la presentación {} ({}, corrida {})", presentationId, e.javaClass.name, correlation)
            log.debug("JOB-09: traza del video de la presentación {}", presentationId, e)
        } else {
            log.warn("JOB-09: video de la presentación {} FALLIDO con {} (corrida {})", presentationId, code, correlation)
        }
        platform(organizationId) {
            records.patch(
                Presentations.OBJECT,
                presentationId,
                mapOf(Presentations.VIDEO_STATUS to Presentations.FAILED, Presentations.VIDEO_FAILURE to code),
                Presentations.VIDEO_REASON
            )
        }
    }

    private suspend fun <T> platform(
        organizationId: UUID,
        block: suspend () -> T
    ): T = records.asPlatform(organizationId, Presentations.JOB_SOURCE) { block() }
}

// JOB-09 DeckGenerationJob (ADR-0031, architecture.md §8): en una sola réplica (ClusterLock sgspe.presentation, distinto del
// de JOB-05, para no hacer fila detrás de un borrador), por cada organización con presentaciones lee como la plataforma las
// pendientes (DeckQueue) y las compone de a una, cada una con su candado sgspe.presentation:<id>; después, los MP4 pendientes
// (ASM-148), con el mismo candado. una organización que falla no detiene a las demás
@Component
class DeckGenerationJob(
    private val records: Records,
    private val organizations: Organizations,
    private val generation: DeckGeneration,
    private val video: DeckVideoGeneration,
    private val locks: ClusterLock,
    private val properties: DeliverableProperties,
    private val clock: Clock
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // las presentaciones intentadas; null si otra réplica tenía el candado
    suspend fun runOnce(): Int? {
        val lease = locks.tryLock(Presentations.JOB_LOCK) ?: return null
        return lease.use {
            var done = 0
            for ((organization, _) in organizations.slugs(Presentations.OBJECT)) {
                try {
                    done += SweepTiming.timed(log, NAME, organization) { organization(organization) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    log.error("{}: falló la organización {} ({})", NAME, organization, e.javaClass.name)
                    log.debug("{}: traza de la organización {}", NAME, organization, e)
                }
            }
            done
        }
    }

    private suspend fun organization(organization: UUID): Int {
        val now = clock.instant()
        val pending =
            records.asPlatform(organization, Presentations.JOB_SOURCE) {
                records
                    .all(Presentations.OBJECT, mapOf(Presentations.STATUS to Presentations.QUEUED)) +
                    records
                        .all(Presentations.OBJECT, mapOf(Presentations.STATUS to Presentations.RUNNING))
                        .filter { DeckQueue.pending(it.attributes, now, properties.slides.staleAfter) }
            }
        var done = 0
        for (presentation in pending.sortedBy { it.createdAt ?: Instant.EPOCH }) {
            val lease = locks.tryLock(Presentations.lock(presentation.id)) ?: continue
            lease.use { generation.run(organization, UUID.fromString(presentation.id)) }
            done++
        }
        val videos =
            records.asPlatform(organization, Presentations.JOB_SOURCE) {
                records
                    .all(Presentations.OBJECT, mapOf(Presentations.VIDEO_STATUS to Presentations.QUEUED)) +
                    records
                        .all(Presentations.OBJECT, mapOf(Presentations.VIDEO_STATUS to Presentations.RUNNING))
                        .filter { DeckQueue.videoPending(it.attributes, now, properties.slides.staleAfter) }
            }
        for (presentation in videos.sortedBy { Deliverables.instant(it.attributes[Presentations.VIDEO_REQUESTED_AT]) ?: Instant.EPOCH }) {
            val lease = locks.tryLock(Presentations.lock(presentation.id)) ?: continue
            lease.use { video.run(organization, UUID.fromString(presentation.id)) }
            done++
        }
        return done
    }

    companion object {
        const val NAME = "JOB-09 DeckGenerationJob"
    }
}

// el reloj de JOB-09: cada sgspe.deliverable.slides.poll después de terminar la vuelta anterior; sgspe.jobs.enabled=false lo
// apaga (las pruebas llaman a runOnce)
@Component
@EnableScheduling
@ConditionalOnProperty(name = ["sgspe.jobs.enabled"], havingValue = "true", matchIfMissing = true)
class DeckGenerationSchedule(
    private val job: DeckGenerationJob
) {
    @Scheduled(fixedDelayString = "\${sgspe.deliverable.slides.poll:5s}")
    suspend fun poll() {
        job.runOnce()
    }
}
