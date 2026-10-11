package sgspe.scope

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.beans.factory.ObjectProvider
import sgspe.common.Callers
import sgspe.common.DerivedVersions
import sgspe.common.ModelCatalog
import sgspe.common.ObjectDefinitions
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
import java.time.Duration
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
        object : ProjectReach(records, Permissions(mock(CurrentUser::class.java), mock(ObjectDefinitions::class.java)), clock) {
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
    fun `un hijo de un unico padre opcional se acota por la tabla de su padre y admite el vacio`() {
        val definitions = Definitions(mapOf("project_assignment" to assignment, "study" to definition("study", "project")))
        val text = condition(scope(definitions = definitions), definition("locality_profile", "study"))!!
        assertTrue(text.startsWith("\"study\" IS NULL OR \"study\" IN (SELECT t1.\"id\" FROM \"data\".\"study_tbl\" t1"), text)
        assertEquals(listOf("project_assignment", "study"), definitions.asked)
    }

    @Test
    fun `un hijo de un padre obligatorio no admite el vacio`() {
        val definitions =
            Definitions(mapOf("project_assignment" to assignment, "agreement" to definition("agreement", "project")))
        val text = condition(scope(definitions = definitions), definition("agreement_clause", "agreement"))!!
        assertTrue(text.startsWith("\"agreement\" IN (SELECT t1.\"id\" FROM \"data\".\"agreement_tbl\" t1"), text)
        assertEquals(listOf("project_assignment", "agreement"), definitions.asked)
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

    // ASM-114: la entrega con dos padres, sin «IS NULL OR»
    @Test
    fun `una entrega se acota por sus dos padres, sin el vacio`() {
        val definitions =
            Definitions(
                mapOf(
                    "project_assignment" to assignment,
                    "commitment" to definition("commitment", "project"),
                    "social_contribution" to definition("social_contribution", "project")
                )
            )
        val text = condition(scope(definitions = definitions), definition("delivery", "commitment", "social_contribution"))!!
        assertTrue(text.startsWith("(\"commitment\" IN (SELECT t1.\"id\" FROM \"data\".\"commitment_tbl\" t1"), text)
        assertTrue(text.contains(") OR (\"social_contribution\" IN (SELECT t1.\"id\" FROM \"data\".\"social_contribution_tbl\" t1"), text)
        // sin el «IS NULL OR» de un único padre opcional; «mis proyectos» lleva los suyos sobre las fechas de la asignación
        assertFalse(text.contains("\"commitment\" IS NULL") || text.contains("\"social_contribution\" IS NULL"), text)
        assertEquals(listOf("project_assignment", "commitment", "social_contribution"), definitions.asked)
    }

    // Review Focus 1: la entrega de un tenant de F4 no tiene la columna social_contribution ni existe el objeto: se lee por su
    // compromiso, sin pedir la definición que falta
    @Test
    fun `en un tenant sin el modelo de F5 la entrega se acota solo por su compromiso`() {
        val definitions = Definitions(mapOf("project_assignment" to assignment, "commitment" to definition("commitment", "project")))
        val text = condition(scope(definitions = definitions), definition("delivery", "commitment"))!!
        assertTrue(text.startsWith("\"commitment\" IN (SELECT t1.\"id\" FROM \"data\".\"commitment_tbl\" t1"), text)
        assertFalse(text.contains("\"commitment\" IS NULL"), text)
        assertEquals(listOf("project_assignment", "commitment"), definitions.asked)
    }

    // ASM-114 y Review Focus 4: el nieto sube por su indicador; a un salto le falta su columna y no se admite ningún registro
    @Test
    fun `una medicion se acota por su indicador y el programa de su indicador`() {
        val definitions =
            Definitions(
                mapOf(
                    "project_assignment" to assignment,
                    "program_indicator" to definition("program_indicator", "social_program"),
                    "social_program" to definition("social_program", "project")
                )
            )
        val text = condition(scope(definitions = definitions), definition("program_measurement", "program_indicator"))!!
        assertTrue(
            text.startsWith(
                "\"program_indicator\" IN (SELECT t1.\"id\" FROM \"data\".\"program_indicator_tbl\" t1 WHERE t1.organization_id = :p " +
                    "AND t1.\"social_program\" IN (SELECT t2.\"id\" FROM \"data\".\"social_program_tbl\" t2"
            ),
            text
        )
        assertEquals(listOf("project_assignment", "program_indicator", "social_program"), definitions.asked)
        val broken =
            Definitions(
                mapOf(
                    "project_assignment" to assignment,
                    "program_indicator" to definition("program_indicator", "name"),
                    "social_program" to definition("social_program", "project")
                )
            )
        assertEquals("false", condition(scope(definitions = broken), definition("program_measurement", "program_indicator")))
    }

    @Test
    fun `un objeto sin clasificar no admite ningun registro`() {
        assertEquals("false", condition(scope(), definition("indicator_measurement", "project")))
    }

    // la caché del esquema: dos lecturas seguidas piden las definiciones del alcance una sola vez, y una definición vieja (un
    // project_assignment a medio aplicar, sin end_date) sigue sin admitir ningún registro mientras dura en la caché
    @Test
    fun `con ObjectDefinitions las definiciones del alcance se piden una vez y una vieja falla cerrado`() {
        val partial =
            Definitions(
                mapOf(
                    "project_assignment" to definition("project_assignment", "project", "member_user_id", "active", "start_date"),
                    "stakeholder" to definition("stakeholder", "project")
                )
            )
        val cache = ObjectDefinitions(partial::load, Duration.ofSeconds(30), 100) { 0L }
        val scope = ProjectReadScope(reach { false }, catalog, schemas, clock) { org, name -> cache.recent(org, name) }
        val assessment = definition("stakeholder_assessment", "stakeholder")
        assertEquals("false", condition(scope, assessment))
        assertEquals("false", condition(scope, assessment))
        assertEquals(listOf("project_assignment", "stakeholder"), partial.asked)
    }

    // el constructor @Autowired: dos lecturas seguidas cargan cada definición del alcance una sola vez (por recent)
    @Test
    fun `el constructor de Spring pide las definiciones por la cache una sola vez`() {
        val counter = Definitions(mapOf("project_assignment" to assignment, "study" to definition("study", "project")))
        val cache = ObjectDefinitions(counter::load, Duration.ofSeconds(30), 100) { 0L }
        val scope = ProjectReadScope(reach { false }, cache, catalog, schemas, clock)
        val child = definition("locality_profile", "study")
        val first = condition(scope, child)!!
        assertTrue(first.startsWith("\"study\" IS NULL OR \"study\" IN (SELECT t1.\"id\""), first)
        assertEquals(first, condition(scope, child))
        assertEquals(listOf("project_assignment", "study"), counter.asked)
    }
}
