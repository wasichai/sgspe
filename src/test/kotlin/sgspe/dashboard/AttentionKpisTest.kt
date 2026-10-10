package sgspe.dashboard

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

// los KPI-GRV puros (dashboards.md §4.5): cada planteamiento una vez (los originales reclasificados no cuentan), los estados de
// §3.2 y las fechas de §12, sin base
class AttentionKpisTest {
    private val start = LocalDate.parse("2026-10-01")
    private val end = LocalDate.parse("2026-10-31")

    private fun date(text: String?) = text?.let(LocalDate::parse)

    private fun row(
        type: String,
        id: String,
        state: String = "received",
        received: String? = "2026-10-05",
        responded: String? = null,
        resolved: String? = null,
        closed: String? = null,
        resolutionDue: String? = null,
        responseDue: String? = null,
        deadline: String? = null,
        channel: String? = "PRESENCIAL",
        severity: String? = null,
        stakeholder: String? = null,
        anonymous: Boolean = false,
        reclassified: Map<String, String> = emptyMap()
    ) = AttentionRow(
        type,
        id,
        state,
        date(received),
        date(responded),
        date(resolved),
        date(closed),
        date(resolutionDue),
        date(responseDue),
        deadline,
        channel,
        "EMPLEO",
        severity,
        "loc-1",
        null,
        stakeholder,
        anonymous,
        reclassified
    )

    // una queja reclasificada como consulta: el original cerrado no cuenta
    private val original = row("grievance", "q1", "closed", closed = "2026-10-06")
    private val successor = row("inquiry", "c1", reclassified = mapOf("reclassified_from_grievance" to "q1"))

    private fun breakdown(
        result: IndicatorResult,
        by: String
    ) = result.breakdowns.first { it.by == by }.rows

    @Test
    fun `KPI-GRV-01 cuenta cada planteamiento una vez, por tipo y canal`() {
        val rows = listOf(original, successor, row("grievance", "q2", channel = "CARTA"), row("grievance", "q3", received = "2026-09-30"))
        val result = AttentionKpis.received(rows, start, end)
        assertEquals(2, result.value)
        assertEquals(listOf(mapOf("key" to "grievance", "value" to 1), mapOf("key" to "inquiry", "value" to 1)), breakdown(result, "type"))
        assertEquals(setOf("PRESENCIAL", "CARTA"), breakdown(result, "channel").map { it["key"] }.toSet())
    }

    @Test
    fun `KPI-GRV-02 cuenta las abiertas por tipo y estado`() {
        val rows = listOf(original, successor, row("grievance", "q2", "rejected"), row("stakeholder_request", "s1", "declined"))
        val result = AttentionKpis.open(rows)
        assertEquals(2, result.value)
        assertEquals(setOf("inquiry.received", "stakeholder_request.declined"), breakdown(result, "workflow_state").map { it["key"] }.toSet())
    }

    @Test
    fun `KPI-GRV-03 cuenta las quejas resueltas en el periodo por severidad`() {
        val rows =
            listOf(row("grievance", "q1", "resolved", resolved = "2026-10-10", severity = "ALTA"), row("grievance", "q2", "in_assessment", resolved = null))
        val result = AttentionKpis.resolved(rows, start, end)
        assertEquals(1, result.value)
        assertEquals(listOf(mapOf("key" to "ALTA", "value" to 1)), breakdown(result, "severity"))
    }

    @Test
    fun `KPI-GRV-04 y 05 separan cerradas, rechazadas y desistidas`() {
        val rows =
            listOf(
                original,
                successor,
                row("inquiry", "c2", "closed", closed = "2026-10-08"),
                row("grievance", "q2", "rejected", closed = "2026-10-09"),
                row("grievance", "q3", "withdrawn", closed = "2026-10-09")
            )
        assertEquals(1, AttentionKpis.closed(rows, start, end).value)
        val rejected = AttentionKpis.rejected(rows, start, end)
        assertEquals(1, rejected.value)
        assertEquals(listOf(mapOf("key" to "rejected", "value" to 1), mapOf("key" to "withdrawn", "value" to 1)), breakdown(rejected, "workflow_state"))
    }

    @Test
    fun `KPI-GRV-06 cuenta las abiertas vencidas`() {
        val rows =
            listOf(
                row("grievance", "q1", deadline = "VENCIDO"),
                row("inquiry", "c1", "closed", deadline = "VENCIDO"),
                row("inquiry", "c2", deadline = "AL DIA")
            )
        assertEquals(1, AttentionKpis.overdue(rows).value)
    }

