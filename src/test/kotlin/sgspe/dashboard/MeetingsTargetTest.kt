package sgspe.dashboard

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

// R1 (decisión 3 del plan 8.2a): la meta mensual de un proyecto es la suya y, sin ella, la del catálogo; sin ninguna, 0
class MeetingsTargetTest {
    @Test
    fun `la del proyecto manda, despues la del catalogo y sin ninguna es cero`() {
        assertEquals(3L, EngagementKpis.projectTarget(3, 2))
        assertEquals(2L, EngagementKpis.projectTarget(null, 2))
        assertEquals(0L, EngagementKpis.projectTarget(null, null))
    }
}
