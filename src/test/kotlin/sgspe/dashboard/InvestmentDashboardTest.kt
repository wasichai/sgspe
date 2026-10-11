package sgspe.dashboard

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import sgspe.common.Records
import sgspe.investment.Contributions
import wasichai.core.data.RecordResponse
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

// el tablero investment sin base (dashboards.md §5.10, especificación de F5 §3.9): sus cuatro KPI en orden, lo que cada uno
// lee y los filtros que no aplica, y que lo comprometido usa las mismas definiciones de estado que CampaignCommitted (decisión
// 4 del plan 5.2b: Commitments.COMMITTED y Contributions.COMMITTED)
class InvestmentDashboardTest {
    private val all =
        DashboardFilters(
            listOf(UUID.randomUUID()),
            LocalDate.parse("2026-10-01"),
            LocalDate.parse("2026-10-31"),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            "AIDS",
            null,
            UUID.randomUUID(),
            UUID.randomUUID(),
            2026
        )

    private fun ignored(code: String) = Kpis.ignoredFilters(Kpis.BY_CODE.getValue(code), all)

    @Test
    fun `el tablero investment trae los cuatro KPI-INV en el orden de sus zonas`() {
        assertEquals(listOf("KPI-INV-01", "KPI-INV-02", "KPI-INV-03", "KPI-INV-04"), Kpis.DASHBOARDS.getValue(Kpis.INVESTMENT))
        for (code in Kpis.DASHBOARDS.getValue(Kpis.INVESTMENT)) {
            assertEquals(Kpis.INVESTMENT, Kpis.BY_CODE.getValue(code).dashboard, code)
        }
        assertTrue(
            Kpis.BY_CODE
                .getValue("KPI-INV-01")
                .requires
                .containsAll(listOf("social_contribution.total_amount", "commitment.amount"))
        )
        assertTrue(
            Kpis.BY_CODE
                .getValue("KPI-INV-02")
                .requires
                .containsAll(listOf("program_budget.allocated_amount", "program_budget.executed_amount"))
        )
        assertTrue(
            Kpis.BY_CODE
                .getValue("KPI-INV-03")
                .requires
                .containsAll(listOf("delivery.delivery_date", "delivery.social_contribution"))
        )
        assertTrue(
            Kpis.BY_CODE
                .getValue("KPI-INV-04")
                .requires
                .containsAll(listOf("beneficiary_group.reached_count", "commitment.beneficiaries_count"))
        )
    }

    // §3.9: project, explorationCampaign, program, year, el periodo y locality; cada KPI declara los que no aplica (P27)
    @Test
    fun `cada KPI-INV declara los filtros que no aplica`() {
        assertEquals(listOf("administrativeUnit", "locality", "community", "areaOfInfluence", "scope", "owner", "program", "year"), ignored("KPI-INV-01"))
        assertEquals(listOf("administrativeUnit", "locality", "community", "areaOfInfluence", "explorationCampaign", "scope", "owner"), ignored("KPI-INV-02"))
        assertEquals(
            listOf("administrativeUnit", "community", "areaOfInfluence", "explorationCampaign", "scope", "owner", "program", "year"),
            ignored("KPI-INV-03")
        )
        assertEquals(listOf("administrativeUnit", "community", "areaOfInfluence", "explorationCampaign", "scope", "owner", "year"), ignored("KPI-INV-04"))
    }

    @Test
    fun `lo comprometido de una campana usa los estados de CampaignCommitted`() {
        assertEquals(setOf("approved", "procured", "delivered", "closed"), Contributions.COMMITTED)
        val campaign = UUID.randomUUID().toString()

        fun row(
            state: String,
            amount: String,
            field: String
        ) = RecordResponse(UUID.randomUUID().toString(), null, null, mapOf("exploration_campaign" to campaign, field to amount, "currency" to "PEN"), state)
        val records =
            mock(Records::class.java) { call ->
                if (call.method.name != "all") return@mock null
                when (call.arguments[0]) {
                    "commitment" -> listOf(row("draft", "1", "amount"), row("approved", "10", "amount"), row("closed", "20", "amount"))
                    "social_contribution" ->
                        listOf(
                            row("requested", "100", "total_amount"),
                            row("approved", "200", "total_amount"),
                            row("delivered", "300", "total_amount"),
                            row("rejected", "400", "total_amount")
                        )
                    else -> emptyList<RecordResponse>()
                }
            }
        val data = DashboardData(records, all, { true }, LocalDate.parse("2026-10-15"))
        val amounts = runBlocking { data.committedOf(listOf(campaign)) }.map { it.amount }
        assertEquals(listOf("10", "20", "200", "300").map(::BigDecimal), amounts)
    }
}
