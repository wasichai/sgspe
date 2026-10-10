package sgspe.risk

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ServerWebExchange
import sgspe.common.Callers
import sgspe.common.CommandAnswer
import sgspe.common.Finding
import sgspe.common.Idempotency
import sgspe.common.IdempotencyKey
import sgspe.common.LIMA
import sgspe.common.ModelCatalog
import sgspe.common.Permissions
import sgspe.common.Records
import sgspe.common.StrictBody
import sgspe.common.StrictQuery
import sgspe.common.Transactions
import sgspe.common.TransitionRoles
import sgspe.common.Values
import sgspe.common.toCodedException
import wasichai.core.common.Actions
import wasichai.core.common.ValidationException
import wasichai.core.data.ChangeReason
import wasichai.core.data.RecordResponse
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class RiskAssessmentResult(
    val assessment: RecordResponse,
    val risk: RecordResponse
)

// lo que manda el cliente en `attributes`, validado (api.md §4.7): probabilidad e impacto enteros de 1 a 5, la fecha no futura
// en Lima, residual verdadero o falso (falso si no viene) y las notas. lo calculado (puntaje, nivel, tendencia) no se manda
class RiskAssessmentInput(
    val date: LocalDate,
    val probability: Int,
    val impact: Int,
    val residual: Boolean,
    val notes: String?
) {
    val score: Int get() = probability * impact

    companion object {
        val ALLOWED: Set<String> = setOf("assessment_date", "probability", "impact", "residual", "notes")

        fun parse(
            body: Map<String, Any?>,
            today: LocalDate
        ): RiskAssessmentInput {
            StrictBody.requireOnly(body, setOf("attributes"))
            val attributes = StrictBody.objectOf(body, "attributes")
            StrictBody.requireOnly(attributes, ALLOWED, "attributes.")
            for (name in listOf("assessment_date", "probability", "impact")) {
                if (Values.text(attributes[name]) == null) {
                    throw Finding("FIELD_REQUIRED", "La revisión exige $name", "attributes.$name", status = HttpStatus.BAD_REQUEST).toCodedException()
                }
            }
            val date =
                runCatching { LocalDate.parse(attributes["assessment_date"].toString()) }.getOrNull()
                    ?: throw outOfRange("assessment_date", "debe ser una fecha AAAA-MM-DD")
            if (date.isAfter(today)) throw outOfRange("assessment_date", "no puede ser futura")
            val residual =
                when (val raw = attributes["residual"]) {
                    null -> false
                    is Boolean -> raw
                    else -> throw ValidationException("Revisión inválida", "attributes.residual", "debe ser verdadero o falso")
                }
            return RiskAssessmentInput(
                date,
                scale(attributes["probability"], "probability"),
                scale(attributes["impact"], "impact"),
                residual,
                Values.text(attributes["notes"])
            )
        }

        // un número de json entero de 1 a 5: un texto como "4" no es una escala
        private fun scale(
            value: Any?,
            name: String
        ): Int {
            val number = (value as? Number)?.let(Values::decimal)
            if (number == null || number.stripTrailingZeros().scale() > 0 || number < BigDecimal.ONE || number > BigDecimal(5)) {
                throw outOfRange(name, "debe ser un entero de 1 a 5")
            }
            return number.toInt()
        }

        private fun outOfRange(
            name: String,
            detail: String
        ) = Finding("OUT_OF_RANGE", "$name $detail", "attributes.$name", status = HttpStatus.BAD_REQUEST).toCodedException()
    }
}

// una revisión guardada, como la lee el plan de la siguiente
data class AssessmentRow(
    val id: String,
    val date: LocalDate,
    val residual: Boolean,
    val score: Int,
    val level: String?,
    val createdAt: Instant?
) {
    companion object {
        // la más reciente: por fecha y, a igual fecha, por creación
        val ORDER: Comparator<AssessmentRow> = compareBy<AssessmentRow>({ it.date }, { it.createdAt ?: Instant.MIN })

        fun of(record: RecordResponse): AssessmentRow? {
            val a = record.attributes
            val date = Values.text(a["assessment_date"])?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
            return AssessmentRow(
                record.id,
                date,
                Values.bool(a["residual"]) == true,
                Values.decimal(a[Risks.SCORE])?.toInt() ?: 0,
                Values.text(a[Risks.LEVEL]),
                record.createdAt
            )
        }
    }
}

