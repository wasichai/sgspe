package sgspe.stakeholder

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import sgspe.common.Callers
import sgspe.common.Finding
import sgspe.common.IfMatch
import sgspe.common.LIMA
import sgspe.common.ModelCatalog
import sgspe.common.Permissions
import sgspe.common.ProjectParameters
import sgspe.common.Records
import sgspe.common.StrictBody
import sgspe.common.Transactions
import sgspe.common.TransitionRoles
import sgspe.common.Values
import sgspe.common.Versions
import sgspe.common.toCodedException
import wasichai.core.common.Actions
import wasichai.core.common.FieldViolation
import wasichai.core.common.ValidationException
import wasichai.core.data.RecordResponse
import wasichai.core.metadata.MetadataService
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import java.util.UUID

data class AssessmentWarning(
    val code: String,
    val message: String
)

data class AssessmentResult(
    val assessment: RecordResponse,
    val stakeholder: RecordResponse,
    val warnings: List<AssessmentWarning>
)

data class AssessmentPreview(
    val computed: Map<String, Any?>,
    val warnings: List<AssessmentWarning>
)

// las evaluaciones de grupos de interés (api.md §4.3, RF-STK-005): fechadas e inmutables (stakeholder_assessment es
// appendOnly, apiOnly y requiresReason), con los índices, el grupo y la prioridad calculados aquí, la evaluación anterior
// como línea base y los valores vigentes del grupo de interés escritos en la misma transacción. la primera evaluación
// lleva el grupo de interés a `assessed` con la transición de comando `assess`, y antes `profile` si sigue identificado:
// las dos transiciones pasan por Records.transition, así StakeholderWriteGuard corre también por este camino. todo se
// escribe como quien llama, con la marca de sgspe (nunca asPlatform: esto ocurre dentro de una petición).
// evaluar escribe `stance`, un campo restringido (GAP-024): solo pueden hacerlo los roles que lo escriben
@Service
class Assessments(
    private val records: Records,
    private val versions: Versions,
    private val transactions: Transactions,
    private val permissions: Permissions,
    private val metadata: MetadataService,
    private val catalog: ModelCatalog,
    private val callers: Callers,
    private val clock: Clock,
    @Value("\${sgspe.stakeholder.stance-divergence-days:30}") private val divergenceDays: Long
) {
    suspend fun preview(
        stakeholderId: UUID,
        body: Map<String, Any?>
    ): AssessmentPreview {
        val input = AssessmentInput.parse(body, catalog, today(), requireDate = false)
        val stakeholder = records.get(STAKEHOLDER, stakeholderId)
        val plan = plan(stakeholder, input)
        return AssessmentPreview(plan.computed, plan.warnings)
    }

    suspend fun register(
        stakeholderId: UUID,
        body: Map<String, Any?>,
        reason: String?,
        ifMatch: String?
    ): AssessmentResult {
        val input = AssessmentInput.parse(body, catalog, today(), requireDate = true)
        if (reason.isNullOrBlank()) throw ValidationException("Una evaluación exige un motivo", "reason", "envíe la cabecera X-Change-Reason")
        val expected = IfMatch.parse(ifMatch)
        val user = callers.require()
        permissions.require(user, "Evaluar un grupo de interés", Actions.CREATE to ASSESSMENT, Actions.UPDATE to STAKEHOLDER)
        requireStanceWritable(input)
        // el candado del grupo de interés va primero: es la clave que toman los demás comandos sobre él
        return transactions.locked("stakeholder:$stakeholderId") {
            var stakeholder = records.get(STAKEHOLDER, stakeholderId)
            versions.check(STAKEHOLDER, stakeholderId, expected, stakeholder.updatedAt, "El grupo de interés")
            if (stakeholder.state == RETIRED) {
                throw Finding(
                    "STATE_MISMATCH",
                    "Un grupo de interés retirado no se evalúa: reincorpórelo primero",
                    "state",
                    status = HttpStatus.CONFLICT
                ).toCodedException()
            }
            val pending =
                when (stakeholder.state) {
                    IDENTIFIED -> listOf("profile", "assess")
                    else -> listOf("assess")
                }
            // en `assessed` no hay transición que aplicar, pero reevaluar sigue exigiendo el rol de assess (api.md §4.3)
            TransitionRoles.require(catalog, user, STAKEHOLDER, pending)
            if (stakeholder.state == IDENTIFIED) stakeholder = records.transition(STAKEHOLDER, stakeholderId, "profile", reason)
            val plan = plan(stakeholder, input)
            val assessment =
                records.create(
                    ASSESSMENT,
                    plan.stored +
                        mapOf("stakeholder" to stakeholderId.toString(), "assessor_user_id" to user.userId.toString(), "assessor_user_email" to user.email),
                    reason
                )
            // solo se escribe lo que cambia (P11): una evaluación que repite los valores vigentes no toca el grupo de interés
            val changes = plan.current.filter { (name, value) -> !Values.same(value, stakeholder.attributes[name]) }
            if (changes.isNotEmpty()) records.patch(STAKEHOLDER, stakeholderId, changes, reason)
            if (stakeholder.state != ASSESSED) records.transition(STAKEHOLDER, stakeholderId, "assess", reason)
            AssessmentResult(assessment, records.get(STAKEHOLDER, stakeholderId), plan.warnings)
        }
    }

    private class Plan(
        val computed: Map<String, Any?>,
        val stored: Map<String, Any?>,
        val current: Map<String, Any?>,
        val warnings: List<AssessmentWarning>
    )

    private suspend fun plan(
        stakeholder: RecordResponse,
        input: AssessmentInput
    ): Plan {
        val attributes = stakeholder.attributes
        val projectId = Records.uuid(attributes["project"])
        val project = projectId?.let { records.find("project", it)?.attributes }.orEmpty()
        val scores =
            InfluenceClassifier.scores(input.indicators)
                ?: throw ValidationException(
                    "Faltan indicadores",
                    listOf(FieldViolation("attributes", "informe al menos un indicador de interés y uno de poder, de 1 a 5"))
                )
        val computedGroup =
            InfluenceClassifier.group(
                scores.interestIndex,
                scores.powerIndex,
                ProjectParameters.decimal(project, "interest_threshold"),
                ProjectParameters.decimal(project, "power_threshold")
            )
        val overridden = input.influenceGroup != null && input.influenceGroup != computedGroup
        if (overridden && input.overrideReason == null) {
            throw ValidationException("Ajustar el grupo exige justificación", "attributes.override_reason", "explique por qué el grupo no es $computedGroup")
        }
        val group = input.influenceGroup ?: computedGroup
        val history =
            records
                .all(ASSESSMENT, filters = mapOf("stakeholder" to stakeholder.id))
                .map { it to LocalDate.parse(Values.text(it.attributes["assessment_date"])!!) }
                .sortedWith(compareBy({ it.second }, { it.first.createdAt }))
        val date = input.date ?: today()
        // la evaluación anterior es la línea base de la evaluación; la de la postura es la anterior más reciente que la
        // informó (P18): una evaluación sin postura no la reemplaza
        val baseline = history.lastOrNull { it.second <= date }
        val stanceBaseline = history.lastOrNull { it.second <= date && Values.text(it.first.attributes["stance"]) != null }
        val latest = history.none { it.second > date }
        val baselineStance = stanceBaseline?.let { Values.text(it.first.attributes["stance"]) }
        val stanceChanged = baselineStance != null && input.stance != null && baselineStance != input.stance
        // la prioridad mira la postura que rige en la fecha de la evaluación: la que trae o, si no trae, la última informada
        val rules = rules(projectId)
        val priority =
            Prioritizer.priority(
                rules,
                group,
                input.stance ?: baselineStance,
                Values.text(attributes["stakeholder_scope"]),
                Values.text(attributes["stakeholder_type"])
            )
        val warnings = mutableListOf<AssessmentWarning>()
        if (stanceChanged) {
            // «cercanas»: la línea base de la postura cae dentro de la ventana (api.md §4.3, M9 del pre-flight)
            val days = ChronoUnit.DAYS.between(stanceBaseline.second, date)
            if (days <= divergenceDays) {
                warnings += AssessmentWarning("STANCE_DIVERGENCE", "La postura difiere de la evaluación de hace $days días: revise la fuente (CON-006)")
            }
        }
        if (!latest) {
            warnings +=
                AssessmentWarning(
                    "BACKDATED_ASSESSMENT",
                    "Hay evaluaciones posteriores: esta no cambia los índices ni el grupo vigentes; la postura y la confianza siguen el último valor informado"
                )
        }
        if (priority == null) {
            warnings += AssessmentWarning("NO_PRIORITY_RULE", "Ninguna fila de la matriz de priorización coincide: la prioridad queda vacía")
        }
        val stored =
            scores.components +
                mapOf(
                    "interest_index" to scores.interestIndex,
                    "power_index" to scores.powerIndex,
                    "influence_score" to scores.influenceScore,
                    "influence_group" to group,
                    "group_overridden" to overridden,
                    // la justificación solo se guarda si el grupo quedó ajustado
                    "override_reason" to input.overrideReason.takeIf { overridden },
                    "baseline_assessment" to baseline?.first?.id,
                    "stance_changed" to stanceChanged
                )
        // los valores vigentes (P18): la postura y la confianza son el último valor informado de cada una, en el orden de las
        // fechas, con esta evaluación incluida; el resto sale de la última evaluación y no cambia si esta es atrasada
        val timeline = history.map { Values.text(it.first.attributes["stance"]) to Values.decimal(it.first.attributes["trust_score"])?.toInt() }.toMutableList()
        timeline.add(history.count { it.second <= date }, input.stance to input.trustScore)
        val currentStance = timeline.lastOrNull { it.first != null }?.first
        val current =
            mapOf(
                "stance" to currentStance,
                "trust_score" to timeline.lastOrNull { it.second != null }?.second
            ) +
                if (latest) {
                    mapOf(
                        "influence_group" to group,
                        "information_priority" to priority,
                        "interest_index" to scores.interestIndex,
                        "power_index" to scores.powerIndex,
                        "last_assessment_date" to date.toString()
                    )
                } else if (!Values.same(currentStance, attributes["stance"])) {
                    // una evaluación atrasada que cambia la postura vigente: la prioridad sigue al grupo vigente con esa postura
                    mapOf(
                        "information_priority" to
                            Prioritizer.priority(
                                rules,
                                Values.text(attributes["influence_group"]),
                                currentStance,
                                Values.text(attributes["stakeholder_scope"]),
                                Values.text(attributes["stakeholder_type"])
                            )
                    )
                } else {
                    emptyMap()
                }
        // el cuerpo de la evaluación: lo que mandó el cliente (sin la justificación, que solo queda si el grupo se ajustó) y lo calculado
        return Plan(stored + ("information_priority" to priority), (input.attributes - "override_reason") + stored, current, warnings)
    }

    // la matriz de priorización del proyecto: prioritization_rule.stakeholder_scope es Rule.scope e information_priority es
    // Rule.priority
    private suspend fun rules(projectId: UUID?): List<Prioritizer.Rule> =
        projectId
            ?.let { records.all("prioritization_rule", filters = mapOf("project" to it.toString())) }
            .orEmpty()
            .mapNotNull { row ->
                Values.text(row.attributes["information_priority"])?.let { priority ->
                    Prioritizer.Rule(
                        Values.text(row.attributes["influence_group"]),
                        Values.text(row.attributes["stance"]),
                        Values.text(row.attributes["stakeholder_scope"]),
                        Values.text(row.attributes["stakeholder_type"]),
                        priority
                    )
                }
            }

    // evaluar escribe stance en el grupo de interés y, si viene, en la evaluación: los dos son restringidos (GAP-024)
    private suspend fun requireStanceWritable(input: AssessmentInput) {
        val onStakeholder =
            metadata
                .definitionOf(STAKEHOLDER)
                .fields
                .firstOrNull { it.name == "stance" }
                ?.editable == true
        val onAssessment =
            input.stance == null || metadata
                .definitionOf(ASSESSMENT)
                .fields
                .firstOrNull { it.name == "stance" }
                ?.editable == true
        if (!onStakeholder || !onAssessment) {
            throw Finding(
                "FIELD_NOT_WRITABLE",
                "Evaluar escribe la postura del grupo de interés, un campo que tus roles no pueden escribir (GAP-024)",
                "stance",
                status = HttpStatus.FORBIDDEN
            ).toCodedException()
        }
    }

    private fun today(): LocalDate = LocalDate.now(clock.withZone(LIMA))

    companion object {
        const val STAKEHOLDER = "stakeholder"
        const val ASSESSMENT = "stakeholder_assessment"
        const val IDENTIFIED = "identified"
        const val ASSESSED = "assessed"
        const val RETIRED = "retired"
    }
}

