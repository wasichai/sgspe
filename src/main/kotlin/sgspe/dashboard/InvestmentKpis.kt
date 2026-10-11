package sgspe.dashboard

import sgspe.commitment.Commitments
import sgspe.common.Amounts
import sgspe.investment.Contributions
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

// una campaña como la leen los KPI-INV: su tope, su moneda y su ejecutado derivado (CampaignExecutionRecalculator, ASM-063)
data class CampaignRow(
    val id: String,
    val project: String?,
    val name: String?,
    val cap: BigDecimal?,
    val currency: String?,
    val executed: BigDecimal?,
    val otherCurrencyItems: Int
)

// un monto comprometido de una campaña: un compromiso o un aporte en su conjunto «comprometido» (dashboards.md §3.2)
data class CommittedAmount(
    val campaign: String,
    val amount: BigDecimal?,
    val currency: String?
)

// `state`: el estado de WF-G, para que el portal no muestre ejecución de un programa por aprobar (S43, plan 5.3)
data class ProgramRow(
    val id: String,
    val project: String?,
    val code: String?,
    val name: String?,
    val currency: String?,
    val state: String? = null
)

// un presupuesto anual de un programa, con su ejecutado registrado a mano (decisión 4 de la especificación, ASM-063)
data class BudgetRow(
    val program: String,
    val year: Int?,
    val allocated: BigDecimal?,
    val executed: BigDecimal?,
    val currency: String?
)

data class GroupRow(
    val program: String,
    val target: Long?,
    val reached: Long?
)

data class ContributionRow(
    val id: String,
    val project: String?,
    val state: String?,
    val total: BigDecimal?,
    val currency: String?,
    val beneficiaries: Long?
)

// una entrega de un compromiso o de un aporte (`parent`), con su fecha y su monto entregado (vacío en las de aportes)
data class DeliveryRow(
    val parent: String,
    val date: LocalDate?,
    val amount: BigDecimal?
)

// los KPI-INV (dashboards.md §4.7, especificación de F5 §3.9) como funciones puras. las filas llegan leídas como quien mira y
// acotadas a los proyectos y, donde aplica, a la localidad. solo se suman montos de una misma moneda: la cifra titular va en PEN
// (dashboards.md §3.3, decisión 3 del plan 5.2b) y cada desglose lleva su moneda; lo que está en otra se advierte (ASM-063)
object InvestmentKpis {
    const val PEN = CommitmentKpis.DEFAULT_CURRENCY
    const val COMMITMENTS = "COMPROMISOS"
    const val CONTRIBUTIONS = "APORTES"
    const val PROGRAMS = "PROGRAMAS"

    // los estados en que un aporte cuenta como ejecutado (dashboards.md §3.2, ASM-063), los mismos de Contributions (plan 5.2a)
    val EXECUTED: Set<String> = Contributions.EXECUTED

    private val SOURCES = listOf(COMMITMENTS, CONTRIBUTIONS, PROGRAMS)

    // un monto de una fuente en un proyecto
    private data class Amount(
        val project: String,
        val source: String,
        val amount: BigDecimal?,
        val currency: String?
    )

    // KPI-INV-01: por campaña, el tope, lo comprometido (en la moneda de la campaña) y lo ejecutado; la cifra, lo ejecutado
    // sobre el tope de las campañas en PEN que tienen tope
    fun campaigns(
        campaigns: List<CampaignRow>,
        committed: List<CommittedAmount>
    ): IndicatorResult {
        val byCampaign = committed.filter { it.amount != null }.groupBy { it.campaign }
        val rows =
            campaigns.sortedWith(compareBy<CampaignRow>({ it.name.orEmpty() }, { it.id })).map { c ->
                val own = byCampaign[c.id].orEmpty()
                val same = if (c.currency == null) emptyList() else own.filter { it.currency == c.currency }
                val amount = if (c.currency == null) null else sum(same.map { it.amount })
                val executed = c.executed?.let(::scaled)
                linkedMapOf<String, Any?>(
                    "key" to c.id,
                    "label" to (c.name ?: c.id),
                    "currency" to c.currency,
                    "cap" to c.cap?.let(::scaled),
                    "committed" to amount,
                    "executed" to executed,
                    "committedPercent" to percentOf(amount, c.cap),
                    "executedPercent" to percentOf(executed, c.cap),
                    "committedOtherCurrencyCount" to (own.size - same.size),
                    "otherCurrencyCount" to c.otherCurrencyItems
                )
            }
        val pen = campaigns.filter { it.currency == PEN && it.cap != null }
        val cap = sum(pen.map { it.cap })
        val executed = sum(pen.map { it.executed })
        val other = rows.sumOf { (it["committedOtherCurrencyCount"] as Int) + (it["otherCurrencyCount"] as Int) }
        return IndicatorResult(
            if (pen.isEmpty()) null else Numbers.percent(executed, cap),
            numerator = if (pen.isEmpty()) null else executed,
            denominator = if (pen.isEmpty()) null else cap,
            breakdowns = listOf(Breakdown("campaign", rows)),
            warnings = if (other > 0) listOf("$other montos en una moneda distinta de la de su campaña no se suman (ASM-063)") else emptyList()
        )
    }

