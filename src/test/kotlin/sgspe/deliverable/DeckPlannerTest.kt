package sgspe.deliverable

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import sgspe.privacy.Disclosure
import sgspe.privacy.ReportDisclosure
import sgspe.privacy.ReportFixtures
import sgspe.privacy.ReportModel
import java.io.File

// el planificador de presentaciones (deliverables.md §16.3 a §16.5, RQ-REP-035 a RQ-REP-038): T1 y T2 ensamblados con sus
// fuentes falsas, divulgados por nivel y contados a cada público. todo ficticio (RQ-SEC-018)
class DeckPlannerTest {
    // T1 con narrativa que trae datos que ningún nivel externo puede mostrar: un nombre por mención, un teléfono y un documento
    private val t1: ReportModel =
        runBlocking {
            SocialDiagnosisType()
                .assemble(
                    T1Source.input(
                        mapOf(
                            "introduccion" to "Se conversó con [[person:${T1Source.PERSON}]], que dio su teléfono 987654321 y su DNI 40123456.",
                            "necesidades" to "1. Reforzar el equipo social en las localidades altas.\n2. Ordenar el registro de compromisos por localidad."
                        )
                    ),
                    T1Source.source()
                )
        }.model

    // el T1 sintético con material para la revisión visual (DeckDemo)
    private val demo: ReportModel = DeckDemo.model()

    private val t2: ReportModel =
        FieldReportTypeTest().let { fixture -> runBlocking { FieldReportType().assemble(fixture.input(), fixture.source()) }.model }

    // el T2 sintético y los bytes de sus fotos
    private val t2demo = DeckDemo.t2()

    private fun plan(
        model: ReportModel,
        level: Disclosure,
        audience: Audience
    ): DeckSpec =
        DeckPlanner.plan(DeckPlanner.Input(ReportDisclosure.apply(model, level), level, audience, if (model === t2demo.first) t2demo.second else emptyMap()))

    private fun text(deck: DeckSpec) = String(deck.bytes(), Charsets.UTF_8)

    @Test
    fun `cada publico cuenta el mismo T1 con su propio arco`() {
        val direccion = plan(t1, Disclosure.EXTERNO, Audience.DIRECCION).slides.map { it.id }
        val campo = plan(t1, Disclosure.EXTERNO, Audience.CAMPO).slides.map { it.id }
        val investigacion = plan(t1, Disclosure.EXTERNO, Audience.INVESTIGACION).slides.map { it.id }
        val comunidad = plan(t1, Disclosure.PUBLICO, Audience.COMUNIDAD).slides.map { it.id }
        assertEquals("tesis", direccion[1])
        assertTrue("que-es" in comunidad && "glosario" in comunidad)
        assertTrue(investigacion.indexOf("metodo") < investigacion.indexOf("cobertura"))
        assertTrue(campo.any { it.startsWith("parte-") })
        assertFalse(direccion.any { it.startsWith("parte-") })
        assertEquals(4, setOf(direccion, campo, investigacion, comunidad).size)
        listOf(direccion, campo, investigacion, comunidad).forEach {
            assertEquals("portada", it.first())
            assertEquals("cierre", it.last())
        }
    }

    @Test
    fun `las cifras son las mismas para todos los publicos de un nivel`() {
        fun trust(audience: Audience) =
            plan(t1, Disclosure.EXTERNO, audience)
                .slides
                .single { it.id == "confianza" }
                .data
                .let { (it as SlideData.Trend).series.single().values }
        val direccion = trust(Audience.DIRECCION)
        assertEquals(direccion, trust(Audience.CAMPO))
        assertEquals(direccion, trust(Audience.INVESTIGACION))
        val fact = ReportDisclosure.apply(t1, Disclosure.EXTERNO).facts!!.single { it.key == "confianza_por_mes" }
        assertEquals(fact.rows.map { DeckFormat.plain(it.value) }, direccion)
    }

