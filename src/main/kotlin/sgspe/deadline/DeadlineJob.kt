package sgspe.deadline

import kotlinx.coroutines.CancellationException
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import sgspe.common.Attention
import sgspe.common.Criteria
import sgspe.common.JobTasks
import sgspe.common.LIMA
import sgspe.common.Organizations
import sgspe.common.ProjectParameters
import sgspe.common.Records
import sgspe.common.SweepTiming
import sgspe.common.Transactions
import sgspe.common.Values
import sgspe.notification.ConfidentialReaders
import sgspe.notification.NoticeTexts
import sgspe.risk.RiskLevels
import sgspe.risk.RiskReview
import sgspe.risk.RiskReviewTasks
import sgspe.risk.Risks
import wasichai.core.data.RecordResponse
import wasichai.core.platform.ClusterLock
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

// la vuelta de JOB-01 sin candado, para probar su aislamiento con dobles: una organización que falla se registra y no detiene
// a las demás, y al final lanza si alguna falló (como JOB-03 y JOB-08)
class DeadlineSweep(
    private val organizations: suspend () -> List<UUID>,
    private val asPlatform: suspend (UUID, suspend () -> Int) -> Int,
    private val refresh: suspend (UUID) -> Int
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun run(): Int {
        var written = 0
        val failed = mutableListOf<UUID>()
        val all = organizations()
        for (organization in all) {
            try {
                written += SweepTiming.timed(log, "DeadlineJob", organization) { asPlatform(organization) { refresh(organization) } }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed += organization
                log.error("DeadlineJob falló en la organización {}", organization, e)
            }
        }
        check(failed.isEmpty()) { "DeadlineJob falló en ${failed.size} organizaciones: $failed (se escribieron $written registros)" }
        log.info("DeadlineJob: {} registros y tareas escritos en {} organizaciones", written, all.size)
        return written
    }
}

