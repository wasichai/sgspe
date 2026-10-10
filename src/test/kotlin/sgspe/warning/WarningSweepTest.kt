package sgspe.warning

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import sgspe.commitment.Commitments
import java.util.UUID

// la vuelta de JOB-04 por organización (especificación de F4 §3.7), sin base: una que falla no detiene a las demás, y la vuelta
// lanza al final
class WarningSweepTest {
    @Test
    fun `una organizacion que falla no detiene a las demas`() {
        val good = UUID.randomUUID()
        val bad = UUID.randomUUID()
        val seen = mutableListOf<UUID>()
        val sweep =
            WarningSweep(
                { listOf(bad, good) },
                { _, block -> block() },
                { organization ->
                    seen += organization
                    if (organization == bad) error("falla de prueba") else 2
                }
            )
        val error = assertThrows<IllegalStateException> { runBlocking { sweep.run() } }
        assertEquals(listOf(bad, good), seen)
        assertEquals(true, error.message!!.contains("1 organizaciones"))
        assertEquals(3, runBlocking { WarningSweep({ listOf(good) }, { _, block -> block() }, { 3 }).run() })
    }

    // COMPROMISOS VENCIDOS cuenta los estados en que JOB-01 calcula deadline_status
    @Test
    fun `los compromisos pendientes son los de JOB-01`() {
        assertEquals(Commitments.PENDING, WarningRulesJob.PENDING)
    }
}
