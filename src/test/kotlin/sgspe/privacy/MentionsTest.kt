package sgspe.privacy

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

// especificación de F8 §2.2.9, ASM-137: INTERNO al nombre; EXTERNO al cargo y la organización, al código GI- o a la localidad
class MentionsTest {
    private val id = "11111111-1111-1111-1111-111111111111"
    private val text = "Se habló con [[person:$id]] y con [[stakeholder:${id.uppercase()}]]."

    @Test
    fun `INTERNO resuelve al nombre`() {
        val people = mapOf("person:$id" to MentionTarget("Persona Ejemplo 1", "Presidente", "Comunidad Ejemplo"))
        assertEquals("Se habló con Persona Ejemplo 1 y con una persona.", Mentions.resolve(text, people, Disclosure.INTERNO))
    }

    @Test
    fun `EXTERNO cita por cargo, por codigo o por localidad, nunca por nombre ni por id`() {
        fun cite(target: MentionTarget) = Mentions.resolve("[[person:$id]]", mapOf("person:$id" to target), Disclosure.EXTERNO)
        assertEquals("Presidente, Comunidad Ejemplo", cite(MentionTarget("Persona Ejemplo 1", "Presidente", "Comunidad Ejemplo", "GI-1")))
        assertEquals("GI-111111111111", cite(MentionTarget("Persona Ejemplo 1", stakeholderCode = "GI-111111111111")))
        assertEquals("una persona de Caserío Ejemplo", cite(MentionTarget("Persona Ejemplo 1", locality = "Caserío Ejemplo")))
        assertEquals("una persona", cite(MentionTarget("Persona Ejemplo 1")))
    }

    @Test
    fun `las claves salen en orden, en minusculas y sin repetir`() {
        assertEquals(listOf("person:$id", "stakeholder:$id"), Mentions.keys("$text [[person:$id]]"))
    }
}
