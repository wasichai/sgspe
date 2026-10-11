package sgspe.dashboard

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

// los KPI-INV (dashboards.md §4.7, especificación de F5 §3.9, decisión 3 del plan 5.2b), sin base: la moneda de cada cifra,
// lo que no se suma y los desgloses
class InvestmentKpisTest {
    private fun amount(text: String?) = text?.let(::BigDecimal)

    private fun same(
        expected: String,
        actual: Any?
    ) = assertEquals(0, BigDecimal(expected).compareTo(actual as BigDecimal), "$actual")

    private fun commitment(
        id: String,
        state: String,
        currency: String? = "PEN",
        beneficiaries: Long? = null
    ) = CommitmentRow(
        id = id,
        project = "p1",
        state = state,
        ownerEmail = null,
        locality = null,
        community = null,
        topic = null,
        theme = null,
        formality = null,
        agreedDate = null,
        dueDate = null,
        fulfilledDate = null,
        cancelledDate = null,
        deadlineStatus = null,
        amount = null,
        currency = currency,
        executed = null,
        beneficiaries = beneficiaries
    )

    private fun rows(
        result: IndicatorResult,
        by: String
    ) = result.breakdowns.first { it.by == by }.rows

    @Test
    fun `KPI-INV-01 compara lo comprometido y lo ejecutado de cada campana con su tope, en su moneda`() {
        val campaigns =
            listOf(
                CampaignRow("c1", "p1", "Campaña A", amount("10000"), "PEN", amount("2500"), 1),
                CampaignRow("c2", "p1", "Campaña B", amount("500"), "USD", amount("0"), 0),
                CampaignRow("c3", "p1", "Campaña C", null, "PEN", null, 0)
            )
        val committed =
            listOf(
                CommittedAmount("c1", amount("3000"), "PEN"),
                CommittedAmount("c1", amount("2000"), "PEN"),
                CommittedAmount("c1", amount("400"), "USD"),
                CommittedAmount("c1", null, "PEN"),
                CommittedAmount("c2", amount("100"), "USD")
            )
        val result = InvestmentKpis.campaigns(campaigns, committed)
        // la cifra: lo ejecutado sobre el tope de las campañas en PEN con tope (solo c1)
        assertEquals(BigDecimal("25.0"), result.value)
        same("2500", result.numerator)
        same("10000", result.denominator)
        val byKey = rows(result, "campaign").associateBy { it["key"] }
        assertEquals(listOf("c1", "c2", "c3"), rows(result, "campaign").map { it["key"] })
        same("5000", byKey.getValue("c1")["committed"])
        assertEquals(BigDecimal("50.0"), byKey.getValue("c1")["committedPercent"])
        assertEquals(BigDecimal("25.0"), byKey.getValue("c1")["executedPercent"])
        assertEquals(1, byKey.getValue("c1")["committedOtherCurrencyCount"])
        assertEquals(1, byKey.getValue("c1")["otherCurrencyCount"])
        same("100", byKey.getValue("c2")["committed"])
        assertEquals(BigDecimal("20.0"), byKey.getValue("c2")["committedPercent"])
        assertNull(byKey.getValue("c3")["committedPercent"])
        assertEquals(1, result.warnings.size)
    }

    @Test
    fun `KPI-INV-01 sin campanas en PEN no tiene cifra y una campana sin moneda no compara lo comprometido`() {
        val result =
            InvestmentKpis.campaigns(
                listOf(CampaignRow("c1", "p1", "Campaña A", amount("100"), null, null, 0)),
                listOf(CommittedAmount("c1", amount("50"), "PEN"))
            )
        assertNull(result.value)
        val row = rows(result, "campaign").single()
        assertNull(row["committed"])
        assertEquals(1, row["committedOtherCurrencyCount"])
    }

