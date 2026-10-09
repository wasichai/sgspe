package sgspe.commitment

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import org.springframework.beans.factory.ObjectProvider
import sgspe.common.Callers
import sgspe.common.Records
import sgspe.common.WriteView
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordResponse
import wasichai.core.data.RecordService
import wasichai.core.data.RecordWrite
import wasichai.core.data.RelatedRecordService
import wasichai.core.metadata.MetadataService
import wasichai.workflow.WorkflowService
import java.util.UUID

// RN-ENG-001 sobre un origen que no se lee: una edición que conserva el origen guardado y cambia origin_justification lo juzga
// (la plataforma no mira un valor sin cambios, así que sus 400 no lo cubren) y, si quien edita ya no lo lee, falla cerrado con
// NotReadable (OBJECT_NOT_READABLE), nunca con un pase. sin base: Records.find devuelve null como el 404 de wasichai
class CommitmentRecordRulesTest {
    private val origin = UUID.randomUUID()

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> provider(): ObjectProvider<T> = mock(ObjectProvider::class.java) { null } as ObjectProvider<T>

    private fun rules(visible: Map<UUID, RecordResponse>): CommitmentRecordRules {
        val records =
            object : Records(
                provider<RecordService>(),
                provider<RelatedRecordService>(),
                provider<WorkflowService>(),
                mock(MetadataService::class.java),
                mock(Callers::class.java)
            ) {
                override suspend fun find(
                    objectName: String,
                    id: UUID
                ): RecordResponse? = visible[id]
            }
        val before = mapOf("origin_engagement" to origin.toString(), "origin_justification" to "antes")
        val view =
            mock(WriteView::class.java) { invocation ->
                when (invocation.method.name) {
                    "after" -> before + ("origin_justification" to "después")
                    "changed" -> setOf("origin_justification")
                    else -> null
                }
            }
        return CommitmentRecordRules(records, view)
    }

    private fun edit() =
        RecordWrite(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            Commitments.COMMITMENT,
            UUID.randomUUID(),
            RecordChangeKind.UPDATED,
            mapOf("origin_engagement" to origin.toString(), "origin_justification" to "antes"),
            mapOf("origin_justification" to "después"),
            null,
            null
        )

    @Test
    fun `editar la justificacion de un origen que quien edita ya no lee es NotReadable`() {
        val error = assertThrows<NotReadable> { runBlocking { rules(emptyMap()).findings(edit(), dryRun = false) } }
        assertEquals("engagement", error.objectName)
    }

    @Test
    fun `con el origen legible la edicion se juzga por su tipo`() {
        val engagement = RecordResponse(origin.toString(), null, null, mapOf("engagement_type" to "REUNION"))
        assertEquals(emptyList<Any>(), runBlocking { rules(mapOf(origin to engagement)).findings(edit(), dryRun = false) })
    }
}
