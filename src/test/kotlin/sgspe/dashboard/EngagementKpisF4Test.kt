package sgspe.dashboard

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import sgspe.common.ModelCatalog
import java.math.BigDecimal
import java.time.LocalDate

// KPI-ENG-06 a 08 (dashboards.md §4.3, #11 a #13 de la Tabla 9.5), sin base
class EngagementKpisF4Test {
    private fun engagement(
        id: String,
        type: String
    ) = EngagementRow(id, "p", type, "Monitoreo $id", LocalDate.parse("2026-10-0${id.last()}"), emptyMap())

    private fun mention(
        id: String,
        tone: String?,
        outlet: String? = null,
        name: String? = null,
        topic: String? = null,
        fulfilled: Boolean = false
    ) = MentionRow(id, outlet, name, tone, topic, fulfilled)

    @Test
    fun `KPI-ENG-06 cuenta los monitoreos participativos con sus grupos de interes`() {
        val rows = listOf(engagement("e1", "MONITOREO PARTICIPATIVO"), engagement("e2", "REUNION"), engagement("e3", "MONITOREO PARTICIPATIVO"))
        val result = EngagementKpis.monitoring(rows, mapOf("e1" to listOf("g1", "g2", "g1")))
        assertEquals(2, result.value)
        assertEquals(
            listOf(2, 0),
            result.breakdowns
                .single()
                .rows
                .map { it["value"] }
        )
    }

    @Test
    fun `KPI-ENG-07 cuenta las menciones de compromisos cumplidos por medio`() {
        val mentions =
            listOf(
                mention("m1", "POSITIVO", outlet = "o1", fulfilled = true),
                mention("m2", "NEUTRO", name = " Radio Ejemplo ", fulfilled = true),
                mention("m3", "POSITIVO")
            )
        val result = EngagementKpis.fulfilledCommunications(mentions, mapOf("o1" to "Diario Ejemplo"))
        assertEquals(2, result.value)
        assertEquals(
            setOf("Diario Ejemplo", "Radio Ejemplo"),
            result.breakdowns
                .single()
                .rows
                .map { it["label"] }
                .toSet()
        )
    }

    @Test
    fun `KPI-ENG-08 da el porcentaje de menciones negativas y de medios por tono`() {
        val mentions =
            listOf(
                mention("m1", "NEGATIVO", name = "Radio Ejemplo"),
                mention("m2", "POSITIVO", name = "radio ejemplo"),
                mention("m3", "NEGATIVO", outlet = "o1", topic = "t1"),
                mention("m4", "NEUTRO", outlet = "o1", topic = "t1")
            )
        val result = EngagementKpis.tones(mentions, mapOf("o1" to "Diario Ejemplo"), mapOf("t1" to "Agua"))
        assertEquals(BigDecimal("50.0"), result.value)
        assertEquals(2, result.numerator)
        assertEquals(4, result.denominator)
        val tones =
            result.breakdowns
                .first { it.by == "tone" }
                .rows
                .associateBy { it["key"] }
        // los dos medios tienen una mención negativa; solo la radio, una positiva (el nombre escrito se normaliza)
        assertEquals(BigDecimal("100.0"), tones.getValue("NEGATIVO")["outletPercent"])
        assertEquals(BigDecimal("50.0"), tones.getValue("POSITIVO")["outletPercent"])
        assertEquals(
            2,
            result.breakdowns
                .first { it.by == "outlet" }
                .rows.size
        )
        assertEquals(
            "Agua",
            result.breakdowns
                .first { it.by == "topic" }
                .rows
                .first { it["key"] == "t1" }["label"]
        )
    }

    @Test
    fun `los KPI-ENG de F4 no tienen tablero, y los de medios ignoran el territorio`() {
        for (code in listOf("KPI-ENG-06", "KPI-ENG-07", "KPI-ENG-08")) assertEquals(null, Kpis.BY_CODE.getValue(code).dashboard, code)
        assertEquals(true, "locality" in Kpis.BY_CODE.getValue("KPI-ENG-08").ignores)
        assertEquals(false, "locality" in Kpis.BY_CODE.getValue("KPI-ENG-06").ignores)
        val catalog = ModelCatalog.fromClasspath()
        assertEquals(true, EngagementKpis.MONITORING in catalog.enumOf("engagement", "engagement_type"))
        assertEquals(EngagementKpis.TONES, catalog.enumOf("media_mention", "tone"))
    }
}
