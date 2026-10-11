package sgspe.investment

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import wasichai.core.data.RecordChange
import wasichai.core.data.RecordChangeKind
import java.util.UUID

// ProgramTotalsListener sin base: a qué programas recalcula cada escritura (decisión 6 del plan 5.2b) y que un fallo del
// escritor no rompe la escritura que lo disparó
class ProgramTotalsListenerTest {
    private val p1 = UUID.randomUUID()
    private val p2 = UUID.randomUUID()
    private val refreshed = mutableListOf<UUID>()
    private var failing = false

    private val writer: ProgramTotalsWriter =
        mock(ProgramTotalsWriter::class.java) { call ->
            if (call.method.name != "refresh") return@mock null
            refreshed += call.arguments[0] as UUID
            if (failing) throw IllegalStateException("sin escritura")
            true
        }
    private val listener = ProgramTotalsListener(writer)

    private fun changed(
        objectName: String,
        kind: RecordChangeKind,
        before: Map<String, Any?>?,
        after: Map<String, Any?>?,
        id: UUID = UUID.randomUUID()
    ): List<UUID> {
        refreshed.clear()
        runBlocking {
            listener.recordChanged(RecordChange(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), objectName, id, kind, before, after))
        }
        return refreshed.toList()
    }

    private fun budget(program: UUID) = mapOf<String, Any?>("social_program" to program.toString(), "allocated_amount" to 1000, "currency" to "PEN")

    @Test
    fun `el alta y la baja de un presupuesto recalculan su programa`() {
        assertEquals(listOf(p1), changed(Programs.BUDGET, RecordChangeKind.CREATED, null, budget(p1)))
        assertEquals(listOf(p1), changed(Programs.BUDGET, RecordChangeKind.DELETED, budget(p1), null))
    }

    // la entrada 2 del Review Focus
    @Test
    fun `mover un presupuesto recalcula el programa de origen y el de destino`() {
        assertEquals(setOf(p1, p2), changed(Programs.BUDGET, RecordChangeKind.UPDATED, budget(p1), budget(p2)).toSet())
    }

    @Test
    fun `un cambio del ejecutado no recalcula nada`() {
        assertEquals(emptyList<UUID>(), changed(Programs.BUDGET, RecordChangeKind.UPDATED, budget(p1), budget(p1) + ("executed_amount" to 400)))
    }

    // la entrada 1 del Review Focus
    @Test
    fun `cambiar la moneda del programa lo recalcula y cambiar su nombre no`() {
        val program = mapOf<String, Any?>("currency" to "PEN", "name" to "Programa Ejemplo")
        assertEquals(listOf(p1), changed(Programs.PROGRAM, RecordChangeKind.UPDATED, program, program + ("currency" to "USD"), p1))
        assertEquals(emptyList<UUID>(), changed(Programs.PROGRAM, RecordChangeKind.UPDATED, program, program + ("name" to "Otro"), p1))
        assertEquals(listOf(p1), changed(Programs.PROGRAM, RecordChangeKind.CREATED, null, program, p1))
        assertEquals(emptyList<UUID>(), changed(Programs.PROGRAM, RecordChangeKind.TRANSITIONED, program, program, p1))
    }

    @Test
    fun `un escritor que falla no rompe la escritura`() {
        failing = true
        assertEquals(setOf(p1, p2), changed(Programs.BUDGET, RecordChangeKind.UPDATED, budget(p1), budget(p2)).toSet())
    }
}
