package sgspe.dashboard

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import sgspe.common.Records
import wasichai.core.common.WasichaiException
import wasichai.core.data.RecordRow
import java.time.LocalDate
import java.util.UUID

// Crítico 1 de la revisión del plan 8.2a: con el lector de la plataforma, las relaciones MANY_TO_MANY se leen por
// Records.relatedAsPlatform (Records.related exige un usuario) y el tope de interacciones sigue igual; con la persona, por
// Records.related
class DashboardDataPlatformTest {
    private val organization = UUID.randomUUID()
    private val engagement = UUID.randomUUID()
    private val stakeholder = UUID.randomUUID()
    private val calls = mutableListOf<String>()

    private val records: Records =
        mock(Records::class.java) { call ->
            calls += call.method.name
            when (call.method.name) {
                "relatedAsPlatform" -> listOf(RecordRow(stakeholder, null, null, emptyMap()))
                "related" -> emptyList<Any>()
                else -> null
            }
        }

    private fun data(
        platform: UUID?,
        maxRelated: Int = 200
    ) = DashboardData(
        records,
        DashboardFilters.of(listOf(UUID.randomUUID()), LocalDate.parse("2026-09-01"), LocalDate.parse("2026-09-30")),
        { true },
        LocalDate.parse("2026-10-13"),
        maxRelated = maxRelated,
        platform = platform
    )

    @Test
    fun `la plataforma lee las M2M sin usuario y la persona con el suyo`() {
        val linked = runBlocking { data(organization).stakeholdersOf(listOf(engagement.toString())) }
        assertEquals(mapOf(engagement.toString() to listOf(stakeholder.toString())), linked)
        assertEquals(listOf("relatedAsPlatform"), calls)
        calls.clear()
        runBlocking { data(null).stakeholdersOf(listOf(engagement.toString())) }
        assertEquals(listOf("related"), calls.filter { it.startsWith("related") })
    }

    @Test
    fun `el tope de interacciones vale tambien para la plataforma`() {
        assertThrows<WasichaiException> {
            runBlocking { data(organization, maxRelated = 1).stakeholdersOf(listOf(UUID.randomUUID().toString(), UUID.randomUUID().toString())) }
        }
        assertEquals(emptyList<String>(), calls.filter { it.startsWith("related") })
    }
}