    @Test
    fun `KPI-GRV-07 es el porcentaje de recibidas con respuesta`() {
        val rows =
            listOf(
                original,
                successor.copy(responded = date("2026-10-07")),
                row("grievance", "q2"),
                row("stakeholder_request", "s1", "closed", responded = "2026-10-09")
            )
        val result = AttentionKpis.absolved(rows, start, end)
        assertEquals(BigDecimal("66.7"), result.value)
        assertEquals(2, result.numerator)
        assertEquals(3, result.denominator)
    }

    @Test
    fun `KPI-GRV-08 desglosa recibidas y abiertas, con las anonimas aparte`() {
        val rows = listOf(row("grievance", "q1", severity = "ALTA", stakeholder = "g1"), row("inquiry", "c1", anonymous = true))
        val result = AttentionKpis.breakdowns(rows, start, end, mapOf("loc-1" to "Localidad Ejemplo"), mapOf("g1" to "Asociación Ejemplo"))
        assertEquals(2, result.value)
        val stakeholders = breakdown(result, "stakeholder").associate { it["key"] to it["label"] }
        assertEquals(mapOf("g1" to "Asociación Ejemplo", "ANONIMA" to "Anónimas"), stakeholders)
        assertEquals("Localidad Ejemplo", breakdown(result, "territory").single()["label"])
        assertEquals(1, breakdown(result, "severity").first { it["key"] == "ALTA" }["open"])
    }

    @Test
    fun `KPI-GRV-09 da doce meses de recibidas y cerradas`() {
        val rows = listOf(original, successor, row("inquiry", "c2", "closed", received = "2026-08-02", closed = "2026-10-03"))
        val result = AttentionKpis.receivedVsClosed(rows, end)
        val series = result.series!!
        assertEquals(12, series.size)
        assertEquals("2026-10", series.last()["period"])
        assertEquals(1, series.last()["received"])
        assertEquals(1, series.last()["closed"])
        assertEquals(1, series.first { it["period"] == "2026-08" }["received"])
    }

    @Test
    fun `KPI-GRV-10 da la mediana y el porcentaje dentro de plazo, y la solicitud solo la mediana`() {
        val rows =
            listOf(
                row("grievance", "q1", "resolved", received = "2026-10-01", resolved = "2026-10-11", resolutionDue = "2026-10-31"),
                row("grievance", "q2", "resolved", received = "2026-09-01", resolved = "2026-10-11", resolutionDue = "2026-10-01"),
                row("inquiry", "c1", "answered", received = "2026-10-01", responded = "2026-10-05", responseDue = "2026-10-16"),
                row("stakeholder_request", "s1", "declined", received = "2026-10-01", responded = "2026-10-21")
            )
        val result = AttentionKpis.attentionTime(rows, start, end)
        // días: 10, 40, 4, 20; mediana 15
        assertEquals(BigDecimal("15.0"), result.value)
        assertEquals(2, result.numerator)
        assertEquals(3, result.denominator)
        val byType = breakdown(result, "type").associateBy { it["key"] }
        assertEquals(BigDecimal("25.0"), byType.getValue("grievance")["value"])
        assertEquals(BigDecimal("50.0"), byType.getValue("grievance")["onTime"])
        assertTrue("onTime" !in byType.getValue("stakeholder_request"))
        assertEquals(BigDecimal("20.0"), byType.getValue("stakeholder_request")["value"])
        assertNull(AttentionKpis.median(emptyList()))
    }

    @Test
    fun `el tablero attention trae los diez KPI-GRV en orden y la marca de confidenciales`() {
        assertEquals((1..10).map { "KPI-GRV-%02d".format(it) }, Kpis.DASHBOARDS.getValue("attention"))
        assertTrue((1..10).all { Kpis.BY_CODE.getValue("KPI-GRV-%02d".format(it)).dashboard == "attention" })
        assertTrue(Kpis.confidentialSensitive("KPI-GRV-08") && Kpis.confidentialSensitive("KPI-ENG-05"))
        assertTrue(!Kpis.confidentialSensitive("KPI-ENG-04") && !Kpis.confidentialSensitive("KPI-COM-01"))
    }
}
