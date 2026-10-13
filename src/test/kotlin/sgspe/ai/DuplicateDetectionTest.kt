package sgspe.ai

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import org.springframework.beans.factory.ObjectProvider
import org.springframework.http.HttpStatus
import sgspe.common.Callers
import sgspe.common.DerivedVersions
import sgspe.common.ModelCatalog
import sgspe.common.Records
import wasichai.core.common.PageResponse
import wasichai.core.common.WasichaiException
import wasichai.core.data.RecordQuery
import wasichai.core.data.RecordResponse
import wasichai.core.data.RecordService
import wasichai.core.data.RelatedRecordService
import wasichai.core.metadata.MetadataService
import wasichai.workflow.WorkflowService
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

// la lectura de AI-07 sobre un RecordService de prueba (ai-architecture.md §4.1, decisión 11 del plan 7.2c): dos quejas iguales
// del mismo proyecto. las sugerencias ya propuestas se leen por target_record_id de los registros leídos, con un tope de
// KNOWN_PER_RECORD por cada uno de max-records; pasar el tope con los registros que se comparan es 400 TOO_MANY_RECORDS sobre target; los veredictos que no valen
// se cuentan como descartados
class DuplicateDetectionTest {
    private val project = UUID.fromString("00000000-0000-0000-0000-0000000000b1")
    private val first = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val second = UUID.fromString("00000000-0000-0000-0000-000000000002")
    private val queries = mutableListOf<Pair<String, RecordQuery>>()

    private fun grievance(
        id: UUID,
        received: String
    ) = RecordResponse(
        id.toString(),
        Instant.parse(received),
        Instant.parse(received),
        mapOf(
            "project" to project.toString(),
            "description" to "El agua del canal de riego del anexo norte llega turbia desde la perforación.",
            "received_at" to received
        ),
        "received",
        emptyMap()
    )

    private val grievances = listOf(grievance(first, "2026-10-01T15:00:00Z"), grievance(second, "2026-10-03T15:00:00Z"))

    private fun suggestion(
        target: UUID,
        other: UUID
    ) = RecordResponse(
        UUID.randomUUID().toString(),
        Instant.EPOCH,
        Instant.EPOCH,
        mapOf(
            "suggestion_kind" to "DUPLICADO",
            "target_object" to "grievance",
            "target_record_id" to target.toString(),
            "suggested_value" to """{"other_record_id":"$other","reasons":["Textos parecidos"]}"""
        ),
        "rejected",
        emptyMap()
    )

    private fun records(suggestions: List<RecordResponse>): Records {
        val service: RecordService =
            mock(RecordService::class.java) { invocation ->
                val objectName = invocation.arguments[0] as String
                when (invocation.method.name) {
                    "get" -> grievances.single { it.id == invocation.arguments[1].toString() }
                    "list" -> {
                        val query = invocation.arguments[1] as RecordQuery
                        queries += objectName to query
                        val rows = if (objectName == DuplicateDetection.SUGGESTION) suggestions else grievances
                        PageResponse.of(rows, 0, rows.size, null, null)
                    }
                    else -> null
                }
            }
        return Records(
            provider(service),
            provider<RelatedRecordService>(null),
            provider<WorkflowService>(null),
            mock(MetadataService::class.java),
            mock(Callers::class.java),
            mock(ModelCatalog::class.java),
            mock(DerivedVersions::class.java)
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> provider(value: T?): ObjectProvider<T> = mock(ObjectProvider::class.java) { value } as ObjectProvider<T>

    private val ai = AiProperties(models = mapOf("ai-07" to "modelo-de-prueba"), prompts = mapOf("ai-07" to "1"))

    private fun detection(
        records: Records,
        model: StructuredModel,
        maxRecords: Int
    ) = DuplicateDetection(
        records,
        ModelCalls(model, Prompts(ai), RecordedInteractions(), ai, Clock.fixed(Instant.parse("2026-10-13T15:00:00Z"), ZoneOffset.UTC)),
        DuplicateProperties(maxRecords = maxRecords),
        ai
    )

    private val unreachable = FakeModel { error("sin pares no se llama al modelo") }

    private fun detect(
        detection: DuplicateDetection,
        budget: RunBudget = RunBudget(8)
    ) = runBlocking { detection.detect(DuplicateTarget("grievance", second), NameDictionary(emptyList()), budget) }

    @Test
    fun `las sugerencias ya propuestas se leen por target_record_id, con un tope mayor que el de max-records`() {
        // dos registros que comparar (el tope justo) y cinco sugerencias DUPLICADO (más que max-records, menos que su tope): una es
        // la del par, ya rechazada
        val known = listOf(suggestion(second, first)) + (1..4).map { suggestion(UUID.randomUUID(), UUID.randomUUID()) }
        val findings = detect(detection(records(known), unreachable, maxRecords = 2))
        assertNull(findings.interaction)
        assertEquals(emptyList<DuplicateProposal>(), findings.proposals)
        val (_, query) = queries.single { it.first == DuplicateDetection.SUGGESTION }
        assertEquals(mapOf("suggestion_kind" to "DUPLICADO", "target_object" to "grievance"), query.filters)
        assertEquals(1, query.criteria.size)
    }

    @Test
    fun `mas sugerencias DUPLICADO que su tope tambien es 400 TOO_MANY_RECORDS sobre target, sin llamar al modelo`() {
        val many = (1..2 * DuplicateDetection.KNOWN_PER_RECORD + 1).map { suggestion(UUID.randomUUID(), UUID.randomUUID()) }
        val error = assertThrows<WasichaiException> { detect(detection(records(many), unreachable, maxRecords = 2)) }
        assertEquals("TOO_MANY_RECORDS" to "target", error.violations.single().let { it.message to it.field })
    }

    @Test
    fun `mas registros que max-records es 400 TOO_MANY_RECORDS sobre target, sin llamar al modelo`() {
        val error = assertThrows<WasichaiException> { detect(detection(records(emptyList()), unreachable, maxRecords = 1)) }
        assertEquals(HttpStatus.BAD_REQUEST.value(), error.status.value())
        assertEquals("TOO_MANY_RECORDS" to "target", error.violations.single().let { it.message to it.field })
    }

    @Test
    fun `los veredictos que no valen cuentan como descartados y la llamada gasta una del tope`() {
        val verdicts =
            DuplicateVerdicts(
                listOf(
                    DuplicateVerdict(1, true, 1.5),
                    DuplicateVerdict(1, true, 0.9),
                    DuplicateVerdict(9, true, 0.8),
                    DuplicateVerdict(1, false, 0.1)
                )
            )
        val budget = RunBudget(8)
        val findings = detect(detection(records(emptyList()), FakeModel { verdicts }, maxRecords = 10), budget)
        assertEquals(listOf(second to first), findings.proposals.map { it.targetRecordId to it.otherRecordId })
        assertEquals(2, findings.discarded)
        assertEquals(1, budget.spent)
    }
}
