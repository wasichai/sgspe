package sgspe.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import wasichai.core.common.WasichaiException

// ningún marcador de seudonimización llega a un registro de dominio (ai-architecture.md §4.2 y §12.2): ni por la aceptación de
// una sugerencia ni por la de un candidato a compromiso, tampoco escondido en una lista o un objeto
class PseudonymMarkersTest {
    @Test
    fun `un marcador por llamada o estable se reconoce en un valor`() {
        for (text in listOf("[PERSONA]", "Hola [PERSONA_2]", "[PERSONA:00000000-0000-0000-0000-0000000000a1]", "[DOCUMENTO]", "[CORREO_1]", "[CITA]")) {
            assertTrue(PseudonymMarkers.has(text), text)
        }
        for (text in listOf("Ana Ejemplo", "[Persona]", "[PERSONAS]", "(PERSONA_1)")) assertFalse(PseudonymMarkers.has(text), text)
    }

    @Test
    fun `la ruta del primer marcador, tambien dentro de una lista o un objeto`() {
        assertNull(PseudonymMarkers.pathOf("title", "Reparar el canal"))
        assertNull(PseudonymMarkers.pathOf("due_date", null))
        assertNull(PseudonymMarkers.pathOf("amount", 12))
        assertEquals("title", PseudonymMarkers.pathOf("title", "Informar a [PERSONA]"))
        assertEquals("detail.who", PseudonymMarkers.pathOf("detail", mapOf("what" to "canal", "who" to "[PERSONA_1]")))
        assertEquals("topics", PseudonymMarkers.pathOf("topics", listOf("agua", "[DOCUMENTO]")))
    }

    @Test
    fun `un marcador en cualquier atributo es 400 PSEUDONYM_NOT_ALLOWED sobre attributes y su ruta`() {
        PseudonymMarkers.requireNone(mapOf("title" to "Reparar el canal", "beneficiaries_detail" to null))
        val refused =
            assertThrows<WasichaiException> {
                PseudonymMarkers.requireNone(
                    mapOf(
                        "title" to "Reparar el canal",
                        "extra" to mapOf("note" to "con [PERSONA:00000000-0000-0000-0000-0000000000a1]")
                    )
                )
            }
        assertEquals(400, refused.status.value())
        assertEquals("attributes.extra.note" to "PSEUDONYM_NOT_ALLOWED", refused.violations.single().let { it.field to it.message })
    }
}
