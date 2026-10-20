package sgspe.privacy

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// PUBLICO de las presentaciones (ASM-146, deliverables.md §16.4): sale del modelo nominal por una lista permitida que falla
// cerrada. solo los hechos tipados aptos y sin marca, con SmallCells en los conteos y «reservado» en los promedios y valores de
// menos de 5 casos; ningún párrafo, tabla, gráfico, mapa, mención, cita ni foto con personas
class PublicDisclosureTest {
    private val nominal = HostileFixtures.model()
    private val public = ReportDisclosure.apply(nominal, Disclosure.PUBLICO)

    private fun json(model: ReportModel) = String(CanonicalJson.bytes(model), Charsets.UTF_8)

    private fun fact(key: String) = public.facts.orEmpty().single { it.key == key }

    @Test
    fun `ningun marcador identificable sobrevive en PUBLICO`() {
        val text = json(public)
        HostileFixtures.NEVER_PUBLIC.forEach { assertFalse(it in text, it) }
        assertFalse("[[person:" in text)
    }

    @Test
    fun `PUBLICO deja solo titulos, fotos sin personas y los indices`() {
        val blocks = public.sections.single().blocks
        assertEquals(listOf("Heading", "Photos", "Toc"), blocks.map { it::class.simpleName })
        assertEquals(listOf(HostileFixtures.LANDSCAPE), (blocks[1] as Block.Photos).evidence)
        assertEquals(setOf(HostileFixtures.LANDSCAPE), public.photos.keys)
        assertEquals("Percepciones de una persona", public.sections.single().title)
        assertTrue(public.maps.isEmpty())
        assertTrue(public.people.isEmpty())
        assertNull(public.meta.coverMap)
        assertEquals("PUBLICO", public.meta.level)
    }

    @Test
    fun `PUBLICO deja solo los hechos aptos, sin marca y de clase conocida`() {
        assertEquals(
            listOf("grupos_por_localidad", "confianza_por_mes", "monto_del_aporte", "indicadores_publicos"),
            public.facts.orEmpty().map { it.key }
        )
    }

    @Test
    fun `un conteo pasa por SmallCells y lo oculto suma al menos 5`() {
        val rows = fact("grupos_por_localidad").rows
        assertEquals(listOf(12, null, null, null), rows.map { it.count })
        assertEquals(listOf(null, true, true, true), rows.map { it.reserved })
    }

    @Test
    fun `un promedio o un valor con menos de 5 casos, o sin ellos, sale reservado`() {
        val trust = fact("confianza_por_mes").rows
        assertEquals(listOf("3.20", null, null), trust.map { it.value })
        assertEquals(listOf(9, null, null), trust.map { it.n })
        assertEquals(listOf(null, true, true), trust.map { it.reserved })
        val indicators = fact("indicadores_publicos").rows
        assertEquals(listOf("80", null), indicators.map { it.value })
        assertEquals(listOf("90", null), indicators.map { it.target })
    }

    @Test
    fun `un monto sale tal cual`() {
        assertEquals("500.00", fact("monto_del_aporte").rows.single().value)
    }

    @Test
    fun `EXTERNO quita los hechos marcados y resuelve las menciones de los rotulos`() {
        val external = ReportDisclosure.apply(nominal, Disclosure.EXTERNO)
        val keys = external.facts.orEmpty().map { it.key }
        assertFalse("posturas" in keys)
        assertTrue("monitoreo" in keys)
        val text = json(external)
        listOf(HostileFixtures.NAME, HostileFixtures.PHONE, HostileFixtures.DNI, HostileFixtures.PLACE).forEach { assertFalse(it in text, it) }
        assertEquals(
            "Reuniones con Presidente Zz, Comunidad Zz",
            external.facts
                .orEmpty()
                .single { it.key == "monitoreo" }
                .rows
                .single()
                .labels
                .single()
        )
    }

    @Test
    fun `INTERNO conserva todos los hechos`() {
        val internal = ReportDisclosure.apply(nominal, Disclosure.INTERNO)
        assertEquals(nominal.facts!!.size, internal.facts!!.size)
    }

    @Test
    fun `un modelo sin hechos da PUBLICO sin hechos`() {
        val bare = ReportDisclosure.apply(ReportFixtures.model(), Disclosure.PUBLICO)
        assertNull(bare.facts)
        assertFalse(HostileFixtures.NAME in json(bare))
        assertFalse("Persona Ejemplo 1" in json(bare))
    }
}
