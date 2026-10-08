package sgspe.scope

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordWrite
import java.util.UUID

// la decisión de a qué proyectos pertenece una escritura, sin base: falla cerrado cuando no se resuelve (security.md §9.2)
class ProjectResolverTest {
    private val mine = UUID.randomUUID()
    private val other = UUID.randomUUID()
    private val plan = UUID.randomUUID()
    private val orphanPlan = UUID.randomUUID()

    // las relaciones obligatorias: todas menos export_log.project y locality_profile.study
    private val required = { objectName: String, field: String -> !(objectName == "export_log" || (objectName == "locality_profile" && field == "study")) }

    // el plan `plan` es de `mine`; `orphanPlan` ya no tiene proyecto
    private val parentProject: suspend (String, UUID) -> UUID? = { _, id -> if (id == plan) mine else null }

    private fun write(
        objectName: String,
        kind: RecordChangeKind,
        before: Map<String, Any?>?,
        recordId: UUID? = UUID.randomUUID()
    ) = RecordWrite(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), objectName, recordId, kind, before, null, null, null)

    private fun resolve(
        change: RecordWrite,
        after: Map<String, Any?>
    ) = runBlocking { ProjectResolver.resolve(change, after, required, parentProject) }

    @Test
    fun `una raiz da el proyecto de antes y el de despues`() {
        val change = write("engagement", RecordChangeKind.UPDATED, mapOf("project" to mine.toString()))
        val moved = resolve(change, mapOf("project" to other.toString()))
        assertEquals(setOf(mine, other), moved.projects)
        assertFalse(moved.unresolved)
        assertEquals("project", moved.field)
    }

    @Test
    fun `una raiz sin proyecto guardado no se resuelve, salvo que el modelo lo permita`() {
        val orphan = write("engagement", RecordChangeKind.DELETED, mapOf("project" to null))
        assertTrue(resolve(orphan, orphan.before.orEmpty()).unresolved)
        val adopt = write("engagement", RecordChangeKind.UPDATED, mapOf("project" to null))
        assertTrue(resolve(adopt, mapOf("project" to other.toString())).unresolved)
        val tenantLevel = write("export_log", RecordChangeKind.DELETED, mapOf("project" to null))
        val open = resolve(tenantLevel, tenantLevel.before.orEmpty())
        assertFalse(open.unresolved)
        assertTrue(open.projects.isEmpty())
    }

    @Test
    fun `un alta sin la relacion obligatoria no es de alcance (lo rechaza wasichai con su 400), y el alta de un proyecto no pide alcance`() {
        val root = resolve(write("engagement", RecordChangeKind.CREATED, null), emptyMap())
        assertFalse(root.unresolved)
        assertTrue(root.projects.isEmpty())
        val child = resolve(write("planned_activity", RecordChangeKind.CREATED, null), emptyMap())
        assertFalse(child.unresolved)
        assertTrue(child.projects.isEmpty())
        val project = resolve(write("project", RecordChangeKind.CREATED, null, recordId = null), emptyMap())
        assertFalse(project.unresolved)
        assertTrue(project.projects.isEmpty())
    }

    @Test
    fun `un alta con un padre que no da proyecto sigue sin resolverse`() {
        assertTrue(resolve(write("planned_activity", RecordChangeKind.CREATED, null), mapOf("engagement_plan" to orphanPlan.toString())).unresolved)
        assertEquals(
            setOf(mine),
            resolve(write("planned_activity", RecordChangeKind.CREATED, null), mapOf("engagement_plan" to plan.toString())).projects
        )
    }

    @Test
    fun `un hijo toma el proyecto de su padre, y un padre huerfano o ausente no se resuelve`() {
        val child = write("planned_activity", RecordChangeKind.UPDATED, mapOf("engagement_plan" to plan.toString()))
        val ok = resolve(child, mapOf("engagement_plan" to plan.toString()))
        assertEquals(setOf(mine), ok.projects)
        assertFalse(ok.unresolved)
        assertEquals("engagement_plan", ok.field)
        // el padre existe pero ya no tiene proyecto
        assertTrue(resolve(child, mapOf("engagement_plan" to orphanPlan.toString())).unresolved)
        // el hijo quedó sin padre (ON DELETE SET NULL): nadie lo adopta sin ser ADMIN
        val orphan = write("planned_activity", RecordChangeKind.UPDATED, mapOf("engagement_plan" to null))
        assertTrue(resolve(orphan, mapOf("engagement_plan" to plan.toString())).unresolved)
        assertTrue(resolve(write("planned_activity", RecordChangeKind.DELETED, mapOf("engagement_plan" to null)), mapOf("engagement_plan" to null)).unresolved)
    }

    @Test
    fun `locality_profile sin estudio es del tenant, y con un estudio sin proyecto no se resuelve`() {
        val free = write("locality_profile", RecordChangeKind.UPDATED, mapOf("study" to null))
        val open = resolve(free, mapOf("study" to null))
        assertFalse(open.unresolved)
        assertTrue(open.projects.isEmpty())
        assertTrue(resolve(free, mapOf("study" to orphanPlan.toString())).unresolved)
    }

    @Test
    fun `un objeto fuera del alcance no da proyectos`() {
        val change = write("locality", RecordChangeKind.UPDATED, mapOf("project" to mine.toString()))
        val scope = resolve(change, change.before.orEmpty())
        assertTrue(scope.projects.isEmpty())
        assertFalse(scope.unresolved)
    }
}
