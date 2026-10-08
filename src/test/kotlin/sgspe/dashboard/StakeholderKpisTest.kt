package sgspe.dashboard

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

// los KPI-STK de dashboards.md §4.2 con datos ficticios, sin base
class StakeholderKpisTest {
    private fun stakeholder(
        id: String,
        group: String? = null,
        scope: String? = "AIDS",
        stance: String? = null,
        state: String = "assessed",
        person: String? = "p-$id",
        organization: String? = null,
        interest: String? = null,
        power: String? = null
    ) = StakeholderRow(id, state, group, scope, "LIDER LOCAL", stance, interest?.let(::BigDecimal), power?.let(::BigDecimal), person, organization)

    private fun assessment(
        stakeholder: String,
        date: String,
        stance: String?,
        trust: Int? = null
    ) = AssessmentRow(stakeholder, LocalDate.parse(date), stance, trust, false, null, null)

    private val start = LocalDate.parse("2026-05-01")
    private val end = LocalDate.parse("2026-05-31")

    @Test
    fun `KPI-STK-01 y 02 cuentan vigentes, con SIN EVALUAR y sin los retirados`() {
        val rows =
            listOf(
                stakeholder("a", "ESTRATEGICO", stance = "EN CONTRA"),
                stakeholder("b", "ESTRATEGICO", stance = "A FAVOR"),
                stakeholder("c"),
                stakeholder("d", "POLITICO", stance = "EN CONTRA", state = "retired")
            )
        val byGroup = StakeholderKpis.byInfluenceGroup(rows)
        assertEquals(3, byGroup.value)
        val groups = byGroup.breakdowns.first { it.by == "influence_group" }.rows
        assertEquals(mapOf("ESTRATEGICO" to 2, "SIN EVALUAR" to 1), groups.associate { it["key"] to it["value"] })
        val cross = byGroup.breakdowns.first { it.by == "influence_group_by_scope" }.rows
        assertEquals(2, cross.first { it["key"] == "ESTRATEGICO|AIDS" }["value"])
        assertEquals(1, cross.first { it["key"] == "SIN EVALUAR|AIDS" }["value"])
        val stances =
            StakeholderKpis
                .byStance(rows, stanceReadable = true)
                .breakdowns
                .first { it.by == "stance" }
                .rows
        assertEquals(listOf("A FAVOR", "NEUTRAL", "EN CONTRA PERO ABIERTO A ESCUCHAR", "EN CONTRA", "SIN EVALUAR"), stances.map { it["key"] })
        assertEquals(listOf(1, 0, 0, 1, 1), stances.map { it["value"] })
    }

    @Test
    fun `KPI-STK-03 dibuja solo evaluados vigentes y oculta la postura a quien no la lee`() {
        val rows = listOf(stakeholder("a", "ESTRATEGICO", stance = "EN CONTRA", interest = "3.5", power = "4"), stakeholder("b"))
        val last = AssessmentRow("a", LocalDate.parse("2026-05-02"), "EN CONTRA", 3, false, null, null, BigDecimal("14.00"))
        val result =
            StakeholderKpis.matrix(
                rows,
                mapOf("a" to last),
                mapOf("a" to "Teniente Gobernador, Caserío Ejemplo"),
                BigDecimal(3),
                BigDecimal(3),
                stanceReadable = false
            )
        val point =
            result.breakdowns
                .first { it.by == "stakeholder" }
                .rows
                .single()
        assertEquals("a", point["key"])
        assertEquals("Teniente Gobernador, Caserío Ejemplo", point["label"])
        assertEquals(BigDecimal("14.00"), point["value"])
        assertEquals(BigDecimal("3.5"), point["interest"])
        assertEquals(BigDecimal("4"), point["power"])
        assertFalse(point.containsKey("stance"))
        assertEquals(1, result.numerator)
        assertEquals(2, result.denominator)
        val readable = StakeholderKpis.matrix(rows, mapOf("a" to last), emptyMap(), BigDecimal(3), BigDecimal(3), stanceReadable = true)
        assertEquals(
            "EN CONTRA",
            readable.breakdowns
                .first { it.by == "stakeholder" }
                .rows
                .single()["stance"]
        )
    }