    // KPI-INV-02: por programa, año y moneda, el asignado y el ejecutado registrado; con `year`, solo ese año. la cifra, el
    // ejecutado sobre el asignado de los presupuestos en PEN
    fun programs(
        programs: List<ProgramRow>,
        budgets: List<BudgetRow>,
        year: Int?
    ): IndicatorResult {
        val byId = programs.associateBy { it.id }
        val chosen = budgets.filter { it.program in byId && it.currency != null && (year == null || it.year == year) }
        val rows =
            chosen
                .groupBy { Triple(it.program, it.year, it.currency!!) }
                .entries
                .sortedWith(compareBy({ label(byId[it.key.first]) }, { it.key.second ?: 0 }, { it.key.third }))
                .map { (key, list) ->
                    val allocated = sum(list.map { it.allocated })
                    val executed = sum(list.map { it.executed })
                    linkedMapOf<String, Any?>(
                        "key" to "${key.first}:${key.second}:${key.third}",
                        "program" to key.first,
                        "label" to label(byId[key.first]),
                        "year" to key.second,
                        "currency" to key.third,
                        "allocated" to allocated,
                        "executed" to executed,
                        "percent" to Numbers.percent(executed, allocated),
                        "state" to byId[key.first]?.state
                    )
                }
        val pen = chosen.filter { it.currency == PEN }
        val allocated = sum(pen.map { it.allocated })
        val executed = sum(pen.map { it.executed })
        val other = chosen.size - pen.size
        return IndicatorResult(
            if (pen.isEmpty()) null else Numbers.percent(executed, allocated),
            numerator = if (pen.isEmpty()) null else executed,
            denominator = if (pen.isEmpty()) null else allocated,
            breakdowns = listOf(Breakdown("program", rows)),
            warnings = if (other > 0) listOf("$other presupuestos en una moneda distinta de PEN no se suman a la cifra (ASM-063)") else emptyList()
        )
    }

    // KPI-INV-03: lo ejecutado en el periodo por fuente: el delivered_amount de las entregas del periodo de los compromisos (en la
    // moneda del compromiso), el total_amount de los aportes ejecutados cuya entrega cae en el periodo y el executed_amount de los
    // presupuestos de los años que cruzan el periodo; por proyecto y moneda, frente al asignado de esos presupuestos y a los
    // topes de sus campañas. sin `withPrograms` (filtro de localidad) los programas no se suman. la cifra, lo ejecutado en PEN
    fun executed(
        commitments: List<CommitmentRow>,
        commitmentDeliveries: List<DeliveryRow>,
        contributions: List<ContributionRow>,
        contributionDeliveries: List<DeliveryRow>,
        programs: List<ProgramRow>,
        budgets: List<BudgetRow>,
        campaigns: List<CampaignRow>,
        start: LocalDate,
        end: LocalDate,
        withPrograms: Boolean,
        projectCodes: Map<String, String>
    ): IndicatorResult {
        fun inPeriod(date: LocalDate?) = date != null && !date.isBefore(start) && !date.isAfter(end)
        val byCommitment = commitments.associateBy { it.id }
        val done = mutableListOf<Amount>()
        for (delivery in commitmentDeliveries.filter { inPeriod(it.date) }) {
            val c = byCommitment[delivery.parent] ?: continue
            done += Amount(c.project.orEmpty(), COMMITMENTS, delivery.amount, c.currency)
        }
        val delivered = contributionDeliveries.filter { inPeriod(it.date) }.map { it.parent }.toSet()
        for (s in contributions.filter { it.state in EXECUTED && it.id in delivered }) {
            done += Amount(s.project.orEmpty(), CONTRIBUTIONS, s.total, s.currency)
        }
        val byProgram = programs.associateBy { it.id }
        val years = start.year..end.year
        val inYears = if (withPrograms) budgets.filter { it.program in byProgram && it.year != null && it.year in years } else emptyList()
        for (b in inYears) done += Amount(byProgram[b.program]?.project.orEmpty(), PROGRAMS, b.executed, b.currency)
        val budget = inYears.map { Amount(byProgram[it.program]?.project.orEmpty(), PROGRAMS, it.allocated, it.currency) }
        val caps = campaigns.map { Amount(it.project.orEmpty(), "", it.cap, it.currency) }
        val shown = done.filter { it.amount != null && it.currency != null }
        val sources =
            shown
                .groupBy { it.source to it.currency!! }
                .entries
                .sortedWith(compareBy({ SOURCES.indexOf(it.key.first) }, { it.key.second }))
                .map { (key, list) ->
                    linkedMapOf<String, Any?>(
                        "key" to "${key.first}:${key.second}",
                        "source" to key.first,
                        "currency" to key.second,
                        "value" to sum(list.map { it.amount })
                    )
                }
        val keys =
            (shown + budget + caps)
                .filter { it.currency != null }
                .map { it.project to it.currency!! }
                .distinct()
                .sortedWith(compareBy({ projectCodes[it.first] ?: it.first }, { it.second }))
        val projects =
            keys.map { (project, currency) ->
                fun of(
                    list: List<Amount>,
                    source: String? = null
                ) = sum(list.filter { it.project == project && it.currency == currency && (source == null || it.source == source) }.map { it.amount })
                linkedMapOf<String, Any?>(
                    "key" to "$project:$currency",
                    "project" to project,
                    "label" to (projectCodes[project] ?: project),
                    "currency" to currency,
                    "executed" to of(shown),
                    "commitments" to of(shown, COMMITMENTS),
                    "contributions" to of(shown, CONTRIBUTIONS),
                    "programs" to of(shown, PROGRAMS),
                    "budget" to of(budget),
                    "cap" to of(caps)
                )
            }
        val other = shown.count { it.currency != PEN }
        val warnings =
            listOfNotNull(
                if (other > 0) "$other montos ejecutados en una moneda distinta de PEN no se suman a la cifra (ASM-063)" else null,
                if (withPrograms) null else "Los programas no tienen territorio: con un filtro de localidad no se suman"
            )
        return IndicatorResult(
            sum(shown.filter { it.currency == PEN }.map { it.amount }),
            breakdowns = listOf(Breakdown("source", sources), Breakdown("project", projects)),
            warnings = warnings
        )
    }

