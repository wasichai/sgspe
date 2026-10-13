package sgspe.ai

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import wasichai.core.identity.AuthenticatedUser
import java.time.Instant
import java.util.UUID

// los filtros de AI-05 (ai-architecture.md §8, ASM-130): con la pregunta en curso enmascaran, seudonimizan y restauran con su tabla;
// sin ella fallan cerrado y la excepción no dice el dato
class AssistantFiltersTest {
    private val caller = AuthenticatedUser(UUID.randomUUID(), UUID.randomUUID(), "cro@sgspe.test", emptyList())
    private val names = NameDictionary(listOf(KnownPerson(UUID.fromString("00000000-0000-0000-0000-0000000000a1"), "Ana", "Ejemplo")))

    @Test
    fun `sin pregunta en curso los filtros fallan cerrado y no dicen el dato`() {
        val failure =
            assertThrows<IllegalStateException> {
                runBlocking(TestCallers.context()) {
                    PseudonymizingResultFilter().filter(caller, "get_record", mapOf("object" to "person"), """{"attributes":{"given_names":"Ana"}}""")
                }
            }
        assertFalse("Ana" in failure.message.orEmpty())
        assertThrows<IllegalStateException> { runBlocking(TestCallers.context()) { RestoringAnswerFilter().restore(caller, "[PERSONA_1]") } }
    }

    @Test
    fun `con la pregunta en curso enmascaran, seudonimizan y restauran con su tabla`() {
        val run = AssistantRun("¿Qué pidió?", names, "ai-05@1", Instant.parse("2026-10-13T15:00:00Z"))
        val (sent, restored) =
            runBlocking(TestCallers.context()) {
                AssistantRun.bind(run) {
                    val sent =
                        PseudonymizingResultFilter().filter(
                            caller,
                            "get_record",
                            mapOf("object" to "engagement"),
                            """{"attributes":{"description":"Ana Ejemplo pidió el canal","title":"Reunión"}}"""
                        )
                    sent to RestoringAnswerFilter().restore(caller, "[PERSONA_1] pidió el canal; [PERSONA] no se dice")
                }
            }
        assertEquals("""{"attributes":{"description":"[PERSONA_1] pidió el canal","title":"Reunión"}}""", sent)
        assertEquals("Ana Ejemplo pidió el canal; [PERSONA] no se dice", restored)
    }
}
