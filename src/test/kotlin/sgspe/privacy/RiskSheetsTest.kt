package sgspe.privacy

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import sgspe.common.ModelCatalog
import sgspe.risk.Risks

// las hojas de risk-register por nivel (deliverables.md §10, ASM-101), sin base
class RiskSheetsTest {
    private val catalog = ModelCatalog.fromClasspath()
    private val all = RiskSheets.REQUIRES.toSet()

    private fun line(
        state: String = "assessed",
        attributes: Map<String, Any?> = emptyMap()
    ) = RiskLine(
        "r1",
        state,
        mapOf(
            "code" to "RS-1",
            "title" to "Expectativas de empleo",
            "risk_source" to "Texto libre de la fuente",
            "risk_level" to "ALTO",
            "review_status" to "REVISION VENCIDA",
            "owner_user_email" to "responsable@sgspe.test",
            "locality" to "loc"
        ) + attributes,
        listOf("8f14e45f-ceea-467a-9575-000000000001" to "Asociación Ejemplo"),
        "2026-10-05",
        "AUMENTA",
        MeasureCount(3, 1, 1)
    )

    private fun cells(sheet: XlsxSheet): List<String> = sheet.header + sheet.rows.flatten().map { it.toString() }

    @Test
    fun `INTERNO nombra a los grupos de interes y trae el texto libre y el responsable`() {
        val context = RiskContext(all, mapOf("loc" to "Localidad Ejemplo"))
        val sheet = RiskSheets.matrix(listOf(line()), context, Disclosure.INTERNO)
        val values = cells(sheet)
        assertTrue("Asociación Ejemplo" in values)
        assertTrue("Texto libre de la fuente" in values)
        assertTrue("responsable@sgspe.test" in values)
        assertTrue("Localidad Ejemplo" in values)
        assertFalse("Medidas" in sheet.header)
    }

    @Test
    fun `EXTERNO quita el texto libre y los correos, cita por GI- y cuenta las medidas`() {
        val readable = all.filter { RiskSheets.allowed(it, Disclosure.EXTERNO) }.toSet()
        val sheet = RiskSheets.matrix(listOf(line()), RiskContext(readable), Disclosure.EXTERNO)
        val values = cells(sheet)
        assertFalse("Texto libre de la fuente" in values)
        assertFalse("responsable@sgspe.test" in values)
        assertFalse("Asociación Ejemplo" in values)
        assertTrue(values.any { it.startsWith("GI-") })
        assertTrue(sheet.header.containsAll(listOf("Medidas", "Medidas completadas", "Medidas atrasadas")))
        assertFalse(RiskSheets.allowed("risk_assessment.notes", Disclosure.EXTERNO))
        assertFalse(RiskSheets.allowed("potential_impact.description", Disclosure.EXTERNO))
    }

    @Test
    fun `la situacion de revision solo sale en los estados que la calculan`() {
        val closed = cells(RiskSheets.matrix(listOf(line(state = "closed")), RiskContext(all), Disclosure.INTERNO))
        assertFalse("REVISION VENCIDA" in closed)
        assertTrue("REVISION VENCIDA" in cells(RiskSheets.matrix(listOf(line()), RiskContext(all), Disclosure.INTERNO)))
    }

    @Test
    fun `PUBLICO cuenta sobre la grilla completa con la marca reservado`() {
        // seis visibles; el uno se oculta y, para que lo oculto sume al menos cinco, también los cinco de MEDIO (SmallCells)
        val active =
            List(6) { mapOf("risk_level" to "ALTO", "category" to "AMBIENTAL") } + List(5) { mapOf("risk_level" to "MEDIO", "category" to "OTRO") } +
                mapOf("risk_level" to "BAJO", "category" to "OTRO")
        val sheets = RiskSheets.aggregates(active, all, catalog.enumOf("social_risk", "category"), catalog.enumOf("social_risk", "treatment_decision"))
        assertEquals(listOf("Por nivel y categoría", "Por tratamiento y nivel"), sheets.map { it.name })
        val byCategory = sheets.first().rows.toList()
        assertEquals((4 + 2) * (catalog.enumOf("social_risk", "category").size + 2), byCategory.size)
        assertEquals(6, byCategory.first { it[0] == "ALTO" && it[1] == "AMBIENTAL" }[2])
        assertEquals(SmallCells.HIDDEN, byCategory.first { it[0] == "BAJO" && it[1] == "OTRO" }[2])
        assertTrue(sheets.last().rows.any { it[0] == RiskSheets.UNDECIDED })
    }

    // privacy solo depende de common: sus copias de los niveles y estados son las de sgspe.risk
    @Test
    fun `los niveles, los estados y los campos son los del modelo`() {
        assertEquals(Risks.LEVELS, RiskSheets.LEVELS)
        assertEquals(Risks.REVIEWED, RiskSheets.REVIEWED)
        val fields = { o: String -> catalog.objectOrNull(o)!!.fields.keys + catalog.manyToOneFields(o).map { it.first } }
        assertEquals(emptyList<String>(), RiskSheets.RISK_FIELDS.filter { it !in fields(RiskSheets.RISK) })
        assertEquals(emptyList<String>(), RiskSheets.ASSESSMENT_FIELDS.filter { it !in fields(RiskSheets.ASSESSMENT) })
        assertEquals(emptyList<String>(), RiskSheets.MEASURE_FIELDS.filter { it !in fields(RiskSheets.MEASURE) })
        assertEquals(emptyList<String>(), RiskSheets.IMPACT_FIELDS.filter { it !in fields(RiskSheets.IMPACT) })
        for ((field, target) in RiskSheets.LINKED) assertEquals(target.first, catalog.manyToOneFields(RiskSheets.RISK).toMap()[field], field)
    }
}
