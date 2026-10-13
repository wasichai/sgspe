package sgspe.ai

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

// lo que sale hacia el modelo de cada herramienta de wasichai-agent 1.2.0 (ASM-130, ai-architecture.md §8): los campos
// restringidos del objeto leído con su marcador tipado y el texto seudonimizado, con las formas reales de AgentTools
class ToolResultsTest {
    private val ana = UUID.fromString("00000000-0000-0000-0000-0000000000a1")
    private val names = NameDictionary(listOf(KnownPerson(ana, "Ana", "Ejemplo")))
    private val id = "12345678-1234-4234-8234-123456789012"

    private fun screen(
        input: Map<String, Any?>,
        text: String,
        table: PseudonymTable = PseudonymTable()
    ): String = ToolResults.screen(input, text, names, table)

    @Test
    fun `get_record de una persona enmascara sus campos restringidos y deja el id`() {
        val out =
            screen(
                mapOf("object" to "person", "id" to id),
                """{"id":"$id","attributes":{"given_names":"Ana","surnames":"Ejemplo","id_document_number":"40123456","sex":"FEMENINO"},"state":null}"""
            )
        assertEquals(
            """{"id":"$id","attributes":{"given_names":"[PERSONA]","surnames":"[PERSONA]","id_document_number":"[DOCUMENTO]","sex":"FEMENINO"},"state":null}""",
            out
        )
    }

    @Test
    fun `query_records seudonimiza el texto libre y deja los conteos`() {
        val out =
            screen(
                mapOf("object" to "engagement"),
                """{"object":"engagement","total":40123456,"returned":1,"records":[{"id":"$id","state":"held","attributes":""" +
                    """{"title":"Reunión","description":"Ana Ejemplo pidió el canal; su celular es 987654321."},"hasGeometry":false}]}"""
            )
        assertTrue("\"total\":40123456" in out, out)
        assertTrue("\"id\":\"$id\"" in out, out)
        assertTrue("[PERSONA_1] pidió el canal; su celular es [TELEFONO_1]." in out, out)
        assertFalse("Ana" in out || "987654321" in out, out)
    }

    @Test
    fun `related_records enmascara por el objeto relacionado, no por el de la entrada`() {
        val out =
            screen(
                mapOf("object" to "engagement", "id" to id, "relationship" to "engagement_participants"),
                """{"relationship":"engagement_participants","relatedObject":"engagement_participant","total":1,"returned":1,""" +
                    """"records":[{"id":"$id","attributes":{"participant_name":"Rosa Ccori Ejemplo","phone":"964123456",""" +
                    """"email":"rosa@sgspe.test","attended":true}}]}"""
            )
        assertTrue("\"participant_name\":\"[PERSONA]\"" in out, out)
        assertTrue("\"phone\":\"[TELEFONO]\"" in out, out)
        assertTrue("\"email\":\"[CORREO]\"" in out, out)
        assertTrue("\"attended\":true" in out, out)
    }

    @Test
    fun `el historial enmascara el antes y el despues de un campo restringido`() {
        val out =
            screen(
                mapOf("object" to "grievance", "id" to id),
                """{"object":"grievance","record":"$id","history":[{"id":"h1","userEmail":"gestor@sgspe.test","changes":[""" +
                    """{"field":"person","before":null,"after":"$id"},{"field":"description","before":"Sin datos","after":"Ana Ejemplo reclamó"}]}]}"""
            )
        assertTrue("{\"field\":\"person\",\"before\":null,\"after\":\"[PERSONA]\"}" in out, out)
        assertTrue("\"after\":\"[PERSONA_1] reclamó\"" in out, out)
        assertTrue("\"userEmail\":\"[CORREO_1]\"" in out, out)
    }

    @Test
    fun `un valor restringido que se distingue se enmascara despues en el texto libre`() {
        val table = PseudonymTable()
        screen(
            mapOf("object" to "engagement"),
            """{"relatedObject":"engagement_participant","records":[{"attributes":{"participant_name":"Rosa Ccori Ejemplo"}}]}""",
            table
        )
        assertEquals(
            """{"attributes":{"description":"Firmó [PERSONA]."}}""",
            screen(mapOf("object" to "engagement"), """{"attributes":{"description":"Firmó Rosa Ccori Ejemplo."}}""", table)
        )
    }

