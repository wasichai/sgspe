package sgspe.investment

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import org.springframework.beans.factory.ObjectProvider
import sgspe.commitment.NotReadable
import sgspe.common.Callers
import sgspe.common.DerivedVersions
import sgspe.common.ModelCatalog
import sgspe.common.Permissions
import sgspe.common.Records
import sgspe.common.Transactions
import wasichai.core.common.ForbiddenException
import wasichai.core.common.NotFoundException
import wasichai.core.common.PageResponse
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordResponse
import wasichai.core.data.RecordService
import wasichai.core.data.RecordWrite
import wasichai.core.data.RelatedRecordService
import wasichai.core.metadata.MetadataService
import wasichai.workflow.WorkflowService
import java.util.UUID

// SupportPolicyGuard en seco, con un RecordService de mentira: lo que no se puede leer (OBJECT_NOT_READABLE), los montos
// guardados no válidos (CAP_DATA_INVALID) y la campaña sin moneda (nunca bloquea)
class SupportPolicyGuardTest {
    private val campaign = UUID.randomUUID()
    private var campaignAttributes: Map<String, Any?>? = mapOf("social_support_cap" to 1000, "currency" to "PEN")
    private var campaignDenied = false
    private var committed: List<Map<String, Any?>> = emptyList()
    private var projectAttributes: Map<String, Any?> = emptyMap()

    private val service: RecordService =
        mock(RecordService::class.java) { invocation ->
            when (invocation.method.name) {
                "get" ->
                    when (invocation.arguments[0] as String) {
                        "exploration_campaign" ->
                            when {
                                campaignDenied -> throw ForbiddenException("sin lectura")
                                campaignAttributes == null -> throw NotFoundException("no existe")
                                else -> RecordResponse(campaign.toString(), null, null, campaignAttributes!!)
                            }
                        else -> RecordResponse(UUID.randomUUID().toString(), null, null, projectAttributes)
                    }
                "list" ->
                    PageResponse.of(committed.map { RecordResponse(UUID.randomUUID().toString(), null, null, it) }, 0, 200, null, null)
                else -> null
            }
        }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> provider(value: T?): ObjectProvider<T> = mock(ObjectProvider::class.java) { value } as ObjectProvider<T>

    private val guard =
        SupportPolicyGuard(
            Records(
                provider(service),
                provider<RelatedRecordService>(null),
                provider<WorkflowService>(null),
                mock(MetadataService::class.java),
                mock(Callers::class.java),
                mock(ModelCatalog::class.java),
                mock(DerivedVersions::class.java)
            ),
            mock(Callers::class.java),
            mock(Permissions::class.java),
            mock(Transactions::class.java)
        )

    private fun approve(amount: Int = 100) =
        RecordWrite(
            organizationId = UUID.randomUUID(),
            userId = UUID.randomUUID(),
            objectId = UUID.randomUUID(),
            objectName = "commitment",
            recordId = UUID.randomUUID(),
            kind = RecordChangeKind.TRANSITIONED,
            transition = "approve",
            before = mapOf("exploration_campaign" to campaign.toString(), "amount" to amount, "currency" to "PEN", "project" to UUID.randomUUID().toString())
        )

    private fun findings(amount: Int = 100) = runBlocking { guard.findings(approve(amount), dryRun = true) }

    @Test
    fun `una campana que quien aprueba no lee, o no existe, es NotReadable y no pasa`() {
        campaignDenied = true
        assertEquals("exploration_campaign", assertThrows<NotReadable> { findings() }.objectName)
        campaignDenied = false
        campaignAttributes = null
        assertEquals("exploration_campaign", assertThrows<NotReadable> { findings() }.objectName)
    }

    @Test
    fun `un monto guardado no valido se omite y bloquea con CAP_DATA_INVALID, o advierte con la politica apagada`() {
        committed = listOf(mapOf("amount" to "no es un monto", "currency" to "PEN"), mapOf("amount" to 300, "currency" to "PEN"))
        val blocked = findings().single()
        assertEquals("CAP_DATA_INVALID", blocked.code)
        assertEquals("exploration_campaign", blocked.field)
        assertTrue(blocked.blocking)
        projectAttributes = mapOf("policy_cap_blocks_approval" to false)
        assertFalse(findings().single().blocking)
        // el válido sí se suma: 300 + 800 pasa el tope de 1000
        projectAttributes = emptyMap()
        assertEquals(setOf("CAP_DATA_INVALID", "SUPPORT_CAP_EXCEEDED"), findings(800).map { it.code }.toSet())
    }

    @Test
    fun `un tope guardado no valido es CAP_DATA_INVALID`() {
        campaignAttributes = mapOf("social_support_cap" to "mucho", "currency" to "PEN")
        assertEquals("CAP_DATA_INVALID", findings().single().code)
    }

    @Test
    fun `una campana sin moneda nunca bloquea`() {
        campaignAttributes = mapOf("social_support_cap" to 10, "currency" to null)
        val found = findings(1_000_000).single()
        assertEquals("CAP_OTHER_CURRENCY", found.code)
        assertFalse(found.blocking)
    }
}
