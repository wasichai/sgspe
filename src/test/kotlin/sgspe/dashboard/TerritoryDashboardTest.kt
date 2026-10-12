package sgspe.dashboard

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions
import sgspe.common.Records
import java.time.LocalDate
import java.util.UUID

// el tablero territory y KPI-TER-01 sin base (dashboards.md §4.8 y §5.9): KPI-TER-02 a 04 en el tablero; KPI-TER-01 se pide
// por la ficha y, sin localidad ni comunidad, no está disponible antes de leer nada
class TerritoryDashboardTest {
    @Test
    fun `el tablero territory reune KPI-TER-02 a 04 y los cuatro KPI-TER son suyos`() {
        assertEquals(listOf("KPI-TER-02", "KPI-TER-03", "KPI-TER-04"), Kpis.DASHBOARDS.getValue(Kpis.TERRITORY))
        val territorial = Kpis.ALL.filter { it.code.startsWith("KPI-TER-") }
        assertEquals(listOf("KPI-TER-01", "KPI-TER-02", "KPI-TER-03", "KPI-TER-04"), territorial.map { it.code })
        assertTrue(territorial.all { it.dashboard == Kpis.TERRITORY })
        assertTrue(Kpis.confidentialSensitive(TerritorySummary.CODE))
        assertEquals(emptyList<String>(), Kpis.BY_CODE.getValue(TerritorySummary.CODE).requires)
    }

    @Test
    fun `KPI-TER-01 sin localidad ni comunidad no esta disponible y no lee nada`() {
        val records = mock(Records::class.java)
        val filters =
            DashboardFilters(listOf(UUID.randomUUID()), LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-31"), null, null, null, null, null, null, null)
        val value =
            runBlocking {
                KpiEvaluation.evaluate(
                    Kpis.BY_CODE.getValue(TerritorySummary.CODE),
                    DashboardData(records, filters, { true }, LocalDate.parse("2026-10-11"))
                )
            }
        assertFalse(value.available)
        assertEquals(UnavailableReason.TERRITORY_REQUIRED, value.unavailableReason)
        assertEquals(listOf("locality", "community"), value.missingFields)
        verifyNoInteractions(records)
    }

    // H5: KPI-TER-02 a 04 no usan el periodo; si vino periodStart o periodEnd lo declaran, y el mes por defecto no
    @Test
    fun `KPI-TER-02 a 04 declaran el periodo pedido en ignoredFilters y KPI-TER-01 no`() {
        val place = UUID.randomUUID()

        fun filters(periodGiven: Boolean) =
            DashboardFilters(
                listOf(UUID.randomUUID()),
                LocalDate.parse("2026-10-01"),
                LocalDate.parse("2026-10-31"),
                null,
                place,
                null,
                null,
                null,
                null,
                null,
                periodGiven = periodGiven
            )
        for (code in listOf("KPI-TER-02", "KPI-TER-03", "KPI-TER-04")) {
            assertEquals(listOf("periodStart", "periodEnd", "locality"), Kpis.ignoredFilters(Kpis.BY_CODE.getValue(code), filters(true)), code)
            assertEquals(listOf("locality"), Kpis.ignoredFilters(Kpis.BY_CODE.getValue(code), filters(false)), code)
        }
        assertTrue("periodStart" !in Kpis.ignoredFilters(Kpis.BY_CODE.getValue(TerritorySummary.CODE), filters(true)))
        // ningún otro indicador declara el periodo: su respuesta no cambia
        assertTrue(Kpis.ALL.filterNot { it.code in setOf("KPI-TER-02", "KPI-TER-03", "KPI-TER-04") }.none { "periodStart" in it.ignores })
    }
}
