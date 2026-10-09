package sgspe.deadline

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import sgspe.commitment.Commitments
import sgspe.privacy.MatrixSheets
import java.time.LocalDate
import java.util.UUID

// JOB-01: situación de plazo y de vigencia y niveles de escalamiento (RN-WFL-001, ASM-006, ASM-030), sin base
class DeadlinesTest {
    private val today = LocalDate.parse("2026-10-09")
    private val commitment = Deadlines.TRACKED.first { it.objectName == "commitment" }
    private val agreement = Deadlines.TRACKED.first { it.objectName == "agreement" }

    @Test
    fun `al dia, por vencer (hoy incluido) y vencido`() {
        assertEquals("AL DIA", Deadlines.status(today.plusDays(8), today, 7))
        assertEquals("POR VENCER", Deadlines.status(today.plusDays(7), today, 7))
        assertEquals("POR VENCER", Deadlines.status(today, today, 7))
        assertEquals("VENCIDO", Deadlines.status(today.minusDays(1), today, 7))
        assertNull(Deadlines.status(null, today, 7))
    }

    @Test
    fun `solo se calcula en los estados de su tabla`() {
        val due = mapOf("due_date" to today.minusDays(3).toString())
        assertEquals("VENCIDO", Deadlines.wanted(commitment, "in_progress", due, today, 7))
        assertNull(Deadlines.wanted(commitment, "pending_approval", due, today, 7))
        assertNull(Deadlines.wanted(commitment, "fulfilled", due, today, 7))
    }

    @Test
    fun `la vigencia de un acuerdo, sin fin, VIGENTE, vencida, VENCIDO`() {
        assertEquals("VIGENTE", Deadlines.wanted(agreement, "active", mapOf("expiry_date" to null), today, 7))
        assertEquals("POR VENCER", Deadlines.wanted(agreement, "suspended", mapOf("expiry_date" to today.plusDays(2).toString()), today, 7))
        assertEquals("VENCIDO", Deadlines.wanted(agreement, "active", mapOf("expiry_date" to today.minusDays(1).toString()), today, 7))
        assertNull(Deadlines.wanted(agreement, "concluded", mapOf("expiry_date" to today.minusDays(1).toString()), today, 7))
    }

    @Test
    fun `los niveles de escalamiento con los umbrales del proyecto`() {
        assertEquals(0, Deadlines.level(today.minusDays(6), today, 7, 15))
        assertEquals(1, Deadlines.level(today.minusDays(7), today, 7, 15))
        assertEquals(2, Deadlines.level(today.minusDays(15), today, 7, 15))
        assertEquals("escalation:commitment:x:2026-10-01:1", DeadlineJob.escalationKey("x", LocalDate.parse("2026-10-01"), 1))
    }

    @Test
    fun `una organizacion que falla no detiene a las demas y la vuelta lanza al final`() {
        val (bad, good) = UUID.randomUUID() to UUID.randomUUID()
        val done = mutableListOf<UUID>()
        val sweep = DeadlineSweep({ listOf(bad, good) }, { _, block -> block() }, { org -> if (org == bad) error("sin base") else 1.also { done += org } })
        val e = assertThrows<IllegalStateException> { runBlocking { sweep.run() } }
        assertEquals(listOf(good), done)
        assertTrue(e.message!!.contains(bad.toString()))
    }

    // la reprogramación (RF-COM-003) recalcula deadline_status en su transacción: solo cuando cambia la fecha que mide
    @Test
    fun `solo un cambio de la fecha de vencimiento de un compromiso recalcula su situacion al momento`() {
        val before = mapOf("due_date" to "2026-01-05", "title" to "Canal")
        assertEquals(commitment, Deadlines.rescheduled("commitment", before, before + ("due_date" to "2027-02-01")))
        assertNull(Deadlines.rescheduled("commitment", before, before + ("title" to "Canal de riego")))
        assertNull(Deadlines.rescheduled("commitment", null, before))
        assertNull(Deadlines.rescheduled("agreement", mapOf("expiry_date" to "2026-01-01"), mapOf("expiry_date" to "2027-01-01")))
        // el compromiso vencido que se reprograma a una fecha futura deja de estar VENCIDO ese mismo día
        assertEquals("AL DIA", Deadlines.wanted(commitment, "in_progress", mapOf("due_date" to "2027-02-01"), today, 7))
    }

    // la matriz de compromisos (privacy, que solo depende de common) muestra deadline_status en los mismos estados
    @Test
    fun `la matriz y JOB-01 usan los mismos estados de plazo`() {
        assertEquals(Commitments.PENDING, MatrixSheets.DEADLINE_STATES)
        assertEquals(Commitments.PENDING, commitment.states)
    }
}