    @Test
    fun `KPI-INV-02 da el avance por programa, anio y moneda y la cifra solo en PEN`() {
        val programs =
            listOf(ProgramRow("g1", "p1", "PRG-1", "Programa Ejemplo", "PEN", "executing"), ProgramRow("g2", "p1", "PRG-2", "Otro programa", "PEN", "planned"))
        val budgets =
            listOf(
                BudgetRow("g1", 2026, amount("1000"), amount("250"), "PEN"),
                BudgetRow("g1", 2027, amount("400"), amount("100"), "USD"),
                BudgetRow("g2", 2026, amount("3000"), amount("750"), "PEN"),
                BudgetRow("gx", 2026, amount("9999"), amount("9999"), "PEN")
            )
        val all = InvestmentKpis.programs(programs, budgets, null)
        assertEquals(BigDecimal("25.0"), all.value)
        assertEquals(listOf("g1:2026:PEN", "g1:2027:USD", "g2:2026:PEN"), rows(all, "program").map { it["key"] })
        assertEquals("PRG-1 · Programa Ejemplo", rows(all, "program").first()["label"])
        assertEquals(BigDecimal("25.0"), rows(all, "program").first()["percent"])
        assertEquals("executing", rows(all, "program").first()["state"])
        assertEquals(1, all.warnings.size)
        val only2027 = InvestmentKpis.programs(programs, budgets, 2027)
        assertNull(only2027.value)
        assertEquals(listOf("g1:2027:USD"), rows(only2027, "program").map { it["key"] })
    }

    @Test
    fun `KPI-INV-03 suma lo ejecutado del periodo por fuente y por proyecto y aparte las otras monedas`() {
        val start = LocalDate.parse("2026-10-01")
        val end = LocalDate.parse("2026-10-31")
        val commitments = listOf(commitment("m1", "in_progress"), commitment("m2", "in_progress", "USD"))
        val commitmentDeliveries =
            listOf(
                DeliveryRow("m1", LocalDate.parse("2026-10-05"), amount("700")),
                DeliveryRow("m1", LocalDate.parse("2026-09-30"), amount("999")),
                DeliveryRow("m2", LocalDate.parse("2026-10-10"), amount("50"))
            )
        val contributions =
            listOf(
                ContributionRow("s1", "p1", "delivered", amount("2000"), "PEN", 30),
                ContributionRow("s2", "p1", "closed", amount("300"), "PEN", 5),
                ContributionRow("s3", "p1", "procured", amount("900"), "PEN", 9)
            )
        val contributionDeliveries =
            listOf(
                DeliveryRow("s1", LocalDate.parse("2026-10-10"), null),
                DeliveryRow("s2", LocalDate.parse("2026-11-02"), null),
                DeliveryRow("s3", LocalDate.parse("2026-10-12"), null)
            )
        val programs = listOf(ProgramRow("g1", "p1", "PRG-1", "Programa Ejemplo", "PEN", "closed"))
        val budgets = listOf(BudgetRow("g1", 2026, amount("1000"), amount("250"), "PEN"), BudgetRow("g1", 2025, amount("800"), amount("800"), "PEN"))
        val campaigns = listOf(CampaignRow("c1", "p1", "Campaña A", amount("10000"), "PEN", null, 0))
        val codes = mapOf("p1" to "PRY-1")
        val result =
            InvestmentKpis.executed(
                commitments,
                commitmentDeliveries,
                contributions,
                contributionDeliveries,
                programs,
                budgets,
                campaigns,
                start,
                end,
                true,
                codes
            )
        same("2950", result.value)
        assertEquals(
            listOf("COMPROMISOS:PEN", "COMPROMISOS:USD", "APORTES:PEN", "PROGRAMAS:PEN"),
            rows(result, "source").map { it["key"] }
        )
        val pen = rows(result, "project").first { it["key"] == "p1:PEN" }
        assertEquals("PRY-1", pen["label"])
        same("2950", pen["executed"])
        same("700", pen["commitments"])
        same("2000", pen["contributions"])
        same("250", pen["programs"])
        same("1000", pen["budget"])
        same("10000", pen["cap"])
        same("50", rows(result, "project").first { it["key"] == "p1:USD" }["executed"])
        assertEquals(1, result.warnings.size)
        // con un filtro de localidad los programas, que no tienen territorio, no se suman y se advierte
        val local =
            InvestmentKpis.executed(
                commitments,
                commitmentDeliveries,
                contributions,
                contributionDeliveries,
                emptyList(),
                emptyList(),
                campaigns,
                start,
                end,
                false,
                codes
            )
        same("2700", local.value)
        assertTrue(local.warnings.any { it.startsWith("Los programas") }, "${local.warnings}")
    }

