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

// la clasificación de los indicadores (§4.1 de la especificación de F8, security.md §9.7): el catálogo indicator es un maestro
// del tenant, que todos leen sin asignación; la medición es una raíz con `project` opcional: sin proyecto es del tenant, con
// proyecto se acota por él. desde 8.2a, 36 raíces y 21 maestros
class ProjectScopeF8Test {
    private val project = UUID.randomUUID()
    private val catalog = ModelCatalog.fromClasspath()

    private fun resolve(before: Map<String, Any?>) =
        runBlocking {
            val write =
                RecordWrite(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    MEASUREMENT,
                    UUID.randomUUID(),
                    RecordChangeKind.UPDATED,
                    before,
                    null,
                    null,
                    null
                )
            ProjectResolver.resolve(write, before, catalog::isRequiredRelation) { _, _ -> null }
        }

    @Test
    fun `el catalogo es del tenant y la medicion una raiz con proyecto opcional`() {
        assertEquals(ReadGroup.TENANT, ProjectScope.readGroup("indicator"))
        assertEquals(ReadGroup.ROOT, ProjectScope.readGroup(MEASUREMENT))
        assertFalse(catalog.isRequiredRelation(MEASUREMENT, ProjectScope.PROJECT))
        assertEquals(36, ProjectScope.ROOTS.size)
        assertEquals(21, ProjectScope.TENANT.size)
    }

    @Test
    fun `una medicion del tenant no pide asignacion y una de proyecto se acota por el`() {
        val tenant = resolve(mapOf("project" to null))
        assertFalse(tenant.unresolved)
        assertTrue(tenant.projects.isEmpty())
        assertEquals(setOf(project), resolve(mapOf("project" to project.toString())).projects)
    }

    companion object {
        private const val MEASUREMENT = "indicator_measurement"
    }
}
