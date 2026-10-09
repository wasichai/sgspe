package sgspe.privacy

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.beans.factory.ObjectProvider
import sgspe.common.Callers
import sgspe.common.DerivedVersions
import sgspe.common.ModelCatalog
import sgspe.common.Records
import wasichai.core.data.RecordResponse
import wasichai.core.data.RecordService
import wasichai.core.data.RelatedRecordService
import wasichai.core.identity.AuthenticatedUser
import wasichai.core.metadata.MetadataService
import wasichai.workflow.WorkflowService
import java.time.Clock
import java.util.UUID

// ExportProjects.resolve con un Records que suspende de verdad (como el de la base): una llamada suspendida dentro de un lambda
// en línea en posición de cola se compilaba como llamada de cola y devolvía Unit a quien llamaba (CI del plan 3.2)
class ExportProjectsTest {
    private val userId = UUID.randomUUID()
    private val project = UUID.randomUUID()
    private val user = AuthenticatedUser(userId, UUID.randomUUID(), "persona@sgspe.test", listOf("STAKEHOLDER_MANAGER"), null)

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> provider(): ObjectProvider<T> = mock(ObjectProvider::class.java) { null } as ObjectProvider<T>

    private val suspending =
        object : Records(
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
                criteria: List<wasichai.core.data.RecordCriterion>,
                ids: List<UUID>?,
                max: Int?,
                field: String
            ): List<RecordResponse> {
                delay(1)
                return listOf(
                    RecordResponse(
                        UUID.randomUUID().toString(),
                        null,
                        null,
                        mapOf("member_user_id" to userId.toString(), "project" to project.toString(), "active" to true)
                    )
                )
            }
        }

    private val exports = ExportProjects(suspending, Clock.systemUTC(), 20000)

    @Test
    fun `un solo proyecto pedido por quien lo tiene asignado devuelve los pedidos aunque la lectura suspenda`() {
        assertEquals(listOf(project), runBlocking { exports.resolve(user, listOf(project)) })
    }

    @Test
    fun `sin proyectos pedidos devuelve mis proyectos aunque la lectura suspenda`() {
        assertEquals(listOf(project), runBlocking { exports.resolve(user, emptyList()) })
    }
}
