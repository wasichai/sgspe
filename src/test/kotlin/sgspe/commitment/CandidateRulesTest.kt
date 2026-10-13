package sgspe.commitment

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpStatus
import sgspe.common.ModelCatalog
import sgspe.common.RuleException
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordWrite
import wasichai.core.identity.AuthenticatedUser
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

    // desde F7 (Decisión 2 del plan 7.1): la llamada a la IA del candidato sale del catálogo y se juzga; una interacción del
    // tenant (AI-05, JOB-07) no es de otro proyecto, como una evidencia del tenant
    @Test
    fun `una interaccion de IA del tenant o del proyecto pasa y una de otro proyecto no`() {
        val project = UUID.randomUUID()
        assertTrue("ai_interaction" to "ai_interaction" in CandidateLinks.fields(ModelCatalog.fromClasspath()))
        assertEquals(emptyList<String>(), CandidateLinks.mismatches(project, mapOf("ai_interaction" to ("ai_interaction" to null))))
        assertEquals(emptyList<String>(), CandidateLinks.mismatches(project, mapOf("ai_interaction" to ("ai_interaction" to project))))
        assertEquals(
            listOf("ai_interaction"),
            CandidateLinks.mismatches(project, mapOf("ai_interaction" to ("ai_interaction" to UUID.randomUUID())))
        )
    }

    // RQ-AI-003: solo una persona acepta o descarta un candidato; una cuenta de servicio no, aunque tenga el rol
    @Test
    fun `una cuenta de servicio con el rol recibe 403 PERSON_REQUIRED y una persona pasa`() {
        val service = AuthenticatedUser(UUID.randomUUID(), UUID.randomUUID(), "svc@sgspe.test", listOf("COMMITMENT_MANAGER"), "cuenta-servicio")
        val refused = assertThrows<RuleException> { Candidates.requirePerson(service) }
        assertEquals(HttpStatus.FORBIDDEN, refused.status)
        assertEquals("PERSON_REQUIRED", refused.violations.single().message)
        val person = AuthenticatedUser(UUID.randomUUID(), UUID.randomUUID(), "persona@sgspe.test", listOf("COMMITMENT_MANAGER"), null)
        assertDoesNotThrow { Candidates.requirePerson(person) }
    }
}