// lo que manda el cliente en `attributes`, validado: los indicadores y la confianza de 1 a 5 (wasichai no tiene mínimos
// ni máximos), los ENUM contra su catálogo, la fecha no futura. lo calculado no se manda
class AssessmentInput(
    val attributes: Map<String, Any?>,
    val indicators: Map<String, Int?>,
    val date: LocalDate?,
    val stance: String?,
    val trustScore: Int?,
    val influenceGroup: String?,
    val overrideReason: String?
) {
    companion object {
        val TEXTS =
            listOf(
                "stance_reasons",
                "concerns",
                "expectations",
                "project_knowledge",
                "company_knowledge",
                "team_closeness",
                "override_reason"
            )
        val BOOLEANS = listOf("open_to_dialogue", "communication_sufficient", "information_adequate")
        val ALLOWED: Set<String> =
            (
                listOf("assessment_date", "basis", "stance", "trust_score", "position", "influence_group", "group_overridden") + TEXTS + BOOLEANS +
                    InfluenceClassifier.INDICATORS
            ).toSet()

        fun parse(
            body: Map<String, Any?>,
            catalog: ModelCatalog,
            today: LocalDate,
            requireDate: Boolean
        ): AssessmentInput {
            StrictBody.requireOnly(body, setOf("attributes"))
            val attributes = StrictBody.objectOf(body, "attributes")
            StrictBody.requireOnly(attributes, ALLOWED, "attributes.")
            val violations = mutableListOf<FieldViolation>()
            val indicators =
                InfluenceClassifier.INDICATORS.associateWith { name -> scale(attributes[name], "attributes.$name", violations) }
            val trust = scale(attributes["trust_score"], "attributes.trust_score", violations)
            val date =
                Values.text(attributes["assessment_date"])?.let {
                    try {
                        LocalDate.parse(it)
                    } catch (_: DateTimeParseException) {
                        violations += FieldViolation("attributes.assessment_date", "debe ser una fecha AAAA-MM-DD")
                        null
                    }
                }
            if (date == null && requireDate && violations.none { it.field == "attributes.assessment_date" }) {
                violations += FieldViolation("attributes.assessment_date", "es obligatoria")
            }
            if (date != null && date.isAfter(today)) violations += FieldViolation("attributes.assessment_date", "no puede ser futura")
            val basis = Values.text(attributes["basis"])
            if (requireDate && basis == null) violations += FieldViolation("attributes.basis", "es obligatoria")
            enumCheck(basis, catalog.enumOf(Assessments.ASSESSMENT, "basis"), "attributes.basis", violations)
            val stance = Values.text(attributes["stance"])
            enumCheck(stance, catalog.enumOf(Assessments.ASSESSMENT, "stance"), "attributes.stance", violations)
            // group_overridden lo manda el portal (Fase 2.3) junto con el ajuste: false descarta influence_group; true lo exige
            val overriddenFlag = Values.bool(attributes["group_overridden"])
            val group = if (overriddenFlag == false) null else Values.text(attributes["influence_group"])
            if (overriddenFlag == true && group == null) violations += FieldViolation("attributes.influence_group", "elija el grupo del ajuste")
            enumCheck(group, catalog.enumOf(Assessments.ASSESSMENT, "influence_group"), "attributes.influence_group", violations)
            if (violations.isNotEmpty()) throw ValidationException("Evaluación inválida", violations)
            // influence_group lo calcula sgspe: si el cliente lo manda es un ajuste manual, que va en el resultado
            return AssessmentInput(
                attributes = attributes - "influence_group" - "group_overridden",
                indicators = indicators,
                date = date,
                stance = stance,
                trustScore = trust,
                influenceGroup = group,
                overrideReason = Values.text(attributes["override_reason"])
            )
        }

        private fun scale(
            value: Any?,
            field: String,
            violations: MutableList<FieldViolation>
        ): Int? {
            if (value == null) return null
            // un número de json: un texto como "4" no es un indicador
            val number = (value as? Number)?.let(Values::decimal)
            if (number == null || number.stripTrailingZeros().scale() > 0 || number < BigDecimal.ONE || number > BigDecimal(5)) {
                violations += FieldViolation(field, "debe ser un entero de 1 a 5")
                return null
            }
            return number.toInt()
        }

        private fun enumCheck(
            value: String?,
            options: List<String>,
            field: String,
            violations: MutableList<FieldViolation>
        ) {
            if (value != null && options.isNotEmpty() && value !in options) violations += FieldViolation(field, "debe ser uno de ${options.joinToString(", ")}")
        }
    }
}