    // KPI-INV-04: tres bloques que no se suman, porque una persona puede estar en varios (dashboards.md §4.7): los alcanzados y la
    // meta de los grupos de beneficiarios de cada programa, los beneficiarios de los aportes ejecutados y los de los compromisos
    // cumplidos. la cifra, los alcanzados de los programas
    fun beneficiaries(
        programs: List<ProgramRow>,
        groups: List<GroupRow>,
        contributions: List<ContributionRow>,
        commitments: List<CommitmentRow>
    ): IndicatorResult {
        val byId = programs.associateBy { it.id }
        val own = groups.filter { it.program in byId }
        val rows =
            own
                .groupBy { it.program }
                .entries
                .sortedBy { label(byId[it.key]) }
                .map { (program, list) ->
                    linkedMapOf<String, Any?>(
                        "key" to program,
                        "label" to label(byId[program]),
                        "target" to list.sumOf { it.target ?: 0L },
                        "reached" to list.sumOf { it.reached ?: 0L }
                    )
                }
        val reached = own.sumOf { it.reached ?: 0L }
        val executed = contributions.filter { it.state in EXECUTED }
        val fulfilled = commitments.filter { it.state in Commitments.FULFILLED }
        val sources =
            listOf(
                linkedMapOf<String, Any?>("key" to PROGRAMS, "value" to reached, "target" to own.sumOf { it.target ?: 0L }, "count" to own.size),
                linkedMapOf<String, Any?>("key" to CONTRIBUTIONS, "value" to executed.sumOf { it.beneficiaries ?: 0L }, "count" to executed.size),
                linkedMapOf<String, Any?>("key" to COMMITMENTS, "value" to fulfilled.sumOf { it.beneficiaries ?: 0L }, "count" to fulfilled.size)
            )
        return IndicatorResult(reached, breakdowns = listOf(Breakdown("source", sources), Breakdown("program", rows)))
    }

    // cómo se nombra un programa: código y nombre, o su id si no se lee ninguno
    private fun label(program: ProgramRow?): String = listOfNotNull(program?.code, program?.name).joinToString(" · ").ifEmpty { program?.id.orEmpty() }

    private fun percentOf(
        part: BigDecimal?,
        whole: BigDecimal?
    ): BigDecimal? = if (part == null || whole == null) null else Numbers.percent(part, whole)

    private fun sum(values: List<BigDecimal?>): BigDecimal = scaled(values.filterNotNull().fold(BigDecimal.ZERO, BigDecimal::add))

    // P44: los montos salen con la escala de Amounts, para que el JSON no cambie con la fila de origen
    private fun scaled(value: BigDecimal): BigDecimal = value.setScale(Amounts.MAX_SCALE, RoundingMode.HALF_UP)
}