    @Test
    fun `KPI-INV-04 da tres bloques de beneficiarios sin sumarlos`() {
        val programs =
            listOf(
                ProgramRow("g1", "p1", "PRG-1", "Programa Ejemplo", "PEN", "executing"),
                ProgramRow("g2", "p1", "PRG-2", "Otro programa", "PEN", "monitoring")
            )
        val groups = listOf(GroupRow("g1", 100, 40), GroupRow("g1", 50, null), GroupRow("g2", null, 5), GroupRow("gx", 1000, 1000))
        val contributions =
            listOf(
                ContributionRow("s1", "p1", "delivered", null, "PEN", 30),
                ContributionRow("s2", "p1", "closed", null, "PEN", 20),
                ContributionRow("s3", "p1", "approved", null, "PEN", 99)
            )
        val commitments =
            listOf(
                commitment("m1", "fulfilled", beneficiaries = 12),
                commitment("m2", "verified", beneficiaries = 8),
                commitment("m3", "in_progress", beneficiaries = 100)
            )
        val result = InvestmentKpis.beneficiaries(programs, groups, contributions, commitments)
        assertEquals(45L, result.value)
        val sources = rows(result, "source").associateBy { it["key"] }
        assertEquals(45L, sources.getValue("PROGRAMAS")["value"])
        assertEquals(150L, sources.getValue("PROGRAMAS")["target"])
        assertEquals(50L, sources.getValue("APORTES")["value"])
        assertEquals(2, sources.getValue("APORTES")["count"])
        assertEquals(20L, sources.getValue("COMPROMISOS")["value"])
        val g1 = rows(result, "program").first { it["key"] == "g1" }
        assertEquals(150L, g1["target"])
        assertEquals(40L, g1["reached"])
    }

    // decisión del usuario del 2026-10-11: los KPI-INV cuentan un programa solo desde «Planificado» de WF-G
    @Test
    fun `KPI-INV-02 a 04 cuentan un programa solo desde Planificado`() {
        val counted = listOf("planned", "executing", "monitoring", "evaluation", "suspended", "closed")
        val left = listOf("draft", "pending_approval", "cancelled", "rejected", null)
        assertEquals(counted.toSet(), InvestmentKpis.COUNTED_PROGRAM_STATES)
        val programs = (counted + left).mapIndexed { i, state -> ProgramRow("g$i", "p1", "PRG-$i", "Programa $state", "PEN", state) }
        val budgets = programs.map { BudgetRow(it.id, 2026, amount("100"), amount("10"), "PEN") }
        val groups = programs.map { GroupRow(it.id, 10, 1) }
        val inPlay = programs.filter { it.state in counted }.map { it.id }
        val progress = InvestmentKpis.programs(programs, budgets, null)
        assertEquals(inPlay, rows(progress, "program").map { it["program"] })
        same("600", progress.denominator)
        same("60", progress.numerator)
        val executed =
            InvestmentKpis.executed(
                emptyList(),
                emptyList(),
                emptyList(),
                emptyList(),
                programs,
                budgets,
                emptyList(),
                LocalDate.parse("2026-10-01"),
                LocalDate.parse("2026-10-31"),
                true,
                emptyMap()
            )
        same("60", executed.value)
        same("600", rows(executed, "project").single()["budget"])
        val beneficiaries = InvestmentKpis.beneficiaries(programs, groups, emptyList(), emptyList())
        assertEquals(6L, beneficiaries.value)
        assertEquals(inPlay, rows(beneficiaries, "program").map { it["key"] })
        // un programa en draft, pending_approval o cancelled, solo, da conjuntos vacíos
        for (state in listOf("draft", "pending_approval", "cancelled")) {
            val one = listOf(ProgramRow("gx", "p1", "PRG-X", "Programa X", "PEN", state))
            val alone = listOf(BudgetRow("gx", 2026, amount("100"), amount("10"), "PEN"))
            assertNull(InvestmentKpis.programs(one, alone, null).value, state)
            assertTrue(rows(InvestmentKpis.programs(one, alone, null), "program").isEmpty(), state)
            assertEquals(0L, InvestmentKpis.beneficiaries(one, listOf(GroupRow("gx", 10, 1)), emptyList(), emptyList()).value, state)
        }
    }
}