// JOB-01 DeadlineJob (sgspe.deadline, workflows.md): cada hora, una sola réplica (ClusterLock.tryLock), como la plataforma de
// cada organización. recorre por objeto (Organizations.having): un tenant sin el modelo de F4 sigue con los objetos de F3.
// escribe deadline_status de compromisos, tareas, quejas y consultas y validity_status de acuerdos con los parámetros de su
// proyecto, solo si cambia (P11) y bajo el candado del registro, con el motivo que nombra al trabajo (P16); crea la tarea de
// escalamiento de los niveles 1 y 2 de un compromiso, una queja o una consulta vencidos (generator_key, sin asignado ni fecha:
// la toma uno de los avisados; su título solo lleva el código). las de una QCS confidencial, de los dos niveles, no la delatan
// (D-1): su clave lleva la huella en lugar del id, su título y su descripción son genéricos y solo se vinculan al proyecto
// (ASM-102). desde el
// plan 4.2b mide también la revisión de los riesgos (ASM-099): mueve next_review_date si cambió la cadencia (última revisión +
// review_every_days o risk_review_days), escribe review_status y crea la tarea risk_review:<id>:<next_review_date> para el
// responsable al pasar a POR REVISAR o REVISION VENCIDA. nunca aplica transiciones (RN-WFL-001). el día de Lima se lee una vez
// por vuelta: todas las organizaciones se miden con la misma fecha. los avisos de plazo los publica DeadlineNotices, que lee lo
// que este trabajo escribió
@Component
class DeadlineJob(
    private val records: Records,
    private val organizations: Organizations,
    private val transactions: Transactions,
    private val tasks: JobTasks,
    private val locks: ClusterLock,
    private val clock: Clock
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // los registros escritos y las tareas creadas; null si otra réplica tenía el candado
    suspend fun runOnce(): Int? {
        val lease = locks.tryLock(LOCK) ?: return null
        return lease.use {
            val today = LocalDate.now(clock.withZone(LIMA))
            // las organizaciones que tienen cada objeto seguido, una vez por vuelta
            val having =
                Deadlines.TRACKED
                    .map { it.objectName }
                    .distinct()
                    .associateWith { organizations.having(it).toSet() }
            DeadlineSweep(
                {
                    having.values
                        .flatten()
                        .distinct()
                        .sorted()
                },
                { organization, block -> records.asPlatform(organization, SOURCE, block) },
                { organization -> refresh(organization, today, Deadlines.TRACKED.filter { organization in having.getValue(it.objectName) }) }
            ).run()
        }
    }

    private suspend fun refresh(
        organization: UUID,
        today: LocalDate,
        tracked: List<Deadlines.Tracked>
    ): Int {
        val projects = mutableMapOf<String, Map<String, Any?>>()

        suspend fun project(id: String?): Map<String, Any?> =
            if (id == null) emptyMap() else projects.getOrPut(id) { records.find("project", UUID.fromString(id))?.attributes.orEmpty() }
        var written = 0
        var created = 0
        var failed = 0
        for (tracked in tracked) {
            // solo los estados donde se calcula, más los que salieron de ellos con el campo todavía lleno (hay que vaciarlo):
            // los registros cerrados que ya se vaciaron no se vuelven a leer cada hora
            val rows = records.all(tracked.objectName, criteria = listOf(Criteria.inStateOrFilled(tracked.states, tracked.field)))
            // la última revisión de cada riesgo, de la que corre su cadencia: una lectura por organización
            val latest = if (tracked.review) latestAssessments(rows) else emptyMap()
            for (row in rows) {
                try {
                    val params = project(Values.text(row.attributes["project"]))
                    // bajo el candado, la última revisión se relee: una revisión guardada durante la vuelta no se pisa con la fecha
                    // vieja de la lectura inicial
                    val outcome =
                        write(tracked, row) { record, reread ->
                            val last = if (tracked.review && reread) latestAssessments(listOf(record))[record.id] else latest[record.id]
                            desired(tracked, record, today, params, last)
                        }
                    if (outcome.written) written++
                    // JobTasks.ensure va fuera de toda transacción: write ya cerró la suya (un único duplicado aborta la abierta)
                    if (tracked.escalates) created += escalate(tracked, row, outcome.locked, today, params)
                    if (tracked.review) created += reviewTask(row, outcome.locked)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    failed++
                    log.error("DeadlineJob: no se actualizó {} {} de la organización {}", tracked.objectName, row.id, organization, e)
                }
            }
        }
        check(failed == 0) { "no se actualizaron $failed registros de la organización $organization (se escribieron $written y se crearon $created tareas)" }
        log.info("DeadlineJob: organización {}, {} situaciones escritas y {} tareas de escalamiento creadas", organization, written, created)
        return written + created
    }

    // lo que hizo write: si escribió y el registro que releyó bajo el candado (null si no tomó el candado o ya no existe)
    private class Outcome(
        val written: Boolean,
        val locked: RecordResponse?
    )

    // relee bajo el candado y recalcula, por si una persona lo cambió entretanto; sin cambio en lo leído, no toma el candado
    private suspend fun write(
        tracked: Deadlines.Tracked,
        row: RecordResponse,
        desired: suspend (RecordResponse, Boolean) -> Map<String, Any?>
    ): Outcome {
        if (changed(row, desired(row, false)).isEmpty()) return Outcome(false, null)
        val id = UUID.fromString(row.id)
        return transactions.locked("${tracked.objectName}:$id") {
            val current = records.find(tracked.objectName, id) ?: return@locked Outcome(false, null)
            val changes = changed(current, desired(current, true))
            if (changes.isEmpty()) Outcome(false, current) else Outcome(true, records.patch(tracked.objectName, id, changes, REASON))
        }
    }

    // lo que el registro debe tener: la situación de plazo o de vigencia y, en un riesgo, también su próxima revisión
    private fun desired(
        tracked: Deadlines.Tracked,
        record: RecordResponse,
        today: LocalDate,
        params: Map<String, Any?>,
        latest: LocalDate?
    ): Map<String, Any?> =
        if (tracked.review) {
            RiskReview.changes(record.state, record.attributes, latest, RiskLevels.cadence(record.attributes, params), today)
        } else {
            mapOf(tracked.field to Deadlines.wanted(tracked, record.state, record.attributes, today, ProjectParameters.integer(params, "due_soon_days")))
        }

    private fun changed(
        record: RecordResponse,
        wanted: Map<String, Any?>
    ): Map<String, Any?> = wanted.filter { (name, value) -> !Values.same(record.attributes[name], value) }

    // la fecha de la última revisión (residual o no) de cada riesgo revisado de `rows`, por id
    private suspend fun latestAssessments(rows: List<RecordResponse>): Map<String, LocalDate> =
        rows
            .filter { it.state in Risks.REVIEWED }
            .map { UUID.fromString(it.id) }
            .chunked(CHUNK)
            .flatMap { chunk -> records.all(Risks.ASSESSMENT, criteria = listOf(Criteria.anyOf(Risks.SOCIAL_RISK, chunk))) }
            .mapNotNull { a ->
                val risk = Values.text(a.attributes[Risks.SOCIAL_RISK]) ?: return@mapNotNull null
                val date = Values.text(a.attributes["assessment_date"])?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return@mapNotNull null
                risk to date
            }.groupBy({ it.first }, { it.second })
            .mapValues { (_, dates) -> dates.max() }

    // la tarea de revisión de un riesgo POR REVISAR o con REVISION VENCIDA (ASM-099): una por riesgo y fecha, para su
    // responsable y con la fecha de la revisión. decide, como escalate, con el registro releído y no con la fila inicial
    private suspend fun reviewTask(
        row: RecordResponse,
        locked: RecordResponse?
    ): Int {
        if (locked == null && !reviewDue(row)) return 0
        val record = locked ?: records.find(Risks.SOCIAL_RISK, UUID.fromString(row.id)) ?: return 0
        if (!reviewDue(record)) return 0
        val next = Values.text(record.attributes[Risks.NEXT_REVIEW]) ?: return 0
        val code = Values.text(record.attributes["code"]) ?: record.id
        val owner = Values.text(record.attributes[Risks.OWNER])
        val email = Values.text(record.attributes[Risks.OWNER_EMAIL])
        val assignee = if (owner != null && email != null) mapOf("assignee_user_id" to owner, "assignee_user_email" to email) else emptyMap()
        val created =
            tasks.ensure(
                reviewKey(record.id, next),
                mapOf(
                    "title" to "Revisar el riesgo $code",
                    "description" to "La revisión del riesgo vence el $next.",
                    "project" to record.attributes["project"],
                    Risks.SOCIAL_RISK to record.id,
                    "due_date" to next
                ) + assignee,
                REVIEW_REASON
            )
        return if (created) 1 else 0
    }

    private fun reviewDue(record: RecordResponse): Boolean =
        record.state in Risks.REVIEWED && Values.text(record.attributes[Risks.REVIEW_STATUS]) in setOf(RiskReview.DUE, RiskReview.OVERDUE)

    // la fecha de vencimiento y el nivel alcanzado de un registro vencido en un estado con plazo; null si no escala
    private fun escalation(
        tracked: Deadlines.Tracked,
        record: RecordResponse,
        today: LocalDate,
        params: Map<String, Any?>
    ): Pair<LocalDate, Int>? {
        if (record.state !in tracked.states) return null
        val due = Deadlines.due(tracked, record.state, record.attributes) ?: return null
        if (!today.isAfter(due)) return null
        val level =
            Deadlines.level(due, today, ProjectParameters.integer(params, "escalation_first_days"), ProjectParameters.integer(params, "escalation_second_days"))
        return if (level == 0) null else due to level
    }

    // la tarea de cada nivel alcanzado de un registro vencido: una por registro, fecha y nivel. decide con el registro releído
    // bajo el candado de write o, si write no lo tomó, con uno releído ahora: la fila de la lectura inicial puede tener horas en
    // una organización grande, y una reprogramación, un cumplimiento o una respuesta entretanto no deben escalar. el título y la
    // descripción solo llevan el tipo, el código y la fecha: nunca texto libre. las de una QCS confidencial (los dos niveles, D-1)
    // las leen todos los lectores de task del proyecto: llevan la huella en la clave, el texto genérico y ningún vínculo con la QCS
    private suspend fun escalate(
        tracked: Deadlines.Tracked,
        row: RecordResponse,
        locked: RecordResponse?,
        today: LocalDate,
        params: Map<String, Any?>
    ): Int {
        if (locked == null && escalation(tracked, row, today, params) == null) return 0
        val record = locked ?: records.find(tracked.objectName, UUID.fromString(row.id)) ?: return 0
        val (due, level) = escalation(tracked, record, today, params) ?: return 0
        val code = Values.text(record.attributes["code"]) ?: record.id
        val confidential = tracked.objectName in Attention.OBJECTS && Attention.isConfidential(record.attributes)
        return (1..level).count { reached ->
            if (confidential) {
                val project = Values.text(record.attributes["project"])
                if (project == null) {
                    // sin proyecto, la tarea genérica no tendría ningún vínculo: no se crea (el log no lleva datos de la QCS)
                    log.warn("DeadlineJob: una {} confidencial vencida no tiene proyecto; no se crea su tarea genérica de escalamiento", tracked.objectName)
                    return@count false
                }
                val (title, description) = NoticeTexts.qcsConfidentialEscalation(NoticeTexts.projectName(params, project), reached)
                return@count tasks.ensure(
                    confidentialEscalationKey(tracked.objectName, record.id, due, reached),
                    mapOf("title" to title, "description" to description, "project" to project),
                    TASK_REASON
                )
            }
            tasks.ensure(
                escalationKey(tracked.objectName, record.id, due, reached),
                mapOf(
                    "title" to "Escalamiento nivel $reached: ${OVERDUE.getValue(tracked.objectName).format(code)}",
                    "description" to "El plazo venció el $due y sigue abierto. Tómela quien atienda el escalamiento.",
                    "project" to record.attributes["project"],
                    tracked.objectName to record.id
                ),
                TASK_REASON
            )
        }
    }

    companion object {
        const val LOCK = "sgspe.deadline"
        const val NAME = "JOB-01 DeadlineJob"

        // la etiqueta de sus filas de auditoría (WAS-ADR-0050)
        const val SOURCE = "job:deadline"
        const val REASON = "situación de plazo ($NAME)"
        const val TASK_REASON = "tarea de escalamiento ($NAME)"
        const val REVIEW_REASON = "tarea de revisión de riesgo ($NAME)"
        private const val CHUNK = 500

        // la tarea de revisión de un riesgo (ASM-099): una por riesgo y próxima revisión
        fun reviewKey(
            risk: String,
            next: String
        ): String = RiskReviewTasks.key(risk, next)

        fun escalationKey(
            objectName: String,
            id: String,
            due: LocalDate,
            level: Int
        ): String = "escalation:$objectName:$id:$due:$level"

        // la de los niveles 1 y 2 de una QCS confidencial: la huella del registro (la del aviso genérico) en lugar de su id
        fun confidentialEscalationKey(
            objectName: String,
            id: String,
            due: LocalDate,
            level: Int
        ): String = JobTasks.confidentialEscalationKey(objectName, ConfidentialReaders.fingerprint(objectName, id, due), due, level)

        // la de un compromiso (F3)
        fun escalationKey(
            commitment: String,
            due: LocalDate,
            level: Int
        ): String = escalationKey("commitment", commitment, due, level)

        // cómo nombra la tarea de escalamiento al registro vencido, solo con su código
        val OVERDUE: Map<String, String> = mapOf("commitment" to "compromiso %s vencido", "grievance" to "queja %s vencida", "inquiry" to "consulta %s vencida")
    }
}

// el reloj de JOB-01, cada hora en la zona de Lima (ASM-006); sgspe.jobs.enabled=false lo apaga (las pruebas llaman a runOnce)
@Component
@EnableScheduling
@ConditionalOnProperty(name = ["sgspe.jobs.enabled"], havingValue = "true", matchIfMissing = true)
class DeadlineSchedule(
    private val job: DeadlineJob
) {
    @Scheduled(cron = "\${sgspe.jobs.deadline.cron:0 5 * * * *}", zone = "America/Lima")
    suspend fun hourly() {
        job.runOnce()
    }
}
