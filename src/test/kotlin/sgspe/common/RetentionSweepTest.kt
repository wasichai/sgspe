package sgspe.common

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

// JOB-08 (workflows.md): cada paso de retención corre; uno que falla no detiene a los demás y la vuelta lanza al final
class RetentionSweepTest {
    private fun step(
        name: String,
        purge: suspend () -> Long
    ) = object : RetentionStep {
        override val name = name

        override suspend fun purge(): Long = purge()
    }

    @Test
    fun `devuelve lo purgado por paso`() {
        val result = runBlocking { RetentionSweep(listOf(step("a") { 3 }, step("b") { 0 })).run() }
        assertEquals(mapOf("a" to 3L, "b" to 0L), result)
    }

    @Test
    fun `un paso que falla no detiene a los demas y la vuelta lanza al final`() {
        var ran = false
        val e =
            assertThrows<IllegalStateException> {
                runBlocking {
                    RetentionSweep(
                        listOf(
                            step("roto") { error("sin base") },
                            step("bueno") {
                                ran = true
                                2
                            }
                        )
                    ).run()
                }
            }
        assertTrue(ran)
        assertTrue(e.message!!.contains("roto"), e.message)
    }
}
