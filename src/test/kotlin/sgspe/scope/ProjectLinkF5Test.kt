package sgspe.scope

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import sgspe.common.ExactlyOneGuard
import sgspe.common.ModelCatalog

// la regla «mismo proyecto» de BC-10 y de las tareas (elección 5 de la especificación de F5): los campos que juzga salen del
// catálogo, así que basta con que el objeto esté en ProjectLinkGuard.OBJECTS
class ProjectLinkF5Test {
    private val catalog = ModelCatalog.fromClasspath()

    @Test
    fun `el aporte, el programa y la tarea se juzgan`() {
        assertTrue(ProjectLinkGuard.OBJECTS.containsAll(setOf("social_contribution", "social_program", "task")))
        val contribution = ProjectLinks.fieldsOf(catalog, "social_contribution").toMap()
        for (field in listOf("exploration_campaign", "stakeholder", "commitment", "agreement")) assertTrue(field in contribution, field)
        assertEquals("agreement", ProjectLinks.fieldsOf(catalog, "social_program").toMap()["agreement"])
        val task = ProjectLinks.fieldsOf(catalog, "task").toMap()
        for (field in ExactlyOneGuard.GROUPS
            .getValue("task")
            .single()
            .filter { it in catalog.objects }) {
            assertTrue(field in task, field)
        }
    }

    @Test
    fun `una tarea puede nacer de un programa`() {
        assertTrue("social_program" in ExactlyOneGuard.GROUPS.getValue("task").single())
    }
}