// lo que escribe una revisión: la risk_assessment (`stored`), los valores vigentes o residuales del riesgo (`current`) y la
// fecha de la última revisión de cualquier tipo, de la que corre la cadencia
class AssessmentPlan(
    val stored: Map<String, Any?>,
    val current: Map<String, Any?>,
    val lastDate: LocalDate
)

// el plan de una revisión, puro (api.md §4.7, ASM-099): las revisiones de un tipo, residual o no, van en orden de fecha (409
// ASSESSMENT_OUT_OF_ORDER); la primera no es residual (409 FIRST_ASSESSMENT_NOT_RESIDUAL); el nivel sale de los umbrales del
// proyecto y la tendencia del puntaje de la revisión anterior del mismo tipo
object AssessmentPlanner {
    fun plan(
        input: RiskAssessmentInput,
        history: List<AssessmentRow>,
        thresholds: RiskThresholds
    ): AssessmentPlan {
        if (input.residual && history.none { !it.residual }) {
            throw Finding(
                "FIRST_ASSESSMENT_NOT_RESIDUAL",
                "La primera revisión de un riesgo no es residual: evalúe primero el riesgo inherente",
                "attributes.residual"
            ).toCodedException()
        }
        val previous = history.filter { it.residual == input.residual }.maxWithOrNull(AssessmentRow.ORDER)
        if (previous != null && input.date.isBefore(previous.date)) {
            throw Finding(
                "ASSESSMENT_OUT_OF_ORDER",
                "Ya hay una revisión ${if (input.residual) "residual" else "no residual"} del ${previous.date}: una revisión no es anterior a la última de su tipo",
                "attributes.assessment_date"
            ).toCodedException()
        }
        val level = thresholds.level(input.score)
        val stored =
            mapOf(
                "assessment_date" to input.date.toString(),
                "probability" to input.probability,
                "impact" to input.impact,
                Risks.SCORE to input.score,
                Risks.LEVEL to level,
                "residual" to input.residual,
                "trend" to RiskLevels.trend(previous?.score, input.score),
                "notes" to input.notes
            )
        val current =
            if (input.residual) {
                mapOf(
                    "residual_probability" to input.probability,
                    "residual_impact" to input.impact,
                    "residual_score" to input.score,
                    "residual_level" to level
                )
            } else {
                mapOf("probability" to input.probability, "impact" to input.impact, Risks.SCORE to input.score, Risks.LEVEL to level)
            }
        val last = (history.map { it.date } + input.date).max()
        return AssessmentPlan(stored, current, last)
    }
}

