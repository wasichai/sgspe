package sgspe.scope

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import sgspe.common.ExactlyOneGuard
import sgspe.common.ModelCatalog
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordWrite
import java.util.UUID

// la clasificación de F8 (§4.1 y §4.2 de la especificación de F8, security.md §9.7): el catálogo indicator es un maestro del
// tenant, que todos leen sin asignación; la medición es una raíz con `project` opcional: sin proyecto es del tenant, con
// proyecto se acota por él (plan 8.2a). el entregable es una raíz con `project` obligatorio, sus secciones y sus versiones
// cuelgan de él y la plantilla es un maestro del tenant (ASM-135); un entregable no cita el área, el estudio ni la versión
// de otro proyecto (ProjectLinkGuard, Decisión 4 del plan 8.1) y una tarea puede salir de un entregable (database-model.md
// §17). desde F8, 37 raíces, 29 hijos y 22 maestros
class ProjectScopeF8Test {
    private val project = UUID.randomUUID()
    private val catalog = ModelCatalog.fromClasspath()

    private fun resolve(
        objectName: String,
        record: Map<String, Any?>,
        read: suspend (String, UUID) -> Map<String, Any?>? = { _, _ -> null }
    ) = runBlocking {
        val write =
            RecordWrite(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                objectName,
                UUID.randomUUID(),
                RecordChangeKind.UPDATED,
                record,
                null,
                null,
                null
            )
        ProjectResolver.resolve(write, record, catalog::isRequiredRelation, read)
    }

    @Test
    fun `el catalogo es del tenant y la medicion una raiz con proyecto opcional`() {
        assertEquals(ReadGroup.TENANT, ProjectScope.readGroup("indicator"))
        assertEquals(ReadGroup.ROOT, ProjectScope.readGroup(MEASUREMENT))
        assertFalse(catalog.isRequiredRelation(MEASUREMENT, ProjectScope.PROJECT))
    }

    @Test
    fun `una medicion del tenant no pide asignacion y una de proyecto se acota por el`() {
        val tenant = resolve(MEASUREMENT, mapOf("project" to null))
        assertFalse(tenant.unresolved)
        assertTrue(tenant.projects.isEmpty())
        assertEquals(setOf(project), resolve(MEASUREMENT, mapOf("project" to project.toString())).projects)
    }

    @Test
    fun `el entregable es raiz, sus secciones y versiones hijas y la plantilla del tenant`() {
        assertEquals(ReadGroup.ROOT, ProjectScope.readGroup("deliverable"))
        assertTrue(catalog.isRequiredRelation("deliverable", ProjectScope.PROJECT))
        for (name in listOf("deliverable_section", "deliverable_version")) {
            assertEquals(ReadGroup.CHILD, ProjectScope.readGroup(name), name)
            assertEquals(listOf("deliverable" to "deliverable"), ProjectScope.CHILDREN[name], name)
            assertEquals(listOf("deliverable" to "deliverable"), ProjectScope.chain(name, "deliverable"), name)
        }
        assertEquals(ReadGroup.TENANT, ProjectScope.readGroup("deliverable_template"))
        assertEquals(37, ProjectScope.ROOTS.size)
        assertEquals(29, ProjectScope.CHILDREN.size)
        assertEquals(22, ProjectScope.TENANT.size)
    }

    @Test
    fun `una seccion toma el proyecto de su entregable`() {
        val deliverable = UUID.randomUUID()
        val read: suspend (String, UUID) -> Map<String, Any?>? = { objectName, id ->
            if (objectName == "deliverable" && id == deliverable) mapOf("project" to project.toString()) else null
        }
        val resolution = resolve("deliverable_section", mapOf("deliverable" to deliverable.toString()), read)
        assertFalse(resolution.unresolved)
        assertEquals(setOf(project), resolution.projects)
    }

    @Test
    fun `un entregable se juzga por su area, su estudio y su version vigente, y una tarea puede salir de el`() {
        assertTrue("deliverable" in ProjectLinkGuard.OBJECTS)
        assertEquals(
            mapOf("area_of_influence" to "area_of_influence", "study" to "study", "current_version" to "deliverable_version"),
            ProjectLinks.fieldsOf(catalog, "deliverable").toMap()
        )
        assertEquals(
            "deliverable",
            ExactlyOneGuard.GROUPS
                .getValue("task")
                .single()
                .last()
        )
        assertEquals("deliverable", ProjectLinks.fieldsOf(catalog, "task").toMap()["deliverable"])
    }

    companion object {
        private const val MEASUREMENT = "indicator_measurement"
    }
}
