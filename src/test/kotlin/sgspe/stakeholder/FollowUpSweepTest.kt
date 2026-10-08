package sgspe.stakeholder

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

// el aislamiento de JOB-03 sin base: una organización que falla no detiene a las demás, y la vuelta lanza al final para que
// el fallo se vea (como JOB-02) en lugar de quedar solo en el registro
class FollowUpSweepTest {
    private val orgA = UUID.randomUUID()
    private val orgB = UUID.randomUUID()
    private val orgC = UUID.randomUUID()
    private val visited = mutableListOf<UUID>()

    private fun sweep(failIn: Set<UUID>) =
        FollowUpSweep(
            { listOf(orgA, orgB, orgC) },
            { _, block -> block() },
            { organization ->
                visited += organization
                check(organization !in failIn) { "falla a propósito" }
                2
            }
        )

    @Test
    fun `sin fallos devuelve los grupos de interes escritos de todas las organizaciones`() {
        assertEquals(6, runBlocking { sweep(emptySet()).run() })
        assertEquals(listOf(orgA, orgB, orgC), visited)
    }

    @Test
    fun `una organizacion que falla no detiene a las demas y la vuelta lanza al final`() {
        val failure = assertThrows(IllegalStateException::class.java) { runBlocking { sweep(setOf(orgA, orgC)).run() } }
        assertEquals(listOf(orgA, orgB, orgC), visited)
        assertTrue(failure.message!!.contains("2 organizaciones"), failure.message)
        assertTrue(failure.message!!.contains(orgA.toString()) && failure.message!!.contains(orgC.toString()), failure.message)
    }
}