// las revisiones de riesgo (api.md §4.7, RF-RSK-001, ASM-099): en una transacción y bajo el candado del riesgo, valida, calcula
// puntaje, nivel y tendencia, crea la risk_assessment (appendOnly y apiOnly: este endpoint es su única vía) con quien revisa,
// escribe los valores vigentes o los residuales, next_review_date (última revisión + cadencia) y review_status con la marca, y
// en la primera revisión aplica assess, una transición de comando. todo como quien llama, con el motivo de la petición. la
// escritura de risk_level dispara AUT-11 (CRITICO) y la revisión, RiskNoticeListener (ALTO o CRITICO)
@Service
class RiskAssessments(
    private val records: Records,
    private val transactions: Transactions,
    private val permissions: Permissions,
    private val catalog: ModelCatalog,
    private val callers: Callers,
    private val clock: Clock
) {
    suspend fun register(
        riskId: UUID,
        body: Map<String, Any?>,
        reason: String?
    ): RiskAssessmentResult {
        val today = today()
        val input = RiskAssessmentInput.parse(body, today)
        if (reason.isNullOrBlank()) throw ValidationException("Una revisión de riesgo exige un motivo", "reason", "envíe la cabecera X-Change-Reason")
        val user = callers.require()
        permissions.require(user, "Revisar un riesgo", Actions.CREATE to Risks.ASSESSMENT, Actions.UPDATE to Risks.SOCIAL_RISK)
        return transactions.locked("${Risks.SOCIAL_RISK}:$riskId") {
            val risk = records.get(Risks.SOCIAL_RISK, riskId)
            if (risk.state == Risks.CLOSED) {
                throw Finding("STATE_MISMATCH", "Un riesgo cerrado no se revisa", "state").toCodedException()
            }
            val first = risk.state == Risks.IDENTIFIED
            if (first) TransitionRoles.require(catalog, user, Risks.SOCIAL_RISK, listOf(Risks.ASSESS))
            val project = Records.uuid(risk.attributes["project"])?.let { records.find("project", it)?.attributes }.orEmpty()
            val thresholds = RiskLevels.thresholds(project)
            if (!thresholds.valid()) {
                throw Finding(
                    "RISK_THRESHOLDS_INVALID",
                    "Los umbrales de nivel del proyecto no son válidos (de 1 a 25 y crecientes): corríjalos antes de revisar",
                    "project"
                ).toCodedException()
            }
            val history =
                records
                    .all(Risks.ASSESSMENT, filters = mapOf(Risks.SOCIAL_RISK to riskId.toString()), max = MAX_HISTORY, field = Risks.SOCIAL_RISK)
                    .mapNotNull(AssessmentRow::of)
            val plan = AssessmentPlanner.plan(input, history, thresholds)
            val assessment =
                records.create(
                    Risks.ASSESSMENT,
                    plan.stored +
                        mapOf(Risks.SOCIAL_RISK to riskId.toString(), "assessor_user_id" to user.userId.toString(), "assessor_user_email" to user.email),
                    reason
                )
            val next = RiskReview.next(plan.lastDate, RiskLevels.cadence(risk.attributes, project))
            val state = if (first) Risks.ASSESSED else risk.state
            val changes =
                plan.current +
                    mapOf(Risks.NEXT_REVIEW to next?.toString(), Risks.REVIEW_STATUS to if (state in Risks.REVIEWED) RiskReview.status(next, today) else null)
            // solo lo que cambia (P11)
            val changed = changes.filter { (name, value) -> !Values.same(value, risk.attributes[name]) }
            if (changed.isNotEmpty()) records.patch(Risks.SOCIAL_RISK, riskId, changed, reason)
            if (first) records.transition(Risks.SOCIAL_RISK, riskId, Risks.ASSESS, reason)
            RiskAssessmentResult(assessment, records.get(Risks.SOCIAL_RISK, riskId))
        }
    }

    private fun today(): LocalDate = LocalDate.now(clock.withZone(LIMA))

    companion object {
        // las revisiones de un riesgo que se leen para ordenar la nueva: una por semana son unas 520 en diez años
        const val MAX_HISTORY = 5_000
    }
}

// api.md §4.7: POST /api/sgspe/social-risks/{id}/assessments, con Idempotency-Key: un reenvío con la misma clave devuelve la
// respuesta de la primera revisión sin revisar otra vez. el ETag es el updatedAt del riesgo
@RestController
@RequestMapping("/api/sgspe/social-risks/{id}/assessments")
class RiskAssessmentController(
    private val assessments: RiskAssessments,
    private val idempotency: Idempotency
) {
    @PostMapping
    suspend fun register(
        @PathVariable id: UUID,
        @RequestBody body: Map<String, Any?>,
        @RequestHeader(name = ChangeReason.HEADER, required = false) reason: String?,
        @RequestHeader(name = IdempotencyKey.HEADER, required = false) key: String?,
        exchange: ServerWebExchange
    ): ResponseEntity<ByteArray> {
        StrictQuery.requireOnly(exchange.request.queryParams, emptySet())
        return idempotency.respond(key, "POST", exchange.request.path.value(), body) {
            val result = assessments.register(id, body, ChangeReason.fromHeader(reason))
            CommandAnswer(HttpStatus.CREATED, result, result.risk.updatedAt, "/api/objects/${Risks.ASSESSMENT}/records/${result.assessment.id}")
        }
    }
}
