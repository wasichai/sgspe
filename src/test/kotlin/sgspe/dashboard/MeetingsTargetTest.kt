package sgspe.dashboard

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions
import sgspe.common.Records
import java.time.LocalDate
import java.util.UUID

// R1 (decisión 3 del plan 8.2a): la meta mensual de un proyecto es la suya y, sin ella, la del catálogo; sin ninguna, 0. en
// un tenant con el catálogo, quien no lee su meta recibe KPI-ENG-02 no disponible, nunca con otra meta
class MeetingsTargetTest {
    private val today: LocalDate = LocalDate.parse("2026-10-14")
    private val filters = DashboardFilters.of(listOf(UUID.randomUUID()), LocalDate.parse("2026-09-01"), LocalDate.parse("2026-09-30"))

    @Test
    fun `la del proyecto manda, despues la del catalogo y sin ninguna es cero`() {
        assertEquals(3L, EngagementKpis.projectTarget(3, 2))
        assertEquals(2L, EngagementKpis.projectTarget(null, 2))
        assertEquals(0L, EngagementKpis.projectTarget(null, null))
    }

    @Test
    fun `sin leer la meta del catalogo KPI-ENG-02 no esta disponible, con la razon de lo que falta`() {
        val records = mock(Records::class.java)
        val kpi = Kpis.BY_CODE.getValue(Kpis.MEETINGS)
        for ((hidden, reason) in listOf(
            setOf("indicator") to UnavailableReason.OBJECT_NOT_READABLE,
            setOf(Kpis.MEETINGS_TARGET) to UnavailableReason.FIELD_NOT_READABLE
        )) {
            // sin el objeto indicator, o con el objeto pero sin su meta
            val data = DashboardData(records, filters, { path -> path !in hidden && path.substringBefore('.') !in hidden }, today, hasCatalog = true)
            val result = runBlocking { kpi.compute(data) }
            assertFalse(result.available)
            assertEquals(reason to listOf(Kpis.MEETINGS_TARGET), result.unavailableReason to result.missingFields)
        }
        verifyNoInteractions(records)
    }

    // un tenant de F7 (sin el catálogo) no mira el catálogo: sigue con la meta de ASM-019
    @Test
    fun `sin el catalogo en el tenant no hace falta leerlo`() {
        val data = DashboardData(mock(Records::class.java), filters, { !it.startsWith("indicator") }, today)
        assertFalse(runBlocking { data.meetingsDefaultUnreadable() })
    }
}
