package sgspe.common

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.beans.factory.ObjectProvider
import wasichai.core.common.ForbiddenException
import wasichai.core.data.RecordCriterion
import wasichai.core.data.RecordResponse
import wasichai.core.data.RecordService
import wasichai.core.data.RelatedRecordService
import wasichai.core.identity.AuthenticatedUser
import wasichai.core.identity.CurrentUser
import wasichai.core.metadata.MetadataService
import wasichai.workflow.WorkflowService
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

// ProjectReach (ASM-106, elección 6 de la especificación 4.4): quién lee todos los proyectos, sin literales de rol, y las
// asignaciones vigentes de quien llama, con la misma vigencia que ProjectScopeGuard (hoy en Lima)
class ProjectReachTest {
    private val userId = UUID.randomUUID()
    private val user = AuthenticatedUser(userId, UUID.randomUUID(), "persona@sgspe.test", listOf("ANALYST"), null)
    private val admin = AuthenticatedUser(UUID.randomUUID(), UUID.randomUUID(), "admin@sgspe.test", listOf("ADMIN"), null)
    private val current = UUID.randomUUID()
    private val expired = UUID.randomUUID()
    private val inactive = UUID.randomUUID()

    // el 15 de octubre de 2026 a las 03:00 UTC todavía es el 14 en Lima
    private val clock = Clock.fixed(Instant.parse("2026-10-15T03:00:00Z"), ZoneOffset.UTC)

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> provider(): ObjectProvider<T> = mock(ObjectProvider::class.java) { null } as ObjectProvider<T>

    private fun row(attributes: Map<String, Any?>) = RecordResponse(UUID.randomUUID().toString(), null, null, attributes)

    // un Records que suspende de verdad, como el de la base (la lección de ExportProjectsTest)
    private fun records(
        rows: List<RecordResponse>,
        forbidden: Boolean = false
    ) = object : Records(
        provider<RecordService>(),
        provider<RelatedRecordService>(),
        provider<WorkflowService>(),
        mock(MetadataService::class.java),
        mock(Callers::class.java),
        mock(ModelCatalog::class.java),
        mock(DerivedVersions::class.java)
    ) {
        override suspend fun all(
            objectName: String,
            filters: Map<String, String>,
            criteria: List<RecordCriterion>,
            ids: List<UUID>?,
            max: Int?,
            field: String
        ): List<RecordResponse> {
            delay(1)
            if (forbidden) throw ForbiddenException("Missing permission READ")
            assertEquals(ProjectReach.ASSIGNMENT, objectName)
            assertEquals(mapOf("member_user_id" to userId.toString()), filters)
            return rows
        }
    }

    // Permissions con la respuesta fija de has; cuenta las preguntas
    private class Held(
        private val holds: Boolean
    ) : Permissions(mock(CurrentUser::class.java), mock(MetadataService::class.java)) {
        var asked = 0

        override suspend fun has(
            user: AuthenticatedUser,
            action: String,
            objectName: String
        ): Boolean {
            asked++
            assertEquals(ProjectReach.READ_ALL_PROJECTS to ProjectReach.PROJECT, action to objectName)
            return holds
        }
    }

    private fun assignment(
        project: UUID,
        active: Boolean = true,
        start: String? = null,
        end: String? = null,
        member: UUID = userId
    ) = row(
        mapOf(
            "project" to project.toString(),
            "member_user_id" to member.toString(),
            "active" to active,
            "start_date" to start,
            "end_date" to end
        )
    )

    @Test
    fun `lee todo el ADMIN y quien tiene READ_ALL_PROJECTS sobre project, y nadie mas`() {
        val none = Held(false)
        assertTrue(runBlocking { ProjectReach(records(emptyList()), none, clock).readsAll(admin) })
        assertEquals(0, none.asked, "al ADMIN no se le pregunta")
        assertTrue(runBlocking { ProjectReach(records(emptyList()), Held(true), clock).readsAll(user) })
        assertFalse(runBlocking { ProjectReach(records(emptyList()), none, clock).readsAll(user) })
    }

    @Test
    fun `dentro de RequestMemo se pregunta una sola vez`() {
        val held = Held(true)
        val reach = ProjectReach(records(emptyList()), held, clock)
        runBlocking { RequestMemo.within { repeat(3) { reach.readsAll(user) } } }
        assertEquals(1, held.asked)
    }

    @Test
    fun `mis proyectos son las asignaciones vigentes hoy en Lima, sin repetidos`() {
        val rows =
            listOf(
                assignment(current, start = "2026-10-01", end = "2026-10-14"),
                assignment(current),
                assignment(expired, end = "2026-10-13"),
                assignment(inactive, active = false),
                // el 15 todavía no empieza en Lima
                assignment(UUID.randomUUID(), start = "2026-10-15"),
                // una fila de otra persona que un filtro ignorado dejaría pasar
                assignment(UUID.randomUUID(), member = UUID.randomUUID())
            )
        val reach = ProjectReach(records(rows), Held(false), clock)
        assertEquals(listOf(current), runBlocking { reach.assigned(user) })
        assertEquals(listOf(current), runBlocking { reach.mine(user) })
    }

    @Test
    fun `quien lee todo no tiene proyectos por defecto, y sin READ sobre las asignaciones no tiene ninguno`() {
        assertNull(runBlocking { ProjectReach(records(listOf(assignment(current))), Held(true), clock).mine(user) })
        assertEquals(emptyList<UUID>(), runBlocking { ProjectReach(records(emptyList(), forbidden = true), Held(false), clock).mine(user) })
    }
}
