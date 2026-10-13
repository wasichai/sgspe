package sgspe.ai

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// la cita literal sobre el texto seudonimizado que recibió el modelo (ai-architecture.md §6.1 y §15, especificación de F7 §2.2.8)
class CitationCheckTest {
    private val source =
        "Acta de la asamblea.\n[PERSONA_1] (DNI [DOCUMENTO_1]) pidió reparar el canal  de riego.\nLa empresa se compromete a “reparar el canal”."

    @Test
    fun `una cita literal con los marcadores de la llamada se encuentra, aunque cambien los espacios`() {
        assertTrue(CitationCheck.found("[PERSONA_1] (DNI [DOCUMENTO_1]) pidió reparar el canal de riego", source))
        assertTrue(CitationCheck.found("  pidió reparar\nel canal de riego. ", source))
    }

    @Test
    fun `las comillas tipograficas y las del borde no cuentan`() {
        assertTrue(CitationCheck.found("\"La empresa se compromete a \"reparar el canal\"\"", source))
        assertTrue(CitationCheck.found("«se compromete a “reparar el canal”»", source))
    }

    @Test
    fun `una cita inventada, con el nombre real o demasiado corta se descarta`() {
        assertFalse(CitationCheck.found("La empresa se compromete a construir un colegio", source))
        assertFalse(CitationCheck.found("Ana Ejemplo pidió reparar el canal de riego", source))
        assertFalse(CitationCheck.found("el canal", source))
        assertFalse(CitationCheck.found(null, source))
        assertFalse(CitationCheck.found("", source))
    }

    @Test
    fun `la forma compuesta y la descompuesta son la misma letra`() {
        assertTrue(CitationCheck.found("pidió reparar el canal", source))
    }
}