    @Test
    fun `KPI-STK-04 y 07 cuentan grupos de interes distintos y los de dos o mas interacciones`() {
        val rows = listOf(stakeholder("a"), stakeholder("b", person = null, organization = "o-1", scope = "AIIS")).associateBy { it.id }
        val day = LocalDate.parse("2026-05-10")
        val contacts = listOf(Contact("a", "e1", day), Contact("a", "e2", day), Contact("b", "e1", day))
        val result =
            StakeholderKpis.contacted(
                contacts,
                rows,
                mapOf("a" to "FEMENINO"),
                mapOf(
                    "a" to listOf("Ronda Ejemplo"),
                    "b" to listOf("Asociación Ejemplo")
                )
            )
        assertEquals(2, result.value)
        assertEquals(
            setOf("FEMENINO", "ORGANIZACION"),
            result.breakdowns
                .first { it.by == "sex" }
                .rows
                .map { it["key"] }
                .toSet()
        )
        val repeated = StakeholderKpis.repeatedContact(contacts)
        assertEquals(BigDecimal("50.0"), repeated.value)
        assertEquals(1, repeated.numerator)
    }

    @Test
    fun `KPI-STK-05 toma la postura antes del periodo o la primera del periodo`() {
        val rows = listOf(stakeholder("a"), stakeholder("b"), stakeholder("c"))
        val assessments =
            mapOf(
                "a" to listOf(assessment("a", "2026-04-10", "EN CONTRA")),
                "b" to listOf(assessment("b", "2026-05-05", "EN CONTRA PERO ABIERTO A ESCUCHAR")),
                "c" to listOf(assessment("c", "2026-04-10", "A FAVOR"))
            )
        val result =
            StakeholderKpis.oppositionContacted(
                rows,
                assessments,
                listOf(Contact("a", "e1", LocalDate.parse("2026-05-10"))),
                start,
                end,
                stanceReadable = true
            )
        assertEquals(2, result.denominator)
        assertEquals(1, result.numerator)
        assertEquals(BigDecimal("50.0"), result.value)
    }

    @Test
    fun `KPI-STK-06 compara la ultima del periodo con la linea base, con direccion y cobertura`() {
        val rows = listOf(stakeholder("a"), stakeholder("b"), stakeholder("c"))
        val assessments =
            mapOf(
                "a" to listOf(assessment("a", "2026-04-10", "EN CONTRA"), assessment("a", "2026-05-20", "NEUTRAL")),
                "b" to listOf(assessment("b", "2026-04-10", "EN CONTRA PERO ABIERTO A ESCUCHAR"), assessment("b", "2026-05-20", "EN CONTRA")),
                "c" to listOf(assessment("c", "2026-04-10", "EN CONTRA"))
            )
        val result = StakeholderKpis.oppositionChanged(rows, assessments, start, end, stanceReadable = true)
        assertEquals(BigDecimal("100.0"), result.value)
        val direction =
            result.breakdowns
                .first { it.by == "direction" }
                .rows
                .associate { it["key"] to it["value"] }
        assertEquals(1, direction["MORE_FAVORABLE"])
        assertEquals(1, direction["LESS_FAVORABLE"])
        val coverage =
            result.breakdowns
                .first { it.by == "coverage" }
                .rows
                .associate { it["key"] to it["value"] }
        assertEquals(2, coverage["evaluated"])
        assertEquals(3, coverage["opposition"])
    }

    @Test
    fun `KPI-STK-08 promedia la ultima confianza hasta cada fin de mes, sin datos no inventa cero`() {
        val rows = listOf(stakeholder("a"), stakeholder("b"))
        val assessments =
            mapOf(
                "a" to listOf(assessment("a", "2026-03-10", null, 2), assessment("a", "2026-05-10", null, 4)),
                "b" to listOf(assessment("b", "2026-05-15", null, 3))
            )
        val result = StakeholderKpis.trust(rows, assessments, end)
        assertEquals(12, result.series!!.size)
        assertEquals(BigDecimal("3.50"), result.value)
        assertEquals(BigDecimal("2.00"), result.series.first { it["period"] == "2026-04" }["value"])
        assertNull(result.series.first { it["period"] == "2026-01" }["value"])
    }

    // ASM-087: la línea base es la anterior evaluación que informó la postura, y la última del periodo es la última que la informó
    @Test
    fun `KPI-STK-06 salta las evaluaciones que no informan la postura`() {
        val rows = listOf(stakeholder("a"), stakeholder("b"))
        val assessments =
            mapOf(
                // la del 10 de abril no informa postura: la línea base sigue siendo EN CONTRA, no vacía
                "a" to listOf(assessment("a", "2026-03-10", "EN CONTRA"), assessment("a", "2026-04-10", null, 3), assessment("a", "2026-05-20", "NEUTRAL")),
                // la última del periodo no informa postura: cuenta NEUTRAL, la última informada
                "b" to listOf(assessment("b", "2026-04-10", "EN CONTRA"), assessment("b", "2026-05-10", "NEUTRAL"), assessment("b", "2026-05-20", null, 4))
            )
        val result = StakeholderKpis.oppositionChanged(rows, assessments, start, end, stanceReadable = true)
        assertEquals(2, result.numerator)
        assertEquals(2, result.denominator)
        assertEquals(
            2,
            result.breakdowns
                .first { it.by == "direction" }
                .rows
                .first { it["key"] == "MORE_FAVORABLE" }["value"]
        )
    }

