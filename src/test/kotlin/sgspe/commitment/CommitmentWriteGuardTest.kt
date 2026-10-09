package sgspe.commitment

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.common.Finding
import wasichai.core.common.WasichaiException
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordWrite
import java.util.UUID

// el orden de las reglas de CommitmentWriteGuard (P49), la parada en el primer bloqueo y OBJECT_NOT_READABLE, sin base
class CommitmentWriteGuardTest {
    private val ran = mutableListOf<String>()

    private fun rule(
        name: String,
        at: Int,
        result: () -> List<Finding>
    ) = object : CommitmentRule {
        override val order = at

        override suspend fun findings(
            change: RecordWrite,
            dryRun: Boolean
        ): List<Finding> {
            ran += name
            return result()
        }
    }

    private fun write(objectName: String = Commitments.COMMITMENT) =
        RecordWrite(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), objectName, UUID.randomUUID(), RecordChangeKind.UPDATED, null, null, null, null)

    private fun block(code: String) = listOf(Finding(code, "bloqueo"))

    private fun guard() =
        CommitmentWriteGuard(
            listOf(
                rule("late", CommitmentRuleOrder.CANDIDATE) { block("LATE") },
                rule("approval", CommitmentRuleOrder.APPROVAL + 1) { block("APPROVAL") },
                rule("record", CommitmentRuleOrder.RECORD) { block("RECORD") }
            )
        )

    @Test
    fun `la escritura corre las reglas por orden y se detiene en el primer bloqueo`() {
        val e = runCatching { runBlocking { guard().beforeWrite(write()) } }.exceptionOrNull() as WasichaiException
        assertEquals("RECORD", e.violations.first().message)
        assertEquals(listOf("record"), ran)
    }

    @Test
    fun `la consulta en seco junta todos los hallazgos en el orden de las reglas`() {
        val codes = runBlocking { guard().findings(write(), dryRun = true) }.map { it.code }
        assertEquals(listOf("RECORD", "APPROVAL", "LATE"), codes)
        assertEquals(listOf("record", "approval", "late"), ran)
    }

    @Test
    fun `una advertencia no detiene la escritura y un objeto ajeno no corre ninguna regla`() {
        val g = CommitmentWriteGuard(listOf(rule("warn", 1) { listOf(Finding("W", "aviso", blocking = false)) }, rule("next", 2) { emptyList() }))
        runBlocking { g.beforeWrite(write()) }
        assertEquals(listOf("warn", "next"), ran)
        runBlocking { guard().beforeWrite(write("project")) }
        assertEquals(2, ran.size)
    }

    @Test
    fun `una regla que no puede leer es un 403 OBJECT_NOT_READABLE que nombra el objeto`() {
        val g = CommitmentWriteGuard(listOf(rule("blind", 1) { throw NotReadable("engagement") }))
        val found = runBlocking { g.findings(write(), dryRun = false) }.single()
        assertEquals("OBJECT_NOT_READABLE" to HttpStatus.FORBIDDEN, found.code to found.status)
        assertEquals("engagement", found.field)
        assertTrue(runCatching { runBlocking { g.beforeWrite(write()) } }.exceptionOrNull() is WasichaiException)
    }
}
