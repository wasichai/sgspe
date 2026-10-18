package sgspe.deliverable

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import wasichai.core.common.WasichaiException
import java.util.UUID

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

    // revisión de la Tarea 15: el 409 PROJECT_MISMATCH nunca falla abierto. un resumen ejecutivo sin proyecto en la sugerencia se
    // compara con su destino (el proyecto); todo proyecto conocido debe ser el de la sección, y el resumen exige conocer uno
    @Test
    fun `el proyecto de la seccion se compara con todo proyecto conocido de la sugerencia`() {
        val own = UUID.randomUUID()
        val other = UUID.randomUUID()
        // resumen ejecutivo sin proyecto en la sugerencia y una sección de otro proyecto que su destino: 409
        assertFalse(DeliverableSections.projectMatches(other, listOf(null, own), requireKnown = true))
        assertTrue(DeliverableSections.projectMatches(own, listOf(null, own), requireKnown = true))
        assertTrue(DeliverableSections.projectMatches(own, listOf(own, own), requireKnown = true))
        assertFalse(DeliverableSections.projectMatches(own, listOf(own, other), requireKnown = true))
        assertFalse(DeliverableSections.projectMatches(own, listOf(null, null), requireKnown = true))
        // un borrador de sección tiene la sección como destino: sin proyecto en la sugerencia no hay con qué comparar
        assertTrue(DeliverableSections.projectMatches(own, listOf(null), requireKnown = false))
        assertFalse(DeliverableSections.projectMatches(own, listOf(other), requireKnown = false))
    }

    // revisión final de 8.2c, Minor 4: un resumen ejecutivo solo se copia a una sección que el tipo marca con IA; si no, 422
    // SECTION_NOT_ASSISTED sobre attributes.section antes de escribir (el origen ASISTIDO POR IA no tiene vuelta atrás)
    @Test
    fun `un resumen ejecutivo solo se copia a una seccion que el tipo marca con IA`() {
        val types = DeliverableTypes(listOf(SocialDiagnosisType(), FieldReportType()))
        DeliverableSections.requireAssisted(types, SocialDiagnosisType.NAME, "introduccion")
        DeliverableSections.requireAssisted(types, FieldReportType.NAME, "antecedentes")
        for ((type, key) in listOf(
            SocialDiagnosisType.NAME to "portada",
            SocialDiagnosisType.NAME to "indice",
            FieldReportType.NAME to "apoyo",
            "OTRO" to "x"
        )) {
            val e = assertThrows<WasichaiException> { DeliverableSections.requireAssisted(types, type, key) }
            assertEquals("SECTION_NOT_ASSISTED" to "attributes.section", e.violations.first().let { it.message to it.field }, "$type $key")
        }
    }

    // I1 de la revisión de #105 a #111: las secciones del tipo que el entregable no tiene, en el orden del tipo
    @Test
    fun `el completado crea solo las secciones que faltan, en el orden del tipo`() {
        val type = SocialDiagnosisType()
        val present = type.sections.map { it.key }.toSet() - setOf("reuniones_politicas", "percepciones_mineria")
        assertEquals(
            listOf("percepciones_mineria", "reuniones_politicas"),
            DeliverableSectionsListener.missing(type, present).map { it.key }
        )
        assertTrue(DeliverableSectionsListener.missing(type, type.sections.map { it.key }.toSet()).isEmpty())
    }
}
