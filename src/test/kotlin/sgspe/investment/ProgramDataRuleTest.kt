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
import sgspe.common.Values
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

// las reglas de los hijos del programa sin base (especificación de F5 §3.6): las de datos llegan por la regla y la de «mismo
// programa» lee la iniciativa como quien escribe
class ProgramDataRuleTest {
    private val program = UUID.randomUUID().toString()
    private val other = UUID.randomUUID().toString()
    private val initiative = UUID.randomUUID().toString()
    private var initiativeProgram: String = program
    private var initiativeDenied = false

    private val service: RecordService =
        mock(RecordService::class.java) { call ->
            if (call.method.name != "get") return@mock null
            if (initiativeDenied) throw ForbiddenException("sin lectura")
            RecordResponse(initiative, null, null, mapOf(Programs.PROGRAM to initiativeProgram))
        }

    // WriteView como la real: un alta es lo que manda; un cambio, lo que manda contra lo guardado
    @Suppress("UNCHECKED_CAST")
    private val view: WriteView =
        mock(WriteView::class.java) { call ->
            val change = call.arguments[0] as RecordWrite
            when (call.method.name) {
                "after" -> change.attributes.orEmpty()
                "changed" -> {
                    val after = call.arguments[1] as Map<String, Any?>
                    val before = change.before.orEmpty()
                    if (change.kind == RecordChangeKind.CREATED) {
                        after.filterValues { Values.text(it) != null }.keys
                    } else {
                        (after.keys + before.keys).filter { !Values.same(after[it], before[it]) }.toSet()
                    }
                }
                else -> null
            }
        }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> provider(value: T?): ObjectProvider<T> = mock(ObjectProvider::class.java) { value } as ObjectProvider<T>

    private val rule =
        ProgramDataRule(
            Records(
                provider(service),
                provider<RelatedRecordService>(null),
                provider<WorkflowService>(null),
                mock(MetadataService::class.java),
                mock(Callers::class.java),
                mock(ModelCatalog::class.java),
                mock(DerivedVersions::class.java)
            ),
            view
        )

    private fun codes(
        objectName: String,
        attributes: Map<String, Any?>,
        before: Map<String, Any?>? = null
    ): List<Pair<String, String?>> {
        val kind = if (before == null) RecordChangeKind.CREATED else RecordChangeKind.UPDATED
        val change = RecordWrite(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), objectName, UUID.randomUUID(), kind, before, attributes)
        return runBlocking { rule.findings(change, dryRun = true) }.map { it.code to it.field }
    }

    private val none = emptyList<Pair<String, String?>>()

    @Test
    fun `un grupo con una iniciativa de otro programa es PROGRAM_MISMATCH sobre social_initiative`() {
        val group = mapOf(Programs.PROGRAM to program, Programs.INITIATIVE to initiative, "name" to "Familias")
        assertEquals(none, codes(Programs.GROUP, group))
        initiativeProgram = other
        assertEquals(listOf("PROGRAM_MISMATCH" to "social_initiative"), codes(Programs.GROUP, group))
        assertEquals(none, codes(Programs.GROUP, mapOf(Programs.PROGRAM to program, "name" to "Sin iniciativa")))
    }

    // la entrada 4 del Review Focus
    @Test
    fun `cambiar el programa de un grupo tambien se juzga contra su iniciativa`() {
        val before = mapOf<String, Any?>(Programs.PROGRAM to program, Programs.INITIATIVE to initiative, "name" to "Familias")
        assertEquals(listOf("PROGRAM_MISMATCH" to "social_initiative"), codes(Programs.GROUP, before + (Programs.PROGRAM to other), before))
        // un cambio que no toca el programa ni la iniciativa no la lee
        initiativeDenied = true
        assertEquals(none, codes(Programs.GROUP, before + ("name" to "Familias usuarias"), before))
    }

    @Test
    fun `una iniciativa que quien escribe no lee es NotReadable`() {
        initiativeDenied = true
        val group = mapOf(Programs.PROGRAM to program, Programs.INITIATIVE to initiative)
        assertEquals(Programs.INITIATIVE, assertThrows<NotReadable> { codes(Programs.GROUP, group) }.objectName)
    }

    @Test
    fun `las reglas de datos llegan por la regla en el alta y en el cambio`() {
        assertEquals(listOf("OUT_OF_RANGE" to "budget_year"), codes(Programs.BUDGET, mapOf(Programs.PROGRAM to program, "budget_year" to 1999)))
        val measurement = mapOf<String, Any?>("period_start_date" to "2026-04-01", "period_end_date" to "2026-04-30")
        assertEquals(
            listOf("OUT_OF_RANGE" to "period_end_date"),
            codes(Programs.MEASUREMENT, measurement + ("period_end_date" to "2026-03-31"))
        )
        val group = mapOf<String, Any?>(Programs.PROGRAM to program, "target_count" to 10)
        assertEquals(listOf("OUT_OF_RANGE" to "target_count"), codes(Programs.GROUP, group + ("target_count" to -3), group))
    }

    @Test
    fun `un vinculo, una transicion y otro objeto no se juzgan`() {
        initiativeDenied = true
        val link =
            RecordWrite(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                Programs.PROGRAM,
                UUID.randomUUID(),
                RecordChangeKind.UPDATED,
                emptyMap(),
                mapOf("rel:evidence_social_program" to "x")
            )
        assertEquals(emptyList<Any>(), runBlocking { rule.findings(link, dryRun = true) })
        assertEquals(none, codes("commitment", mapOf("start_date" to "2026-06-01", "end_date" to "2026-01-01")))
    }
}