    // el orden importa (Pseudonymizer: nunca un JSON entero): primero se enmascaran los campos restringidos de todo el resultado y
    // después se seudonimiza cada texto, así un teléfono restringido que aparece antes en el texto libre sale sin número
    @Test
    fun `un telefono restringido sale sin numero aunque el texto libre lo nombre antes`() {
        val out =
            screen(
                mapOf("object" to "engagement"),
                """{"relatedObject":"engagement_participant","records":[{"attributes":{"notes":"Llamar al 964 123 456"}},""" +
                    """{"attributes":{"participant_name":"Rosa Ccori Ejemplo","phone":"964123456"}}]}"""
            )
        assertTrue("\"notes\":\"Llamar al [TELEFONO]\"" in out, out)
        assertFalse("TELEFONO_" in out || "964" in out, out)
    }

    @Test
    fun `un documento restringido que llega como numero se enmascara y queda reservado`() {
        val table = PseudonymTable()
        val out =
            screen(
                mapOf("object" to "person", "id" to id),
                """{"id":"$id","attributes":{"id_document_number":40123456,"notes":"DNI 40123456"},"version":3}""",
                table
            )
        assertTrue("\"id_document_number\":\"[DOCUMENTO]\"" in out, out)
        assertTrue("\"notes\":\"DNI [DOCUMENTO]\"" in out, out)
        assertTrue("\"version\":3" in out, out)
        assertEquals("""{"description":"[DOCUMENTO]"}""", screen(mapOf("object" to "engagement"), """{"description":"40123456"}""", table))
    }

    // un valor visto primero en texto libre (numerado) y después como campo restringido no vuelve en la respuesta
    @Test
    fun `lo que luego resulta restringido no se restaura`() {
        val table = PseudonymTable()
        val first = screen(mapOf("object" to "engagement"), """{"attributes":{"description":"Llamar al 964 123 456"}}""", table)
        assertEquals("""{"attributes":{"description":"Llamar al [TELEFONO_1]"}}""", first)
        screen(mapOf("object" to "engagement"), """{"relatedObject":"engagement_participant","records":[{"attributes":{"phone":"964123456"}}]}""", table)
        assertEquals("Llamar al [TELEFONO]", table.restore("Llamar al [TELEFONO_1]"))
    }

    @Test
    fun `los numeros del json no se toman por documentos ni telefonos`() {
        val out = screen(mapOf("object" to "investment"), """{"object":"investment","total":40123456,"records":[{"attributes":{"amount":987654321}}]}""")
        assertEquals("""{"object":"investment","total":40123456,"records":[{"attributes":{"amount":987654321}}]}""", out)
    }

    @Test
    fun `un marcador escrito en el dato no se restaura como si fuera de la tabla`() {
        val table = PseudonymTable()
        val out = screen(mapOf("object" to "engagement"), """{"attributes":{"description":"[PERSONA_1] y Ana Ejemplo"}}""", table)
        assertEquals("""{"attributes":{"description":"(PERSONA_1) y [PERSONA_1]"}}""", out)
        assertEquals("(PERSONA_1) y Ana Ejemplo", table.restore("(PERSONA_1) y [PERSONA_1]"))
    }

    @Test
    fun `un error o un resumen que no es json solo se seudonimiza`() {
        assertEquals("""{"error":"Missing permission READ"}""", screen(mapOf("object" to "person"), """{"error":"Missing permission READ"}"""))
        assertEquals("person record $id", screen(mapOf("object" to "person", "id" to id), "person record $id"))
        assertEquals("refused: [PERSONA_1] no existe", screen(emptyMap(), "refused: Ana Ejemplo no existe"))
    }

    // un texto con forma de json que no se puede leer no se envía: sin leerlo no se sabe qué campo es restringido (falla cerrado)
    @Test
    fun `un texto con forma de json que no se puede leer no sale y no dice el dato`() {
        val broken = assertThrows<IllegalStateException> { screen(emptyMap(), "{no es json Ana Ejemplo") }
        assertFalse("Ana" in broken.message.orEmpty() || broken.cause != null, broken.message)
        val deep =
            "[".repeat(1200) +
                """{"relatedObject":"engagement_participant","records":[{"attributes":{"participant_name":"Rosa Ccori Ejemplo"}}]}""" +
                "]".repeat(1200)
        val failure = assertThrows<IllegalStateException> { screen(mapOf("object" to "engagement"), deep) }
        assertFalse("Rosa" in failure.message.orEmpty(), failure.message)
    }

    @Test
    fun `los decimales pasan tal cual`() {
        val text = """{"object":"investment","records":[{"attributes":{"amount":10000000.00,"ratio":0.80,"count":3}}]}"""
        assertEquals(text, screen(mapOf("object" to "investment"), text))
    }
}
