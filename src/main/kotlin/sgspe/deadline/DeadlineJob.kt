package sgspe.deadline

import kotlinx.coroutines.CancellationException
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import sgspe.commitment.Commitments
import sgspe.common.JobTasks
import sgspe.common.LIMA
import sgspe.common.Organizations
import sgspe.common.ProjectParameters
import sgspe.common.Records
import sgspe.common.Transactions
import sgspe.common.Values
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
                written += asPlatform(organization) { refresh(organization) }
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
// cada organización con el modelo de F3 (TenantDirectory). escribe deadline_status de compromisos y tareas y validity_status de
// acuerdos con los parámetros de su proyecto, solo si cambia (P11) y bajo el candado del registro, con el motivo que nombra al
// trabajo (P16); crea la tarea de escalamiento de los niveles 1 y 2 de un compromiso vencido (generator_key, sin asignado ni
// fecha: la toma uno de los avisados). nunca aplica transiciones (RN-WFL-001). el día de Lima se lee una vez por vuelta: todas
// las organizaciones se miden con la misma fecha. los avisos de plazo los publica DeadlineNotices, que lee lo que este trabajo
// escribió
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
            DeadlineSweep(
                { organizations.having(Commitments.COMMITMENT) },
                { organization, block -> records.asPlatform(organization, SOURCE, block) },
                { organization -> refresh(organization, today) }
            ).run()
        }
    }

    private suspend fun refresh(
        organization: UUID,
        today: LocalDate
    ): Int {
        val projects = mutableMapOf<String, Map<String, Any?>>()

        suspend fun project(id: String?): Map<String, Any?> =
            if (id == null) emptyMap() else projects.getOrPut(id) { records.find("project", UUID.fromString(id))?.attributes.orEmpty() }
        var written = 0
        var created = 0
        var failed = 0
        for (tracked in Deadlines.TRACKED) {
            for (row in records.all(tracked.objectName)) {
                try {
                    val params = project(Values.text(row.attributes["project"]))
                    if (write(tracked, row, today, ProjectParameters.integer(params, "due_soon_days"))) written++
                    // JobTasks.ensure va fuera de toda transacción: write ya cerró la suya (un único duplicado aborta la abierta)
                    if (tracked.objectName == Commitments.COMMITMENT) created += escalate(row, today, params)
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

    // true si escribió; relee bajo el candado y recalcula, por si una persona lo cambió entretanto
    private suspend fun write(
        tracked: Deadlines.Tracked,
        row: RecordResponse,
        today: LocalDate,
        dueSoonDays: Long
    ): Boolean {
        val wanted = Deadlines.wanted(tracked, row.state, row.attributes, today, dueSoonDays)
        if (Values.same(row.attributes[tracked.field], wanted)) return false
        val id = UUID.fromString(row.id)
        return transactions.locked("${tracked.objectName}:$id") {
            val current = records.find(tracked.objectName, id) ?: return@locked false
            val again = Deadlines.wanted(tracked, current.state, current.attributes, today, dueSoonDays)
            if (Values.same(current.attributes[tracked.field], again)) {
                false
            } else {
                records.patch(tracked.objectName, id, mapOf(tracked.field to again), REASON)
                true
            }
        }
    }

    // la tarea de cada nivel alcanzado de un compromiso vencido y pendiente: una por compromiso, fecha y nivel
    private suspend fun escalate(
        row: RecordResponse,
        today: LocalDate,
        params: Map<String, Any?>
    ): Int {
        if (row.state !in Commitments.PENDING) return 0
        val due = Values.text(row.attributes["due_date"])?.let(LocalDate::parse) ?: return 0
        if (!today.isAfter(due)) return 0
        val level =
            Deadlines.level(due, today, ProjectParameters.integer(params, "escalation_first_days"), ProjectParameters.integer(params, "escalation_second_days"))
        val code = Values.text(row.attributes["code"]) ?: row.id
        return (1..level).count { reached ->
            tasks.ensure(
                escalationKey(row.id, due, reached),
                mapOf(
                    "title" to "Escalamiento nivel $reached: compromiso $code vencido",
                    "description" to "El compromiso venció el $due y sigue abierto. Tómela quien atienda el escalamiento.",
                    "project" to row.attributes["project"],
                    "commitment" to row.id
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

        fun escalationKey(
            commitment: String,
            due: LocalDate,
            level: Int
        ): String = "escalation:${Commitments.COMMITMENT}:$commitment:$due:$level"
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
