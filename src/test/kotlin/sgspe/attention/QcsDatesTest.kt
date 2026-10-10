package sgspe.attention

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import sgspe.common.HolidayCalendar
import sgspe.common.ModelCatalog
import java.time.LocalDate

// los plazos y las fechas de transición de las QCS (ASM-096, ASM-100, dashboards.md §12), sin base
class QcsDatesTest {
    private val calendar = HolidayCalendar.of(mapOf("2026" to listOf(LocalDate.parse("2026-10-08"))))
    private val today = LocalDate.parse("2026-10-20")

    @Test
    fun `la fecha de recepcion es la de Lima`() {
        // las 02:00 UTC del 6 son las 21:00 del 5 en Lima
        assertEquals(LocalDate.parse("2026-10-05"), QcsDueDates.receivedDate("2026-10-06T02:00:00Z"))
        assertEquals(LocalDate.parse("2026-10-06"), QcsDueDates.receivedDate("2026-10-06T05:00:00Z"))
    }

    @Test
    fun `una queja mide el acuse en dias habiles y la resolucion en dias calendario`() {
        // recibida el lunes 5 de octubre de 2026; el jueves 8 es feriado (Combate de Angamos): el quinto día hábil es el 13
        val dates = QcsDueDates.of("grievance", "2026-10-05T15:00:00Z", emptyMap(), calendar)
        assertEquals(mapOf("ack_due_date" to "2026-10-13", "resolution_due_date" to "2026-11-04"), dates)
        val custom = QcsDueDates.of("grievance", "2026-10-05T15:00:00Z", mapOf("grievance_ack_days" to 2, "grievance_resolution_days" to 10), calendar)
        assertEquals(mapOf("ack_due_date" to "2026-10-07", "resolution_due_date" to "2026-10-15"), custom)
    }

    @Test
    fun `una consulta mide la respuesta y una solicitud no tiene plazo`() {
        assertEquals(mapOf("response_due_date" to "2026-10-20"), QcsDueDates.of("inquiry", "2026-10-05T15:00:00Z", emptyMap(), calendar))
        assertEquals(emptyMap<String, String>(), QcsDueDates.of("stakeholder_request", "2026-10-05T15:00:00Z", emptyMap(), calendar))
        assertEquals(emptyMap<String, String>(), QcsDueDates.of("inquiry", null, emptyMap(), calendar))
    }

    @Test
    fun `un plazo guardado no se pisa`() {
        val computed = mapOf("ack_due_date" to "2026-10-13", "resolution_due_date" to "2026-11-04")
        assertEquals(mapOf("resolution_due_date" to "2026-11-04"), QcsDueDates.missing(mapOf("ack_due_date" to "2026-10-12"), computed))
    }

    @Test
    fun `resolver, reabrir y cerrar escriben sus fechas`() {
        assertEquals(mapOf("resolved_date" to "2026-10-20"), QcsTransitionDates.changes("grievance", "resolve", "resolved", today))
        assertEquals(mapOf("resolved_date" to null), QcsTransitionDates.changes("grievance", "reopen", "in_assessment", today))
        assertEquals(mapOf("closed_date" to "2026-10-20"), QcsTransitionDates.changes("grievance", "withdraw_received", "withdrawn", today))
        assertEquals(mapOf("closed_date" to "2026-10-20"), QcsTransitionDates.changes("stakeholder_request", "close_declined", "closed", today))
        assertEquals(emptyMap<String, Any?>(), QcsTransitionDates.changes("stakeholder_request", "decline", "declined", today))
    }

    @Test
    fun `los plazos y las fechas son derivados del modelo`() {
        val catalog = ModelCatalog.fromClasspath()
        for ((objectName, fields) in QcsDueDates.FIELDS) {
            assertEquals(emptyList<String>(), fields.filter { it !in catalog.derivedFields(objectName) }, objectName)
            assertEquals(true, QcsTransitionDates.CLOSED in catalog.derivedFields(objectName), objectName)
        }
        assertEquals(true, QcsTransitionDates.RESOLVED in catalog.derivedFields("grievance"))
    }
}
