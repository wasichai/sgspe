package sgspe.common

import org.springframework.http.HttpStatus
import java.math.BigDecimal

// los parámetros y políticas de `project` con su valor por defecto (database-model.md §6, ASM-019). desde 0.5.0
// wasichai aplica el defaultValue a un alta que omite el campo (UPS-15), no a un cambio ni a un null explícito: un
// parámetro vacío vale lo que dice la ficha, y todo el código de sgspe lo lee aquí para que el tablero, la guarda y el
// endpoint usen el mismo valor
object ProjectParameters {
    private val DECIMALS = mapOf("interest_threshold" to BigDecimal("3"), "power_threshold" to BigDecimal("3"))
    private val INTEGERS =
        mapOf(
            "stakeholder_review_days" to 30L,
            "inactivity_days" to 90L,
            "meetings_per_month_target" to 2L,
            // F3: plazos y escalamiento (ASM-006, ASM-030)
            "due_soon_days" to 7L,
            "escalation_first_days" to 7L,
            "escalation_second_days" to 15L,
            // F4: plazos de QCS (ASM-005, ASM-100), cadencia de revisión de riesgos (RN-RSK-001) y umbrales de nivel (ASM-004)
            "grievance_ack_days" to 5L,
            "inquiry_response_days" to 15L,
            "grievance_resolution_days" to 30L,
            "risk_review_days" to 7L,
            "risk_medium_min_score" to 5L,
            "risk_high_min_score" to 10L,
            "risk_critical_min_score" to 15L
        )
    private val POLICIES =
        mapOf(
            "policy_minutes_only_formal_types" to true,
            "policy_activation_preconditions" to true,
            // F3: compromisos (RN-ENG-001, RN-ENG-002, ASM-008) y tope de apoyo social (RN-INV-003)
            "policy_workshop_commitments_blocked" to false,
            "policy_senior_approval_for_dialogue" to true,
            "policy_internal_approval" to true,
            "policy_cap_blocks_approval" to true,
            // F4: una queja ALTA o CRITICA la resuelve quien tiene APPROVE sobre grievance (RN-GRV-002)
            "policy_high_severity_requires_approver" to true,
            // F5: los lineamientos de apoyo de los aportes (RN-INV-001, RN-INV-002, RN-INV-005) y la sinergia (RN-INV-004)
            "policy_no_paid_labor" to true,
            "policy_non_monetary_donations" to true,
            "policy_synergy_requires_agreement" to true,
            "policy_priority_warning" to true
        )

    // F4: los plazos y la cadencia van de 1 a 365 días; los umbrales de nivel de riesgo, de 1 a 25 y crecientes (ASM-004)
    val PERIODS: List<String> = listOf("grievance_ack_days", "inquiry_response_days", "grievance_resolution_days", "risk_review_days")
    val THRESHOLDS: List<String> = listOf("risk_medium_min_score", "risk_high_min_score", "risk_critical_min_score")

    fun decimal(
        project: Map<String, Any?>,
        name: String
    ): BigDecimal = Values.decimal(project[name]) ?: DECIMALS.getValue(name)

    fun integer(
        project: Map<String, Any?>,
        name: String
    ): Long = Values.decimal(project[name])?.toLong() ?: INTEGERS.getValue(name)

    fun policy(
        project: Map<String, Any?>,
        name: String
    ): Boolean = Values.bool(project[name]) ?: POLICIES.getValue(name)

    // los 400 OUT_OF_RANGE de los parámetros de F4 que la escritura cambia (`changed`), con el proyecto como queda (`after`):
    // un plazo o una cadencia fuera de 1 a 365, o no entero; un umbral fuera de 1 a 25, no entero, o que no crece
    // estrictamente (medio < alto < crítico, con el valor por defecto en los vacíos). lo aplica ProjectWriteGuard; un valor
    // guardado antes de la regla no bloquea otra escritura del proyecto
    fun outOfRange(
        after: Map<String, Any?>,
        changed: Set<String>
    ): List<Finding> {
        val findings = mutableListOf<Finding>()
        for (name in PERIODS.filter { it in changed }) {
            if (!within(after[name], 1, 365)) findings += outOfRange(name, "debe ser un número entero de días de 1 a 365")
        }
        val touched = THRESHOLDS.filter { it in changed }
        if (touched.isEmpty()) return findings
        val wrong = touched.filter { !within(after[it], 1, 25) }
        findings += wrong.map { outOfRange(it, "debe ser un puntaje entero de 1 a 25") }
        if (wrong.isEmpty()) {
            val values = THRESHOLDS.map { integer(after, it) }
            if (values.zipWithNext().any { (low, high) -> low >= high }) {
                findings += outOfRange(touched.first(), "los umbrales crecen estrictamente: medio < alto < crítico (hoy ${values.joinToString(", ")})")
            }
        }
        return findings
    }

    // vacío vale el valor por defecto, que está en rango
    private fun within(
        value: Any?,
        min: Long,
        max: Long
    ): Boolean {
        val number = Values.decimal(value) ?: return Values.text(value) == null
        val whole = runCatching { number.stripTrailingZeros().longValueExact() }.getOrNull() ?: return false
        return whole in min..max
    }

    private fun outOfRange(
        field: String,
        detail: String
    ): Finding = Finding("OUT_OF_RANGE", "$field $detail", field, status = HttpStatus.BAD_REQUEST)
}