    @Test
    fun `KPI-STK-06 cuenta como evaluado a quien tuvo evaluacion en el periodo sin postura, y no cambio`() {
        val rows = listOf(stakeholder("a"))
        val assessments = mapOf("a" to listOf(assessment("a", "2026-04-10", "EN CONTRA"), assessment("a", "2026-05-10", null, 2)))
        val result = StakeholderKpis.oppositionChanged(rows, assessments, start, end, stanceReadable = true)
        assertEquals(0, result.numerator)
        assertEquals(1, result.denominator)
        assertEquals(BigDecimal("0.0"), result.value)
    }

    @Test
    fun `KPI-STK-05 usa la ultima postura informada antes del periodo`() {
        val rows = listOf(stakeholder("a"))
        val assessments = mapOf("a" to listOf(assessment("a", "2026-03-10", "EN CONTRA"), assessment("a", "2026-04-10", null, 3)))
        val contacts = listOf(Contact("a", "e1", LocalDate.parse("2026-05-10")))
        val result = StakeholderKpis.oppositionContacted(rows, assessments, contacts, start, end, stanceReadable = true)
        assertEquals(1, result.denominator)
        assertEquals(BigDecimal("100.0"), result.value)
    }

    @Test
    fun `KPI-STK-08 toma la ultima confianza informada y no una evaluacion posterior sin ella`() {
        val rows = listOf(stakeholder("a"))
        val assessments = mapOf("a" to listOf(assessment("a", "2026-04-10", "NEUTRAL", 4), assessment("a", "2026-05-10", "NEUTRAL", null)))
        val result = StakeholderKpis.trust(rows, assessments, end)
        assertEquals(BigDecimal("4.00"), result.value)
        assertEquals(1, result.series!!.last()["evaluated"])
    }

    @Test
    fun `KPI-STK-08 no cuenta a los retirados y da la distribucion de 1 a 5`() {
        val rows = listOf(stakeholder("a"), stakeholder("b", state = "retired"), stakeholder("c"))
        val assessments =
            mapOf(
                "a" to listOf(assessment("a", "2026-05-10", null, 4)),
                "b" to listOf(assessment("b", "2026-05-10", null, 1)),
                "c" to listOf(assessment("c", "2026-05-11", null, 4))
            )
        val last = StakeholderKpis.trust(rows, assessments, end).series!!.last()

        @Suppress("UNCHECKED_CAST")
        val distribution = last["distribution"] as Map<String, Int>
        assertEquals(mapOf("1" to 0, "2" to 0, "3" to 0, "4" to 2, "5" to 0), distribution)
        assertEquals(2, last["evaluated"])
    }

    @Test
    fun `sin permiso para leer la postura KPI-STK-02, 05 y 06 no estan disponibles y no calculan nada`() {
        val rows = listOf(stakeholder("a", stance = "EN CONTRA"))
        val assessments = mapOf("a" to listOf(assessment("a", "2026-04-10", "EN CONTRA")))
        val contacts = listOf(Contact("a", "e1", LocalDate.parse("2026-05-10")))
        val results =
            listOf(
                StakeholderKpis.byStance(rows, stanceReadable = false),
                StakeholderKpis.oppositionContacted(rows, assessments, contacts, start, end, stanceReadable = false),
                StakeholderKpis.oppositionChanged(rows, assessments, start, end, stanceReadable = false)
            )
        results.forEach {
            assertFalse(it.available)
            assertEquals("FIELD_NOT_READABLE", it.unavailableReason)
            assertNull(it.value)
            assertNull(it.numerator)
            assertTrue(it.breakdowns.isEmpty())
        }
        assertEquals(listOf("stakeholder.stance"), results[0].missingFields)
        assertEquals(listOf("stakeholder_assessment.stance"), results[1].missingFields)
        assertEquals(listOf("stakeholder_assessment.stance"), results[2].missingFields)
    }

    @Test
    fun `los indicadores que no leen la postura siguen disponibles`() {
        val rows = listOf(stakeholder("a", "ESTRATEGICO", stance = "EN CONTRA"))
        assertTrue(StakeholderKpis.byInfluenceGroup(rows).available)
        assertTrue(StakeholderKpis.trust(rows, emptyMap(), end).available)
    }
}
