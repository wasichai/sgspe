package sgspe.ai

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

// el diccionario de nombres de quien pregunta (especificación de F7 §2.2.4): las person que lee con nombres y apellidos; las demás
// no entran
class PersonNamesTest {
    @Test
    fun `las personas con nombres y apellidos entran y las demas no`() {
        val ana = "00000000-0000-0000-0000-0000000000a1"
        val dictionary =
            PersonNames.of(
                listOf(
                    ana to mapOf("given_names" to "Ana", "surnames" to "Ejemplo"),
                    "00000000-0000-0000-0000-0000000000b2" to mapOf("sex" to "MASCULINO"),
                    "00000000-0000-0000-0000-0000000000c3" to mapOf("given_names" to "Solo")
                )
            )
        val table = PseudonymTable()
        assertEquals("[PERSONA_1] y Solo", Pseudonymizer.pseudonymize("Ana Ejemplo y Solo", dictionary, table))
        assertEquals("[PERSONA:$ana]", table.stable("[PERSONA_1]"))
    }
}
