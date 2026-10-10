package sgspe.warning

import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import sgspe.common.Callers
import sgspe.common.Finding
import sgspe.common.GuardOrder
import sgspe.common.Permissions
import sgspe.common.RuleGuard
import sgspe.common.Values
import sgspe.common.WriteView
import sgspe.common.isLink
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordWrite
import wasichai.core.identity.AuthenticatedUser
import java.math.BigDecimal

// las reglas de WF-J, WF-M y de las reglas de alerta (workflows.md, especificación de F4 §3.7 y §4), puras
object WarningGuardRules {
    const val THRESHOLD = "threshold"
    const val WINDOW = "window_days"
    const val KIND = "rule_kind"

    // confirmar o descartar una alerta es una decisión humana (RN-EWS-001, RQ-SEC-014): nunca la plataforma, una
    // automatización ni una cuenta de servicio, aunque tenga el rol; y exige la acción CONFIRM_ALERT (doble llave)
    fun decision(
        user: AuthenticatedUser?,
        holdsConfirm: Boolean
    ): List<Finding> =
        when {
            user == null || isServiceAccount(user) ->
                listOf(
                    Finding(
                        "HUMAN_DECISION_REQUIRED",
                        "Confirmar o descartar una alerta lo decide una persona, nunca la plataforma ni una cuenta de servicio (RN-EWS-001)",
                        status = HttpStatus.FORBIDDEN
                    )
                )
            !holdsConfirm ->
                listOf(Finding("ACTION_REQUIRED", "Confirmar o descartar una alerta exige la acción CONFIRM_ALERT", status = HttpStatus.FORBIDDEN))
            else -> emptyList()
        }

    fun isServiceAccount(user: AuthenticatedUser): Boolean = user.serviceAccount != null || user.email.endsWith(Warnings.SERVICE_ACCOUNT_DOMAIN)

    // cerrar un incidente exige su respuesta inmediata y sus consecuencias (RF-EWS-001)
    fun incidentClose(record: Map<String, Any?>): List<Finding> =
        listOf("immediate_response", "consequences").filter { Values.text(record[it]) == null }.map { field ->
            Finding("FIELD_REQUIRED", "Cerrar un incidente exige $field", field, status = HttpStatus.BAD_REQUEST)
        }

    // una regla de alerta: threshold entero (de 1 en adelante; de 1 a 25 en NIVEL DE RIESGO) y window_days vacío o entero de 1 a
    // 365 (ASM-097). se juzga si la escritura cambia el umbral, la ventana o el tipo; un alta, siempre
    fun rule(
        after: Map<String, Any?>,
        changed: Set<String>
    ): List<Finding> {
        if (changed.none { it in setOf(THRESHOLD, WINDOW, KIND) }) return emptyList()
        val findings = mutableListOf<Finding>()
        val max = if (Values.text(after[KIND]) == Warnings.RISK_LEVEL) Warnings.MAX_SCORE else Long.MAX_VALUE
        val threshold = whole(after[THRESHOLD])
        if (threshold == null || threshold < 1 || threshold > max) {
            val range = if (max == Warnings.MAX_SCORE) "un puntaje entero de 1 a 25" else "un número entero desde 1"
            findings += outOfRange(THRESHOLD, "el umbral es $range")
        }
        if (Values.text(after[WINDOW]) != null) {
            val window = whole(after[WINDOW])
            if (window == null || window !in 1..Warnings.MAX_WINDOW) findings += outOfRange(WINDOW, "la ventana es un número entero de días de 1 a 365")
        }
        return findings
    }

    // un número entero, venga como Long, BigDecimal o texto; null si falta o tiene parte fraccionaria
    private fun whole(value: Any?): Long? {
        val number: BigDecimal = Values.decimal(value) ?: return null
        return runCatching { number.stripTrailingZeros().longValueExact() }.getOrNull()
    }

    private fun outOfRange(
        field: String,
        detail: String
    ) = Finding("OUT_OF_RANGE", detail, field, status = HttpStatus.BAD_REQUEST)
}

// WarningWriteGuard (sgspe.warning, especificación de F4 §3.7): la decisión humana y la doble llave de confirm y dismiss de
// WF-J, el cierre de un incidente de WF-M y la validez de las reglas de alerta, por cualquier puerta (la API genérica, la
// consulta en seco y JOB-04). `dismiss` exige además motivo (TransitionReasonGuard)
@Component
@Order(GuardOrder.DOMAIN)
class WarningWriteGuard(
    private val callers: Callers,
    private val permissions: Permissions,
    private val view: WriteView
) : RuleGuard() {
    override suspend fun findings(
        change: RecordWrite,
        dryRun: Boolean
    ): List<Finding> =
        when {
            change.objectName == Warnings.WARNING && change.kind == RecordChangeKind.TRANSITIONED && change.transition in Warnings.DECISIONS -> {
                val user = if (change.userId == null) null else callers.userOrNull()
                WarningGuardRules.decision(user, user != null && permissions.has(user, Warnings.CONFIRM_ALERT, Warnings.WARNING))
            }
            change.objectName == Warnings.INCIDENT && change.kind == RecordChangeKind.TRANSITIONED && change.transition == Warnings.INCIDENT_CLOSE ->
                WarningGuardRules.incidentClose(change.before.orEmpty())
            change.objectName == Warnings.RULE &&
                (change.kind == RecordChangeKind.CREATED || change.kind == RecordChangeKind.UPDATED) &&
                !change.isLink() -> {
                val after = view.after(change)
                val changed = if (change.kind == RecordChangeKind.CREATED) after.keys else view.changed(change, after)
                WarningGuardRules.rule(after, changed)
            }
            else -> emptyList()
        }
}
