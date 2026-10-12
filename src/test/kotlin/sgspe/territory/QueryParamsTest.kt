package sgspe.territory

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import wasichai.core.common.WasichaiException
import java.math.BigDecimal
import java.time.LocalDate

// los límites de las consultas espaciales (especificación de F6 §3.1, ASM-126): fuera de rango es 400 OUT_OF_RANGE y un valor
// que no se puede leer, 400 INVALID_VALUE, los dos con el parámetro en errors[0].field
class QueryParamsTest {
    private val limits = TerritoryQueryProperties()
    private val today = LocalDate.parse("2026-10-11")

    private fun failure(block: () -> Unit): Pair<String, String> {
        val error = assertThrows<WasichaiException> { block() }
        assertEquals(400, error.status.value())
        val violation = error.violations.single()
        return violation.message to violation.field
    }

    @Test
    fun `bufferM va de 1 a 20000 metros y sin valor toma el que se pida`() {
        assertEquals(500, QueryParams.bufferM(null, limits))
        assertNull(QueryParams.bufferM(null, limits, default = null))
        assertEquals(20000, QueryParams.bufferM("20000", limits))
        assertEquals("OUT_OF_RANGE" to "bufferM", failure { QueryParams.bufferM("0", limits) })
        assertEquals("OUT_OF_RANGE" to "bufferM", failure { QueryParams.bufferM("20001", limits) })
        assertEquals("INVALID_VALUE" to "bufferM", failure { QueryParams.bufferM("cien", limits) })
    }

    @Test
    fun `distanceKm va de 0,1 a 50 y se devuelve en metros`() {
        assertEquals(0, BigDecimal("5000").compareTo(QueryParams.distanceM(null, limits)))
        assertEquals(0, BigDecimal("100").compareTo(QueryParams.distanceM("0.1", limits)))
        assertEquals("OUT_OF_RANGE" to "distanceKm", failure { QueryParams.distanceM("0.05", limits) })
        assertEquals("OUT_OF_RANGE" to "distanceKm", failure { QueryParams.distanceM("50.5", limits) })
        assertEquals("INVALID_VALUE" to "distanceKm", failure { QueryParams.distanceM("lejos", limits) })
    }

    @Test
    fun `limit va de 1 a 5000 y por defecto es el tope`() {
        assertEquals(5000, QueryParams.limit(null, limits))
        assertEquals(1, QueryParams.limit("1", limits))
        assertEquals("OUT_OF_RANGE" to "limit", failure { QueryParams.limit("0", limits) })
        assertEquals("OUT_OF_RANGE" to "limit", failure { QueryParams.limit("5001", limits) })
    }

    @Test
    fun `el periodo por defecto son los 12 meses que terminan hoy y no pasa de 5 anios`() {
        assertEquals(Period(LocalDate.parse("2025-10-12"), today), QueryParams.period(null, null, today, limits))
        assertEquals(
            Period(LocalDate.parse("2021-01-01"), LocalDate.parse("2025-12-31")),
            QueryParams.period("2021-01-01", "2025-12-31", today, limits)
        )
        assertEquals("OUT_OF_RANGE" to "periodEnd", failure { QueryParams.period("2021-01-01", "2026-01-01", today, limits) })
        assertEquals("OUT_OF_RANGE" to "periodStart", failure { QueryParams.period("2026-10-02", "2026-10-01", today, limits) })
        assertEquals("INVALID_VALUE" to "periodStart", failure { QueryParams.period("01/10/2026", null, today, limits) })
        // una fecha extrema no desborda en un 500
        assertEquals("OUT_OF_RANGE" to "periodEnd", failure { QueryParams.period(null, "+999999999-12-31", today, limits) })
        assertEquals("OUT_OF_RANGE" to "periodEnd", failure { QueryParams.period("+999999998-01-01", "+999999999-12-31", today, limits) })
        assertEquals("OUT_OF_RANGE" to "periodEnd", failure { QueryParams.period(null, "-999999999-01-01", today, limits) })
    }

    @Test
    fun `el periodo en instantes de Lima cubre el ultimo dia entero`() {
        val period = Period(LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-31"))
        assertEquals("2026-10-01T05:00:00Z", period.from().toString())
        assertEquals("2026-11-01T05:00:00Z", period.until().toString())
    }

    @Test
    fun `una coordenada es un numero en su rango y una lista de proyectos son UUID`() {
        assertEquals(-78.5, QueryParams.coordinate("lon", "-78.5", -180.0, 180.0))
        assertEquals("INVALID_VALUE" to "lat", failure { QueryParams.coordinate("lat", null, -90.0, 90.0) })
        assertEquals("OUT_OF_RANGE" to "lon", failure { QueryParams.coordinate("lon", "-181", -180.0, 180.0) })
        assertEquals("INVALID_VALUE" to "project", failure { QueryParams.uuids("project", listOf("no-es-uuid")) })
        assertEquals(emptyList<Any>(), QueryParams.uuids("project", null))
    }

    @Test
    fun `un anio fuera de 1900 a 9999 es OUT_OF_RANGE en su campo`() {
        assertEquals("OUT_OF_RANGE" to "periodStart", failure { QueryParams.period("1899-12-31", "1900-06-01", today, limits) })
        assertEquals("OUT_OF_RANGE" to "periodEnd", failure { QueryParams.period(null, "+10000-01-01", today, limits) })
        assertEquals("OUT_OF_RANGE" to "periodStart", failure { QueryParams.period("-0001-01-01", null, today, limits) })
    }
}
