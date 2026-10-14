package sgspe.dashboard

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

// las listas de Closability frente a los KPI calculados de verdad (con KpiFixtureData, sin base): cada KPI de PERIODLESS
// ignora el periodo pedido (lee lo mismo y da la misma cifra con dos periodos distintos; decisión 27, R7) y el primer
// desglose de cada KPI es agregado (AGGREGATE) o por registro (RECORD_LEVEL): uno nuevo que no esté en ninguna lista hace
// fallar la prueba hasta que se decida si se congela (decisión 26, R5)
class ClosabilityKpisTest {
    private val today = LocalDate.parse("2026-10-13")
    private val september = LocalDate.parse("2026-09-01") to LocalDate.parse("2026-09-30")
    private val july = LocalDate.parse("2026-07-01") to LocalDate.parse("2026-07-31")
    private val project = UUID.nameUUIDFromBytes("proyecto-ejemplo-a".toByteArray())

    private class Run(
        val reads: List<String>,
        val value: IndicatorValue
    )

    // el KPI calculado sobre el periodo, con filas fechadas en septiembre y en julio
    private fun run(
        code: String,
        period: Pair<LocalDate, LocalDate>
    ): Run {
        val fixture = KpiFixtureData(project, listOf(LocalDate.parse("2026-09-15"), LocalDate.parse("2026-07-15")))
        val data =
            DashboardData(
                fixture.records,
                DashboardFilters.of(listOf(project), period.first, period.second),
                { true },
                today,
                componentLand = fixture.componentLand
            )
        val value = runBlocking { KpiEvaluation.evaluate(Kpis.BY_CODE.getValue(code), data) }
        return Run(fixture.reads.toList(), value)
    }

    // lo que congela una medición: el valor, el numerador, el denominador y el desglose
    private fun frozen(value: IndicatorValue) = listOf(value.available, value.value, value.numerator, value.denominator, value.breakdowns)

    @Test
    fun `cada KPI sin periodo ignora el periodo pedido`() {
        for (code in Closability.PERIODLESS) {
            val first = run(code, september)
            val second = run(code, july)
            assertEquals(first.reads, second.reads, "$code lee según el periodo")
            assertEquals(frozen(first.value), frozen(second.value), "$code calcula según el periodo")
        }
        // el control: un KPI del periodo sí cambia con el periodo, así que la comparación de arriba distingue
        for (code in listOf("KPI-ENG-01", "KPI-COM-08", "KPI-GRV-01")) {
            val first = run(code, september)
            val second = run(code, july)
            assertTrue(first.reads != second.reads || frozen(first.value) != frozen(second.value), "$code debería depender del periodo")
        }
    }

    // los de postura y los sin escalar no llegan a congelar un desglose (Closability.before)
    @Test
    fun `el primer desglose de cada KPI es agregado o por registro`() {
        val firsts =
            Kpis.ALL.filter { it.code !in Closability.STANCE_KPIS && it.code !in Closability.NO_SCALAR }.associate { kpi ->
                kpi.code to
                    run(kpi.code, september)
                        .value.breakdowns
                        ?.firstOrNull()
                        ?.by
            }
        val unknown = firsts.filterValues { it != null && it !in Closability.AGGREGATE && it !in Closability.RECORD_LEVEL }
        assertEquals(emptyMap<String, String?>(), unknown)
        assertTrue(Closability.AGGREGATE.intersect(Closability.RECORD_LEVEL).isEmpty())
        // la lista de agregados no guarda nombres que ningún KPI da primero
        assertEquals(
            Closability.AGGREGATE,
            firsts.values
                .filterNotNull()
                .filter { it in Closability.AGGREGATE }
                .toSet()
        )
        assertNotEquals(0, firsts.values.count { it != null })
    }
}
