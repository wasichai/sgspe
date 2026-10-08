package sgspe.engagement

import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import sgspe.common.Finding
import sgspe.common.GuardOrder
import sgspe.common.ProjectParameters
import sgspe.common.Records
import sgspe.common.RuleGuard
import sgspe.common.Values
import sgspe.common.WriteView
import sgspe.common.isLink
import wasichai.core.common.ForbiddenException
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordResponse
import wasichai.core.data.RecordWrite
import java.math.BigDecimal

// las guardas de WF-C y de la lista de asistentes (workflows.md, domain-model §7.2): fecha programada para programar,
// realización con al menos un asistente, minuta para el acta, motivo de cancelación, cierre sin preguntas pendientes
// (RN-ENG-005) y cierre sin acta solo para los tipos que no la exigen (RN-ENG-004, ASM-033). desde F4, una pregunta
// derivada a una QCS también cuenta como atendida; ese paso lo agrega la derivación.
// los asistentes y las preguntas se leen como quien llama, en seco y al escribir por igual: no se puede usar asPlatform
// dentro de una petición. quien no puede leerlos no puede verificar la condición, y es un 403 que lo dice (como las partes
// de ProjectWriteGuard) y no un falso verde
@Component
@Order(GuardOrder.DOMAIN)
class EngagementWriteGuard(
    private val records: Records,
    private val view: WriteView
) : RuleGuard() {
    override suspend fun findings(
        change: RecordWrite,
        dryRun: Boolean
    ): List<Finding> =
        when {
            change.objectName == PARTICIPANT -> participant(change)
            change.objectName == ENGAGEMENT && change.kind == RecordChangeKind.TRANSITIONED -> transition(change)
            else -> emptyList()
        }

    private suspend fun participant(change: RecordWrite): List<Finding> {
        if (change.kind != RecordChangeKind.CREATED && change.kind != RecordChangeKind.UPDATED) return emptyList()
        if (change.isLink()) return emptyList()
        val count = Values.decimal(view.after(change)["represented_people_count"])
        if (count != null && count >= BigDecimal.ONE) return emptyList()
        return listOf(
            Finding(
                "REPRESENTED_PEOPLE",
                "represented_people_count es al menos 1: una fila representa a una persona o a un grupo",
                "represented_people_count",
                status = HttpStatus.BAD_REQUEST
            )
        )
    }

    private suspend fun transition(change: RecordWrite): List<Finding> {
        val engagement = change.before.orEmpty()
        return when (change.transition) {
            "schedule", "reschedule" -> required(engagement, "scheduled_at", "Programar exige la fecha y hora (scheduled_at)")
            "register_held", "register_held_unplanned" ->
                required(engagement, "held_at", "Registrar la realización exige la fecha y hora en que ocurrió (held_at)") + attendance(change)
            "draft_minutes", "submit_minutes" -> required(engagement, "minutes", "El acta necesita la minuta (minutes)")
            "close" -> pendingQuestions(change)
            "close_light" -> lightClose(change, engagement) + pendingQuestions(change)
            "cancel_planned", "cancel_scheduled", "cancel_postponed" ->
                required(engagement, "cancel_reason", "Cancelar exige el motivo de la cancelación (cancel_reason)")
            else -> emptyList()
        }
    }

    private fun required(
        engagement: Map<String, Any?>,
        field: String,
        message: String
    ): List<Finding> = if (Values.text(engagement[field]) == null) listOf(Finding("FIELD_REQUIRED", message, field)) else emptyList()

    private suspend fun attendance(change: RecordWrite): List<Finding> =
        when (val rows = childrenOf(change, PARTICIPANT)) {
            is Children.Unreadable -> listOf(rows.finding)
            is Children.Rows ->
                if (rows.records.any { Values.bool(it.attributes["attended"]) == true }) {
                    emptyList()
                } else {
                    listOf(Finding("ATTENDANCE_REQUIRED", "Registrar la realización exige al menos un participante que asistió (attended)", "attended"))
                }
        }

    private suspend fun pendingQuestions(change: RecordWrite): List<Finding> =
        when (val rows = childrenOf(change, QUESTION)) {
            is Children.Unreadable -> listOf(rows.finding)
            is Children.Rows -> {
                val pending = rows.records.count { !answered(it.attributes) }
                if (pending == 0) {
                    emptyList()
                } else {
                    listOf(Finding("PENDING_QUESTIONS", "Quedan $pending preguntas sin respuesta: respóndalas antes de cerrar (RN-ENG-005)", QUESTION))
                }
            }
        }

    private sealed interface Children {
        class Rows(
            val records: List<RecordResponse>
        ) : Children

        class Unreadable(
            val finding: Finding
        ) : Children
    }

    private suspend fun childrenOf(
        change: RecordWrite,
        objectName: String
    ): Children {
        val id = change.recordId ?: return Children.Rows(emptyList())
        return try {
            Children.Rows(records.all(objectName, filters = mapOf("engagement" to id.toString())))
        } catch (_: ForbiddenException) {
            Children.Unreadable(
                Finding(
                    "OBJECT_NOT_READABLE",
                    "No puedes leer $objectName, así que no se puede verificar la condición de la transición",
                    objectName,
                    status = HttpStatus.FORBIDDEN
                )
            )
        }
    }

    private suspend fun lightClose(
        change: RecordWrite,
        engagement: Map<String, Any?>
    ): List<Finding> {
        val type = Values.text(engagement["engagement_type"])
        val project = Records.uuid(engagement["project"])?.let { records.find("project", it)?.attributes }.orEmpty()
        val onlyFormal = ProjectParameters.policy(project, "policy_minutes_only_formal_types")
        if (onlyFormal && type !in FORMAL_TYPES) return emptyList()
        val why = if (onlyFormal) "una interacción de tipo $type cierra con acta aprobada" else "el proyecto exige acta en todas las interacciones"
        return listOf(Finding("MINUTES_REQUIRED", "No se puede cerrar sin acta: $why (RN-ENG-004)", "engagement_type"))
    }

    companion object {
        const val ENGAGEMENT = "engagement"
        const val PARTICIPANT = "engagement_participant"
        const val QUESTION = "engagement_question"

        // los tipos formales de RN-ENG-004: la única fuente. el tablero (KPI-ENG) y la matriz de 2.1 los leen de aquí
        val FORMAL_TYPES = setOf("REUNION", "REUNION INFORMATIVA", "TALLER", "ASAMBLEA", "MESA DE DIALOGO")

        // los estados de WF-C desde que se registró la realización, sin los de antes (planned, scheduled, postponed) ni
        // cancelled: lo que cuenta como una interacción que ocurrió (seguimiento de actores y tableros)
        val HELD_STATES = setOf("held", "minutes_drafted", "minutes_in_review", "minutes_approved", "closed")

        // atendida: respondida en el lugar o con su respuesta posterior (answered_at)
        fun answered(question: Map<String, Any?>): Boolean = Values.bool(question["answered_on_site"]) == true || Values.text(question["answered_at"]) != null
    }
}
