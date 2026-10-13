package sgspe.ai

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

// la seudonimización antes de cada llamada (ai-architecture.md §12.2, RQ-AI-009): personas del diccionario, formatos de
// documento, teléfono y correo, y valores restringidos conocidos; lo demás, intacto. datos ficticios (RQ-SEC-018)
class PseudonymizerTest {
    private val ana = UUID.fromString("00000000-0000-0000-0000-0000000000a1")
    private val bruno = UUID.fromString("00000000-0000-0000-0000-0000000000b2")
    private val names =
        NameDictionary(
            listOf(
                KnownPerson(ana, "Ana", "Ejemplo"),
                KnownPerson(bruno, "Bruno José", "Quispe Ejemplo"),
                KnownPerson(null, "Solo", null)
            )
        )

    private fun run(
        text: String,
        table: PseudonymTable = PseudonymTable(),
        restricted: List<RestrictedValue> = emptyList()
    ): String = Pseudonymizer.pseudonymize(text, names, table, restricted)

    @Test
    fun `una persona del diccionario lleva el mismo marcador en todas sus formas`() {
        assertEquals(
            "[PERSONA_1] pidió el canal, [PERSONA_1] firmó y [PERSONA_1] y [PERSONA_1] también.",
            run("Ana Ejemplo pidió el canal, ANA EJEMPLO firmó y Ejemplo, Ána y ana  ejemplo también.")
        )
    }

    @Test
    fun `dos personas tienen marcadores distintos y el nombre mas largo gana`() {
        assertEquals("[PERSONA_1] y [PERSONA_2] se reunieron.", run("Bruno José Quispe Ejemplo y Ana Ejemplo se reunieron."))
    }

    @Test
    fun `un nombre de una sola palabra no entra al diccionario`() {
        assertEquals("Solo vino Ana con Ejemplo.", run("Solo vino Ana con Ejemplo."))
    }

    @Test
    fun `documento, telefono y correo por su formato`() {
        assertEquals(
            "DNI [DOCUMENTO_1], RUC [DOCUMENTO_2], celular [TELEFONO_1], fijo [TELEFONO_2] y correo [CORREO_1].",
            run("DNI 40123456, RUC 10401234567, celular +51 987 654 321, fijo (01) 234-5678 y correo ana.ejemplo@sgspe.test.")
        )
    }

    @Test
    fun `los ids, las fechas y los montos quedan como estan`() {
        val text = "registro 12345678-1234-4234-8234-123456789012 del 2026-10-13T15:00:00Z por S/ 40123456.50 y código 2026-1013"
        assertEquals(text, run(text))
    }

    @Test
    fun `un valor restringido conocido sale con su marcador tipado sin numero`() {
        val restricted = listOf(RestrictedValue(Marker.PERSONA, "Rosa Ccori Ejemplo"), RestrictedValue(Marker.PERSONA, "Flores"))
        assertEquals("Firmó [PERSONA] junto a las flores.", run("Firmó rosa ccori ejemplo junto a las flores.", restricted = restricted))
    }

    @Test
    fun `un valor restringido que la tabla ya vio se enmascara en el texto que sigue`() {
        val table = PseudonymTable()
        table.reserve(Marker.TELEFONO, "964 123 456")
        assertEquals("Llamar al [TELEFONO].", run("Llamar al 964 123 456.", table))
    }

    @Test
    fun `nada de lo reemplazado queda en el texto`() {
        val out = run("Ana Ejemplo (DNI 40123456) llamó del 987654321 y escribió a ana@sgspe.test")
        assertEquals("[PERSONA_1] (DNI [DOCUMENTO_1]) llamó del [TELEFONO_1] y escribió a [CORREO_1]", out)
        for (value in listOf("Ana", "Ejemplo", "40123456", "987654321", "ana@sgspe.test")) assertFalse(value in out, "$value en $out")
    }

    @Test
    fun `solo se distingue un valor de dos palabras, seis digitos o un correo`() {
        assertTrue(Pseudonymizer.distinctive("Rosa Ccori"))
        assertTrue(Pseudonymizer.distinctive("40123456"))
        assertTrue(Pseudonymizer.distinctive("rosa@sgspe.test"))
        assertFalse(Pseudonymizer.distinctive("Flores"))
        assertFalse(Pseudonymizer.distinctive("12345678-1234-4234-8234-123456789012"))
        assertFalse(Pseudonymizer.distinctive("  "))
    }
}
