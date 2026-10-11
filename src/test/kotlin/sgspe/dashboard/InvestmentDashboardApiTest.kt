package sgspe.dashboard

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import sgspe.F5ProgramApiTest
import sgspe.common.DerivedFieldsJob
import tools.jackson.databind.JsonNode
import java.math.BigDecimal

// el tablero investment (dashboards.md §5.10, especificación de F5 §3.9 y §5): los cuatro KPI-INV con montos en dos monedas,
// los filtros program y year y un proyecto ajeno con conjuntos vacíos. las fechas caen en octubre de 2026
class InvestmentDashboardApiTest : F5ProgramApiTest() {
    @Autowired
    private lateinit var job: DerivedFieldsJob

    private val period = "periodStart=2026-10-01&periodEnd=2026-10-31"

    private fun dashboard(
        query: String,
        token: String = this.token
    ): JsonNode = tree(send("GET", "/api/sgspe/dashboards/investment?$query", null, HttpStatus.OK, token))

    private fun indicator(
        body: JsonNode,
        code: String
    ): JsonNode = body["indicators"].values().first { it["code"].asString() == code }

    private fun rows(
        kpi: JsonNode,
        by: String
    ): List<JsonNode> =
        kpi["breakdowns"]
            .values()
            .first { it["by"].asString() == by }["rows"]
            .values()
            .toList()

    private fun same(
        expected: String,
        node: JsonNode?
    ) = assertEquals(0, BigDecimal(expected).compareTo(node!!.decimalValue()), "$node")

    @Test
    fun `el tablero trae los cuatro KPI-INV en orden y el catalogo los da disponibles`() {
        val body = dashboard("project=${projectWithoutInternalApproval()}&$period")
        assertEquals(listOf("KPI-INV-01", "KPI-INV-02", "KPI-INV-03", "KPI-INV-04"), body["indicators"].values().map { it["code"].asString() })
        val catalog =
            tree(send("GET", "/api/sgspe/dashboards/indicators", null, HttpStatus.OK)).values().filter { it["code"].asString().startsWith("KPI-INV-") }
        assertEquals(4, catalog.size)
        assertTrue(catalog.all { it["available"].asBoolean() && it["dashboard"].asString() == "investment" })
    }

    @Test
    fun `KPI-INV-01 suma lo comprometido de compromisos y aportes en la moneda de la campana y cuenta el resto`() {
        val project = projectWithoutInternalApproval()
        val investor = member("SOCIAL_INVESTMENT_MANAGER", project)
        // F5ApiTest.campaign: una campaña del proyecto con tope de 10 000 PEN
        val withCap = campaign(project, 10000, "PEN")
        approvedCommitment(project, attributes = mapOf("exploration_campaign" to withCap, "amount" to 3000))
        registerApprovedContribution(project, investor, lineAmount = 2000, attributes = mapOf("exploration_campaign" to withCap))
        registerApprovedContribution(project, investor, currency = "USD", lineAmount = 500, attributes = mapOf("exploration_campaign" to withCap))
        registerContribution(project, investor, lineAmount = 999, attributes = mapOf("exploration_campaign" to withCap))
        val kpi = indicator(dashboard("project=$project&$period"), "KPI-INV-01")
        val row = rows(kpi, "campaign").single()
        assertEquals(withCap, row["key"].asString())
        same("10000", row["cap"])
        same("5000", row["committed"])
        assertEquals(1, row["committedOtherCurrencyCount"].asInt())
        same("50.0", row["committedPercent"])
        assertTrue(kpi["warnings"].values().isNotEmpty())
    }

    @Test
    fun `KPI-INV-02 da el avance por programa, anio y moneda, con program y year`() {
        val project = projectWithoutInternalApproval()
        val investor = member("SOCIAL_INVESTMENT_MANAGER", project)
        val program = socialProgram(project, investor)
        programBudget(program, 2026, 1000, executed = 250, creator = investor)
        programBudget(program, 2027, 400, "USD", executed = 100, creator = investor)
        socialProgram(project, investor).also { programBudget(it, 2026, 3000, executed = 750, creator = investor) }
        val all = indicator(dashboard("project=$project&$period"), "KPI-INV-02")
        same("25.0", all["value"])
        assertEquals(3, rows(all, "program").size)
        val one = indicator(dashboard("project=$project&$period&program=$program&year=2027"), "KPI-INV-02")
        assertTrue(one["value"].isNull)
        assertEquals(listOf("$program:2027:USD"), rows(one, "program").map { it["key"].asString() })
        val ignored = indicator(dashboard("project=$project&program=$program&year=2027"), "KPI-INV-01")["ignoredFilters"]
        assertEquals(listOf("program", "year"), ignored.values().map { it.asString() })
    }

