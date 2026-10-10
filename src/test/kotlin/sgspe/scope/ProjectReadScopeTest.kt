package sgspe.scope

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.beans.factory.ObjectProvider
import sgspe.common.Callers
import sgspe.common.DerivedVersions
import sgspe.common.ModelCatalog
import sgspe.common.Permissions
import sgspe.common.ProjectReach
import sgspe.common.Records
import wasichai.core.common.NotFoundException
import wasichai.core.data.RecordService
import wasichai.core.data.RelatedRecordService
import wasichai.core.identity.AuthenticatedUser
import wasichai.core.identity.CurrentUser
import wasichai.core.metadata.CustomField
import wasichai.core.metadata.CustomObject
import wasichai.core.metadata.FieldType
import wasichai.core.metadata.MetadataService
import wasichai.core.metadata.ObjectDefinition
import wasichai.core.platform.WasichaiSchemas
import wasichai.workflow.WorkflowService
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

// ProjectReadScope sin base (ASM-106): los maestros no cuestan consultas, READ_ALL_PROJECTS exime, y falla cerrado si los
// permisos o una definición fallan o le falta una columna. la condición misma la prueba ProjectReadConditionTest
class ProjectReadScopeTest {
    private val organization = UUID.randomUUID()
    private val caller = AuthenticatedUser(UUID.randomUUID(), organization, "persona@sgspe.test", listOf("ANALYST"), null)
    private val catalog = ModelCatalog.fromClasspath()
    private val schemas = WasichaiSchemas("wasichai", "data")
    private val clock = Clock.fixed(Instant.parse("2026-10-15T03:00:00Z"), ZoneOffset.UTC)

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> provider(): ObjectProvider<T> = mock(ObjectProvider::class.java) { null } as ObjectProvider<T>

    private val records =
        Records(
            provider<RecordService>(),
            provider<RelatedRecordService>(),
            provider<WorkflowService>(),
            mock(MetadataService::class.java),
            mock(Callers::class.java),
            mock(ModelCatalog::class.java),
            mock(DerivedVersions::class.java)
        )

    // ProjectReach con la respuesta fija (o la falla) de readsAll
    private fun reach(readsAll: () -> Boolean) =
        object : ProjectReach(records, Permissions(mock(CurrentUser::class.java), mock(MetadataService::class.java)), clock) {
            override suspend fun readsAll(user: AuthenticatedUser): Boolean = readsAll()
        }

    private fun definition(
        name: String,
        vararg fields: String
    ) = ObjectDefinition(
        CustomObject(UUID.randomUUID(), organization, name, name, name, null, true, "${name}_tbl", null, null),
        fields.mapIndexed { i, field ->
            CustomField(
                UUID.randomUUID(),
                UUID.randomUUID(),
                field,
                field,
                FieldType.TEXT,
                field,
                false,
                false,
                null,
                null,
                i,
                null,
                null,
                emptyMap(),
                true,
                true
            )
        }
    )

    private val assignment = definition("project_assignment", "project", "member_user_id", "active", "start_date", "end_date")

    // las definiciones que el alcance pide, por nombre; cuenta las consultas
    private class Definitions(
        private val known: Map<String, ObjectDefinition>
    ) {
        val asked = mutableListOf<String>()

        suspend fun load(
            organization: UUID,
            name: String
        ): ObjectDefinition {
            asked += name
            return known[name] ?: throw NotFoundException("Object $name does not exist")
        }
    }

    private fun scope(
        readsAll: () -> Boolean = { false },
        definitions: Definitions = Definitions(mapOf("project_assignment" to assignment))
    ) = ProjectReadScope(reach(readsAll), catalog, schemas, clock, definitions::load)

    private fun condition(
        scope: ProjectReadScope,
        definition: ObjectDefinition
    ): String? = runBlocking { scope.criterion(caller, definition) }?.condition(definition) { ":p" }

    @Test
    fun `un maestro del tenant no se acota ni consulta nada`() {
        val definitions = Definitions(emptyMap())
        var asked = false
        val readsAll = {
            asked = true
            false
        }
        assertNull(runBlocking { scope(readsAll, definitions).criterion(caller, definition("person", "given_names")) })
        assertEquals(false, asked)
        assertEquals(emptyList<String>(), definitions.asked)
    }

    @Test
    fun `quien tiene READ_ALL_PROJECTS no se acota`() {
        assertNull(runBlocking { scope({ true }).criterion(caller, definition("stakeholder", "project")) })
    }

    @Test
    fun `una raiz se acota por la tabla de las asignaciones del tenant`() {
        val text = condition(scope(), definition("stakeholder", "project"))!!
        assertTrue(text.startsWith("\"project\" IN (SELECT a.\"project\" FROM \"data\".\"project_assignment_tbl\" a"), text)
    }

    @Test
    fun `un hijo se acota por la tabla de su padre y la relacion opcional admite el vacio`() {
        val definitions =
            Definitions(mapOf("project_assignment" to assignment, "commitment" to definition("commitment", "project")))
        val text = condition(scope(definitions = definitions), definition("delivery", "commitment"))!!
        assertTrue(text.startsWith("\"commitment\" IS NULL OR \"commitment\" IN (SELECT t.\"id\" FROM \"data\".\"commitment_tbl\" t"), text)
        assertEquals(listOf("project_assignment", "commitment"), definitions.asked)
    }

    @Test
    fun `si los permisos fallan, falta la definicion del padre o falta una columna, ningun registro`() {
        val failing = scope({ throw IllegalStateException("la base no responde") })
        assertEquals("false", condition(failing, definition("stakeholder", "project")))
        assertEquals("false", condition(scope(), definition("stakeholder_assessment", "stakeholder")))
        assertEquals("false", condition(scope(), definition("stakeholder", "name")))
        val partial = Definitions(mapOf("project_assignment" to definition("project_assignment", "project", "member_user_id", "active")))
        assertEquals("false", condition(scope(definitions = partial), definition("stakeholder", "project")))
    }

    @Test
    fun `un objeto sin clasificar no admite ningun registro`() {
        assertEquals("false", condition(scope(), definition("indicator_measurement", "project")))
    }
}
