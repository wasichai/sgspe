package sgspe.scope

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordWrite
import java.util.UUID

// el alcance de escritura de los objetos de F3 (security.md §9.2): las raíces por su proyecto, los hijos por su compromiso o
// su acuerdo, y la evidencia, que puede ser del tenant, solo cuando trae proyecto
class ProjectScopeF3Test {
    private val project = UUID.randomUUID()
    private val commitment = UUID.randomUUID()
    private val agreement = UUID.randomUUID()

    // las relaciones obligatorias de F3: todas menos evidence.project y delivery.commitment (exactamente uno, desde F5 con el aporte)
    private val required = { objectName: String, field: String -> !(objectName == "evidence" || (objectName == "delivery" && field == "commitment")) }
    private val read: suspend (String, UUID) -> Map<String, Any?>? = { _, id ->
        if (id == commitment || id == agreement) mapOf("project" to project.toString()) else null
    }

    private fun write(
        objectName: String,
        before: Map<String, Any?>?
    ) = RecordWrite(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), objectName, UUID.randomUUID(), RecordChangeKind.UPDATED, before, null, null, null)

    private fun resolve(
        objectName: String,
        before: Map<String, Any?>
    ) = runBlocking { ProjectResolver.resolve(write(objectName, before), before, required, read) }

    @Test
    fun `cada objeto de F3 tiene alcance`() {
        for (name in listOf("commitment", "commitment_candidate", "agreement", "task", "evidence")) assertTrue(name in ProjectScope.ROOTS, name)
        for (name in listOf("commitment_action", "commitment_milestone", "commitment_reschedule", "commitment_dependency")) {
            assertEquals(listOf("commitment" to "commitment"), ProjectScope.CHILDREN[name], name)
        }
        // la entrega es primero de su compromiso; desde F5 también puede ser de un aporte (ProjectScopeF5Test)
        assertEquals("commitment" to "commitment", ProjectScope.CHILDREN.getValue("delivery").first())
        for (name in listOf("agreement_party", "agreement_clause")) assertEquals(listOf("agreement" to "agreement"), ProjectScope.CHILDREN[name], name)
    }

    @Test
    fun `una entrega y una clausula toman el proyecto de su padre`() {
        assertEquals(setOf(project), resolve("delivery", mapOf("commitment" to commitment.toString())).projects)
        assertEquals(setOf(project), resolve("agreement_clause", mapOf("agreement" to agreement.toString())).projects)
    }

    @Test
    fun `una evidencia del tenant no pide alcance, y una de proyecto si`() {
        val tenant = resolve("evidence", mapOf("project" to null))
        assertFalse(tenant.unresolved)
        assertTrue(tenant.projects.isEmpty())
        assertEquals(setOf(project), resolve("evidence", mapOf("project" to project.toString())).projects)
    }
}
