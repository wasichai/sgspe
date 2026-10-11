package sgspe.scope

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.util.UUID

// la cadena de un hijo hasta su raíz y su padre presente (ASM-114, security.md §9.7), sin base: lo que comparten la lectura
// (ProjectReadScope), la escritura (ProjectResolver) y los vínculos (ProjectLinks.linkedProject)
class ProjectScopeTest {
    private val project = UUID.randomUUID()
    private val plan = UUID.randomUUID()
    private val orphanPlan = UUID.randomUUID()

    // los registros que se leen al subir: el plan es del proyecto; el huérfano ya no tiene proyecto; el resto no existe
    private val read: suspend (String, UUID) -> Map<String, Any?>? = { _, id ->
        when (id) {
            plan -> mapOf("project" to project.toString())
            orphanPlan -> mapOf("project" to null)
            else -> null
        }
    }

    @Test
    fun `la cadena de un hijo de una raiz es un salto, y la de un campo que no es su padre no existe`() {
        assertEquals(listOf("engagement_plan" to "engagement_plan"), ProjectScope.chain("planned_activity", "engagement_plan"))
        assertEquals(emptyList<Pair<String, String>>(), ProjectScope.chain("planned_activity", "project"))
        assertEquals(emptyList<Pair<String, String>>(), ProjectScope.chain("engagement", "project"))
        assertEquals(emptyList<Pair<String, String>>(), ProjectScope.chain("locality", "project"))
    }

    @Test
    fun `el padre presente es el primero de CHILDREN que trae un id`() {
        val id = UUID.randomUUID().toString()
        assertEquals("agreement" to "agreement", ProjectScope.parentOf("agreement_clause", mapOf("agreement" to id)))
        assertNull(ProjectScope.parentOf("agreement_clause", mapOf("agreement" to null)))
        assertNull(ProjectScope.parentOf("agreement_clause", mapOf("agreement" to "no es un id")))
        assertNull(ProjectScope.parentOf("agreement", mapOf("project" to id)))
    }

    @Test
    fun `el proyecto se lee subiendo la cadena, y un salto que falta o sin proyecto no da proyecto`() {
        runBlocking {
            assertEquals(project, ProjectScope.projectOf("planned_activity", "engagement_plan", plan, read))
            assertNull(ProjectScope.projectOf("planned_activity", "engagement_plan", orphanPlan, read))
            assertNull(ProjectScope.projectOf("planned_activity", "engagement_plan", UUID.randomUUID(), read))
            assertNull(ProjectScope.projectOf("planned_activity", "project", plan, read))
        }
    }
}