    @Test
    fun `un publico no relaja el nivel`() {
        assertEquals("AUDIENCE_LEVEL_MISMATCH", assertThrows<DeckPlanner.NotAvailable> { plan(t1, Disclosure.EXTERNO, Audience.COMUNIDAD) }.code)
        assertEquals("AUDIENCE_LEVEL_MISMATCH", assertThrows<DeckPlanner.NotAvailable> { plan(t1, Disclosure.INTERNO, Audience.INSTITUCIONES) }.code)
        // el modelo tiene que venir ya divulgado para el nivel que se pide
        assertEquals(
            "LEVEL_NOT_APPLIED",
            assertThrows<DeckPlanner.NotAvailable> {
                DeckPlanner.plan(DeckPlanner.Input(ReportDisclosure.apply(t1, Disclosure.INTERNO), Disclosure.EXTERNO, Audience.DIRECCION))
            }.code
        )
    }

    @Test
    fun `un modelo congelado sin hechos tipados no da presentacion`() {
        val v1 = ReportFixtures.model()
        assertEquals("PUBLIC_NOT_AVAILABLE", assertThrows<DeckPlanner.NotAvailable> { plan(v1, Disclosure.PUBLICO, Audience.COMUNIDAD) }.code)
        assertEquals("PRESENTATION_NOT_AVAILABLE", assertThrows<DeckPlanner.NotAvailable> { plan(v1, Disclosure.EXTERNO, Audience.DIRECCION) }.code)
    }

    @Test
    fun `ningun nombre, telefono ni documento llega a la especificacion de EXTERNO o PUBLICO`() {
        val forbidden = listOf("Persona Ejemplo", "987654321", "40123456", "Vivienda del lote 7", "B001-123", "Ferretería Ejemplo")
        val decks =
            listOf(
                plan(t1, Disclosure.EXTERNO, Audience.DIRECCION),
                plan(t1, Disclosure.EXTERNO, Audience.CAMPO),
                plan(t1, Disclosure.PUBLICO, Audience.COMUNIDAD),
                plan(t1, Disclosure.PUBLICO, Audience.INSTITUCIONES),
                plan(t2, Disclosure.EXTERNO, Audience.INSTITUCIONES),
                plan(t2, Disclosure.PUBLICO, Audience.COMUNIDAD)
            )
        decks.forEach { deck -> forbidden.forEach { assertFalse(it in text(deck), "$it en ${deck.meta.audience} ${deck.meta.level}") } }
        // PUBLICO no lleva citas, mapas ni tablas de registros
        decks.filter { it.meta.level == "PUBLICO" }.forEach { deck ->
            assertTrue(deck.slides.none { it.data is SlideData.Quote || it.data is SlideData.MapView || it.data is SlideData.Table })
            assertTrue(deck.assets.isEmpty())
        }
    }

    @Test
    fun `PUBLICO no suma totales ni deriva conteos`() {
        val deck = plan(t1, Disclosure.PUBLICO, Audience.INSTITUCIONES)
        val ids = deck.slides.map { it.id }
        assertFalse("campo-por-localidad" in ids)
        val coverage = deck.slides.single { it.id == "cobertura" }.data as SlideData.Figures
        assertEquals(listOf("Localidades en las áreas de influencia", "Meses de registro"), coverage.items.map { it.label })
        assertFalse(deck.slides.any { "interacciones;" in it.title || " de " in it.title && it.id == "compromisos-estado" })
    }

    @Test
    fun `cada titulo de contenido cabe en dos lineas con la letra de su publico`() {
        for ((model, level, audience) in combos()) {
            val deck = plan(model, level, audience)
            deck.slides
                .filter { it.data !is SlideData.Cover && it.data !is SlideData.Section && it.data !is SlideData.Closing && it.id != "tesis" }
                .forEach { assertTrue(DeckPlanner.titleFits(it.title, audience), "${audience.name}: ${it.title}") }
        }
    }

