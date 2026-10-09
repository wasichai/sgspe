package sgspe.commitment

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordWrite
import java.util.UUID

// las reglas de los candidatos sin base: mark_duplicate exige duplicate_of y P54 sobre los vínculos del candidato
class CandidateRulesTest {
    private fun transition(
        name: String,
        before: Map<String, Any?>,
        objectName: String = Commitments.CANDIDATE
    ) = RecordWrite(
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        objectName,
        UUID.randomUUID(),
        RecordChangeKind.TRANSITIONED,
        before,
        null,
        name,
        "motivo"
    )

    private fun findings(change: RecordWrite) = runBlocking { CandidateRules().findings(change, dryRun = false) }

    @Test
    fun `marcar duplicado sin duplicate_of es FIELD_REQUIRED en duplicate_of`() {
        val found = findings(transition("mark_duplicate", mapOf("proposed_text" to "x"))).single()
        assertEquals("FIELD_REQUIRED" to "duplicate_of", found.code to found.field)
        assertTrue(found.blocking)
    }

    @Test
    fun `con duplicate_of, otra transicion u otro objeto no hay hallazgo`() {
        assertEquals(emptyList<Any>(), findings(transition("mark_duplicate", mapOf("duplicate_of" to UUID.randomUUID()))))
        assertEquals(emptyList<Any>(), findings(transition("reject", emptyMap())))
        assertEquals(emptyList<Any>(), findings(transition("mark_duplicate", emptyMap(), Commitments.COMMITMENT)))
    }

    @Test
    fun `las reglas de los candidatos van despues de las demas familias y en lugares distintos`() {
        val orders = listOf(CandidateRules().order, CandidateLinkRule.ORDER)
        assertTrue(orders.all { it > CommitmentRuleOrder.CANDIDATE && it < CommitmentRuleOrder.CANDIDATE + 100 }, "$orders")
        assertEquals(orders.size, orders.toSet().size)
    }

    @Test
    fun `un vinculo de otro proyecto o sin proyecto no pasa, salvo una evidencia del tenant`() {
        val project = UUID.randomUUID()
        val other = UUID.randomUUID()
        val targets =
            mapOf(
                "engagement" to ("engagement" to project),
                "duplicate_of" to ("commitment" to other),
                "created_commitment" to ("commitment" to null),
                "source_evidence" to ("evidence" to null)
            )
        assertEquals(listOf("duplicate_of", "created_commitment"), CandidateLinks.mismatches(project, targets))
        assertEquals(listOf("source_evidence"), CandidateLinks.mismatches(project, mapOf("source_evidence" to ("evidence" to other))))
        assertEquals(emptyList<String>(), CandidateLinks.mismatches(null, targets))
    }
}
