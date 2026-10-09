package sgspe.commitment

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

// las fechas de transición de un compromiso (domain-model §7.4, dashboards.md §12)
class TransitionDatesTest {
    private val today = LocalDate.parse("2026-10-09")

    @Test
    fun `cumplir, cerrar y cancelar fechan, reabrir vacia fulfilled_date, lo demas no escribe`() {
        assertEquals(mapOf("fulfilled_date" to "2026-10-09"), TransitionDates.changes("register_fulfillment", today))
        assertEquals(mapOf("fulfilled_date" to null), TransitionDates.changes("reopen_execution", today))
        assertEquals(mapOf("closed_date" to "2026-10-09"), TransitionDates.changes("close", today))
        for (cancel in listOf("cancel_draft", "cancel_pending_approval", "cancel_approved", "cancel_in_progress", "cancel_blocked")) {
            assertEquals(mapOf("cancelled_date" to "2026-10-09"), TransitionDates.changes(cancel, today), cancel)
        }
        assertEquals(emptyMap<String, Any?>(), TransitionDates.changes("start", today))
        assertEquals(emptyMap<String, Any?>(), TransitionDates.changes(null, today))
    }
}
