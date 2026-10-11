package sgspe.investment

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import sgspe.common.ModelCatalog
import sgspe.common.ProjectParameters

// las reglas de datos de los programas (especificación de F5 §3.6), sin base: las fechas en orden, el año presupuestal y los
// conteos; y que los nombres de Programs existen en el modelo. el acuerdo vigente de G3 es Synergy.live (plan 5.2a)
class ProgramRulesTest {
    private val catalog = ModelCatalog.fromClasspath()
    private val none = emptyList<Pair<String, String?>>()

    private fun codes(
        objectName: String,
        after: Map<String, Any?>,
        changed: Set<String> = after.keys
    ) = ProgramRules.data(objectName, after, changed).map { it.code to it.field }

    @Test
    fun `una fecha final anterior a la inicial es OUT_OF_RANGE sobre la final`() {
        assertEquals(
            listOf("OUT_OF_RANGE" to "period_end_date"),
            codes(Programs.MEASUREMENT, mapOf("period_start_date" to "2026-04-01", "period_end_date" to "2026-03-31"))
        )
        assertEquals(none, codes(Programs.MEASUREMENT, mapOf("period_start_date" to "2026-04-01", "period_end_date" to "2026-04-01")))
        assertEquals(listOf("OUT_OF_RANGE" to "end_date"), codes(Programs.PROGRAM, mapOf("start_date" to "2026-06-01", "end_date" to "2026-01-01")))
        assertEquals(listOf("OUT_OF_RANGE" to "end_date"), codes(Programs.INITIATIVE, mapOf("start_date" to "2026-06-01", "end_date" to "2026-01-01")))
        // sin una de las dos no hay qué comparar; si la escritura no cambia ninguna, no se juzga
        assertEquals(none, codes(Programs.PROGRAM, mapOf("end_date" to "2026-01-01")))
        assertEquals(none, codes(Programs.PROGRAM, mapOf("start_date" to "2026-06-01", "end_date" to "2026-01-01"), setOf("name")))
    }

    @Test
    fun `el anio presupuestal va de 2000 a 2100 y es entero`() {
        for (valid in listOf<Any>(2000, 2026, "2100", "2026.00")) {
            assertEquals(none, codes(Programs.BUDGET, mapOf("budget_year" to valid)), "$valid")
        }
        for (wrong in listOf<Any>(1999, 2101, "2026.5", "dos mil")) {
            assertEquals(listOf("OUT_OF_RANGE" to "budget_year"), codes(Programs.BUDGET, mapOf("budget_year" to wrong)), "$wrong")
        }
        // un año guardado antes de la regla no bloquea otra escritura del presupuesto
        assertEquals(none, codes(Programs.BUDGET, mapOf("budget_year" to 1999), setOf("allocated_amount")))
    }

    @Test
    fun `los conteos de un grupo son enteros de 0 o mas`() {
        assertEquals(none, codes(Programs.GROUP, mapOf("target_count" to 0, "reached_count" to "40")))
        assertEquals(
            listOf("OUT_OF_RANGE" to "target_count", "OUT_OF_RANGE" to "reached_count"),
            codes(Programs.GROUP, mapOf("target_count" to -1, "reached_count" to "4.5"))
        )
        assertEquals(none, codes(Programs.GROUP, mapOf("target_count" to null, "name" to "Familias")))
    }

    @Test
    fun `los hallazgos de G1, G2, G4 y de mismo programa llevan su codigo, su campo y su estado`() {
        assertEquals("INTERNAL_APPROVAL_REQUIRED" to "project", ProgramRules.internalApproval().let { it.code to it.field })
        assertEquals(403, ProgramRules.actionRequired().status.value())
        assertEquals("record", ProgramRules.actionRequired().field)
        assertEquals(403, ProgramRules.segregation().status.value())
        assertEquals("PROGRAM_MISMATCH" to "social_initiative", ProgramRules.programMismatch().let { it.code to it.field })
        assertEquals(409, ProgramRules.programMismatch().status.value())
    }

    // un renombre en el modelo dejaría de vigilar un campo o una transición en silencio
    @Test
    fun `los nombres de Programs existen en el modelo`() {
        for (name in Programs.OBJECTS + Programs.AGREEMENT_PARTY) assertNotNull(catalog.objectOrNull(name), name)
        assertTrue(Programs.TOTAL_BUDGET in catalog.derivedFields(Programs.PROGRAM))
        assertTrue(Programs.APPROVE_ACTION in catalog.objectOrNull(Programs.PROGRAM)!!.actions)
        for (name in listOf(Programs.SUBMIT, Programs.APPROVE, Programs.PLAN_DIRECT, Programs.CLOSE)) {
            assertNotNull(catalog.transition(Programs.PROGRAM, name), name)
        }
        assertEquals(Programs.PENDING, catalog.transition(Programs.PROGRAM, Programs.SUBMIT)!!.to)
        assertEquals(Programs.PENDING, catalog.transition(Programs.PROGRAM, Programs.APPROVE)!!.from)
        assertEquals(Programs.PARTNER, catalog.relationField(Programs.PROGRAM, "organization"))
        assertEquals(Programs.AGREEMENT, catalog.relationField(Programs.PROGRAM, Programs.AGREEMENT))
        assertEquals(Programs.PARTY_ORGANIZATION, catalog.relationField(Programs.AGREEMENT_PARTY, "organization"))
        assertEquals(Programs.INITIATIVE, catalog.relationField(Programs.GROUP, Programs.INITIATIVE))
        assertEquals(Programs.PROGRAM, catalog.relationField(Programs.BUDGET, Programs.PROGRAM))
        assertTrue(Synergy.EXPIRED in catalog.enumOf(Programs.AGREEMENT, Synergy.VALIDITY))
        val fields = { name: String -> catalog.objectOrNull(name)!!.fields.keys }
        for ((name, period) in ProgramRules.PERIODS) assertTrue(fields(name).containsAll(period.toList()), name)
        for ((name, counts) in ProgramRules.COUNTS) assertTrue(fields(name).containsAll(counts), name)
        assertTrue(Programs.YEAR in fields(Programs.BUDGET))
        assertTrue(fields(Programs.BUDGET).containsAll(listOf(Programs.ALLOCATED, Programs.CURRENCY)))
    }

    // las dos políticas de WF-G valen true si el proyecto las deja vacías (database-model.md §6, ASM-019)
    @Test
    fun `las politicas de sinergia y de aprobacion interna valen true por defecto`() {
        assertTrue(ProjectParameters.policy(emptyMap(), Synergy.POLICY))
        assertTrue(ProjectParameters.policy(emptyMap(), Programs.INTERNAL_POLICY))
    }
}
