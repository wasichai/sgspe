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

    private val compound =
        NameDictionary(
            listOf(
                KnownPerson(bruno, "Bruno José", "Quispe Ejemplo"),
                KnownPerson(ana, "Ana María", "Ejemplo Ccori"),
                KnownPerson(null, "Q'orianka", "Mamani-Ejemplo")
            )
        )

    private fun runWith(
        dictionary: NameDictionary,
        text: String,
        restricted: List<RestrictedValue> = emptyList()
    ): String = Pseudonymizer.pseudonymize(text, dictionary, PseudonymTable(), restricted)

    @Test
    fun `un nombre parcial tambien se enmascara`() {
        assertEquals("[PERSONA_1], [PERSONA_1] y [PERSONA_1]", runWith(compound, "Bruno Quispe, Quispe Ejemplo y Bruno José Quispe Ejemplo"))
        assertEquals("[PERSONA_1] y [PERSONA_1]", runWith(compound, "Ana Ejemplo y Ejemplo Ccori"))
    }

    @Test
    fun `un nombre con apostrofo o guion se reconoce completo y parcial`() {
        assertEquals(
            "[PERSONA_1], [PERSONA_1], [PERSONA_1] y [PERSONA_1]",
            runWith(compound, "Q'orianka Mamani-Ejemplo, Q’orianka Mamani, Mamani-Ejemplo Q'orianka y Q'orianka Mamani Ejemplo")
        )
    }

    @Test
    fun `un valor restringido se reconoce con otros espacios, saltos y orden`() {
        val restricted = listOf(RestrictedValue(Marker.PERSONA, "Rosa Ccori Ejemplo"))
        for (text in listOf("Rosa  Ccori Ejemplo", "rosa\nccori ejemplo", "Rosa\u00A0Ccori\u00A0Ejemplo", "Ccori Ejemplo, Rosa")) {
            assertEquals("Firmó [PERSONA].", runWith(NameDictionary.EMPTY, "Firmó $text.", restricted), text)
        }
    }

    @Test
    fun `un correo restringido se sigue enmascarando tal cual`() {
        val restricted = listOf(RestrictedValue(Marker.CORREO, "rosa@sgspe.test"))
        assertEquals("Escribir a [CORREO].", runWith(NameDictionary.EMPTY, "Escribir a ROSA@sgspe.test.", restricted))
    }

    @Test
    fun `una lista de documentos y un documento con prefijo pegado se enmascaran`() {
        assertEquals("DNI: [DOCUMENTO_1],[DOCUMENTO_2]", run("DNI: 40123456,40123457"))
        assertEquals("DNI[DOCUMENTO_1]", run("DNI40123456"))
        assertEquals("monto 40123456.50 y 40123456,5", run("monto 40123456.50 y 40123456,5"))
    }

    @Test
    fun `un marcador escrito en el texto no se confunde con uno generado`() {
        val table = PseudonymTable()
        val out = Pseudonymizer.pseudonymize("[PERSONA_1] dijo algo; Ana Ejemplo llamó", names, table)
        assertEquals("(PERSONA_1) dijo algo; [PERSONA_1] llamó", out)
        assertEquals("(PERSONA_1) dijo algo; Ana Ejemplo llamó", table.restore(out))
        assertEquals("(PERSONA_1) dijo algo; [PERSONA:$ana] llamó", table.stable(out))
    }

    @Test
    fun `un nombre descompuesto se reconoce`() {
        assertEquals("[PERSONA_1] y [PERSONA_1]", run("A\u0301na Ejemplo y Ana\u0301 Ejemplo"))
    }

    @Test
    fun `mas formas de telefono`() {
        assertEquals("[TELEFONO_1]", run("+51 1 234 5678"))
        assertEquals("[TELEFONO_1] y [TELEFONO_2]", run("987 65 43 21 y 9876-54321"))
    }

    @Test
    fun `un documento restringido pegado a un prefijo sale sin numero y no se restaura`() {
        val table = PseudonymTable()
        val restricted = listOf(RestrictedValue(Marker.DOCUMENTO, "40123456"))
        val out = Pseudonymizer.pseudonymize("DNI40123456 y DNI 40123456 y otro 40123457", names, table, restricted)
        assertEquals("DNI[DOCUMENTO] y DNI [DOCUMENTO] y otro [DOCUMENTO_1]", out)
        assertFalse("40123456" in table.restore(out))
    }

    @Test
    fun `una particula sola no nombra a la persona`() {
        val particles =
            NameDictionary(listOf(KnownPerson(null, "Juan", "De La Cruz"), KnownPerson(null, "Luz", "Del Carmen Ejemplo")))
        val text = "Juan de nuevo, la casa de Juan, Luz del día"
        assertEquals(text, runWith(particles, text))
        assertEquals("[PERSONA_1] y [PERSONA_2]", runWith(particles, "Juan de la Cruz y Luz del Carmen"))
    }

    @Test
    fun `los textos largos se procesan en tiempo lineal`() {
        for (unit in listOf("9", "1.", "a", "a.", "98765432 ", "a@")) {
            val text = unit.repeat(200_000 / unit.length)
            val start = System.nanoTime()
            run(text)
            val seconds = (System.nanoTime() - start) / 1e9
            assertTrue(seconds < 2.0, "$unit: $seconds s")
        }
    }

    @Test
    fun `los correos por la arroba`() {
        assertEquals("[CORREO_1], [CORREO_2] y a@b o @x.com", run("a.b@sgspe.test, c@d.org y a@b o @x.com"))
    }

    private fun restrictedRun(
        kind: Marker,
        value: String,
        text: String
    ): String = runWith(NameDictionary.EMPTY, text, listOf(RestrictedValue(kind, value)))

    @Test
    fun `un restringido dentro de un formato mas ancho sale sin numero`() {
        assertEquals("[DOCUMENTO]", restrictedRun(Marker.DOCUMENTO, "40123456", "10401234567"))
        for (text in listOf("51987654321", "+51 987 654 321", "+51987654321", "987-654-321")) {
            assertEquals("[TELEFONO]", restrictedRun(Marker.TELEFONO, "987654321", text), text)
        }
        for (text in listOf("987654321", "987-654-321", "+51 987 654 321")) {
            assertEquals("[TELEFONO]", restrictedRun(Marker.TELEFONO, "+51 987 654 321", text), text)
        }
        assertEquals("[CORREO]", restrictedRun(Marker.CORREO, "ana@sgspe.test", "xana@sgspe.test"))
    }

    @Test
    fun `una persona del diccionario cuyo nombre incluye un restringido sale sin numero`() {
        val table = PseudonymTable()
        val restricted = listOf(RestrictedValue(Marker.PERSONA, "Ana Ejemplo"))
        val out = Pseudonymizer.pseudonymize("Ana Ejemplo y Bruno José Quispe Ejemplo", names, table, restricted)
        assertEquals("[PERSONA] y [PERSONA_1]", out)
        assertFalse("Ana" in table.restore(out))
        val wide = NameDictionary(listOf(KnownPerson(null, "Ana", "Ejemplo Ccori")))
        val out2 = Pseudonymizer.pseudonymize("Ana Ejemplo Ccori", wide, table, listOf(RestrictedValue(Marker.PERSONA, "Ana Ejemplo")))
        assertEquals("[PERSONA]", out2)
    }

    @Test
    fun `un dominio con miles de etiquetas no desborda la pila`() {
        val text = "a@" + "b.".repeat(5000) + "pe y c@d.org"
        val start = System.nanoTime()
        assertEquals("[CORREO_1] y [CORREO_2]", run(text))
        assertTrue((System.nanoTime() - start) / 1e9 < 2.0)
    }
}
