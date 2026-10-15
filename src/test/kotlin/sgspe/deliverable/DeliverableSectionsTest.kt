package sgspe.deliverable

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import wasichai.core.common.WasichaiException

// especificación de F8 §2.2.9 y §3.9, ASM-137 y decisión 19 del plan 8.2c (Review Focus 4): al adoptar, [PERSONA:<id>] pasa a
// [[person:<id>]]; una persona sin id es 422 UNRESOLVED_PSEUDONYM y otro marcador, 400 PSEUDONYM_NOT_ALLOWED
class DeliverableSectionsTest {
    private val id = "11111111-1111-1111-1111-11111111aaaa"

    private fun code(text: String): String = assertThrows<WasichaiException> { DeliverableSections.markers(text) }.violations.first().message

    @Test
    fun `los marcadores se convierten o se rechazan antes de escribir`() {
        assertEquals("Se acordó con [[person:$id]].", DeliverableSections.markers("Se acordó con [PERSONA:${id.uppercase()}]."))
        assertEquals("Sin marcadores.", DeliverableSections.markers("Sin marcadores."))
        for (text in listOf("Habló [PERSONA].", "Habló [PERSONA_1].")) assertEquals("UNRESOLVED_PSEUDONYM", code(text), text)
        for (text in listOf("DNI [DOCUMENTO].", "Llamar al [TELEFONO_2].", "Ver [CITA].")) assertEquals("PSEUDONYM_NOT_ALLOWED", code(text), text)
    }

    // Tarea 14: SectionText manda el nombre entre «», así que el modelo puede devolver «[PERSONA:<id>]». la conversión quita
    // las comillas solo cuando las dos envuelven el marcador; una sola, o unas que envuelven más texto, se quedan
    @Test
    fun `las comillas angulares que envuelven el marcador se quitan al convertirlo`() {
        assertEquals("Se acordó con [[person:$id]].", DeliverableSections.markers("Se acordó con «[PERSONA:$id]»."))
        assertEquals("«[[person:$id]] y [[person:$id]]»", DeliverableSections.markers("«[PERSONA:$id] y «[PERSONA:$id]»»"))
        assertEquals("con «[[person:$id]].", DeliverableSections.markers("con «[PERSONA:$id]."))
        assertEquals("con [[person:$id]]».", DeliverableSections.markers("con [PERSONA:$id]»."))
        assertEquals("« [[person:$id]] »", DeliverableSections.markers("« [PERSONA:$id] »"))
        assertEquals("«dijo [[person:$id]]»", DeliverableSections.markers("«dijo [PERSONA:$id]»"))
    }
}
