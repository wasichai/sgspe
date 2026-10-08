package sgspe.stakeholder

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import sgspe.engagement.EngagementWriteGuard
import java.time.LocalDate

// JOB-03: last_contact_date y la regla de primer contacto (ASM-032), sin tocar un estado que una persona cambió
class FollowUpTest {
    private val may = LocalDate.parse("2026-05-12")

    @Test
    fun `el primer contacto pasa de SIN CONTACTO o vacio a CONTACTO ESTABLECIDO`() {
        assertEquals(
            mapOf("last_contact_date" to "2026-05-12", "relationship_status" to "CONTACTO ESTABLECIDO"),
            FollowUp.changes(mapOf("relationship_status" to "SIN CONTACTO"), may)
        )
        assertEquals("CONTACTO ESTABLECIDO", FollowUp.changes(mapOf("relationship_status" to null), may)["relationship_status"])
    }

    @Test
    fun `un estado que cambio una persona no se toca, y sin cambio no se escribe nada`() {
        assertEquals(mapOf("last_contact_date" to "2026-05-12"), FollowUp.changes(mapOf("relationship_status" to "RELACION DETERIORADA"), may))
        assertEquals(
            mapOf("last_contact_date" to "2026-05-12"),
            FollowUp.changes(
                mapOf(
                    "last_contact_date" to "2026-04-01",
                    "relationship_status" to "SIN CONTACTO"
                ),
                may
            )
        )
        assertEquals(emptyMap<String, Any?>(), FollowUp.changes(mapOf("last_contact_date" to "2026-05-12"), may))
    }

    @Test
    fun `la fecha es la de Lima`() {
        assertEquals(LocalDate.parse("2026-05-11"), FollowUp.dateOf("2026-05-12T04:59:00Z"))
        assertEquals(LocalDate.parse("2026-05-12"), FollowUp.dateOf("2026-05-12T05:00:00Z"))
    }

    @Test
    fun `la fecha se lee con desfase o como instante, y vacia es nula`() {
        assertEquals(LocalDate.parse("2026-05-11"), FollowUp.dateOf("2026-05-12T04:59:00+00:00"))
        assertEquals(LocalDate.parse("2026-05-11"), FollowUp.dateOf("2026-05-11T23:30:00-05:00"))
        assertEquals(LocalDate.parse("2026-05-12"), FollowUp.dateOf("2026-05-12T10:00:00+02:00"))
        assertEquals(LocalDate.parse("2026-05-12"), FollowUp.dateOf(java.time.Instant.parse("2026-05-12T05:00:00Z")))
        assertEquals(null, FollowUp.dateOf(null))
        assertEquals(null, FollowUp.dateOf(" "))
    }

    @Test
    fun `una interaccion realizada son los estados de la guarda de interacciones`() {
        assertTrue("held" in EngagementWriteGuard.HELD_STATES && "planned" !in EngagementWriteGuard.HELD_STATES)
    }

    @Test
    fun `de varias interacciones queda la fecha mas reciente de cada grupo de interes`() {
        val contacts =
            FollowUp.latest(
                listOf(
                    LocalDate.parse("2026-05-01") to listOf("a", "b"),
                    LocalDate.parse("2026-05-12") to listOf("a"),
                    LocalDate.parse("2026-04-20") to listOf("a", "c")
                )
            )
        assertEquals(mapOf("a" to may, "b" to LocalDate.parse("2026-05-01"), "c" to LocalDate.parse("2026-04-20")), contacts)
    }

    @Test
    fun `el motivo nombra al trabajo`() {
        assertEquals("último contacto (JOB-03 StakeholderFollowUpJob)", StakeholderFollowUpJob.REASON)
    }
}
