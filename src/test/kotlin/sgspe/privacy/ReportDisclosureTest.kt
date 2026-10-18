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

    // revisión final del plan 8.2b, M3: las fuentes de las fotos y los mapas y el texto alternativo de las fotos también resuelven
    // sus menciones, como la fuente de una tabla
    @Test
    fun `las fuentes de fotos y mapas y el texto alternativo resuelven sus menciones`() {
        val mention = "[[${ReportFixtures.PERSON}]]"
        val sourced =
            model.copy(
                sections =
                    model.sections.map { section ->
                        section.copy(
                            blocks =
                                section.blocks.map { block ->
                                    when (block) {
                                        is Block.Photos -> block.copy(source = "Foto de $mention")
                                        is Block.MapFigure -> block.copy(source = "Levantado por $mention")
                                        else -> block
                                    }
                                }
                        )
                    },
                photos = model.photos.mapValues { (_, photo) -> photo.copy(alt = "Entrega a $mention") }
            )
        for (level in listOf(Disclosure.INTERNO, Disclosure.EXTERNO)) {
            val leveled = ReportDisclosure.apply(sourced, level)
            val text = texts(leveled) + leveled.photos.values.joinToString { it.alt }
            assertFalse("[[person" in text || ReportFixtures.PERSON in text, "$level: $text")
            assertEquals(level == Disclosure.INTERNO, "Persona Ejemplo 1" in text, "$level: $text")
            assertTrue("Foto de" in text && "Levantado por" in text && "Entrega a" in text, "$level: $text")
        }
    }

    @Test
    fun `ningun tipo de F8 prepara PUBLICO`() {
        assertThrows(IllegalArgumentException::class.java) { ReportDisclosure.apply(model, Disclosure.PUBLICO) }
    }

    @Test
    fun `la nota EXTERNO solo sale en EXTERNO y una marca restringida sigue fuera de EXTERNO`() {
        val extra = listOf(Block.Paragraph("Aviso reservado", Mark.EXTERNAL_ONLY), Block.Paragraph("Dato sensible", Mark.restricted("x.y")))
        val withNote = model.copy(sections = model.sections.map { it.copy(blocks = it.blocks + extra) })
        val external = texts(ReportDisclosure.apply(withNote, Disclosure.EXTERNO))
        val internal = texts(ReportDisclosure.apply(withNote, Disclosure.INTERNO))
        assertTrue("Aviso reservado" in external)
        assertFalse("Dato sensible" in external)
        assertFalse("Aviso reservado" in internal)
        assertTrue("Dato sensible" in internal)
        assertEquals(withNote, CanonicalJson.read(CanonicalJson.bytes(withNote)))
    }

    @Test
    fun `las categorias de un grafico tambien resuelven sus menciones`() {
        val id = "11111111-1111-1111-1111-111111111111"
        val chart = Block.LineChart("Confianza", listOf("[[person:$id]]", "2026-09"), listOf(ChartSeries("Serie", listOf("1", "2"))))
        val withChart =
            model.copy(
                people = mapOf("person:$id" to MentionTarget("Persona Ejemplo 1", "Presidente", "Comunidad Ejemplo")),
                sections = listOf(model.sections.single().copy(blocks = listOf(chart)))
            )
        val external = ReportDisclosure.apply(withChart, Disclosure.EXTERNO)
        val categories =
            (
                external.sections
                    .single()
                    .blocks
                    .single() as Block.LineChart
            ).categories
        assertEquals(listOf("Presidente, Comunidad Ejemplo", "2026-09"), categories)
    }
}
