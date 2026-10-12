package sgspe.territory

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import wasichai.core.data.RecordResponse
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

// los titulares de un predio son los derechos vigentes hoy en Lima, sin repetidos (ruling de la ola final del plan 6.2a)
class CurrentHoldersTest {
    private val today = LocalDate.parse("2026-10-12")

    private fun right(
        id: String,
        person: String? = null,
        organization: String? = null,
        end: String? = null
    ) = RecordResponse(id, null, null, mapOf("holder_person" to person, "holder_organization" to organization, "end_date" to end))

    @Test
    fun `solo los derechos sin fin o que terminan hoy o despues, sin repetir al titular`() {
        val holders =
            ProjectLandQueries.currentHolders(
                listOf(
                    right("r1", person = "p1"),
                    right("r2", person = "p1", end = "2026-10-12"),
                    right("r3", person = "p2", end = "2026-10-11"),
                    right("r4", organization = "o1", end = "2027-01-01"),
                    right("r5", person = "p3", organization = "o1")
                ),
                today
            )
        assertEquals(listOf("person" to "p1", "organization" to "o1", "person" to "p3"), holders)
    }

    @Test
    fun `hoy es la fecha de Lima aunque en UTC ya sea el dia siguiente`() {
        val clock = Clock.fixed(Instant.parse("2026-10-12T03:00:00Z"), ZoneOffset.UTC)
        assertEquals(LocalDate.parse("2026-10-11"), ProjectLandQueries.today(clock))
    }
}
