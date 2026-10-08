package sgspe.dashboard

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions
import sgspe.common.Records
import wasichai.core.common.WasichaiException
import java.time.LocalDate
import java.util.UUID

// los topes de lectura de los tableros y los filtros que cada KPI ignora (dashboards.md §3.1 y §6.2), sin base
class DashboardDataTest {
    private fun filters(
        community: UUID? = null,
        campaign: UUID? = null,
        locality: UUID? = null
    ) = DashboardFilters(
        listOf(UUID.randomUUID()),
        LocalDate.parse("2026-05-01"),
        LocalDate.parse("2026-05-31"),
        null,
        locality,
        community,
        null,
        campaign,
        null,
        null
    )

    @Test
    fun `pasarse del tope de las M2M es TOO_MANY_RECORDS antes de leer ninguna`() {
        val records = mock(Records::class.java)
        val data = DashboardData(records, filters(), { true }, maxRelated = 1)
        val error = assertThrows<WasichaiException> { runBlocking { data.topicsOf(listOf(UUID.randomUUID().toString(), UUID.randomUUID().toString())) } }
        assertEquals(400, error.status.value())
        assertEquals("TOO_MANY_RECORDS", error.violations.single().message)
        verifyNoInteractions(records)
    }

    @Test
    fun `el tope de las M2M baja a 200 interacciones`() {
        assertEquals(200, DashboardData.MAX_RELATED)
    }

    // P27: stakeholder no tiene comunidad, así que los KPI-STK ignoran community y lo dicen; F2 ignora explorationCampaign
    @Test
    fun `los KPI-STK ignoran community y todos ignoran explorationCampaign, y lo dicen`() {
        val both = filters(community = UUID.randomUUID(), campaign = UUID.randomUUID())
        assertEquals(listOf("community", "explorationCampaign"), Kpis.ignoredFilters(Kpis.BY_CODE.getValue("KPI-STK-01"), both))
        assertEquals(listOf("community", "explorationCampaign"), Kpis.ignoredFilters(Kpis.BY_CODE.getValue("KPI-STK-07"), both))
        assertEquals(listOf("explorationCampaign"), Kpis.ignoredFilters(Kpis.BY_CODE.getValue("KPI-ENG-01"), both))
        assertEquals(emptyList<String>(), Kpis.ignoredFilters(Kpis.BY_CODE.getValue("KPI-STK-01"), filters(locality = UUID.randomUUID())))
    }

    // con community, los grupos de interés no se filtran por una comunidad que no tienen: no quedan en cero
    @Test
    fun `community no filtra a los grupos de interes`() {
        val data = DashboardData(mock(Records::class.java), filters(community = UUID.randomUUID()), { true })
        assertEquals(0, runBlocking { data.stakeholderTerritories() }.size)
        assertEquals(1, runBlocking { data.engagementTerritories() }.size)
    }
}
