package sgspe.investment

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import sgspe.common.ObjectDefinitions
import sgspe.common.Records
import sgspe.common.TenantModel
import wasichai.core.common.NotFoundException
import wasichai.core.data.RecordResponse
import wasichai.core.metadata.ObjectDefinition
import java.math.BigDecimal
import java.util.UUID

// lo comprometido de una campaña (elección 6 de la especificación de F5): compromisos y, con el modelo de F5, aportes; sin el
// registro que se aprueba; un monto inválido se omite y se cuenta
class CampaignCommittedTest {
    private val campaign = UUID.randomUUID()
    private val approving = UUID.randomUUID()
    private val reads = mutableListOf<String>()

    private fun row(
        id: UUID,
        vararg attributes: Pair<String, Any?>
    ) = RecordResponse(id = id.toString(), createdAt = null, updatedAt = null, attributes = mapOf("currency" to "PEN") + attributes)

    private val rows =
        mapOf(
            "commitment" to
                listOf(
                    row(UUID.randomUUID(), "amount" to 400),
                    row(UUID.randomUUID(), "amount" to "no es un monto"),
                    row(approving, "amount" to 9000)
                ),
            "social_contribution" to
                listOf(
                    row(UUID.randomUUID(), "total_amount" to 300),
                    row(UUID.randomUUID(), "total_amount" to 100, "currency" to "USD")
                )
        )

    private val records: Records =
        mock(Records::class.java) { call ->
            if (call.method.name != "all") error("lectura inesperada: ${call.method.name}")
            val name = call.arguments[0] as String
            reads += name
            rows[name].orEmpty()
        }

    private fun tenant(withF5: Boolean): TenantModel =
        TenantModel(
            mock(ObjectDefinitions::class.java) { call ->
                if (call.method.name != "current") return@mock null
                if (!withF5 && call.arguments[1] == "social_contribution") throw NotFoundException("sin el modelo de F5")
                mock(ObjectDefinition::class.java)
            }
        )

    @Test
    fun `con el modelo de F5 suma compromisos y aportes comprometidos sin el que se aprueba`() {
        val committed = runBlocking { CampaignCommitted(records, tenant(withF5 = true)).of(UUID.randomUUID(), campaign, approving) }
        assertEquals(setOf("approved", "procured", "delivered", "closed"), CampaignCommitted.CONTRIBUTION_STATES)
        assertEquals(listOf("commitment", "social_contribution"), reads)
        assertEquals(listOf("400" to "PEN", "300" to "PEN", "100" to "USD"), committed.approved.map { it.amount!!.toPlainString() to it.currency })
        assertEquals(1, committed.invalid)
    }

    // Review Focus 5: un tenant que todavía no tiene el modelo de F5 sigue aprobando compromisos
    @Test
    fun `sin el modelo de F5 solo lee compromisos`() {
        val committed = runBlocking { CampaignCommitted(records, tenant(withF5 = false)).of(UUID.randomUUID(), campaign, null) }
        assertEquals(listOf("commitment"), reads)
        assertEquals(2, committed.approved.size)
        assertEquals(0, BigDecimal("9400").compareTo(committed.approved.mapNotNull { it.amount }.fold(BigDecimal.ZERO, BigDecimal::add)))
    }
}
