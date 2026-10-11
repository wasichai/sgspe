package sgspe.investment

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import org.springframework.beans.factory.ObjectProvider
import sgspe.common.Callers
import sgspe.common.DerivedVersions
import sgspe.common.ModelCatalog
import sgspe.common.NotReadable
import sgspe.common.Records
import sgspe.common.Transactions
import sgspe.common.WriteView
import wasichai.core.common.ForbiddenException
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordResponse
import wasichai.core.data.RecordService
import wasichai.core.data.RecordWrite
import wasichai.core.data.RelatedRecordService
import wasichai.core.metadata.MetadataService
import wasichai.workflow.WorkflowService
import java.util.UUID
import kotlin.coroutines.Continuation

// el comprobante de un aporte sin base (decisión del usuario del 2026-10-11): alta, cambio y baja se juzgan contra el estado
// del aporte, leído como quien escribe y, al escribir, con el candado del aporte
class ContributionReceiptRuleTest {
    private val first = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val second = UUID.fromString("00000000-0000-0000-0000-000000000002")
    private val states = mutableMapOf(first to "procured", second to "procured")
    private val reads = mutableListOf<UUID>()
    private var denied = false

    private val service: RecordService =
        mock(RecordService::class.java) { call ->
            if (call.method.name != "get") return@mock null
            if (denied) throw ForbiddenException("sin lectura")
            val id = call.arguments[1] as UUID
            reads += id
            RecordResponse(id.toString(), null, null, emptyMap(), states[id])
        }

    // WriteView como la real en lo que mira la regla: un alta y un cambio son lo que mandan; una baja, lo guardado
    private val view: WriteView =
        mock(WriteView::class.java) { call ->
            val change = call.arguments[0] as RecordWrite
            if (call.method.name != "after") return@mock null
            if (change.kind == RecordChangeKind.DELETED) change.before.orEmpty() else change.attributes.orEmpty()
        }

    // los candados que se toman, en orden; el bloque corre dentro (Mockito quita la Continuation de `arguments` en una función
    // suspend: está en `rawArguments`)
    private val locked = mutableListOf<String>()

    @Suppress("UNCHECKED_CAST")
    private val transactions: Transactions =
        mock(Transactions::class.java) { call ->
            if (call.method.name != "locked") return@mock null
            locked += call.arguments[0] as String
            (call.arguments[1] as (Continuation<Any?>) -> Any?).invoke(call.rawArguments[2] as Continuation<Any?>)
        }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> provider(value: T?): ObjectProvider<T> = mock(ObjectProvider::class.java) { value } as ObjectProvider<T>

    private val rule =
        ContributionReceiptRule(
            Records(
                provider(service),
                provider<RelatedRecordService>(null),
                provider<WorkflowService>(null),
                mock(MetadataService::class.java),
                mock(Callers::class.java),
                mock(ModelCatalog::class.java),
                mock(DerivedVersions::class.java)
            ),
            view,
            transactions
        )

    private fun codes(
        kind: RecordChangeKind,
        after: UUID?,
        before: UUID? = null,
        objectName: String = Contributions.RECEIPT,
        dryRun: Boolean = false
    ): List<Pair<String, String?>> {
        val beforeRow = before?.let { mapOf(Contributions.CONTRIBUTION to it.toString(), "amount" to "100") }
        val afterRow = after?.let { mapOf(Contributions.CONTRIBUTION to it.toString(), "amount" to "120") }
        val change = RecordWrite(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), objectName, UUID.randomUUID(), kind, beforeRow, afterRow)
        return runBlocking { rule.findings(change, dryRun) }.map { it.code to it.field }
    }

    private val frozen = listOf("DELIVERY_FROZEN" to "social_contribution")
    private val none = emptyList<Pair<String, String?>>()

    @Test
    fun `el alta, el cambio y la baja pasan antes de delivered y se rechazan desde delivered, con el candado del aporte`() {
        for (state in listOf("requested", "approved", "procured")) {
            states[first] = state
            assertEquals(none, codes(RecordChangeKind.CREATED, first), state)
            assertEquals(none, codes(RecordChangeKind.UPDATED, first, first), state)
            assertEquals(none, codes(RecordChangeKind.DELETED, null, first), state)
        }
        for (state in listOf("delivered", "closed", "rejected", "cancelled")) {
            states[first] = state
            assertEquals(frozen, codes(RecordChangeKind.CREATED, first), state)
            assertEquals(frozen, codes(RecordChangeKind.UPDATED, first, first), state)
            assertEquals(frozen, codes(RecordChangeKind.DELETED, null, first), state)
        }
        assertEquals(List(21) { Contributions.lock(first) }, locked)
    }

    @Test
    fun `mover el comprobante se juzga contra los dos aportes, en orden ascendente`() {
        states[first] = "delivered"
        assertEquals(frozen, codes(RecordChangeKind.UPDATED, after = first, before = second))
        assertEquals(listOf(Contributions.lock(first), Contributions.lock(second)), locked)
        assertEquals(listOf(first, second), reads)
        locked.clear()
        reads.clear()
        states[first] = "procured"
        states[second] = "closed"
        assertEquals(frozen, codes(RecordChangeKind.UPDATED, after = first, before = second))
        assertEquals(listOf(Contributions.lock(first), Contributions.lock(second)), locked)
        states[second] = "approved"
        assertEquals(none, codes(RecordChangeKind.UPDATED, after = second, before = first))
    }

    @Test
    fun `la consulta en seco lee el estado sin candado`() {
        states[first] = "delivered"
        assertEquals(frozen, codes(RecordChangeKind.CREATED, first, dryRun = true))
        assertEquals(frozen, codes(RecordChangeKind.UPDATED, after = second, before = first, dryRun = true))
        assertEquals(emptyList<String>(), locked)
        assertEquals(listOf(first, first, second), reads)
    }

    @Test
    fun `un aporte que quien escribe no lee es NotReadable`() {
        denied = true
        assertEquals(Contributions.CONTRIBUTION, assertThrows<NotReadable> { codes(RecordChangeKind.CREATED, first) }.objectName)
        assertEquals(Contributions.CONTRIBUTION, assertThrows<NotReadable> { codes(RecordChangeKind.DELETED, null, first, dryRun = true) }.objectName)
    }

    @Test
    fun `otros objetos y un comprobante sin aporte no se miran`() {
        states[first] = "delivered"
        assertEquals(none, codes(RecordChangeKind.CREATED, first, objectName = Contributions.ITEM))
        assertEquals(none, codes(RecordChangeKind.CREATED, null))
        assertEquals(emptyList<String>(), locked)
    }

    @Test
    fun `la guarda mira los comprobantes`() {
        assertEquals(true, Contributions.RECEIPT in InvestmentWriteGuard.OBJECTS)
        assertEquals("receipt", Contributions.RECEIPT)
    }
}
