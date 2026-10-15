package sgspe.deliverable

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

// especificación de F8 §2.2.17, domain-model §7.4: qué toma JOB-05
class DraftQueueTest {
    private val now = Instant.parse("2026-10-15T15:00:00Z")
    private val stale = Duration.ofMinutes(15)

    private fun pending(
        status: String?,
        requested: String?,
        generated: String?,
        updated: Instant = now
    ) = DraftQueue.pending(
        mapOf(Deliverables.STATUS to status, Deliverables.REQUESTED_AT to requested, Deliverables.GENERATED_AT to generated),
        updated,
        now,
        stale
    )

    @Test
    fun `un pedido sin borrador o posterior al borrador esta pendiente`() {
        assertTrue(pending(null, "2026-10-15T14:59:00Z", null))
        assertTrue(pending(Deliverables.READY, "2026-10-15T14:59:00Z", "2026-10-15T14:00:00Z"))
        assertTrue(pending(Deliverables.FAILED, "2026-10-15T14:59:00Z", "2026-10-15T14:00:00Z"))
        assertFalse(pending(Deliverables.READY, "2026-10-15T13:59:00Z", "2026-10-15T14:00:00Z"))
        assertFalse(pending(null, null, null))
    }

    @Test
    fun `un FALLIDO sin pedido nuevo no se retoma`() {
        assertFalse(pending(Deliverables.FAILED, "2026-10-15T13:59:00Z", "2026-10-15T14:00:00Z"))
    }

    @Test
    fun `uno EN COLA o EN PROCESO solo se retoma pasado stale-after`() {
        assertFalse(pending(Deliverables.QUEUED, "2026-10-15T14:59:00Z", null, updated = now.minusSeconds(60)))
        assertTrue(pending(Deliverables.QUEUED, "2026-10-15T14:00:00Z", null, updated = now.minus(Duration.ofMinutes(16))))
        assertFalse(pending(Deliverables.RUNNING, "2026-10-15T14:50:00Z", "2026-10-15T14:55:00Z"))
        assertTrue(pending(Deliverables.RUNNING, "2026-10-15T14:00:00Z", "2026-10-15T14:40:00Z"))
    }
}
