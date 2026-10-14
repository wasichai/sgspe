package sgspe.dashboard

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import sgspe.common.WriteView
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordWrite
import java.util.UUID

// dashboards.md §10: el código de un indicador es el ID de su KPI y no cambia (400 FIELD_NOT_EDITABLE sobre code); la meta,
// la frecuencia y los textos sí
class IndicatorWriteGuardTest {
    // WriteView como la real en lo que mira la guarda: un cambio es lo que manda quien escribe
    private val view: WriteView =
        mock(WriteView::class.java) { call ->
            if (call.method.name != "after") return@mock null
            (call.arguments[0] as RecordWrite).attributes.orEmpty()
        }
    private val guard = IndicatorWriteGuard(view)

    private fun findings(
        objectName: String,
        kind: RecordChangeKind,
        before: Map<String, Any?>?,
        after: Map<String, Any?>
    ) = runBlocking {
        guard.findings(RecordWrite(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), objectName, UUID.randomUUID(), kind, before, after))
    }

    @Test
    fun `cambiar el codigo es 400 FIELD_NOT_EDITABLE y cambiar la meta pasa`() {
        val before = mapOf("code" to "KPI-ENG-02", "name" to "Reuniones", "target_value" to 2)
        val code = findings("indicator", RecordChangeKind.UPDATED, before, before + ("code" to "KPI-ENG-99")).single()
        assertEquals(Triple("FIELD_NOT_EDITABLE", "code", 400), Triple(code.code, code.field, code.status.value()))
        assertEquals(emptyList<Any>(), findings("indicator", RecordChangeKind.UPDATED, before, before + ("target_value" to 3)))
    }

    @Test
    fun `el alta, otro objeto y un vinculo no se juzgan`() {
        val created = mapOf("code" to "KPI-XYZ-01", "name" to "Propio")
        assertEquals(emptyList<Any>(), findings("indicator", RecordChangeKind.CREATED, null, created))
        assertEquals(emptyList<Any>(), findings("topic", RecordChangeKind.UPDATED, mapOf("code" to "A"), mapOf("code" to "B")))
        assertEquals(emptyList<Any>(), findings("indicator", RecordChangeKind.UPDATED, mapOf("code" to "KPI-ENG-02"), mapOf("rel:x" to "y")))
    }
}
