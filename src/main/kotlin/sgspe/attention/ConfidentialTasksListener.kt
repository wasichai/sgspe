package sgspe.attention

import org.slf4j.LoggerFactory
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import sgspe.common.Attention
import sgspe.common.JobTasks
import sgspe.common.Records
import sgspe.common.Values
import sgspe.deadline.Deadlines
import sgspe.notification.ConfidentialReaders
import sgspe.notification.NoticeTexts
import wasichai.core.data.RecordChange
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordChangeListener
import java.time.LocalDate
import java.util.UUID

// las tareas automáticas de una QCS confidencial, puras (D-1 del 2026-10-10, ASM-102, security.md §9.6): ninguna lleva el id,
// el código ni el vínculo con la QCS. la de acuse de una queja confidencial reemplaza la de AUT-02 (que no corre en una
// confidencial: su condición) y lleva la huella de la fecha de recepción en Lima; al marcar confidencial una QCS existente,
// sus tareas automáticas abiertas (las de escalamiento de JOB-01 y, en una queja, la de acuse de AUT-02) pasan a las genéricas
object ConfidentialTasks {
    // la plantilla del título de AUT-02 (model/automations/grievance.json) con el código de la queja
    const val ACK_TITLE = "Acusar recibo de la queja %s"

    // escalation:<objeto>:<id>:<fecha>:<nivel> de JOB-01 (DeadlineJob.escalationKey) en los niveles 1 y 2
    private val ESCALATION = Regex("escalation:([a-z_]+):([^:]+):(\\d{4}-\\d{2}-\\d{2}):([12])")

    fun becameConfidential(
        before: Map<String, Any?>,
        after: Map<String, Any?>
    ): Boolean = !Attention.isConfidential(before) && Attention.isConfidential(after)

    // la clave de acuse de una queja confidencial: la huella con la fecha de recepción en Lima; null sin received_at
    fun ackKey(
        id: String,
        receivedAt: Any?
    ): String? {
        val received = QcsDueDates.receivedDate(receivedAt) ?: return null
        return JobTasks.confidentialAckKey(ConfidentialReaders.fingerprint(Attention.GRIEVANCE, id, received))
    }

    // la tarea de acuse genérica, sin responsable ni fecha (como AUT-02): solo el proyecto
    fun ackTask(
        project: String,
        projectName: String
    ): Map<String, Any?> {
        val (title, description) = NoticeTexts.qcsConfidentialAck(projectName)
        return mapOf("title" to title, "description" to description, "project" to project)
    }

    // los cambios de una tarea vinculada a la QCS `objectName` `id` que se acaba de marcar confidencial; null si no se toca
    // (cerrada, manual, de otro registro o sin con qué calcular la huella). `code`, el código de la QCS; `state`, el de la
    // tarea; `task`, sus atributos
    fun rewrite(
        objectName: String,
        id: String,
        code: String?,
        receivedAt: Any?,
        projectName: String,
        state: String?,
        task: Map<String, Any?>
    ): Map<String, Any?>? {
        // solo las abiertas (Deadlines.TASK_OPEN): una tarea cerrada no se reescribe, es historia
        if (state !in Deadlines.TASK_OPEN) return null
        val key = Values.text(task[JobTasks.GENERATOR_KEY])
        val escalation = key?.let { ESCALATION.matchEntire(it) }
        if (escalation != null) {
            val (taskObject, taskId, date, level) = escalation.destructured
            if (taskObject != objectName || taskId != id) return null
            val due = LocalDate.parse(date)
            val (title, description) = NoticeTexts.qcsConfidentialEscalation(projectName, level.toInt())
            return mapOf(
                JobTasks.GENERATOR_KEY to
                    JobTasks.confidentialEscalationKey(objectName, ConfidentialReaders.fingerprint(objectName, id, due), due, level.toInt()),
                "title" to title,
                "description" to description,
                objectName to null
            )
        }
        if (key != null || objectName != Attention.GRIEVANCE || code == null) return null
        if (Values.text(task["title"]) != ACK_TITLE.format(code)) return null
        val ack = ackKey(id, receivedAt) ?: return null
        val (title, description) = NoticeTexts.qcsConfidentialAck(projectName)
        return mapOf(JobTasks.GENERATOR_KEY to ack, "title" to title, "description" to description, objectName to null)
    }
}

