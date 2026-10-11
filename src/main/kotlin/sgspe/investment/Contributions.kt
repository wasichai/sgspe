package sgspe.investment

import org.springframework.http.HttpStatus
import sgspe.common.Amounts
import sgspe.common.Finding
import sgspe.common.Records
import sgspe.common.RequestMemo
import sgspe.common.Values
import sgspe.common.readAs
import wasichai.core.data.RecordResponse
import java.math.BigDecimal
import java.util.UUID

// los nombres y los conjuntos de estados de WF-P que comparten la guarda, los comandos, los listeners, JOB-02 y los avisos
// (workflows.md WF-P, dashboards.md §3.2, especificación de F5 §3.3): una sola fuente
object Contributions {
    const val CONTRIBUTION = "social_contribution"
    const val ITEM = "contribution_item"
    const val DELIVERY = "delivery"
    const val TOPIC = "topic"
    const val PRIORITY_TOPICS = "project_priority_topic"
    const val PARENT_TOPIC = "parent_topic"

    const val REQUESTED = "requested"
    const val PROCURED = "procured"
    const val APPROVE = "approve"
    const val APPROVE_DIRECT = "approve_direct"
    const val REGISTER_DELIVERY = "register_delivery"
    const val CLOSE = "close"

    // las transiciones que llegan a approved (P1 a P7 y P9) y las anulaciones, que congelan la entrega
    val APPROVALS: Set<String> = setOf(APPROVE, APPROVE_DIRECT)
    val CANCELS: Set<String> = setOf("cancel_approved", "cancel_procured")

    // comprometido (decisión 1, dashboards.md §3.2): lo que cuenta para el tope de la campaña
    val COMMITTED: Set<String> = setOf("approved", PROCURED, "delivered", "closed")

    // ejecutado (ASM-063): desde la entrega
    val EXECUTED: Set<String> = setOf("delivered", "closed")

    // elección 7: lo que juzgó la aprobación no cambia fuera de requested (CONTRIBUTION_FROZEN)
    val FROZEN_TERMS: List<String> =
        listOf(
            "currency",
            "contribution_type",
            "topic",
            "exploration_campaign",
            "has_institutional_synergy",
            "agreement",
            "guideline_deviation_justification",
            "project"
        )

    const val TOTAL = "total_amount"
    const val LINE_AMOUNT = "line_amount"
    const val ITEM_KIND = "item_kind"
    const val TYPE = "contribution_type"
    const val TOPIC_FIELD = "topic"
    const val JUSTIFICATION = "guideline_deviation_justification"
    const val SYNERGY = "has_institutional_synergy"
    const val AGREEMENT = "agreement"
    const val CAMPAIGN = "exploration_campaign"
    const val CURRENCY = "currency"
    const val BENEFICIARIES = "beneficiaries_count"

    // los ítems de un aporte que se leen como mucho: más es un 400 TOO_MANY_RECORDS, nunca una suma recortada
    const val MAX_ITEMS = 500

    // el candado del aporte (§3.3, «Orden»): lo toman la aprobación, las escrituras de ítems, la captura y la corrección de la
    // entrega, sus vínculos de evidencia, register_delivery y las anulaciones. siempre antes que el de la campaña
    fun lock(id: UUID): String = "$CONTRIBUTION:$id"
}

// los ítems de un aporte como los ve quien escribe, leídos una vez por corrida de InvestmentWriteGuard (que abre un
// RequestMemo): P9, los lineamientos y el tope comparten la lectura. fuera de la guarda (un listener), cada llamada lee
suspend fun Records.itemsOf(contribution: UUID): List<RecordResponse> =
    RequestMemo.remember(Contributions.ITEM to contribution) {
        readAs(Contributions.ITEM) {
            all(
                Contributions.ITEM,
                filters = mapOf(Contributions.CONTRIBUTION to contribution.toString()),
                max = Contributions.MAX_ITEMS,
                field = Contributions.ITEM
            )
        }
    }

// las reglas de datos del aporte y sus ítems, puras (especificación de F5 §3.3, decisión 1 y elección 7)
object ContributionData {
    fun itemsFrozen(state: String?): Finding? =
        if (state == Contributions.REQUESTED) {
            null
        } else {
            Finding(
                "ITEMS_FROZEN",
                "Los ítems de un aporte solo cambian en requested; este está en ${state ?: "ningún estado"}: un cambio después de aprobar es otro aporte (ASM-110)",
                Contributions.CONTRIBUTION
            )
        }

    fun itemMoved(): Finding =
        Finding("ITEMS_FROZEN", "Un ítem no cambia de aporte: bórrelo y regístrelo en el otro mientras esté en requested", Contributions.CONTRIBUTION)

    // los términos que juzgó la aprobación y que la escritura cambia, fuera de requested
    fun frozenTerms(
        state: String?,
        changed: Set<String>
    ): List<Finding> =
        if (state == Contributions.REQUESTED) {
            emptyList()
        } else {
            Contributions.FROZEN_TERMS.filter { it in changed }.map { field ->
                Finding("CONTRIBUTION_FROZEN", "$field no cambia desde la aprobación, que lo juzgó: un aporte distinto se registra de nuevo (ASM-110)", field)
            }
        }

    // total_amount (elección 12): la suma de line_amount, con las cotas de Amounts. AmountFieldsGuard valida cada línea al
    // escribirla, así que un valor guardado no la rompe
    fun total(items: List<Map<String, Any?>>): BigDecimal =
        items.mapNotNull { Amounts.parse(it[Contributions.LINE_AMOUNT], Contributions.LINE_AMOUNT) }.fold(BigDecimal.ZERO, BigDecimal::add)
}

// los conteos de BC-10 (elección 11): beneficiaries_count del aporte y, desde el plan 5.2b, target_count y reached_count
object InvestmentCounts {
    // un entero de 0 en adelante; vacío pasa. se juzga si la escritura lo cambia (en un alta, si llega): 400 OUT_OF_RANGE
    fun nonNegativeInteger(
        field: String,
        after: Map<String, Any?>,
        changed: Set<String>
    ): Finding? {
        if (field !in changed || Values.text(after[field]) == null) return null
        val whole = Values.decimal(after[field])?.let { runCatching { it.stripTrailingZeros().longValueExact() }.getOrNull() }
        if (whole != null && whole >= 0) return null
        return Finding("OUT_OF_RANGE", "$field es un número entero de 0 en adelante", field, status = HttpStatus.BAD_REQUEST)
    }
}
