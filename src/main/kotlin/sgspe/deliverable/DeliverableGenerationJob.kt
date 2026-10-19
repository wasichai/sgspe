package sgspe.deliverable

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import sgspe.common.Criteria
import sgspe.common.LIMA
import sgspe.common.Organizations
import sgspe.common.Records
import sgspe.common.StoredFiles
import sgspe.common.StoredRead
import sgspe.common.SweepTiming
import sgspe.privacy.CanonicalJson
import sgspe.privacy.Disclosure
import sgspe.privacy.PersonalDataScan
import sgspe.privacy.ReportBrand
import sgspe.privacy.ReportDisclosure
import sgspe.privacy.ReportModel
import sgspe.privacy.ReportWarning
import wasichai.core.data.RecordResponse
import wasichai.core.platform.ClusterLock
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

// lo pendiente de JOB-05, en Kotlin sobre los entregables en draft u observed (especificación de F8 §2.2.17, domain-model §7.4):
// un pedido sin intento, o posterior al último intento (draft_generated_at, que JOB-05 escribe al tomar el pedido también si
// termina FALLIDO: un pedido se atiende una sola vez, y un FALLIDO no se reintenta hasta un pedido nuevo); uno EN COLA o EN
// PROCESO solo si lleva más de stale-after (EN PROCESO desde draft_generated_at; EN COLA desde la última escritura del
// registro), porque otra corrida lo tiene o se cayó
object DraftQueue {
    fun pending(
        attributes: Map<String, Any?>,
        updatedAt: Instant?,
        now: Instant,
        staleAfter: Duration
    ): Boolean {
        val requested = Deliverables.instant(attributes[Deliverables.REQUESTED_AT]) ?: return false
        val generated = Deliverables.instant(attributes[Deliverables.GENERATED_AT])
        return when (attributes[Deliverables.STATUS]?.toString()) {
            Deliverables.QUEUED -> updatedAt == null || updatedAt.plus(staleAfter).isBefore(now)
            Deliverables.RUNNING -> generated == null || generated.plus(staleAfter).isBefore(now)
            else -> generated == null || requested.isAfter(generated)
        }
    }
}

// un borrador que no se pudo terminar: su código va a draft_warnings con FALLIDO
class DraftFailure(
    val code: String,
    val section: String? = null
) : RuntimeException(code)

