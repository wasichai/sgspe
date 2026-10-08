package sgspe.privacy

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class DisclosureTest {
    @Test
    fun `los tres niveles del catalogo se reconocen y cualquier otra cosa no`() {
        assertEquals(Disclosure.INTERNO, Disclosure.parse("INTERNO"))
        assertEquals(Disclosure.EXTERNO, Disclosure.parse("EXTERNO"))
        assertEquals(Disclosure.PUBLICO, Disclosure.parse("PUBLICO"))
        for (raw in listOf("interno", "PÚBLICO", "", null, 1)) assertNull(Disclosure.parse(raw), raw.toString())
    }
}
