package sgspe.commitment

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import org.springframework.beans.factory.ObjectProvider
import sgspe.common.Callers
import sgspe.common.DerivedVersions
import sgspe.common.ModelCatalog
import sgspe.common.Records
import wasichai.core.common.ForbiddenException
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordCriterion
import wasichai.core.data.RecordResponse
import wasichai.core.data.RecordService
import wasichai.core.data.RecordWrite
import wasichai.core.data.RelatedRecordService
import wasichai.core.metadata.MetadataService
import wasichai.workflow.WorkflowService
import java.util.UUID

// D10 sin base (RF-COM-004, P12): un compromiso del que se depende y que quien registra no lee no cuenta como resuelto ni como
// abierto: es NotReadable (OBJECT_NOT_READABLE), sin decir cuántos ni cuáles. hoy la lectura es de todo el tenant (CON-041,
// opción A): ningún rol que registra el cumplimiento deja de leer un compromiso, así que esta rama solo se prueba aquí; con
// UPS-01, un compromiso de otro proyecto la alcanzará también por la API
class BlockingDependencyRuleTest {
    private val commitment = UUID.randomUUID()

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> provider(): ObjectProvider<T> = mock(ObjectProvider::class.java) { null } as ObjectProvider<T>

    private fun response(
        id: UUID,
        attributes: Map<String, Any?>,
        state: String? = null
    ) = RecordResponse(id.toString(), null, null, attributes, state)

    // `dependencies`: (depends_on, blocking) de commitment_dependency; `visible`: los compromisos que quien registra lee, con su
    // estado; `commitmentsDenied`: sin READ sobre commitment
    private fun rule(
        dependencies: List<Pair<UUID, Boolean>>,
        visible: Map<UUID, String> = emptyMap(),
        commitmentsDenied: Boolean = false
    ): BlockingDependencyRule {
        val records =
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
                    criteria: List<RecordCriterion>,
                    ids: List<UUID>?,
                    max: Int?,
                    field: String
                ): List<RecordResponse> =
                    when (objectName) {
                        Commitments.DEPENDENCY ->
                            dependencies.map { (dependsOn, blocking) ->
                                response(
                                    UUID.randomUUID(),
                                    mapOf(
                                        "commitment" to commitment.toString(),
                                        "depends_on" to dependsOn.toString(),
                                        "blocking" to blocking
                                    )
                                )
                            }
                        Commitments.COMMITMENT -> {
                            if (commitmentsDenied) throw ForbiddenException("sin lectura")
                            ids.orEmpty().mapNotNull { id -> visible[id]?.let { response(id, emptyMap(), it) } }
                        }
                        else -> error("lectura inesperada de $objectName")
                    }
            }
        return BlockingDependencyRule(records)
    }

    private fun register() =
        RecordWrite(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            Commitments.COMMITMENT,
            commitment,
            RecordChangeKind.TRANSITIONED,
            emptyMap(),
            null,
            Fulfillment.REGISTER,
            "entregado"
        )

    private fun BlockingDependencyRule.run() = runBlocking { findings(register(), dryRun = false) }

    @Test
    fun `una dependencia bloqueante hacia un compromiso que quien registra no lee es NotReadable`() {
        val hidden = UUID.randomUUID()
        val open = UUID.randomUUID()
        val error = assertThrows<NotReadable> { rule(listOf(hidden to true, open to true), visible = mapOf(open to "in_progress")).run() }
        assertEquals(Commitments.COMMITMENT, error.objectName)
    }

    @Test
    fun `sin READ sobre commitment tambien es NotReadable`() {
        val error = assertThrows<NotReadable> { rule(listOf(UUID.randomUUID() to true), commitmentsDenied = true).run() }
        assertEquals(Commitments.COMMITMENT, error.objectName)
    }

    @Test
    fun `un compromiso legible y abierto bloquea, y uno cumplido o cancelado no`() {
        val open = UUID.randomUUID()
        val found = rule(listOf(open to true), visible = mapOf(open to "in_progress")).run().single()
        assertEquals("BLOCKING_DEPENDENCY" to Commitments.DEPENDENCY, found.code to found.field)
        for (state in Fulfillment.RESOLVED) {
            assertEquals(emptyList<Any>(), rule(listOf(open to true), visible = mapOf(open to state)).run(), state)
        }
    }

    // una dependencia que no bloquea no se mira: no hace falta leer su compromiso
    @Test
    fun `una dependencia no bloqueante no lee el compromiso del que depende`() {
        assertEquals(emptyList<Any>(), rule(listOf(UUID.randomUUID() to false), commitmentsDenied = true).run())
    }
}
