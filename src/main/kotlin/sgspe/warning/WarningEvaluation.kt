package sgspe.warning

import sgspe.common.Values
import java.time.Instant
import java.time.LocalDate

// una regla de alerta habilitada como la evalúa JOB-04 (especificación de F4 §4, ASM-097): `project` vacío es una regla del
// tenant; window_days vacío vale 7; severity vacía, MEDIO
data class WarningRule(
    val id: String,
    val name: String,
    val kind: String,
    val project: String?,
    val threshold: Long,
    val windowDays: Long,
    val severity: String
)

// el riesgo que dispara una regla NIVEL DE RIESGO, con lo que la alerta copia: su código para el texto y su territorio
data class RiskHit(
    val id: String,
    val code: String?,
    val score: Long,
    val locality: String?,
    val community: String?
)

// los seis tipos de regla de JOB-04, puros (workflows.md «Tipos de regla de JOB-04», ASM-097): qué reglas aplican a un proyecto,
// la ventana de días de Lima que terminan hoy, cuándo dispara una regla y la alerta que crea. ningún texto lleva datos
// personales, texto libre de otros registros ni valores de campos restringidos: solo el conteo, el umbral, la ventana y, en
// NIVEL DE RIESGO, el código y el puntaje del riesgo
object WarningEvaluation {
    const val DEFAULT_SEVERITY = "MEDIO"

    // la regla guardada, o null si no se puede evaluar (un tipo fuera del catálogo, un umbral o una ventana inválidos guardados
    // antes de WarningWriteGuard): JOB-04 la salta y lo registra
    fun parse(
        id: String,
        attributes: Map<String, Any?>
    ): WarningRule? {
        val kind = Values.text(attributes["rule_kind"])?.takeIf { it in Warnings.KINDS } ?: return null
        val threshold = whole(attributes["threshold"])?.takeIf { it >= 1 && (kind != Warnings.RISK_LEVEL || it <= Warnings.MAX_SCORE) } ?: return null
        val window =
            if (Values.text(attributes["window_days"]) == null) {
                Warnings.DEFAULT_WINDOW
            } else {
                whole(attributes["window_days"])?.takeIf { it in 1..Warnings.MAX_WINDOW } ?: return null
            }
        return WarningRule(
            id,
            Values.text(attributes["name"]) ?: kind,
            kind,
            Values.text(attributes["project"]),
            threshold,
            window,
            Values.text(attributes["severity"]) ?: DEFAULT_SEVERITY
        )
    }

    // las reglas de un proyecto: las suyas y, de cada tipo que no tiene, las del tenant. una regla del proyecto reemplaza a la
    // del tenant del mismo tipo (ASM-097)
    fun applicable(
        rules: List<WarningRule>,
        project: String
    ): List<WarningRule> {
        val own = rules.filter { it.project == project }
        val kinds = own.map { it.kind }.toSet()
        return own + rules.filter { it.project == null && it.kind !in kinds }
    }

    // el primer día de la ventana: los window_days días de Lima que terminan hoy, desde el inicio de hoy − window_days + 1
    fun windowStart(
        today: LocalDate,
        days: Long
    ): LocalDate = today.minusDays(days - 1)

    // dispara cuando el valor medido alcanza el umbral
    fun fires(
        value: Long,
        threshold: Long
    ): Boolean = value >= threshold

    fun title(rule: WarningRule): String = "${rule.name}: ${rule.kind}"

    fun description(
        rule: WarningRule,
        value: Long,
        risk: RiskHit? = null
    ): String {
        val w = rule.windowDays
        val t = rule.threshold
        return when (rule.kind) {
            Warnings.SIGNALS -> "$value señales en $w días (umbral $t)"
            Warnings.QCS -> "$value QCS recibidas en $w días (umbral $t)"
            Warnings.OVERDUE_COMMITMENTS -> "$value compromisos vencidos (umbral $t)"
            Warnings.NEGATIVE_MENTIONS -> "$value menciones negativas en $w días (umbral $t)"
            Warnings.INCIDENTS -> "$value incidentes en $w días (umbral $t)"
            else -> "Riesgo ${risk?.code ?: risk?.id} con puntaje $value (umbral $t)"
        }
    }

    // la clave única de la alerta: regla, proyecto, riesgo (en NIVEL DE RIESGO) y fecha de Lima. frena una carrera entre réplicas
    fun generatorKey(
        rule: WarningRule,
        project: String,
        risk: String?,
        today: LocalDate
    ): String = "warning_rule:${rule.id}:$project${risk?.let { ":$it" }.orEmpty()}:$today"

    // la alerta que crea JOB-04, en detected (WF-J): nunca confirmada ni con datos de una persona
    fun alert(
        rule: WarningRule,
        project: String,
        value: Long,
        risk: RiskHit?,
        now: Instant,
        today: LocalDate
    ): Map<String, Any?> =
        mapOf(
            "title" to title(rule),
            "trigger_description" to description(rule, value, risk),
            "severity" to rule.severity,
            "detected_at" to now.toString(),
            "early_warning_rule" to rule.id,
            "project" to project,
            "ai_detected" to false,
            "generator_key" to generatorKey(rule, project, risk?.id, today)
        ) +
            if (risk == null) {
                emptyMap()
            } else {
                listOfNotNull("social_risk" to risk.id, risk.locality?.let { "locality" to it }, risk.community?.let { "community" to it }).toMap()
            }

    private fun whole(value: Any?): Long? = Values.decimal(value)?.let { runCatching { it.stripTrailingZeros().longValueExact() }.getOrNull() }
}
