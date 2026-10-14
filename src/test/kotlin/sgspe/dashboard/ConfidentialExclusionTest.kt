package sgspe.dashboard

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import sgspe.common.Records
import wasichai.core.data.RecordResponse
import java.time.LocalDate
import java.util.UUID

// decisión 25 (R4): la cifra oficial de un KPI que lee QCS se calcula siempre sin las confidenciales, para que nunca revele
// cuántas se excluyen a quien la lea; sin la marca, DashboardData las cuenta como siempre (lo que quien mira lee)
class ConfidentialExclusionTest {
    private val records: Records =
        mock(Records::class.java) { call ->
            if (call.method.name != "all") return@mock null
            if (call.arguments[0] != "grievance") return@mock emptyList<RecordResponse>()
            listOf(
                RecordResponse(UUID.randomUUID().toString(), null, null, mapOf("received_at" to "2026-09-05T15:00:00Z", "confidential" to true), "received"),
                RecordResponse(UUID.randomUUID().toString(), null, null, mapOf("received_at" to "2026-09-06T15:00:00Z", "confidential" to false), "received")
            )
        }

    private fun data(excludeConfidential: Boolean) =
        DashboardData(
            records,
            DashboardFilters.of(listOf(UUID.randomUUID()), LocalDate.parse("2026-09-01"), LocalDate.parse("2026-09-30")),
            { true },
            LocalDate.parse("2026-10-13"),
            excludeConfidential = excludeConfidential
        )

    @Test
    fun `con la marca, las QCS confidenciales no se leen para la cifra`() {
        assertEquals(2, runBlocking { data(false).attention() }.size)
        val kept = runBlocking { data(true).attention() }
        assertEquals(listOf(false), kept.map { it.confidential })
    }
}