    @Test
    fun `KPI-INV-03 suma lo ejecutado del periodo por fuente y KPI-INV-04 da los beneficiarios en tres bloques`() {
        val project = projectWithoutInternalApproval()
        val investor = member("SOCIAL_INVESTMENT_MANAGER", project)
        val commitment = approvedCommitment(project, attributes = mapOf("amount" to 3000))
        val delivered =
            exchange(
                "POST",
                "/api/sgspe/commitments/$commitment/deliveries",
                mapOf(
                    "attributes" to mapOf("delivery_date" to "2026-10-05", "conformity_given" to true, "delivered_amount" to 700),
                    "evidence" to emptyList<String>()
                ),
                token,
                withReason("entrega de prueba")
            )
        assertEquals(HttpStatus.CREATED, delivered.status, delivered.body)
        registerDeliveredContribution(project, investor, "2026-10-10", lineAmount = 2000)
        registerDeliveredContribution(project, investor, "2026-10-12", currency = "USD", lineAmount = 50)
        val program = socialProgram(project, investor)
        programBudget(program, 2026, 1000, executed = 250, creator = investor)
        beneficiaryGroup(program, creator = investor, attributes = mapOf("target_count" to 100, "reached_count" to 40))
        val body = dashboard("project=$project&$period")
        val executed = indicator(body, "KPI-INV-03")
        same("2950", executed["value"])
        val sources = rows(executed, "source").associate { it["key"].asString() to it["value"] }
        same("700", sources["COMPROMISOS:PEN"])
        same("2000", sources["APORTES:PEN"])
        same("50", sources["APORTES:USD"])
        same("250", sources["PROGRAMAS:PEN"])
        assertTrue(executed["warnings"].values().isNotEmpty())
        val beneficiaries = indicator(body, "KPI-INV-04")
        assertEquals(40, beneficiaries["value"].asInt())
        val blocks = rows(beneficiaries, "source").associateBy { it["key"].asString() }
        assertEquals(60, blocks.getValue("APORTES")["value"].asInt())
        assertEquals(0, blocks.getValue("COMPROMISOS")["value"].asInt())
    }

    @Test
    fun `KPI-INV-01 muestra el ejecutado de la campana que deja JOB-02`() {
        val project = projectWithoutInternalApproval()
        val investor = member("SOCIAL_INVESTMENT_MANAGER", project)
        // F5ApiTest.campaign: una campaña del proyecto con tope de 10 000 PEN
        val withCap = campaign(project, 10000, "PEN")
        registerDeliveredContribution(project, investor, lineAmount = 2500, attributes = mapOf("exploration_campaign" to withCap))
        assertNotNull(runBlocking { job.runOnce(full = true) }, "otro nodo o la programación tenía el candado")
        val kpi = indicator(dashboard("project=$project&$period"), "KPI-INV-01")
        same("25.0", kpi["value"])
        same("2500", rows(kpi, "campaign").single()["executed"])
    }

    @Test
    fun `un proyecto ajeno da conjuntos vacios`() {
        val mine = projectWithoutInternalApproval()
        val theirs = projectWithoutInternalApproval()
        val investor = member("SOCIAL_INVESTMENT_MANAGER", theirs)
        val program = socialProgram(theirs, investor)
        programBudget(program, 2026, 1000, executed = 250, creator = investor)
        beneficiaryGroup(program, creator = investor, attributes = mapOf("target_count" to 100, "reached_count" to 40))
        registerApprovedContribution(theirs, investor)
        val outsider = member("SOCIAL_INVESTMENT_MANAGER", mine)
        val body = dashboard("project=$theirs&$period", outsider.token)
        for (code in listOf("KPI-INV-01", "KPI-INV-02")) {
            val kpi = indicator(body, code)
            assertTrue(kpi["available"].asBoolean(), code)
            assertTrue(kpi["value"].isNull, code)
            assertTrue(kpi["breakdowns"].values().all { it["rows"].isEmpty }, code)
        }
        same("0", indicator(body, "KPI-INV-03")["value"])
        assertEquals(0, indicator(body, "KPI-INV-04")["value"].asInt())
    }

    // la entrada 5 del Review Focus
    @Test
    fun `program y year en otro tablero son 422 y un anio fuera de rango es 400`() {
        val project = projectWithoutInternalApproval()
        assertEquals(HttpStatus.UNPROCESSABLE_CONTENT, exchange("GET", "/api/sgspe/dashboards/risks?project=$project&year=2026").status)
        assertEquals(HttpStatus.UNPROCESSABLE_CONTENT, exchange("GET", "/api/sgspe/dashboards/indicators/KPI-COM-01?program=$project").status)
        assertEquals(HttpStatus.BAD_REQUEST, exchange("GET", "/api/sgspe/dashboards/investment?project=$project&year=1999").status)
        assertEquals(HttpStatus.OK, exchange("GET", "/api/sgspe/dashboards/indicators/KPI-INV-02?project=$project&year=2026").status)
    }
}
