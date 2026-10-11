package sgspe.investment

import org.springframework.http.HttpStatus
import sgspe.common.Finding
import sgspe.common.Values
import java.time.LocalDate

// los nombres de WF-G y de los hijos de social_program (workflows.md WF-G, database-model.md §15, especificación de F5 §3.6):
// una sola lista para las reglas de InvestmentWriteGuard, el listener de totales, el aviso de aprobación y los tableros
object Programs {
    const val PROGRAM = "social_program"
    const val INITIATIVE = "social_initiative"
    const val GROUP = "beneficiary_group"
    const val BUDGET = "program_budget"
    const val INDICATOR = "program_indicator"
    const val MEASUREMENT = "program_measurement"
    const val MILESTONE = "program_milestone"

    // el programa y sus hijos: los objetos de BC-10 que juzgan ProgramApprovalRule y ProgramDataRule
    val OBJECTS: Set<String> = setOf(PROGRAM, INITIATIVE, GROUP, BUDGET, INDICATOR, MEASUREMENT, MILESTONE)

    // las transiciones de WF-G que miran la guarda, el aviso de aprobación y el escalamiento de las tareas
    const val SUBMIT = "submit"
    const val APPROVE = "approve"
    const val PLAN_DIRECT = "plan_direct"
    const val PENDING = "pending_approval"
    const val CLOSE = "close"

    const val PROJECT = "project"
    const val PARTNER = "partner_organization"
    const val AGREEMENT = "agreement"
    const val AGREEMENT_PARTY = "agreement_party"
    const val PARTY_ORGANIZATION = "organization"
    const val CURRENCY = "currency"
    const val TOTAL_BUDGET = "total_budget"
    const val ALLOCATED = "allocated_amount"
    const val YEAR = "budget_year"

    const val INTERNAL_POLICY = "policy_internal_approval"
    const val APPROVE_ACTION = "APPROVE"

    // el campo de los hallazgos sobre quien aprueba (G1, G2), como ApprovalRules.RECORD de los compromisos
    const val RECORD = "record"
}

// las reglas de datos de WF-G y de los hijos, puras (especificación de F5 §3.6): las fechas en orden, el año presupuestal y los
// conteos de personas (InvestmentCounts, plan 5.2a). una regla de datos se juzga en el alta y cuando la escritura cambia alguno
// de sus campos: un valor guardado antes de la regla no bloquea otra escritura del registro (como RiskRules.reviewEvery). el
// acuerdo vigente de G3 es Synergy.live, el mismo de P6 (elección 9)
object ProgramRules {
    const val MIN_YEAR = 2000L
    const val MAX_YEAR = 2100L

    // el inicio y el fin de cada objeto con periodo: el fin no puede ser anterior al inicio
    val PERIODS: Map<String, Pair<String, String>> =
        mapOf(
            Programs.PROGRAM to ("start_date" to "end_date"),
            Programs.INITIATIVE to ("start_date" to "end_date"),
            Programs.MEASUREMENT to ("period_start_date" to "period_end_date")
        )

    // los conteos de personas: enteros, 0 o más
    val COUNTS: Map<String, List<String>> = mapOf(Programs.GROUP to listOf("target_count", "reached_count"))

    // los 400 OUT_OF_RANGE de las fechas, del año y de los conteos del objeto, con el registro como queda (`after`) y los campos
    // que la escritura cambia (`changed`; en un alta, los que llegan con valor)
    fun data(
        objectName: String,
        after: Map<String, Any?>,
        changed: Set<String>
    ): List<Finding> = listOfNotNull(period(objectName, after, changed), year(objectName, after, changed)) + counts(objectName, after, changed)

    private fun period(
        objectName: String,
        after: Map<String, Any?>,
        changed: Set<String>
    ): Finding? {
        val (startField, endField) = PERIODS[objectName] ?: return null
        if (startField !in changed && endField !in changed) return null
        val start = date(after[startField]) ?: return null
        val end = date(after[endField]) ?: return null
        return if (end.isBefore(start)) outOfRange(endField, "$endField no puede ser anterior a $startField") else null
    }

    private fun year(
        objectName: String,
        after: Map<String, Any?>,
        changed: Set<String>
    ): Finding? {
        if (objectName != Programs.BUDGET || Programs.YEAR !in changed || Values.text(after[Programs.YEAR]) == null) return null
        val year = whole(after[Programs.YEAR])
        if (year != null && year in MIN_YEAR..MAX_YEAR) return null
        return outOfRange(Programs.YEAR, "budget_year es un año entero de $MIN_YEAR a $MAX_YEAR")
    }

    private fun counts(
        objectName: String,
        after: Map<String, Any?>,
        changed: Set<String>
    ): List<Finding> = COUNTS[objectName].orEmpty().mapNotNull { InvestmentCounts.nonNegativeInteger(it, after, changed) }

    // el número entero que trae el valor, o null si no es un número entero
    private fun whole(value: Any?): Long? = Values.decimal(value)?.let { runCatching { it.stripTrailingZeros().longValueExact() }.getOrNull() }

    private fun date(value: Any?): LocalDate? = Values.text(value)?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }

    private fun outOfRange(
        field: String,
        message: String
    ): Finding = Finding("OUT_OF_RANGE", message, field, status = HttpStatus.BAD_REQUEST)

    // G4: plan_direct solo sin aprobación interna (ASM-008)
    fun internalApproval(): Finding =
        Finding(
            "INTERNAL_APPROVAL_REQUIRED",
            "El proyecto tiene la aprobación interna activa: el programa se envía a aprobación (G4, ASM-008)",
            Programs.PROJECT
        )

    // G1: la doble llave (RF-WFL-002)
    fun actionRequired(): Finding =
        Finding(
            "ACTION_REQUIRED",
            "Aprobar un programa exige la acción APPROVE sobre social_program (G1, RF-WFL-002)",
            Programs.RECORD,
            status = HttpStatus.FORBIDDEN
        )

    // G2: la segregación (ASM-008)
    fun segregation(): Finding =
        Finding(
            "SEGREGATION_OF_DUTIES",
            "Quien aprueba un programa no puede ser quien lo registró (G2, ASM-008)",
            Programs.RECORD,
            status = HttpStatus.FORBIDDEN
        )

    // la iniciativa de un grupo de beneficiarios es de su mismo programa (elección 5 de la especificación)
    fun programMismatch(): Finding = Finding("PROGRAM_MISMATCH", "La iniciativa del grupo de beneficiarios pertenece a otro programa", Programs.INITIATIVE)
}
