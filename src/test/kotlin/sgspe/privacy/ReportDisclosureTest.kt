package sgspe.privacy

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// especificación de F8 §3.2, §2.2.9 y §2.2.10, deliverables.md §9.2 (criterios 11 y 25 de §13.2)
class ReportDisclosureTest {
    private val model = ReportFixtures.model()

    private fun texts(model: ReportModel): String = CanonicalJson.write(mapOf("s" to model.sections.map { it.blocks.map { b -> b.toString() } }))

    @Test
    fun `INTERNO nombra a las personas y no quita nada`() {
        val internal = ReportDisclosure.apply(model, Disclosure.INTERNO)
        assertEquals("INTERNO", internal.meta.level)
        assertTrue("Persona Ejemplo 1" in texts(internal))
        assertEquals(
            model.sections
                .single()
                .blocks.size,
            internal.sections
                .single()
                .blocks.size
        )
        assertEquals(3, internal.photos.size)
        assertEquals(
            "aW50ZXJubw==",
            internal.maps
                .getValue("predio")
                .internal!!
                .pngBase64
        )
    }

    @Test
    fun `EXTERNO no lleva nombres, restringidos, emisor, numero, fotos sin consentimiento ni comprobante`() {
        val external = ReportDisclosure.apply(model, Disclosure.EXTERNO)
        val text = texts(external)
        assertEquals("EXTERNO", external.meta.level)
        assertFalse("Persona Ejemplo 1" in text, text)
        assertTrue("Presidente, Comunidad Ejemplo" in text, text)
        assertFalse("católica" in text, text)
        assertFalse("Asociación Ejemplo" in text || "B001-123" in text, text)
        assertEquals(setOf(ReportFixtures.CONSENTED), external.photos.keys)
        val blocks = external.sections.single().blocks
        assertEquals(listOf(ReportFixtures.CONSENTED), blocks.filterIsInstance<Block.Photos>().single().evidence)
        assertEquals(
            "ZXh0ZXJubw==",
            external.maps
                .getValue("predio")
                .internal!!
                .pngBase64
        )
        assertTrue(external.people.isEmpty())
    }

    @Test
    fun `un dato vacio de consentimiento cuenta como identificable sin consentimiento`() {
        assertFalse(ReportDisclosure.publishable(PhotoRef("e", "f", "s", "a", PhotoRef.PHOTO)))
        assertTrue(ReportDisclosure.publishable(PhotoRef("e", "f", "s", "a", PhotoRef.PHOTO, identifiable = false)))
        assertFalse(ReportDisclosure.publishable(PhotoRef("e", "f", "s", "a", PhotoRef.RECEIPT, identifiable = false, consent = true)))
    }

    @Test
    fun `ningun tipo de F8 prepara PUBLICO`() {
        assertThrows(IllegalArgumentException::class.java) { ReportDisclosure.apply(model, Disclosure.PUBLICO) }
    }
}