// un borrador de punta a punta (especificación de F8 §3.3, pasos 1 a 4): EN COLA; fija el instante del intento antes de leer
// las secciones (es draft_generated_at: una sección editada después cuenta como cambiada, aunque el ensamblado tarde); lee el
// entregable, sus secciones, el proyecto y la plantilla; ensambla con la clase del tipo, pide los mapas y avisa datos personales; congela el modelo (EN
// PROCESO con draft_generated_at, draft_model_sha256 y draft_revision); vuelve a leer lo congelado y, por nivel, aplica
// ReportDisclosure, escribe el DOCX del borrador y el de la candidata (solo INTERNO), compone con sgspe-latex la candidata y el
// borrador en PDF (ADR-0030) y verifica cada uno; y sube los siete
// archivos con LISTO y los avisos en una sola escritura (DraftFiles). todo error deja FALLIDO con su código. lee y escribe
// como la plataforma (con el origen job:deliverable-generation), nunca aplica transiciones ni escribe secciones. los logs
// llevan ids y códigos, nunca contenido. todo trabajo bloqueante de JOB-05 (POI, Java 2D, ImageIO, PDFBox) corre en
// Dispatchers.IO, nunca en el event loop de Reactor Netty: generate aquí y cada nivel de mapa en GeoServerReportMaps
@Component
class DraftGeneration(
    private val records: Records,
    private val types: DeliverableTypes,
    private val sources: ReportSources,
    private val maps: ReportMaps,
    private val pdf: PdfRenderer,
    private val files: DraftFiles,
    private val stored: StoredFiles,
    private val properties: DeliverableProperties,
    private val clock: Clock
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val writer = DocxWriter(properties.photos)
    private val latex = LatexWriter(properties.photos)

    suspend fun run(
        organizationId: UUID,
        slug: String?,
        deliverableId: UUID
    ) {
        val attemptAt = clock.instant()
        // el id de la corrida: va a sgspe-latex (X-Trace) y a los logs, nunca un dato del entregable
        val correlation = "job-05-${UUID.randomUUID()}"
        try {
            val frozen = platform(organizationId) { freeze(organizationId, slug, deliverableId, attemptAt) } ?: return
            val outputs = withContext(Dispatchers.IO) { generate(organizationId, frozen, correlation) }
            val warnings = frozen.model.warnings
            files.attach(organizationId, deliverableId, outputs, mapOf(Deliverables.STATUS to Deliverables.READY, Deliverables.WARNINGS to text(warnings)))
            log.info(
                "JOB-05: borrador {} LISTO del entregable {} con {} avisos (corrida {})",
                frozen.model.meta.draftRevision,
                deliverableId,
                warnings.size,
                correlation
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failed(organizationId, deliverableId, attemptAt, e)
        } catch (e: StackOverflowError) {
            // un anidado o un archivo que agota la pila o la memoria: sin esto el pedido quedaría EN PROCESO y se retomaría cada
            // stale-after para siempre. lo que el intento reservó ya se soltó al desenrollar; si escribir FALLIDO vuelve a
            // fallar, el Error sube como antes
            failed(organizationId, deliverableId, attemptAt, e)
        } catch (e: OutOfMemoryError) {
            failed(organizationId, deliverableId, attemptAt, e)
        }
    }

    // FALLIDO con su código. los logs, solo la clase de la excepción: su mensaje puede traer contenido del entregable (Jackson,
    // POI); la traza, en DEBUG. un error inesperado (GENERATION_FAILED) va en ERROR; uno con código propio, en WARN
    private suspend fun failed(
        organizationId: UUID,
        deliverableId: UUID,
        attemptAt: Instant,
        e: Throwable
    ) {
        val code =
            when (e) {
                is DraftFailure -> e.code
                is PdfConverterUnavailable -> Deliverables.PDF_CONVERTER_UNAVAILABLE
                is PdfTooLarge -> Deliverables.FILE_TOO_LARGE
                is PdfCompositionFailed -> Deliverables.GENERATION_FAILED
                else -> Deliverables.GENERATION_FAILED
            }
        if (code == Deliverables.GENERATION_FAILED) {
            log.error("JOB-05: borrador FALLIDO del entregable {} con {} ({})", deliverableId, code, e.javaClass.name)
        } else {
            log.warn("JOB-05: borrador FALLIDO del entregable {} con {}", deliverableId, code)
        }
        log.debug("JOB-05: traza del borrador fallido del entregable {}", deliverableId, e)
        platform(organizationId) {
            records.patch(
                Deliverables.DELIVERABLE,
                deliverableId,
                mapOf(
                    Deliverables.STATUS to Deliverables.FAILED,
                    // el intento queda atendido: sin un pedido nuevo, la vuelta siguiente no lo retoma (DraftQueue)
                    Deliverables.GENERATED_AT to attemptAt.toString(),
                    Deliverables.WARNINGS to text(listOf(ReportWarning(code, (e as? DraftFailure)?.section)))
                ),
                Deliverables.JOB_REASON
            )
        }
    }

    private suspend fun <T> platform(
        organizationId: UUID,
        block: suspend () -> T
    ): T = records.asPlatform(organizationId, Deliverables.JOB_SOURCE, block)

    class Frozen(
        val model: ReportModel,
        val bytes: ByteArray,
        val template: ByteArray,
        val author: String?
    )

    // pasos 1 y 2, como la plataforma. null si el entregable ya no está en draft u observed
    private suspend fun freeze(
        organizationId: UUID,
        slug: String?,
        id: UUID,
        attemptAt: Instant
    ): Frozen? {
        val deliverable = records.get(Deliverables.DELIVERABLE, id)
        if (deliverable.state !in Deliverables.REQUESTABLE) return null
        records.patch(Deliverables.DELIVERABLE, id, mapOf(Deliverables.STATUS to Deliverables.QUEUED), Deliverables.JOB_REASON)
        val type = types.of(deliverable.attributes[Deliverables.TYPE]?.toString()) ?: throw DraftFailure(DeliverableDrafts.TYPE_NOT_AVAILABLE)
        val template = template(organizationId, deliverable.attributes[Deliverables.TEMPLATE_KEY]?.toString())
        val scheme = properties.revisions.of(slug)
        val previous = deliverable.attributes[Deliverables.DRAFT_REVISION]?.toString()
        val previousAt = Deliverables.instant(deliverable.attributes[Deliverables.GENERATED_AT])
        val reset = previousAt != null && issuedAfter(id, previousAt)
        val sections =
            records.all(Deliverables.SECTION, mapOf(Deliverables.DELIVERABLE to id.toString())).sortedBy {
                (it.attributes["sort_order"] as? Number)?.toInt()
                    ?: 0
            }
        val input =
            AssemblyInput(
                deliverable = SourceRow(deliverable.id, deliverable.state, deliverable.attributes),
                sections = sections.map { SourceRow(it.id, it.state, it.attributes) },
                templateId = template.id,
                templateSha256 = template.sha256,
                draftRevision = RevisionLabels.nextDraft(previous, reset, scheme),
                issueRevision = RevisionLabels.nextIssue(deliverable.attributes[Deliverables.CURRENT_REVISION]?.toString(), scheme),
                today = LocalDate.now(clock.withZone(LIMA))
            )
        val assembled = type.assemble(input, sources.of(organizationId, type))
        val drawn = assembled.maps.associate { it.key to maps.draw(organizationId, it) }
        val warnings =
            drawn
                .filterValues { it.failed }
                .keys
                .sorted()
                .map { ReportWarning(Deliverables.MAP_FAILED, it) } +
                PersonalDataScan.scan(assembled.texts, assembled.personNames)
        val model =
            assembled.model.copy(
                meta = assembled.model.meta.copy(brand = template.brand),
                maps = drawn,
                warnings = assembled.model.warnings + warnings
            )
        val bytes = CanonicalJson.bytes(model)
        if (bytes.size > properties.maxFileBytes) throw DraftFailure(Deliverables.FILE_TOO_LARGE, Deliverables.DRAFT_MODEL)
        records.patch(
            Deliverables.DELIVERABLE,
            id,
            mapOf(
                Deliverables.STATUS to Deliverables.RUNNING,
                Deliverables.GENERATED_AT to attemptAt.toString(),
                Deliverables.MODEL_SHA256 to CanonicalJson.sha256(bytes),
                Deliverables.DRAFT_REVISION to model.meta.draftRevision
            ),
            Deliverables.JOB_REASON
        )
        val author = assembled.model.meta.preparedBy
        return Frozen(model, bytes, template.bytes, author)
    }

    private class Template(
        val id: String?,
        val sha256: String,
        val bytes: ByteArray,
        val brand: ReportBrand? = null
    )

    // la plantilla más reciente de la clave (especificación de F8 §2.2.4), con su SHA-256 comprobado; sin clave, la base
    private suspend fun template(
        organizationId: UUID,
        key: String?
    ): Template {
        if (key == null) return Template(null, CanonicalJson.sha256(BaseTemplate.bytes), BaseTemplate.bytes)
        val latest =
            records.all(Deliverables.TEMPLATE, mapOf("template_key" to key)).maxByOrNull { it.createdAt ?: Instant.EPOCH }
                ?: throw DraftFailure(Deliverables.TEMPLATE_UNAVAILABLE)
        val descriptor = latest.attributes["file"] as? Map<*, *> ?: throw DraftFailure(Deliverables.TEMPLATE_UNAVAILABLE)
        val bytes = read(organizationId, descriptor, Deliverables.TEMPLATE_UNAVAILABLE)
        // la marca del PDF (ASM-140): se congela por archivo y SHA-256; los bytes del logo se leen al generar, como las fotos
        val logo = latest.attributes[DeliverableTemplates.LOGO] as? Map<*, *>
        val accent = latest.attributes[DeliverableTemplates.ACCENT]?.toString()?.uppercase()
        val brand =
            ReportBrand(logo?.get("id")?.toString(), logo?.get("sha256")?.toString(), accent).takeIf {
                it.logoFile != null || it.accent != null
            }
        return Template(latest.id, descriptor["sha256"].toString(), bytes, brand)
    }

    // los bytes de un archivo de wasichai-files como la plataforma, con su SHA-256 igual al del descriptor (UPS-31)
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

    // hubo una emisión después del borrador anterior: la letra vuelve a la primera (ASM-027)
    private suspend fun issuedAfter(
        id: UUID,
        since: Instant
    ): Boolean = Deliverables.issuedSince(records, id, since, clock.instant())

    // paso 3, desde lo congelado y vuelto a leer: la prueba de que draft_model basta para volver a generar (RQ-REP-026)
    private suspend fun generate(
        organizationId: UUID,
        frozen: Frozen,
        correlation: String
    ): List<DraftFile> {
        val model = CanonicalJson.read(frozen.bytes)
        val photos =
            model.photos.mapValues { (_, ref) ->
                read(organizationId, mapOf("id" to ref.file, "sha256" to ref.sha256), Deliverables.GENERATION_FAILED)
            }
        val brand = model.meta.brand
        val logo = brand?.logoFile?.let { read(organizationId, mapOf("id" to it, "sha256" to brand.logoSha256), Deliverables.TEMPLATE_UNAVAILABLE) }
        val code = model.meta.code
        val out = mutableListOf(DraftFile(Deliverables.DRAFT_MODEL, "$code-modelo.json", Deliverables.MODEL_TYPE, frozen.bytes))
        val levels = listOf(Disclosure.INTERNO, Disclosure.EXTERNO)
        val leveled = levels.associateWith { ReportDisclosure.apply(model, it) }
        val levelPhotos = levels.associateWith { level -> photos.filterKeys { it in leveled.getValue(level).photos.keys } }
        // el PDF se compone desde el mismo modelo del nivel, no desde el DOCX (ADR-0030): un pedido por nivel da la candidata y
        // el borrador, y los dos niveles se piden a la vez (sgspe-latex compone LATEX_CONCURRENCY a la vez); el DOCX de INTERNO
        // se escribe mientras tanto
        val (rendered, docx) =
            coroutineScope {
                val pending =
                    levels.associateWith { level ->
                        val bundle = latex.write(LatexWriter.Input(leveled.getValue(level), frozen.author, levelPhotos.getValue(level), logo, brand?.accent))
                        async { pdf.render(bundle, PdfRequest(correlation)) }
                    }
                val internal = leveled.getValue(Disclosure.INTERNO)
                val photosInternal = levelPhotos.getValue(Disclosure.INTERNO)
                val docx =
                    writer.write(DocxWriter.Input(internal, frozen.template, true, frozen.author, photosInternal)) to
                        writer.write(DocxWriter.Input(internal, frozen.template, false, frozen.author, photosInternal))
                pending.mapValues { it.value.await() } to docx
            }
        for (level in levels) {
            val pdfs = rendered.getValue(level)
            check(pdfs.draft, level, candidate = false)
            check(pdfs.candidate, level, candidate = true)
            val name = "$code-${level.name.lowercase()}"
            if (level == Disclosure.INTERNO) {
                out += DraftFile(Deliverables.DRAFT_INTERNAL_DOCX, "$name-borrador.docx", Deliverables.DOCX_TYPE, docx.first)
                out += DraftFile(Deliverables.DRAFT_INTERNAL_PDF, "$name-borrador.pdf", Deliverables.PDF_TYPE, pdfs.draft)
                out += DraftFile(Deliverables.CANDIDATE_INTERNAL_DOCX, "$name.docx", Deliverables.DOCX_TYPE, docx.second)
                out += DraftFile(Deliverables.CANDIDATE_INTERNAL_PDF, "$name.pdf", Deliverables.PDF_TYPE, pdfs.candidate)
            } else {
                // solo PDF fuera de INTERNO
                out += DraftFile(Deliverables.DRAFT_EXTERNAL_PDF, "$name-borrador.pdf", Deliverables.PDF_TYPE, pdfs.draft)
                out += DraftFile(Deliverables.CANDIDATE_EXTERNAL_PDF, "$name.pdf", Deliverables.PDF_TYPE, pdfs.candidate)
            }
        }
        out.firstOrNull { it.bytes.size > properties.maxFileBytes }?.let { throw DraftFailure(Deliverables.FILE_TOO_LARGE, it.field) }
        return out
    }

    // PDFBox sobre lo que devolvió sgspe-latex: páginas, fuentes de la clase y, en la candidata, PDF/A-2b
    private fun check(
        bytes: ByteArray,
        level: Disclosure,
        candidate: Boolean
    ) {
        if (PdfChecks.problems(bytes, candidate).isNotEmpty()) throw DraftFailure(Deliverables.PDF_CHECK_FAILED, level.name)
    }

    companion object {
        // draft_warnings: un aviso por línea, «CÓDIGO» o «CÓDIGO sección»
        fun text(warnings: List<ReportWarning>): String? = warnings.joinToString("\n") { listOfNotNull(it.code, it.section).joinToString(" ") }.ifEmpty { null }

        fun parse(raw: Any?): List<ReportWarning> =
            raw?.toString().orEmpty().lines().filter { it.isNotBlank() }.map { line ->
                ReportWarning(line.substringBefore(' '), line.substringAfter(' ', "").ifEmpty { null })
            }
    }
}

// JOB-05 DeliverableGenerationJob (workflows.md, especificación de F8 §3.3): en una sola réplica (ClusterLock con la clave
// sgspe.deliverable), por cada organización con entregables (Organizations, con su slug para el esquema de revisiones), lee
// como la plataforma los entregables en draft u observed y toma los pendientes (DraftQueue), cada uno con su candado
// sgspe.deliverable-generation:<id> (Deliverables.generationLock: dos réplicas nunca generan el mismo) y de a uno. una
// organización que falla no detiene a las demás
@Component
class DeliverableGenerationJob(
    private val records: Records,
    private val organizations: Organizations,
    private val generation: DraftGeneration,
    private val locks: ClusterLock,
    private val properties: DeliverableProperties,
    private val clock: Clock
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // los borradores intentados; null si otra réplica tenía el candado
    suspend fun runOnce(): Int? {
        val lease = locks.tryLock(Deliverables.JOB_LOCK) ?: return null
        return lease.use {
            var done = 0
            for ((organization, slug) in organizations.slugs(Deliverables.DELIVERABLE)) {
                try {
                    done += SweepTiming.timed(log, NAME, organization) { organization(organization, slug) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // solo la clase: el mensaje de una excepción de base o de validación puede traer valores; la traza, en DEBUG
                    log.error("{}: falló la organización {} ({})", NAME, organization, e.javaClass.name)
                    log.debug("{}: traza de la organización {}", NAME, organization, e)
                }
            }
            done
        }
    }

    private suspend fun organization(
        organization: UUID,
        slug: String
    ): Int {
        val now = clock.instant()
        val pending: List<RecordResponse> =
            records.asPlatform(organization, SOURCE) {
                records
                    .all(Deliverables.DELIVERABLE, criteria = listOf(Criteria.inState(Deliverables.REQUESTABLE)))
                    .filter { DraftQueue.pending(it.attributes, it.updatedAt, now, properties.generation.staleAfter) }
            }
        var done = 0
        for (deliverable in pending) {
            val lease = locks.tryLock(Deliverables.generationLock(deliverable.id)) ?: continue
            lease.use { generation.run(organization, slug, UUID.fromString(deliverable.id)) }
            done++
        }
        return done
    }

    companion object {
        const val NAME = "JOB-05 DeliverableGenerationJob"
        const val SOURCE = Deliverables.JOB_SOURCE
        const val REASON = Deliverables.JOB_REASON
    }
}

// el reloj de JOB-05: cada sgspe.deliverable.generation.poll después de terminar la vuelta anterior; sgspe.jobs.enabled=false
// lo apaga (las pruebas llaman a runOnce)
@Component
@EnableScheduling
@ConditionalOnProperty(name = ["sgspe.jobs.enabled"], havingValue = "true", matchIfMissing = true)
class DeliverableGenerationSchedule(
    private val job: DeliverableGenerationJob
) {
    @Scheduled(fixedDelayString = "\${sgspe.deliverable.generation.poll:5s}")
    suspend fun poll() {
        job.runOnce()
    }
}
