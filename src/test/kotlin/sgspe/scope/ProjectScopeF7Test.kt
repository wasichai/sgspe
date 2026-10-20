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

// la clasificación de BC-18 (§4 de la especificación de F7, domain-model §4.7): la sugerencia y la interacción son raíces con
// `project` opcional, como export_log, evidence y early_warning_rule: sin proyecto son del tenant y no piden asignación; con
// proyecto, se acotan por él. desde F7, 35 raíces y 27 hijos (F8 suma indicator_measurement, deliverable y sus dos hijos:
// ProjectScopeF8Test)
class ProjectScopeF7Test {
    private val project = UUID.randomUUID()
    private val catalog = ModelCatalog.fromClasspath()
    private val read: suspend (String, UUID) -> Map<String, Any?>? = { _, _ -> null }

    private fun resolve(
        objectName: String,
        before: Map<String, Any?>
    ) = runBlocking {
        val write =
            RecordWrite(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                objectName,
                UUID.randomUUID(),
                RecordChangeKind.UPDATED,
                before,
                null,
                null,
                null
            )
        ProjectResolver.resolve(write, before, catalog::isRequiredRelation, read)
    }

    @Test
    fun `la sugerencia y la interaccion son raices con proyecto opcional`() {
        for (name in AI) {
            assertEquals(ReadGroup.ROOT, ProjectScope.readGroup(name), name)
            assertFalse(catalog.isRequiredRelation(name, ProjectScope.PROJECT), name)
        }
        assertEquals(35, (ProjectScope.ROOTS - setOf("indicator_measurement", "deliverable")).size)
        assertEquals(27, (ProjectScope.CHILDREN.keys - setOf("deliverable_section", "deliverable_version", "deliverable_presentation")).size)
    }

    @Test
    fun `sin proyecto no piden asignacion y con proyecto se acotan por el`() {
        for (name in AI) {
            val tenant = resolve(name, mapOf("project" to null))
            assertFalse(tenant.unresolved, name)
            assertTrue(tenant.projects.isEmpty(), name)
            assertEquals(setOf(project), resolve(name, mapOf("project" to project.toString())).projects, name)
        }
    }

    companion object {
        private val AI = listOf("ai_suggestion", "ai_interaction")
    }
}
