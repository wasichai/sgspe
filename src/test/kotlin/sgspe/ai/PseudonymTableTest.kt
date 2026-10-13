package sgspe.ai

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import java.util.UUID

// las equivalencias de una llamada (ai-architecture.md §12.2): marcadores por llamada, restauración de AI-05 y paso a los
// marcadores estables que se guardan (security.md §7.8)
class PseudonymTableTest {
    private val ana = UUID.fromString("00000000-0000-0000-0000-0000000000a1")

    @Test
    fun `el mismo dato tiene el mismo marcador y cada tipo cuenta aparte`() {
        val table = PseudonymTable()
        assertEquals("[PERSONA_1]", table.mark(Marker.PERSONA, "Ana Ejemplo", ana))
        assertEquals("[PERSONA_1]", table.mark(Marker.PERSONA, "ANA EJEMPLO", ana))
        assertEquals("[PERSONA_2]", table.mark(Marker.PERSONA, "Bruno Ejemplo"))
        assertEquals("[TELEFONO_1]", table.mark(Marker.TELEFONO, "987654321"))
        assertEquals("[TELEFONO_1]", table.mark(Marker.TELEFONO, "987654321"))
    }

    @Test
    fun `restaurar devuelve los valores de la llamada y deja los reservados y los ajenos`() {
        val table = PseudonymTable()
        table.mark(Marker.PERSONA, "Ana Ejemplo", ana)
        table.mark(Marker.TELEFONO, "987 654 321")
        assertEquals(
            "Ana Ejemplo llamó del 987 654 321; [PERSONA] y [PERSONA_7] no.",
            table.restore("[PERSONA_1] llamó del [TELEFONO_1]; [PERSONA] y [PERSONA_7] no.")
        )
    }

    @Test
    fun `al guardar, una persona registrada queda con su id y lo demas sin valor`() {
        val table = PseudonymTable()
        table.mark(Marker.PERSONA, "Ana Ejemplo", ana)
        table.mark(Marker.PERSONA, "Bruno Ejemplo")
        table.mark(Marker.DOCUMENTO, "40123456")
        table.mark(Marker.CORREO, "ana@sgspe.test")
        assertEquals(
            "[PERSONA:$ana] y [PERSONA] con [DOCUMENTO], [CORREO] y [TELEFONO]",
            table.stable("[PERSONA_1] y [PERSONA_2] con [DOCUMENTO_1], [CORREO_1] y [TELEFONO_3]")
        )
    }

    @Test
    fun `solo los valores que se distinguen quedan reservados`() {
        val table = PseudonymTable()
        table.reserve(Marker.PERSONA, "Flores")
        table.reserve(Marker.PERSONA, " Rosa Ccori ")
        table.reserve(Marker.DOCUMENTO, "40123456")
        table.reserve(Marker.PERSONA, "12345678-1234-4234-8234-123456789012")
        assertEquals(listOf("Rosa Ccori", "40123456"), table.reserved().map { it.value })
    }

    @Test
    fun `la tabla no se imprime con sus valores`() {
        val table = PseudonymTable()
        table.mark(Marker.PERSONA, "Ana Ejemplo", ana)
        assertFalse("Ana" in table.toString())
    }
}
