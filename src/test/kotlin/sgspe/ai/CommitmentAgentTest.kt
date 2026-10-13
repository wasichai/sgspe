package sgspe.ai

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import wasichai.core.common.WasichaiException
import java.math.BigDecimal
import java.time.LocalDate

// AI-03 con FakeModel y MockModel (ai-architecture.md §5, §6.1 y §15): un acta ficticia da candidatos con cita y confianza, sin
// responsable sugerido y con marcadores estables; lo repetido, lo inventado y lo que ya es un compromiso del proyecto se descarta
class CommitmentAgentTest {
    private val log = RecordedInteractions()
    private val known = FakeLookups(texts = setOf(Proposals.folded("Construir el reservorio de la Comunidad Ejemplo")))

    private fun agent(model: StructuredModel) = CommitmentAgent(RunFixtures.calls(model, log), known, RunFixtures.properties())

    private val output =
        CommitmentOutput(
            listOf(
                ProposedCommitment(
                    "Reparar el canal de riego del sector bajo",
                    "La empresa se compromete a reparar el canal de riego antes del 30 de noviembre de 2026",
                    "[PERSONA_1] y las familias del sector bajo",
                    "2026-11-30",
                    0.81
                ),
                // la misma propuesta con otras mayúsculas
                ProposedCommitment("REPARAR el canal de riego del sector  bajo", "se compromete a reparar el canal de riego", null, null, 0.7),
                // ya es un compromiso del proyecto
                ProposedCommitment("Construir el reservorio de la Comunidad Ejemplo", "se compromete a reparar el canal de riego", null, null, 0.6),
                // cita inventada
                ProposedCommitment("Construir un colegio", "La empresa construirá un colegio", null, null, 0.9),
                // sin fecha legible: el candidato vale sin fecha
                ProposedCommitment(
                    "Informar el avance a [PERSONA_1]",
                    "[PERSONA_1] (DNI [DOCUMENTO_1], celular [TELEFONO_1]) pidió reparar",
                    null,
                    "pronto",
                    0.55
                )
            )
        )

    @Test
    fun `un acta ficticia da candidatos con cita, confianza y marcadores estables`() {
        val result = runBlocking { agent(FakeModel { output }).run(RunFixtures.minutes(), RunFixtures.NAMES, RunBudget(8)) }
        assertEquals(2, result.candidates.size)
        assertEquals(3, result.discarded)
        val canal = result.candidates[0]
        assertEquals(RunFixtures.PROJECT, canal.project)
        assertEquals("Reparar el canal de riego del sector bajo", canal.proposedText)
        assertEquals("La empresa se compromete a reparar el canal de riego antes del 30 de noviembre de 2026", canal.evidenceQuote)
        assertEquals("[PERSONA:${RunFixtures.ANA}] y las familias del sector bajo", canal.beneficiariesText)
        assertEquals(LocalDate.of(2026, 11, 30), canal.suggestedDueDate)
        assertEquals(BigDecimal("0.81"), canal.confidence)
        assertEquals(RunFixtures.ENGAGEMENT, canal.engagement)
        assertNull(canal.sourceEvidence)
        val aviso = result.candidates[1]
        assertEquals("Informar el avance a [PERSONA:${RunFixtures.ANA}]", aviso.proposedText)
        assertEquals("[PERSONA:${RunFixtures.ANA}] (DNI [DOCUMENTO], celular [TELEFONO]) pidió reparar", aviso.evidenceQuote)
        assertNull(aviso.suggestedDueDate)
        assertEquals("ai-03@1", log.entries.single().promptVersion)
        assertEquals("5 posibles compromisos propuestos", log.entries.single().outputSummary)
    }

    @Test
    fun `una fuente sin proyecto es 400 PROJECT_REQUIRED antes de llamar`() {
        val mock = MockModel(FakeModel { output })
        val refused = assertThrows<WasichaiException> { runBlocking { agent(mock).run(RunFixtures.minutes(project = null), RunFixtures.NAMES, RunBudget(8)) } }
        assertEquals(400, refused.status.value())
        assertEquals("target" to "PROJECT_REQUIRED", refused.violations.single().let { it.field to it.message })
        assertTrue(mock.sent.isEmpty())
        assertTrue(log.entries.isEmpty())
    }

    @Test
    fun `una segunda corrida no repite el candidato que nombra a una persona registrada`() {
        // la primera corrida guardó los candidatos con el marcador estable ([PERSONA:<id>]), no con el nombre restaurado
        val first = runBlocking { agent(FakeModel { output }).run(RunFixtures.minutes(), RunFixtures.NAMES, RunBudget(8)) }
        val stored = FakeLookups(texts = first.candidates.map { Proposals.folded(it.proposedText) }.toSet())
        val again = CommitmentAgent(RunFixtures.calls(FakeModel { output }, log), stored, RunFixtures.properties())
        val second = runBlocking { again.run(RunFixtures.minutes(), RunFixtures.NAMES, RunBudget(8)) }
        assertEquals(listOf("Construir el reservorio de la Comunidad Ejemplo"), second.candidates.map { it.proposedText })
    }

    @Test
    fun `el tope de propuestas por llamada descarta el resto`() {
        val capped = CommitmentAgent(RunFixtures.calls(FakeModel { output }, log), known, RunFixtures.properties(proposals = 1))
        val result = runBlocking { capped.run(RunFixtures.minutes(), RunFixtures.NAMES, RunBudget(8)) }
        assertEquals(1, result.candidates.size)
        assertEquals(4, result.discarded)
    }
}
