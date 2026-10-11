package sgspe.investment

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import sgspe.common.Records
import wasichai.core.data.RecordChange
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordResponse
import java.math.BigDecimal
import java.util.UUID

// ContributionTotalsListener sin base: qué cambios recalculan el total y que solo escribe si cambia
class ContributionTotalsListenerTest {
    private val contribution = UUID.randomUUID()
    private val patches = mutableListOf<Map<*, *>>()
    private var stored: Map<String, Any?> = mapOf("total_amount" to 100)

    private fun response(attributes: Map<String, Any?>) =
        RecordResponse(id = contribution.toString(), createdAt = null, updatedAt = null, attributes = attributes)

    private val records: Records =
        mock(Records::class.java) { call ->
            when (call.method.name) {
                "find" -> response(stored)
                "all" -> listOf(response(mapOf("line_amount" to 300)), response(mapOf("line_amount" to "150.5")))
                "patch" -> {
                    patches += call.arguments[2] as Map<*, *>
                    response(stored)
                }
                else -> null
            }
        }

    private fun change(
        objectName: String,
        kind: RecordChangeKind,
        transition: String? = null
    ) = RecordChange(
        organizationId = UUID.randomUUID(),
        userId = UUID.randomUUID(),
        objectId = UUID.randomUUID(),
        objectName = objectName,
        recordId = if (objectName == Contributions.CONTRIBUTION) contribution else UUID.randomUUID(),
        kind = kind,
        before = if (objectName == Contributions.ITEM) mapOf("social_contribution" to contribution.toString()) else null,
        after = if (objectName == Contributions.ITEM && kind != RecordChangeKind.DELETED) mapOf("social_contribution" to contribution.toString()) else null,
        transition = transition
    )

    private fun run(change: RecordChange) = runBlocking { ContributionTotalsListener(records).recordChanged(change) }

    @Test
    fun `un item creado, cambiado o borrado y la aprobacion recalculan el total`() {
        for (kind in listOf(RecordChangeKind.CREATED, RecordChangeKind.UPDATED, RecordChangeKind.DELETED)) run(change(Contributions.ITEM, kind))
        run(change(Contributions.CONTRIBUTION, RecordChangeKind.TRANSITIONED, "approve"))
        assertEquals(4, patches.size)
        assertEquals(0, BigDecimal("450.5").compareTo(patches.first()[Contributions.TOTAL] as BigDecimal))
    }

    @Test
    fun `un total igual no se escribe y otras transiciones no recalculan`() {
        stored = mapOf("total_amount" to "450.50")
        run(change(Contributions.ITEM, RecordChangeKind.UPDATED))
        stored = mapOf("total_amount" to 1)
        run(change(Contributions.CONTRIBUTION, RecordChangeKind.TRANSITIONED, "register_procurement"))
        run(change(Contributions.CONTRIBUTION, RecordChangeKind.UPDATED))
        assertEquals(0, patches.size)
    }
}