    @Test
    fun `la especificacion es determinista y cambia con el publico`() {
        val a = plan(t1, Disclosure.EXTERNO, Audience.DIRECCION).bytes()
        val b = plan(t1, Disclosure.EXTERNO, Audience.DIRECCION).bytes()
        assertTrue(a.contentEquals(b))
        assertNotEquals(String(a), String(plan(t1, Disclosure.EXTERNO, Audience.CAMPO).bytes()))
    }

    @Test
    fun `el pie dice de que version deriva y que no es un documento emitido`() {
        val deck = plan(t1, Disclosure.EXTERNO, Audience.INSTITUCIONES)
        assertEquals("Derivada de ${t1.meta.code} rev. ${t1.meta.issueRevision}, nivel EXTERNO; no es un documento emitido", deck.meta.derivation)
    }

    @Test
    fun `el acento del tenant se oscurece hasta 4,5 a 1 con el blanco`() {
        assertEquals("#1F4E79", DeckPlanner.accent(null))
        val light = DeckPlanner.accent("FFE699")
        assertTrue(DeckPlanner.contrast(light, "#FFFFFF") >= 4.5, light)
        assertEquals("#1F4E79", DeckPlanner.accent("nada"))
    }

    @Test
    fun `la escala tipografica coincide con la del paquete de laminas`() {
        val tokens = File("slides/src/design/tokens.ts").readText()
        val base = Regex("""const BASE: TypeScale = \{([^}]*)\}""").find(tokens)!!.groupValues[1]
        val scale = DeckDesign.scale(Audience.DIRECCION)
        listOf(
            "display" to scale.display,
            "title" to scale.title,
            "lead" to scale.lead,
            "body" to scale.body,
            "small" to scale.small,
            "min" to scale.min
        ).forEach { (k, v) ->
            assertTrue(Regex("""\b$k: $v\b""").containsMatchIn(base), "$k: $v")
        }
        assertTrue(Regex("""title: 68, lead: 46, body: 38, small: 30, figure: 176, min: 28""").containsMatchIn(tokens))
    }

    // las especificaciones de demostración que el paquete de láminas valida con su esquema y compone (slides/fixtures/generated):
    // si cambian, se regeneran con -Dsgspe.decks.write=true y pasan por la revisión visual (§16.9)
    @Test
    fun `las especificaciones generadas coinciden con las del paquete de laminas`() {
        val dir = File("slides/fixtures/generated")
        val write = System.getProperty("sgspe.decks.write") == "true"
        if (write) dir.mkdirs()
        for ((model, level, audience) in combos()) {
            val name = "${if (model === t2demo.first) "t2" else "t1"}-${audience.name.lowercase()}-${level.name.lowercase()}.json"
            val bytes = plan(model, level, audience).bytes()
            val file = File(dir, name)
            if (write) file.writeBytes(bytes) else assertEquals(file.readText(), String(bytes), name)
        }
    }

    private fun combos(): List<Triple<ReportModel, Disclosure, Audience>> =
        listOf(
            Triple(demo, Disclosure.INTERNO, Audience.DIRECCION),
            Triple(demo, Disclosure.EXTERNO, Audience.CAMPO),
            Triple(demo, Disclosure.EXTERNO, Audience.INVESTIGACION),
            Triple(demo, Disclosure.EXTERNO, Audience.INSTITUCIONES),
            Triple(demo, Disclosure.PUBLICO, Audience.COMUNIDAD),
            Triple(t2demo.first, Disclosure.EXTERNO, Audience.INSTITUCIONES),
            Triple(t2demo.first, Disclosure.EXTERNO, Audience.DIRECCION),
            Triple(t2demo.first, Disclosure.EXTERNO, Audience.CAMPO),
            Triple(t2demo.first, Disclosure.PUBLICO, Audience.COMUNIDAD)
        )
}
