package sgspe.dashboard

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F4ApiTest
import tools.jackson.databind.JsonNode

// KPI-ENG-06 a 08 por el endpoint de indicadores (dashboards.md §4.3 y §11, api.md §4.11): disponibles desde F4 sin zona en un
// tablero; las menciones cuentan por tono y por compromisos cumplidos, y su territorio se ignora y se dice
class EngagementIndicatorsF4ApiTest : F4ApiTest() {
    private fun indicator(
        code: String,
        extra: String = ""
    ): JsonNode =
        tree(
            send(
                "GET",
                "/api/sgspe/dashboards/indicators/$code?project=${testProject()}&periodStart=2026-10-01&periodEnd=2026-10-31$extra",
                null,
                HttpStatus.OK
            )
        )

    @Test
    fun `el catalogo los informa disponibles desde F4`() {
        val catalog = tree(send("GET", "/api/sgspe/dashboards/indicators", null, HttpStatus.OK)).values().associateBy { it["code"].asString() }
        for (code in listOf("KPI-ENG-06", "KPI-ENG-07", "KPI-ENG-08")) assertTrue(catalog.getValue(code)["available"].asBoolean(), code)
    }

    @Test
    fun `las menciones cuentan por tono y por compromisos cumplidos`() {
        mediaMention(testProject(), attributes = mapOf("published_date" to "2026-10-06"))
        mediaMention(testProject(), attributes = mapOf("published_date" to "2026-10-07", "tone" to "POSITIVO", "reports_fulfilled_commitment" to true))
        mediaMention(testProject(), attributes = mapOf("published_date" to "2026-09-30"))
        assertEquals(1, indicator("KPI-ENG-07")["value"].asInt())
        val tones = indicator("KPI-ENG-08", "&locality=${java.util.UUID.randomUUID()}")
        assertEquals(50.0, tones["value"].asDouble())
        assertEquals(2, tones["denominator"].asInt())
        assertEquals(listOf("locality"), tones["ignoredFilters"].values().map { it.asString() })
        assertEquals(0, indicator("KPI-ENG-06")["value"].asInt())
    }
}
