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
import sgspe.common.Records
import sgspe.scope.Assignments
import tools.jackson.databind.json.JsonMapper
import wasichai.core.data.RecordChange
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordResponse
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
        val listener = ApprovalNoticeListener(mock(Notices::class.java), assignments, catalog, senior, mock(Records::class.java))
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
            val definition = mapper.readTree(file).path("definition")
            val transitions = definition.path("transitions").map { it }
            if (step.submit == null) {
                // el aviso sale con el alta: el estado pendiente es el inicial del workflow
                val initial = definition.path("states").map { it }.firstOrNull { it.path("type").asString() == "INITIAL" }
                assertEquals(step.pending, initial?.path("name")?.asString(), "${step.objectName}: estado inicial")
            } else {
                val submit = transitions.firstOrNull { it.path("name").asString() == step.submit }
                assertTrue(submit != null, "${step.objectName}: falta la transición ${step.submit}")
                assertEquals(step.pending, submit!!.path("to").asString(), "${step.objectName}: estado pendiente")
            }
            for (approval in step.approvals + step.direct) {
                val t = transitions.firstOrNull { it.path("name").asString() == approval }
                assertTrue(t != null, "${step.objectName}: falta la transición $approval")
                assertEquals(step.pending, t!!.path("from").asString(), "${step.objectName}: $approval sale del pendiente")
            }
        }
    }

    // elección 14 de F5: el alta de un aporte avisa a los roles de approve y, sin aprobación interna, también a los de
    // approve_direct; si quien crea no lee el proyecto, a la unión (decisión 11 del plan 5.2a)
    @Test
    fun `el alta de un aporte avisa a approve y, sin aprobacion interna o sin leer el proyecto, tambien a approve_direct`() {
        val project = UUID.randomUUID()
        var projectAttributes: Map<String, Any?>? = emptyMap()
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
        val records =
            mock(Records::class.java) { call: InvocationOnMock ->
                if (call.method.name ==
                    "find"
                ) {
                    projectAttributes?.let { RecordResponse(id = project.toString(), createdAt = null, updatedAt = null, attributes = it) }
                } else {
                    null
                }
            }
        val listener = ApprovalNoticeListener(mock(Notices::class.java), assignments, catalog, mock(SeniorApprovalReader::class.java), records)
        val created =
            RecordChange(
                organizationId = UUID.randomUUID(),
                userId = UUID.randomUUID(),
                objectId = UUID.randomUUID(),
                objectName = "social_contribution",
                recordId = UUID.randomUUID(),
                kind = RecordChangeKind.CREATED,
                after = mapOf("project" to project.toString(), "code" to "APO-001", "contribution_type" to "DONACION")
            )
        runBlocking { listener.recordChanged(created) }
        assertEquals(listOf("approve"), asked)
        asked.clear()
        projectAttributes = mapOf("policy_internal_approval" to false)
        runBlocking { listener.recordChanged(created) }
        assertEquals(listOf("approve", "approve_direct"), asked)
        asked.clear()
        projectAttributes = null
        runBlocking { listener.recordChanged(created) }
        assertEquals(listOf("approve", "approve_direct"), asked)
        assertEquals("APO-001 · DONACION", ApprovalNoticeListener.label("social_contribution", created.after!!))
    }

    // especificación de F5 §3.8: el envío de un programa (submit de WF-G) avisa a los roles de approve, y el texto nombra el
    // programa por su código y su nombre, nunca por su descripción
    @Test
    fun `enviar un programa avisa a los roles de approve y se nombra por codigo y nombre`() {
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
        val listener =
            ApprovalNoticeListener(mock(Notices::class.java), assignments, catalog, mock(SeniorApprovalReader::class.java), mock(Records::class.java))
        val change =
            RecordChange(
                organizationId = UUID.randomUUID(),
                userId = UUID.randomUUID(),
                objectId = UUID.randomUUID(),
                objectName = "social_program",
                recordId = UUID.randomUUID(),
                kind = RecordChangeKind.TRANSITIONED,
                after = mapOf("project" to UUID.randomUUID().toString(), "code" to "PRG-1", "name" to "Programa Ejemplo"),
                transition = "submit"
            )
        runBlocking { listener.recordChanged(change) }
        assertEquals(listOf("approve"), asked)
        assertEquals(
            "PRG-1 · Programa Ejemplo",
            ApprovalNoticeListener.label("social_program", mapOf("code" to "PRG-1", "name" to "Programa Ejemplo", "description" to "no va"))
        )
        assertEquals(
            "Programa por aprobar: PRG-1 · Programa Ejemplo" to "El programa social espera su aprobación.",
            NoticeTexts.approvalPending("social_program", "PRG-1 · Programa Ejemplo")
        )
    }
}
