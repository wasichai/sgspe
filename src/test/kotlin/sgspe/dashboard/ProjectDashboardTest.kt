package sgspe.dashboard

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

// dashboards.md §5.2: las zonas del tablero project, en su orden
class ProjectDashboardTest {
    @Test
    fun `las zonas del tablero project estan registradas en su orden`() {
        assertEquals(
            listOf(
                "KPI-STK-01",
                "KPI-STK-02",
                "KPI-STK-03",
                "KPI-ENG-01",
                "KPI-ENG-02",
                "KPI-COM-02",
                "KPI-COM-03",
                "KPI-COM-04",
                "KPI-COM-06",
                "KPI-GRV-01",
                "KPI-GRV-02",
                "KPI-GRV-06",
                "KPI-RSK-01",
                "KPI-RSK-02",
                "KPI-INV-02",
                "KPI-INV-03",
                "KPI-INV-04"
            ),
            Kpis.DASHBOARDS.getValue(Kpis.PROJECT)
        )
    }
}