// ConfidentialTasksListener (workflows.md, «Listeners»; D-1): después de QcsDueDatesListener (120) y de
// RequestClosureListener (130), con un orden propio para que sea determinista, en la transacción de la escritura,
// como quien escribe y con la marca (Records), nunca como la plataforma (wasichai no lo deja dentro de una petición). en el
// alta de una queja confidencial crea la tarea de acuse genérica (sin vínculo, con la clave de huella); al marcar confidencial
// una QCS existente reescribe sus tareas automáticas abiertas a las genéricas y vacía su vínculo. si una escritura falla,
// lanza y la escritura de la QCS se revierte. quitar la marca no revincula ni retitula nada. no usa JobTasks.ensure: hay una
// transacción abierta (su KDoc); comprueba la clave con un conteo y crea
@Component
@Order(131)
class ConfidentialTasksListener(
    private val records: Records
) : RecordChangeListener {
    private val log = LoggerFactory.getLogger(javaClass)

    override suspend fun recordChanged(change: RecordChange) {
        if (change.objectName !in Attention.OBJECTS) return
        val after = change.after ?: return
        when {
            change.kind == RecordChangeKind.CREATED && change.objectName == Attention.GRIEVANCE && Attention.isConfidential(after) ->
                created(change.recordId, after)
            change.kind == RecordChangeKind.UPDATED && change.before != null && ConfidentialTasks.becameConfidential(change.before.orEmpty(), after) ->
                marked(change.objectName, change.recordId, change.before.orEmpty(), after)
        }
    }

    private suspend fun created(
        id: UUID,
        after: Map<String, Any?>
    ) {
        val project = Values.text(after["project"])
        val key = ConfidentialTasks.ackKey(id.toString(), after[Attention.RECEIVED_AT])
        if (project == null || key == null) {
            // sin proyecto la tarea genérica no tendría vínculo, y sin fecha de recepción no hay huella (el log no lleva datos)
            log.warn("ConfidentialTasksListener: una queja confidencial sin proyecto o sin received_at; no se crea su tarea de acuse genérica")
            return
        }
        if (records.count(JobTasks.TASK, filters = mapOf(JobTasks.GENERATOR_KEY to key)) > 0) return
        records.create(JobTasks.TASK, ConfidentialTasks.ackTask(project, projectName(project)) + (JobTasks.GENERATOR_KEY to key), ACK_REASON)
    }

    private suspend fun marked(
        objectName: String,
        id: UUID,
        before: Map<String, Any?>,
        after: Map<String, Any?>
    ) {
        val project = Values.text(after["project"]) ?: Values.text(before["project"])
        if (project == null) {
            // sin proyecto la tarea genérica no nombraría nada ni tendría vínculo: no se reescribe (el log no lleva datos)
            log.warn("ConfidentialTasksListener: una QCS marcada confidencial no tiene proyecto; sus tareas automáticas quedan como están")
            return
        }
        val name = projectName(project)
        val code = Values.text(before["code"]) ?: Values.text(after["code"])
        val tasks = records.all(JobTasks.TASK, filters = mapOf(objectName to id.toString()))
        for (task in tasks) {
            val changes =
                ConfidentialTasks.rewrite(objectName, id.toString(), code, after[Attention.RECEIVED_AT], name, task.state, task.attributes) ?: continue
            val key = changes.getValue(JobTasks.GENERATOR_KEY) as String
            if (records.count(JobTasks.TASK, filters = mapOf(JobTasks.GENERATOR_KEY to key)) > 0) {
                // la genérica ya existe (se quitó la marca y se volvió a poner: JOB-01 creó la genérica y luego la normal): la
                // vieja queda como está (residual documentado en security.md §9.6); el log no lleva datos de la QCS
                log.warn("ConfidentialTasksListener: ya existe la tarea genérica de una QCS marcada confidencial; la tarea vinculada queda como está")
                continue
            }
            records.patch(JobTasks.TASK, UUID.fromString(task.id), changes, MARK_REASON)
        }
    }

    // el nombre del proyecto leído como quien escribe; si no lo lee, el id
    private suspend fun projectName(project: String): String {
        val attributes = Records.uuid(project)?.let { records.find("project", it)?.attributes }.orEmpty()
        return NoticeTexts.projectName(attributes, project)
    }

    companion object {
        const val ACK_REASON = "tarea de acuse genérica de una QCS confidencial"
        const val MARK_REASON = "tarea genérica: la QCS se marcó confidencial"
    }
}
