package sgspe.risk

import sgspe.common.ProjectParameters
import sgspe.common.Values
import java.time.LocalDate

// los nombres de los riesgos sociales (BC-08, database-model.md §13) que comparten RiskWriteGuard, el endpoint de revisiones,
// JOB-01, los avisos y JOB-04
object Risks {
    const val SOCIAL_RISK = "social_risk"
    const val ASSESSMENT = "risk_assessment"
    const val MEASURE = "mitigation_measure"
    const val ASSESS = "assess"
    const val IDENTIFIED = "identified"
    const val ASSESSED = "assessed"
    const val CLOSED = "closed"
    const val OWNER = "owner_user_id"
    const val OWNER_EMAIL = "owner_user_email"
    const val LEVEL = "risk_level"
    const val SCORE = "score"
    const val NEXT_REVIEW = "next_review_date"
    const val REVIEW_STATUS = "review_status"
    const val REVIEW_EVERY = "review_every_days"

    // los estados con puntaje vigente, en que JOB-01 calcula review_status (workflows.md «Plazos y escalamiento») y que
    // evalúa la regla NIVEL DE RIESGO de JOB-04
    val REVIEWED: Set<String> = setOf("assessed", "mitigating", "monitoring")

    // la transición de tratamiento que sigue en cada estado: sus roles reciben risk_escalated (workflows.md «Destinatarios por
    // rol»). un riesgo en identified llega a assessed con su primera revisión, así que su aviso va a los de decide_mitigation
    val NEXT_TREATMENT: Map<String, String> =
        mapOf(
            IDENTIFIED to "decide_mitigation",
            "assessed" to "decide_mitigation",
            "mitigating" to "start_monitoring",
            "monitoring" to "resume_mitigation"
        )

    // los niveles de riesgo de menor a mayor (catálogo «Nivel de riesgo») y los que escalan (RF-NTF-001)
    val LEVELS: List<String> = listOf("BAJO", "MEDIO", "ALTO", "CRITICO")
    val ESCALATED: Set<String> = setOf("ALTO", "CRITICO")
}

// la tarea de revisión de un riesgo (ASM-099): JOB-01 la crea con la clave risk_review:<riesgo>:<next_review_date> y la
// revisión la cierra (api.md §4.7). no entra en la cadena de «tarea vencida»: la revisión vencida avisa por risk_escalated
// (DeadlineNoticePlan.escalatesAsTask). `closing`, las transiciones de WF-N que llevan a done desde cada estado abierto
object RiskReviewTasks {
    const val TASK = "task"
    const val GENERATOR_KEY = "generator_key"
    private const val PREFIX = "risk_review:"

    // los estados abiertos de una tarea (Deadlines.TASK_OPEN) y lo que la lleva a done desde cada uno
    val OPEN: Set<String> = setOf("open", "in_progress", "blocked")
    private val CLOSING: Map<String, List<String>> =
        mapOf("open" to listOf("complete_open"), "in_progress" to listOf("complete"), "blocked" to listOf("unblock", "complete"))

    fun key(
        risk: String,
        next: String
    ): String = "$PREFIX$risk:$next"

    fun isReviewTask(key: String?): Boolean = key != null && key.startsWith(PREFIX)

    fun belongsTo(
        key: String?,
        risk: String
    ): Boolean = key != null && key.startsWith("$PREFIX$risk:")

    fun closing(state: String?): List<String> = CLOSING[state].orEmpty()
}

// los umbrales de nivel de un proyecto (ASM-004, RN-RSK-002): el puntaje mínimo de MEDIO, ALTO y CRITICO
data class RiskThresholds(
    val medium: Long,
    val high: Long,
    val critical: Long
) {
    // válidos como los deja ProjectWriteGuard: de 1 a 25 y crecientes. unos guardados antes de esa regla no lo son
    fun valid(): Boolean = listOf(medium, high, critical).all { it in 1..25 } && medium < high && high < critical

    fun level(score: Int): String =
        when {
            score >= critical -> Risks.LEVELS[3]
            score >= high -> Risks.LEVELS[2]
            score >= medium -> Risks.LEVELS[1]
            else -> Risks.LEVELS[0]
        }
}

// el nivel, la tendencia, el escalamiento y la cadencia de las revisiones de riesgo, puros (RN-RSK-001, RN-RSK-002, ASM-099)
object RiskLevels {
    const val UP = "AUMENTA"
    const val SAME = "SE MANTIENE"
    const val DOWN = "DISMINUYE"

    fun thresholds(project: Map<String, Any?>): RiskThresholds {
        val (medium, high, critical) = ProjectParameters.THRESHOLDS.map { ProjectParameters.integer(project, it) }
        return RiskThresholds(medium, high, critical)
    }

    // frente a la revisión anterior del mismo tipo, por el puntaje; null en la primera
    fun trend(
        previous: Int?,
        current: Int
    ): String? =
        when {
            previous == null -> null
            current > previous -> UP
            current < previous -> DOWN
            else -> SAME
        }

    // el orden de un nivel; -1 sin nivel
    fun rank(level: String?): Int = Risks.LEVELS.indexOf(level)

    // una revisión no residual escala si sube el nivel a ALTO o CRITICO: de uno menor, o de ALTO a CRITICO (RF-NTF-001)
    fun escalates(
        previous: String?,
        current: String?
    ): Boolean = current in Risks.ESCALATED && rank(current) > rank(previous)

    // la cadencia del riesgo: review_every_days (de 1 a 365, RiskWriteGuard) o, vacío, risk_review_days del proyecto (7 por
    // defecto)
    fun cadence(
        risk: Map<String, Any?>,
        project: Map<String, Any?>
    ): Long = Values.decimal(risk[Risks.REVIEW_EVERY])?.toLong() ?: ProjectParameters.integer(project, "risk_review_days")
}

// la situación de revisión de un riesgo, pura (ASM-099, workflows.md «Plazos y escalamiento»): AL DIA; POR REVISAR desde el día
// anterior a next_review_date y ese día; REVISION VENCIDA desde el día siguiente. la cadencia corre desde la última revisión
object RiskReview {
    const val ON_TIME = "AL DIA"
    const val DUE = "POR REVISAR"
    const val OVERDUE = "REVISION VENCIDA"

    fun status(
        next: LocalDate?,
        today: LocalDate
    ): String? =
        when {
            next == null -> null
            today.isAfter(next) -> OVERDUE
            !today.isBefore(next.minusDays(1)) -> DUE
            else -> ON_TIME
        }

    fun next(
        lastAssessment: LocalDate?,
        cadence: Long
    ): LocalDate? = lastAssessment?.plusDays(cadence)

    // lo que JOB-01 deja en un riesgo: en los estados revisados, next_review_date (última revisión + cadencia, o la guardada si
    // no hay revisiones que leer) y review_status; fuera de ellos, review_status vacío y next_review_date como estaba
    fun changes(
        state: String?,
        attributes: Map<String, Any?>,
        lastAssessment: LocalDate?,
        cadence: Long,
        today: LocalDate
    ): Map<String, Any?> {
        if (state !in Risks.REVIEWED) return mapOf(Risks.REVIEW_STATUS to null)
        val stored = Values.text(attributes[Risks.NEXT_REVIEW])?.let(LocalDate::parse)
        val next = next(lastAssessment, cadence) ?: stored
        return listOfNotNull(next?.let { Risks.NEXT_REVIEW to it.toString() }).toMap() + (Risks.REVIEW_STATUS to status(next, today))
    }
}
