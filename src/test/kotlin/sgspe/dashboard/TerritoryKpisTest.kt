package sgspe.dashboard

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.util.LinkedMultiValueMap
import sgspe.common.ModelCatalog
import sgspe.common.Records
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

// KPI-TER-02 a 04 puros y la evaluación de una fila de KPI-TER-01 (dashboards.md §4.8 y §8, especificación de F6 §3.2): la
// superficie suma solo lo medido y lo advierte; los miembros y los componentes se cuentan una vez; una fila sin permiso sale
// no disponible con su razón, nunca cero, y con los filtros que ignora
class TerritoryKpisTest {
    private val today = LocalDate.parse("2026-10-11")

    private fun filters(community: UUID? = null) =
        DashboardFilters(listOf(UUID.randomUUID()), LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-31"), null, null, community, null, null, null, null)

    private fun data(
        readable: Set<String>,
        community: UUID? = null
    ) = DashboardData(mock(Records::class.java), filters(community), { it in readable }, today)

    private fun rows(
        result: IndicatorResult,
        by: String
    ): List<Map<String, Any?>> = result.breakdowns.single { it.by == by }.rows

    @Test
    fun `KPI-TER-02 suma la superficie medida de las areas aprobadas y advierte las que no la tienen`() {
        val result =
            TerritoryKpis.surface(
                listOf(
                    AreaRow("a1", "p", "AIDS", 1, BigDecimal("100.00")),
                    AreaRow("a2", "p", "AIIS", 2, BigDecimal("50.50")),
                    AreaRow("a3", "p", "AID AMBIENTAL", 1, null)
                )
            )
        assertEquals(BigDecimal("150.50"), result.value)
        assertEquals(listOf("a3", "a1", "a2"), rows(result, "area").map { it["key"] })
        assertEquals(1, result.warnings.size)
        assertEquals(BigDecimal("0.00"), TerritoryKpis.surface(emptyList()).value)
    }

    @Test
    fun `KPI-TER-03 cuenta los miembros de las areas aprobadas por tipo de area, de miembro y condicion`() {
        val areas = listOf(AreaRow("a1", "p", "AIDS", 1, null), AreaRow("a2", "p", "AIIS", 1, null))
        val members =
            listOf(
                MemberRow("a1", "locality", "INCLUIDO"),
                MemberRow("a1", "community", "PROVISIONAL"),
                MemberRow("a2", "locality", "INCLUIDO"),
                MemberRow("otra", "locality", "INCLUIDO")
            )
        val result = TerritoryKpis.members(areas, members)
        assertEquals(3, result.value)
        assertEquals(listOf(mapOf("key" to "AIDS", "value" to 2), mapOf("key" to "AIIS", "value" to 1)), rows(result, "areaType"))
        assertEquals(listOf(mapOf("key" to "locality", "value" to 2), mapOf("key" to "community", "value" to 1)), rows(result, "memberType"))
        assertEquals(listOf(mapOf("key" to "INCLUIDO", "value" to 2), mapOf("key" to "PROVISIONAL", "value" to 1)), rows(result, "memberCondition"))
    }

    @Test
    fun `KPI-TER-04 cuenta cada componente una vez y lo desglosa por tierra y por tipo`() {
        val result =
            TerritoryKpis.componentsOnLand(
                listOf(
                    LandRow("c1", "PLATAFORMA", "PROPIEDAD"),
                    LandRow("c1", "PLATAFORMA", "POSESION"),
                    LandRow("c1", "PLATAFORMA", "PROPIEDAD"),
                    LandRow("c2", "ACCESO", "TERRITORIO COMUNAL"),
                    // la tierra de la empresa no es de terceros: ni suma ni desglosa
                    LandRow("c3", "VIA", "PROPIEDAD DE LA EMPRESA"),
                    LandRow("c1", "PLATAFORMA", "PROPIEDAD DE LA EMPRESA")
                )
            )
        assertEquals(2, result.value)
        assertEquals(
            listOf(mapOf("key" to "POSESION", "value" to 1), mapOf("key" to "PROPIEDAD", "value" to 1), mapOf("key" to "TERRITORIO COMUNAL", "value" to 1)),
            rows(result, "tenureType")
        )
        assertEquals(listOf(mapOf("key" to "ACCESO", "value" to 1), mapOf("key" to "PLATAFORMA", "value" to 1)), rows(result, "componentType"))
    }

    @Test
    fun `una fila de KPI-TER-01 sin permiso sale no disponible con su razon y los filtros que ignora`() {
        val kpi =
            Kpi("KPI-X", "Prueba", null, "cosas", listOf("stakeholder.stance"), ignores = setOf("community")) {
                IndicatorResult(7)
            }
        val community = UUID.randomUUID()
        val hidden = runBlocking { TerritoryKpis.component(kpi, data(setOf("stakeholder"), community)) }
        assertEquals("KPI-X", hidden["key"])
        assertEquals(false, hidden["available"])
        assertEquals(UnavailableReason.FIELD_NOT_READABLE, hidden["unavailableReason"])
        assertEquals(listOf("stakeholder.stance"), hidden["missingFields"])
        assertEquals(listOf("community"), hidden["ignoredFilters"])
        val objectless = runBlocking { TerritoryKpis.component(kpi, data(emptySet())) }
        assertEquals(UnavailableReason.OBJECT_NOT_READABLE, objectless["unavailableReason"])
        val shown = runBlocking { TerritoryKpis.component(kpi, data(setOf("stakeholder", "stakeholder.stance"))) }
        assertEquals(true, shown["available"])
        assertEquals(7, shown["value"])
        assertFalse("ignoredFilters" in shown)
        val row = runBlocking { TerritoryKpis.unavailableRow("programs", listOf("social_program.name"), data(setOf("social_program"))) }
        assertEquals(
            mapOf(
                "key" to "programs",
                "available" to false,
                "unavailableReason" to "FIELD_NOT_READABLE",
                "missingFields" to listOf("social_program.name")
            ),
            row
        )
    }

    @Test
    fun `periodGiven dice si vino el periodo`() {
        val catalog = ModelCatalog.fromClasspath()

        fun parse(vararg pairs: Pair<String, String>): DashboardFilters {
            val params = LinkedMultiValueMap<String, String>()
            pairs.forEach { (k, v) -> params.add(k, v) }
            return runBlocking { DashboardFilters.parse(params, DashboardFilters.COMMON, catalog, today) { emptyList() }.resolve() }
        }
        assertFalse(parse().periodGiven)
        assertTrue(parse("periodStart" to "2026-10-01").periodGiven)
        assertTrue(parse("periodEnd" to "2026-10-31").periodGiven)
    }
}
