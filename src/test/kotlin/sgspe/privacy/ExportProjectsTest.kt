package sgspe.privacy

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import org.springframework.beans.factory.ObjectProvider
import sgspe.common.Callers
import sgspe.common.DerivedVersions
import sgspe.common.ModelCatalog
import sgspe.common.ObjectDefinitions
import sgspe.common.Permissions
import sgspe.common.ProjectReach
import sgspe.common.Records
import wasichai.core.common.WasichaiException
import wasichai.core.data.RecordService
import wasichai.core.data.RelatedRecordService
import wasichai.core.identity.AuthenticatedUser
import wasichai.core.identity.CurrentUser
import wasichai.core.metadata.MetadataService
import wasichai.workflow.WorkflowService
import java.time.Clock
import java.util.UUID

// ExportProjects sobre ProjectReach (api.md §4.13, ASM-106): los pedidos, o «mis proyectos»; con un solo proyecto pedido, quien
// no lo alcanza recibe NOT_ASSIGNED, y quien lee todos los proyectos (READ_ALL_PROJECTS) no necesita la asignación. ProjectReach
// suspende de verdad, como el de la base: una llamada suspendida dentro de un lambda en línea en posición de cola se compilaba
// como llamada de cola y devolvía Unit a quien llamaba (CI del plan 3.2)
class ExportProjectsTest {
    private val project = UUID.randomUUID()
    private val user = AuthenticatedUser(UUID.randomUUID(), UUID.randomUUID(), "persona@sgspe.test", listOf("STAKEHOLDER_MANAGER"), null)

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

    private fun exports(
        readsAll: Boolean,
        assigned: List<UUID>
    ) = ExportProjects(
        object : ProjectReach(records, Permissions(mock(CurrentUser::class.java), mock(ObjectDefinitions::class.java)), Clock.systemUTC()) {
            override suspend fun readsAll(user: AuthenticatedUser): Boolean {
                delay(1)
                return readsAll
            }

            override suspend fun assigned(user: AuthenticatedUser): List<UUID> {
                delay(1)
                return assigned
            }
        }
    )

    @Test
    fun `un solo proyecto pedido por quien lo tiene asignado devuelve los pedidos aunque la lectura suspenda`() {
        assertEquals(listOf(project), runBlocking { exports(false, listOf(project)).resolve(user, listOf(project)) })
    }

    @Test
    fun `sin proyectos pedidos devuelve mis proyectos, y todos para quien lee todo`() {
        assertEquals(listOf(project), runBlocking { exports(false, listOf(project)).resolve(user, emptyList()) })
        assertNull(runBlocking { exports(true, emptyList()).resolve(user, emptyList()) })
    }

    @Test
    fun `un solo proyecto ajeno es NOT_ASSIGNED salvo para quien lee todos los proyectos`() {
        val refused = assertThrows<WasichaiException> { runBlocking { exports(false, emptyList()).resolve(user, listOf(project)) } }
        assertEquals(403, refused.status.value())
        assertEquals(listOf(project), runBlocking { exports(true, emptyList()).resolve(user, listOf(project)) })
        // dos o más proyectos no piden asignación: el alcance de lectura acota las filas
        val two = listOf(project, UUID.randomUUID())
        assertEquals(two, runBlocking { exports(false, emptyList()).resolve(user, two) })
    }
}
