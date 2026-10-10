package sgspe.notification

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.invocation.InvocationOnMock
import sgspe.commitment.SeniorApprovalReader
import sgspe.common.ModelCatalog
import sgspe.common.NotReadable
import sgspe.scope.Assignments
import tools.jackson.databind.json.JsonMapper
import wasichai.core.data.RecordChange
import wasichai.core.data.RecordChangeKind
import java.io.File
import java.util.UUID

// el oyente sin base: lo que pasa cuando no se puede evaluar el nivel de un compromiso, y que STEPS sigue al modelo
class ApprovalNoticeListenerTest {
    private val catalog = ModelCatalog.fromClasspath()

    @Test
    fun `si el nivel superior no se puede leer, los destinatarios son la union de approve y approve_senior`() {
        val asked = mutableListOf<String>()
        val assignments =
            mock(Assignments::class.java) { call: InvocationOnMock ->
                if (call.method.name == "recipients") {
                    asked += call.arguments[2] as String
                    emptyList<Any>()
                } else {
                    null
                }
            }
        val senior = mock(SeniorApprovalReader::class.java) { throw NotReadable("project") }
        val listener = ApprovalNoticeListener(mock(Notices::class.java), assignments, catalog, senior)
        val id = UUID.randomUUID()
        val change =
            RecordChange(
                organizationId = UUID.randomUUID(),
                userId = UUID.randomUUID(),
                objectId = UUID.randomUUID(),
                objectName = "commitment",
                recordId = id,
                kind = RecordChangeKind.TRANSITIONED,
                after = mapOf("project" to UUID.randomUUID().toString(), "code" to "C-001"),
                transition = "submit"
            )
        runBlocking { listener.recordChanged(change) }
        assertEquals(listOf("approve", "approve_senior"), asked)
    }

    @Test
    fun `cada paso de STEPS existe en model workflows con su estado pendiente y sus transiciones`() {
        val mapper = JsonMapper.builder().build()
        for (step in ApprovalNoticeListener.STEPS) {
            val file = File("model/workflows/${step.objectName}.json")
            assertTrue(file.exists(), "falta ${file.path}")
            val transitions =
                mapper
                    .readTree(file)
                    .path("definition")
                    .path("transitions")
                    .map { it }
            val submit = transitions.firstOrNull { it.path("name").asString() == step.submit }
            assertTrue(submit != null, "${step.objectName}: falta la transición ${step.submit}")
            assertEquals(step.pending, submit!!.path("to").asString(), "${step.objectName}: estado pendiente")
            for (approval in step.approvals) {
                val t = transitions.firstOrNull { it.path("name").asString() == approval }
                assertTrue(t != null, "${step.objectName}: falta la transición $approval")
                assertEquals(step.pending, t!!.path("from").asString(), "${step.objectName}: $approval sale del pendiente")
            }
        }
    }
}
