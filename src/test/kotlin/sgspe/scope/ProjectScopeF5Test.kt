package sgspe.scope

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import sgspe.common.ModelCatalog
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordWrite
import java.util.UUID

// la clasificación de BC-10 (elección 4 de la especificación de F5: 33 raíces y 27 hijos) y su alcance de escritura
// (security.md §9.2): los ítems y los comprobantes por su aporte, los datos del programa por su programa, la medición por el
// programa de su indicador (dos saltos) y la entrega por su compromiso o por su aporte
class ProjectScopeF5Test {
    private val catalog = ModelCatalog.fromClasspath()
    private val mine = UUID.randomUUID()
    private val other = UUID.randomUUID()
    private val contribution = UUID.randomUUID()
    private val commitment = UUID.randomUUID()
    private val program = UUID.randomUUID()
    private val indicator = UUID.randomUUID()
    private val orphanIndicator = UUID.randomUUID()

    // lo que el resolver lee al subir: el aporte es de `other`; el compromiso y el programa, de `mine`; el indicador cuelga del
    // programa; el indicador huérfano ya no tiene programa; cualquier otro no existe o quien escribe no lo lee
    private val read: suspend (String, UUID) -> Map<String, Any?>? = { _, id ->
        when (id) {
            contribution -> mapOf("project" to other.toString())
            commitment, program -> mapOf("project" to mine.toString())
            indicator -> mapOf("social_program" to program.toString())
            orphanIndicator -> mapOf("social_program" to null)
            else -> null
        }
    }

    private fun resolve(
        objectName: String,
        kind: RecordChangeKind,
        before: Map<String, Any?>?,
        after: Map<String, Any?>
    ) = runBlocking {
        val write = RecordWrite(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), objectName, UUID.randomUUID(), kind, before, null, null, null)
        ProjectResolver.resolve(write, after, catalog::isRequiredRelation, read)
    }

    @Test
    fun `aportes y programas son raices y sus datos cuelgan de ellos`() {
        assertTrue("social_contribution" in ProjectScope.ROOTS && "social_program" in ProjectScope.ROOTS)
        for (name in listOf("contribution_item", "receipt")) {
            assertEquals(listOf("social_contribution" to "social_contribution"), ProjectScope.CHILDREN[name], name)
        }
        for (name in listOf("social_initiative", "beneficiary_group", "program_budget", "program_indicator", "program_milestone")) {
            assertEquals(listOf("social_program" to "social_program"), ProjectScope.CHILDREN[name], name)
        }
        assertEquals(listOf("program_indicator" to "program_indicator"), ProjectScope.CHILDREN["program_measurement"])
        assertEquals(listOf("commitment" to "commitment", "social_contribution" to "social_contribution"), ProjectScope.CHILDREN["delivery"])
        assertEquals(33, (ProjectScope.ROOTS - setOf("ai_suggestion", "ai_interaction", "indicator_measurement", "deliverable")).size)
        assertEquals(27, (ProjectScope.CHILDREN.keys - setOf("deliverable_section", "deliverable_version")).size)
    }

    @Test
    fun `la cadena de una medicion pasa por su indicador hasta el programa`() {
        assertEquals(
            listOf("program_indicator" to "program_indicator", "social_program" to "social_program"),
            ProjectScope.chain("program_measurement", "program_indicator")
        )
        assertEquals(listOf("social_contribution" to "social_contribution"), ProjectScope.chain("delivery", "social_contribution"))
        assertEquals(emptyList<Pair<String, String>>(), ProjectScope.chain("program_measurement", "social_program"))
    }

    @Test
    fun `un item toma el proyecto de su aporte y una medicion el del programa de su indicador`() {
        val item = resolve("contribution_item", RecordChangeKind.CREATED, null, mapOf("social_contribution" to contribution.toString()))
        assertEquals(setOf(other), item.projects)
        assertEquals("social_contribution", item.field)
        assertFalse(item.unresolved)
        val measurement = resolve("program_measurement", RecordChangeKind.CREATED, null, mapOf("program_indicator" to indicator.toString()))
        assertEquals(setOf(mine), measurement.projects)
        assertEquals("program_indicator", measurement.field)
        assertFalse(measurement.unresolved)
        // un indicador sin programa, o uno que quien escribe no lee, no da proyecto: falla cerrado
        assertTrue(resolve("program_measurement", RecordChangeKind.CREATED, null, mapOf("program_indicator" to orphanIndicator.toString())).unresolved)
        assertTrue(resolve("program_measurement", RecordChangeKind.CREATED, null, mapOf("program_indicator" to UUID.randomUUID().toString())).unresolved)
    }

    @Test
    fun `una entrega toma el padre que tenga, de cualquiera de los dos`() {
        val stored = mapOf("commitment" to commitment.toString(), "social_contribution" to null)
        val ofCommitment = resolve("delivery", RecordChangeKind.UPDATED, stored, stored)
        assertEquals(setOf(mine), ofCommitment.projects)
        assertEquals("commitment", ofCommitment.field)
        val ofContribution = resolve("delivery", RecordChangeKind.CREATED, null, mapOf("social_contribution" to contribution.toString()))
        assertEquals(setOf(other), ofContribution.projects)
        assertEquals("social_contribution", ofContribution.field)
        assertFalse(ofContribution.unresolved)
    }

    // Review Focus 2: el ON DELETE SET NULL de wasichai deja una entrega sin padre; solo ADMIN la escribe
    @Test
    fun `una entrega existente sin ninguno de sus padres no se resuelve`() {
        val orphaned = mapOf("commitment" to null, "social_contribution" to null)
        val orphan = resolve("delivery", RecordChangeKind.UPDATED, orphaned, orphaned)
        assertTrue(orphan.unresolved)
        assertEquals("commitment", orphan.field)
        assertTrue(orphan.projects.isEmpty())
        // un alta sin padre no es de alcance: la rechaza ExactlyOneGuard con 400 EXACTLY_ONE
        assertFalse(resolve("delivery", RecordChangeKind.CREATED, null, emptyMap()).unresolved)
    }
}
