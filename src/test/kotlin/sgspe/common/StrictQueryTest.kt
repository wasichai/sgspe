package sgspe.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

// ASM-054 y api.md §3.3: un parámetro de consulta desconocido es un 422 que lo nombra, nunca un filtro ignorado
class StrictQueryTest {
    private val allowed = setOf("project", "periodStart")

    @Test
    fun `deja pasar los parametros admitidos`() {
        StrictQuery.requireOnly(mapOf("project" to listOf("p1"), "periodStart" to listOf("2026-01-01")), allowed)
    }

    @Test
    fun `un parametro desconocido es un 422 que lo nombra y dice cuales se admiten`() {
        val error =
            assertThrows<UnprocessableContentException> { StrictQuery.requireOnly(mapOf("projectId" to listOf("p1"), "project" to listOf("p2")), allowed) }
        assertEquals(422, error.status.value())
        assertEquals("projectId", error.violations.single().field)
        assertEquals("no es un parámetro de esta consulta; se admiten periodStart, project", error.violations.single().message)
    }

    @Test
    fun `varios desconocidos salen todos, en orden`() {
        val error = assertThrows<UnprocessableContentException> { StrictQuery.requireOnly(mapOf("zeta" to listOf(), "alfa" to listOf()), allowed) }
        assertEquals(listOf("alfa", "zeta"), error.violations.map { it.field })
    }
}
