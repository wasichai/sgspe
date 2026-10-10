package sgspe.risk

import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import sgspe.common.Criteria
import sgspe.common.Finding
import sgspe.common.GuardOrder
import sgspe.common.NotReadable
import sgspe.common.Records
import sgspe.common.RuleGuard
import sgspe.common.Values
import sgspe.common.WriteView
import sgspe.common.isLink
import sgspe.common.readAs
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordWrite

// las reglas de datos de WF-F (workflows.md, especificación de F4 §3.6), puras: lo que cada transición exige del riesgo como
// está guardado. RiskWriteGuard suma las que leen las medidas y las revisiones
object RiskRules {
    const val MITIGATE = "MITIGAR"
    val MONITOR: Set<String> = setOf("MONITOREAR", "ACEPTAR")
    const val TREATMENT = "treatment_decision"
    const val CLOSE_REASON = "close_reason"

    val CLOSE: Set<String> = setOf("close_identified", "close_assessed", "close_mitigating", "close_monitoring")

    // las que exigen una medida con responsable (RN-RSK-003) y las que exigen una revisión residual
    val NEEDS_MEASURE: Set<String> = setOf("decide_mitigation", "resume_mitigation")
    val NEEDS_RESIDUAL: Set<String> = setOf("decide_monitoring", "start_monitoring")

    // la cadencia propia de un riesgo, en días, como la del proyecto (ProjectParameters.PERIODS)
    const val MAX_REVIEW_EVERY = 365L

    // assess con probabilidad, impacto y responsable (RF-RSK-001); la decisión de tratamiento que cada camino exige; el motivo de
    // cierre. todo 400 FIELD_REQUIRED sobre el campo: lo que falta lo completa quien transiciona
    fun transition(
        name: String,
        record: Map<String, Any?>
    ): List<Finding> {
        val required =
            when (name) {
                Risks.ASSESS -> listOf("probability", "impact", Risks.OWNER)
                in CLOSE -> listOf(CLOSE_REASON)
                else -> emptyList()
            }
        val findings =
            required.filter { Values.text(record[it]) == null }.mapTo(mutableListOf()) { field ->
                Finding("FIELD_REQUIRED", "La transición $name exige $field", field, status = HttpStatus.BAD_REQUEST)
            }
        val decision = Values.text(record[TREATMENT])
        if (name == "decide_mitigation" && decision != MITIGATE) {
            findings += Finding("FIELD_REQUIRED", "decide_mitigation exige treatment_decision $MITIGATE", TREATMENT, status = HttpStatus.BAD_REQUEST)
        }
        if (name == "decide_monitoring" && decision !in MONITOR) {
            findings += Finding("FIELD_REQUIRED", "decide_monitoring exige treatment_decision MONITOREAR o ACEPTAR", TREATMENT, status = HttpStatus.BAD_REQUEST)
        }
        return findings
    }

    // la cadencia propia (decisión 16 del plan 4.2b): vacía vale risk_review_days del proyecto; si no, un número entero de días
    // de 1 a 365. se juzga si la escritura la cambia; en un alta, siempre que llegue. 400 OUT_OF_RANGE sobre el campo
    fun reviewEvery(
        after: Map<String, Any?>,
        changed: Set<String>
    ): List<Finding> {
        if (Risks.REVIEW_EVERY !in changed || Values.text(after[Risks.REVIEW_EVERY]) == null) return emptyList()
        val days = Values.decimal(after[Risks.REVIEW_EVERY])?.let { runCatching { it.stripTrailingZeros().longValueExact() }.getOrNull() }
        if (days != null && days in 1..MAX_REVIEW_EVERY) return emptyList()
        return listOf(
            Finding(
                "OUT_OF_RANGE",
                "review_every_days es un número entero de días de 1 a 365; vacío, rige risk_review_days del proyecto",
                Risks.REVIEW_EVERY,
                status = HttpStatus.BAD_REQUEST
            )
        )
    }

    fun measureRequired(): Finding =
        Finding("MEASURE_OWNER_REQUIRED", "Mitigar exige al menos una medida de mitigación con responsable (RN-RSK-003)", Risks.MEASURE)

    fun residualRequired(): Finding =
        Finding("RESIDUAL_ASSESSMENT_REQUIRED", "Monitorear exige al menos una revisión residual del riesgo (RN-RSK-003)", Risks.ASSESSMENT)
}

// RiskWriteGuard (sgspe.risk, especificación de F4 §3.6): las reglas de WF-F por cualquier puerta (la API genérica, el endpoint
// de revisiones y la consulta en seco). las de datos son RiskRules; aquí se suman las que leen, como quien transiciona, las
// medidas del riesgo (409 MEASURE_OWNER_REQUIRED) y sus revisiones residuales (409 RESIDUAL_ASSESSMENT_REQUIRED). lo que no
// se puede leer es 403 OBJECT_NOT_READABLE, nunca un pase. en el alta y el cambio, la cadencia propia (400 OUT_OF_RANGE)
@Component
@Order(GuardOrder.DOMAIN)
class RiskWriteGuard(
    private val records: Records,
    private val view: WriteView
) : RuleGuard() {
    override suspend fun findings(
        change: RecordWrite,
        dryRun: Boolean
    ): List<Finding> {
        if (change.objectName != Risks.SOCIAL_RISK) return emptyList()
        if ((change.kind == RecordChangeKind.CREATED || change.kind == RecordChangeKind.UPDATED) && !change.isLink()) {
            val after = view.after(change)
            val changed = if (change.kind == RecordChangeKind.CREATED) after.keys else view.changed(change, after)
            return RiskRules.reviewEvery(after, changed)
        }
        if (change.kind != RecordChangeKind.TRANSITIONED) return emptyList()
        val name = change.transition ?: return emptyList()
        val id = change.recordId ?: return emptyList()
        val findings = RiskRules.transition(name, change.before.orEmpty()).toMutableList()
        if (findings.any { it.blocking }) return findings
        return try {
            if (name in RiskRules.NEEDS_MEASURE && !measureWithOwner(id.toString())) findings += RiskRules.measureRequired()
            if (name in RiskRules.NEEDS_RESIDUAL && !residualAssessed(id.toString())) findings += RiskRules.residualRequired()
            findings
        } catch (e: NotReadable) {
            listOf(
                Finding(
                    "OBJECT_NOT_READABLE",
                    "La regla necesita leer ${e.objectName}: el registro no existe o quien escribe no puede leerlo",
                    e.objectName,
                    status = HttpStatus.FORBIDDEN
                )
            )
        }
    }

    private suspend fun measureWithOwner(risk: String): Boolean =
        readAs(Risks.MEASURE) { records.count(Risks.MEASURE, mapOf(Risks.SOCIAL_RISK to risk), listOf(Criteria.filled(Risks.OWNER))) } > 0

    private suspend fun residualAssessed(risk: String): Boolean =
        readAs(Risks.ASSESSMENT) { records.count(Risks.ASSESSMENT, mapOf(Risks.SOCIAL_RISK to risk, RESIDUAL to "true")) } > 0

    companion object {
        const val RESIDUAL = "residual"
    }
}
