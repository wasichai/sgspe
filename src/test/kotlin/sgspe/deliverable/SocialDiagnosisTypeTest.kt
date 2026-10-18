package sgspe.deliverable

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import sgspe.dashboard.MonitoringRow
import sgspe.dashboard.Official
import sgspe.privacy.Block
import sgspe.privacy.ChartPoint
import sgspe.privacy.Disclosure
import sgspe.privacy.Mark
import sgspe.privacy.PersonalDataScan
import sgspe.privacy.RegisterSheets
import sgspe.privacy.ReportDisclosure
import sgspe.privacy.ReportModel
import sgspe.privacy.ReportWarning
import sgspe.stakeholder.InfluenceClassifier
import tools.jackson.databind.json.JsonMapper
import java.io.File
import java.time.LocalDate
import java.util.UUID

// especificación de F8 §3.8 y §5 («Tipos», SocialDiagnosisTypeTest; plan 8.2c, Tareas 9 a 13): T1 ensamblado con una fuente
// falsa, sin base. todo ficticio (RQ-SEC-018)
class SocialDiagnosisTypeTest {
    private val type = SocialDiagnosisType()

    private fun assemble(
        source: FakeReportSource = T1Source.source(),
        content: Map<String, String> = emptyMap()
    ): Assembled = runBlocking { type.assemble(T1Source.input(content), source) }

    private fun ReportModel.blocks(key: String): List<Block> = sections.single { it.key == key }.blocks

    private fun ReportModel.tables(key: String): List<Block.Table> = blocks(key).filterIsInstance<Block.Table>()

    @Test
    fun `declara las 52 secciones en orden con sus capitulos y las asistidas por IA de la especificacion`() {
        val keys = type.sections.map { it.key }
        assertEquals(52, keys.size)
        assertEquals(keys.size, keys.toSet().size)
        assertEquals(listOf("portada", "indice", "introduccion"), keys.take(3))
        assertEquals(
            setOf(
                "introduccion",
                "areas",
                "contexto",
                "percepciones",
                "percepciones_mineria",
                "percepciones_proyecto",
                "percepciones_empresa",
                "grupos_estrategicos",
                "grupos_politicos",
                "grupos_monitoreo",
                "estrategias",
                "comunicacion",
                "contactos_regulares",
                "reuniones_informativas",
                "reuniones_politicas",
                "necesidades"
            ),
            type.sections
                .filter { it.assisted }
                .map { it.key }
                .toSet()
        )
        assertEquals((1..52).toList(), type.sections.map { it.order })
        assertTrue(type.requiresApprovedArea)
    }

    @Test
    fun `exige estudio, AIDS, periodo, corte y CRS de trabajo`() {
        val missing = runBlocking { type.missingParameters(emptyMap(), emptyMap()) }
        assertEquals(listOf("study", "area_of_influence", "period_start_date", "period_end_date", "cutoff_date", "project.working_crs"), missing)
    }

    @Test
    fun `los capitulos 1 a 4 salen del estudio, las areas y el trabajo de campo, con el mapa de areas`() {
        val assembled = assemble(content = mapOf("introduccion" to "Se conversó con [[person:${T1Source.PERSON}]]."))
        val model = assembled.model
        assertEquals(listOf(Block.Toc::class, Block.CaptionList::class, Block.CaptionList::class), model.blocks("indice").map { it::class })
        val intro = (model.blocks("introduccion").first() as Block.Paragraph).markdown
        assertTrue("Campaña 2026" in intro && "Asociación Ejemplo" in intro && "Consultora Ejemplo" in intro, intro)
        assertEquals("Persona Ejemplo 1", model.people.getValue("person:${T1Source.PERSON}").name)
        val fieldwork = model.tables("trabajo_campo").single()
        assertEquals(listOf("Ámbito", "Localidad", "Entrevistas", "Fichas de observación"), fieldwork.header)
        assertEquals(listOf("AIDS", "Caserío Ejemplo Alto", "1", "0"), fieldwork.rows[0].map { it.text })
        assertEquals(listOf("AIIS", "Caserío Ejemplo Bajo", "0", "1"), fieldwork.rows[1].map { it.text })
        val dates = (model.blocks("trabajo_campo").first() as Block.Paragraph).markdown
        assertTrue(dates.endsWith("Visitas de campo en esas fechas:** 1"), dates)
        val aids = model.tables("aids").single()
        assertTrue(aids.rows.any { it[0].text == "Caserío Ejemplo Alto" && it[1].text == "Incluido" }, "${aids.rows}")
        assertTrue(aids.rows.any { it[0].text == "Comunidad Ejemplo" })
        assertTrue(
            model
                .tables("aiis")
                .single()
                .rows
                .any { it[0].text == "Caserío Ejemplo Bajo (En separación)" }
        )
        assertEquals(Block.MapFigure(DiagnosisStudy.AREAS_MAP, "Áreas de influencia social del proyecto"), model.blocks("aiis").last())
        val map = assembled.maps.single { it.key == DiagnosisStudy.AREAS_MAP }
        assertEquals(MapFrameKind.AREAS, map.frame)
        assertEquals(listOf(T1Source.AIDS, T1Source.AIIS).map(UUID::fromString), map.areas)
        assertEquals("Asociación Ejemplo", map.author)
        val scope = model.sections.single { it.key == "alcance" }
        assertEquals(2 to 3, scope.level to scope.chapter)
        assertEquals(null, model.meta.coverMap)
    }

    // #111: cada contenido sale una vez y la forma sigue al original
    @Test
    fun `la portada no repite proyecto, el capitulo 4 no repite criterios y 3 2 remite al Anexo 2`() {
        val model = assemble().model
        assertEquals("Diagnóstico social del Proyecto Ejemplo A", (model.blocks("portada").single() as Block.Paragraph).markdown)
        assertEquals(emptyList<Block>(), model.blocks("areas"))
        val criteria = model.blocks("aids").filterIsInstance<Block.Paragraph>().map { it.markdown }
        assertEquals(listOf("**Criterios:** Impactos reales o percibidos de la exploración."), criteria)
        assertEquals(
            1,
            model.sections
                .flatMap { it.blocks }
                .filterIsInstance<Block.Paragraph>()
                .count { "Impactos reales o percibidos" in it.markdown }
        )
        val design = model.blocks("diseno")
        assertEquals(emptyList<Block.Table>(), design.filterIsInstance<Block.Table>())
        assertTrue("Anexo 2" in (design.single() as Block.Paragraph).markdown)
        assertEquals(1, model.tables("anexo_instrumentos").size)
    }

    @Test
    fun `el rotulo de criterios no se duplica y la meta sale una vez`() {
        val plain =
            assemble()
                .model
                .blocks("estrategias")
                .filterIsInstance<Block.Paragraph>()
                .map { it.markdown }
        assertTrue(plain.any { it.startsWith("**Meta del periodo.**") }, "$plain")
        val withText = assemble(content = mapOf("estrategias" to "La meta del periodo es mantener la licencia social.")).model.blocks("estrategias")
        assertTrue(withText.filterIsInstance<Block.Paragraph>().none { "**Meta del periodo.**" in it.markdown }, "$withText")
        assertTrue(withText.filterIsInstance<Block.Paragraph>().any { "La meta del periodo" in it.markdown })
        // M6 de la revisión de #105 a #111: con dos planes, la narrativa no oculta la meta de ninguno
        val source = T1Source.source()
        source.add(
            "engagement_plan",
            T1Source.id(800),
            null,
            "project" to T1Source.PROJECT,
            "name" to "Plan 2027",
            "strategic_objective" to "Ampliar el diálogo."
        )
        val two = assemble(source, mapOf("estrategias" to "Texto general de las estrategias.")).model.blocks("estrategias")
        assertEquals(2, two.filterIsInstance<Block.Paragraph>().count { "**Meta del periodo.**" in it.markdown }, "$two")
    }

    @Test
    fun `la historia lleva un subtitulo de nivel 4 por localidad`() {
        val history = assemble().model.blocks("historia")
        val i = history.indexOf(Block.Heading(4, "Caserío Ejemplo Alto"))
        assertTrue(i > 0, "$history")
        assertTrue((history[i + 1] as Block.Paragraph).markdown.startsWith("Reconocimiento:"))
        assertTrue(history.filterIsInstance<Block.Paragraph>().none { it.markdown.startsWith("**Caserío") })
    }

    // #80: la ubicación sale de las unidades administrativas de las localidades del AIDS y de sus unidades superiores
    @Test
    fun `la introduccion da la ubicacion desde las unidades del AIDS`() {
        val model = assemble(T1Source.full()).model
        val text = model.text("introduccion")
        assertTrue(
            text.contains("**Ubicación:** distrito de Distrito Ejemplo, provincia de Provincia Ejemplo, región Región Ejemplo"),
            text
        )
    }

    @Test
    fun `sin unidades administrativas la ubicacion dice Sin dato`() {
        assertTrue(assemble().model.text("introduccion").contains("**Ubicación:** Sin dato"))
    }

    // #80 y RN-STK-007: la última evaluación de cada grupo al corte; oposición es EN CONTRA y EN CONTRA PERO ABIERTO A
    // ESCUCHAR. la postura es restringida como en las Tablas 7.1 a 7.3: INTERNO da los conteos y EXTERNO los reserva
    @Test
    fun `la introduccion cuenta apoyos y oposicion sin nombres`() {
        val source = T1Source.full()
        source.add("stakeholder", T1Source.id(310), null, "project" to T1Source.PROJECT, "organization" to T1Source.BOARD, "stakeholder_scope" to "AIDS")
        source.add(
            "stakeholder_assessment",
            T1Source.id(311),
            null,
            "stakeholder" to T1Source.id(310),
            "assessment_date" to "2026-09-20",
            "stance" to "EN CONTRA PERO ABIERTO A ESCUCHAR"
        )
        // después del corte: no cuenta
        source.add(
            "stakeholder_assessment",
            T1Source.id(312),
            null,
            "stakeholder" to T1Source.id(310),
            "assessment_date" to "2026-10-05",
            "stance" to "A FAVOR"
        )
        val model = assemble(source).model
        val internal = ReportDisclosure.apply(model, Disclosure.INTERNO)
        val line = internal.text("introduccion").lines().single { it.startsWith("**Apoyos y oposición:**") }
        assertEquals(
            "**Apoyos y oposición:** 1 grupo de interés a favor, 2 en oposición y 0 neutrales, según la última evaluación al corte.",
            line.trim()
        )
        assertFalse(T1Source.personNames.any { line.contains(it) }, line)
        val external = ReportDisclosure.apply(model, Disclosure.EXTERNO)
        val reserved = external.text("introduccion").lines().single { it.startsWith("**Apoyos y oposición:**") }
        assertEquals("**Apoyos y oposición:** se reservan en la versión externa.", reserved.trim())
    }

    // #80: 4.2 con una tabla por microcuenca y «Otros miembros» para lo que no cae en ninguna
    @Test
    fun `la seccion 4_2 agrupa los miembros del AIIS por microcuenca`() {
        val tables = assemble(T1Source.full()).model.tables("aiis")
        assertEquals(
            listOf("Miembros del AIIS en la Microcuenca Norte", "Miembros del AIIS en la Microcuenca Sur", "Otros miembros del AIIS"),
            tables.map { it.title }
        )
        assertEquals(listOf("Caserío Ejemplo Bajo (En separación)"), tables[0].rows.map { it[0].text })
        assertEquals(listOf("Caserío Ejemplo Medio"), tables[1].rows.map { it[0].text })
        assertEquals(setOf("Caserío Ejemplo Lejano", "Distrito Ejemplo"), tables[2].rows.map { it[0].text }.toSet())
    }

    @Test
    fun `la seccion 4_2 sin microcuencas deja la tabla unica`() {
        val titles = assemble(T1Source.full(microbasins = emptyMap())).model.tables("aiis").map { it.title }
        assertEquals(listOf("Miembros del área de influencia indirecta social"), titles)
    }

    @Test
    fun `un AIDS que ya no esta aprobado deja FALLIDO con DELIVERABLE_PARAMETERS_MISSING`() {
        val source = T1Source.source()
        source.rows.getValue("area_of_influence").replaceAll { if (it.id == T1Source.AIDS) it.copy(state = "superseded") else it }
        val failure = assertThrows<DraftFailure> { assemble(source) }
        assertEquals(DeliverableDrafts.PARAMETERS_MISSING to "area_of_influence", failure.code to failure.section)
    }

    @Test
    fun `un periodo que falta o no se lee deja FALLIDO con DELIVERABLE_PARAMETERS_MISSING sobre ese campo`() {
        fun failure(
            field: String,
            value: String?
        ): DraftFailure {
            val input = T1Source.input()
            val d = input.deliverable
            val changed = AssemblyInput(d.copy(attributes = d.attributes + (field to value)), input.sections, null, input.templateSha256, "A", "0", input.today)
            return assertThrows<DraftFailure> { runBlocking { type.assemble(changed, T1Source.source()) } }
        }
        assertEquals(DeliverableDrafts.PARAMETERS_MISSING to "period_end_date", failure("period_end_date", null).let { it.code to it.section })
        assertEquals(DeliverableDrafts.PARAMETERS_MISSING to "period_start_date", failure("period_start_date", "no es fecha").let { it.code to it.section })
    }

    @Test
    fun `la fuente falsa rechaza un objeto no declarado y quita los campos no declarados`() {
        val source = FakeReportSource(mapOf("project" to setOf("name")))
        T1Source.source(source)
        val row = runBlocking { source.one("project", UUID.fromString(T1Source.PROJECT)) }
        assertEquals(setOf("name"), row!!.attributes.keys)
        assertThrows<IllegalStateException> { runBlocking { source.all("person") } }
    }

    @Test
    fun `el capitulo 5 va por tema con un titulo por ambito, localidades en columnas y la matriz con X`() {
        val model = assemble().model
        val history = model.blocks("historia")
        assertEquals(Block.Heading(3, DiagnosisScene.AIDS_TITLE), history[0])
        val low = history.indexOf(Block.Heading(4, "Caserío Ejemplo Bajo"))
        assertTrue(low > 0 && "Linderos con el caserío vecino" in (history[low + 1] as Block.Paragraph).markdown, "$history")
        val population = model.tables("poblacion").first()
        assertEquals(listOf("Caserío Ejemplo Alto", "120", "480", "4.00", "10/08/2026", "Trabajo de campo", "Alta"), population.rows.single().map { it.text })
        val services = model.tables("servicios")
        assertEquals(listOf("Servicio", "Caserío Ejemplo Alto"), services[0].header)
        assertEquals(listOf("Fuente de agua", "Manantial"), services[0].rows.first().map { it.text })
        assertEquals(listOf("Fuente de agua", DiagnosisScene.SIN_DATO), services[1].rows.first().map { it.text })
        val matrix = model.tables("organizaciones").first()
        assertTrue(matrix.rows.any { row -> row.map { it.text } == listOf("Junta Directiva Comunal Ejemplo", "Organización comunal", "X") }, "${matrix.rows}")
        assertTrue(matrix.rows.any { row -> row.map { it.text } == listOf("Programa social del Estado", "Programa del Estado", "X") }, "${matrix.rows}")
        val roads = model.tables("vias").first()
        assertEquals(listOf("Caserío Ejemplo Alto", "Trocha", "Caserío Ejemplo Bajo", "Regular", "Combi", "40 min"), roads.rows.single().map { it.text })
        assertTrue(model.tables("educacion_salud").any { table -> table.rows.any { it[1].text == "I. E. N° 101" } })
    }

    // #110: n localidades más en el AIDS, cada una con su perfil y una organización
    private fun withLocalities(count: Int): FakeReportSource {
        val source = T1Source.source()
        repeat(count) { i ->
            val locality = T1Source.id(800 + i * 4)
            source.add("locality", locality, null, "name" to "Caserío Ejemplo $i", "locality_type" to "CASERIO")
            source.add(
                "area_of_influence_member",
                T1Source.id(801 + i * 4),
                null,
                "area_of_influence" to T1Source.AIDS,
                "locality" to locality,
                "member_condition" to "INCLUIDO"
            )
            source.add(
                "locality_profile",
                T1Source.id(802 + i * 4),
                null,
                "locality" to locality,
                "study" to T1Source.STUDY,
                "crops" to "Maíz $i",
                "water_problems" to "Turbiedad $i"
            )
            source.add(
                "organization",
                T1Source.id(803 + i * 4),
                null,
                "name" to "Comité $i Ejemplo",
                "organization_type" to "ORGANIZACION COMUNAL",
                "locality" to locality
            )
        }
        return source
    }

    @Test
    fun `con mas de cinco localidades las tablas de economia y servicios llevan una fila por localidad`() {
        val model = assemble(withLocalities(5)).model
        val economy = model.tables("economia").first()
        assertEquals(listOf("Localidad", "Cultivos", "Ganadería", "Otras actividades", "Problemática (problemas del agua)"), economy.header)
        assertEquals(6, economy.rows.size)
        assertTrue(economy.wide)
        assertTrue(
            economy.rows.any { r ->
                r.map { it.text }.let { it[0] == "Caserío Ejemplo 3" && it[1] == "Maíz 3" && it[4] == "Turbiedad 3" }
            },
            "${economy.rows}"
        )
        assertEquals(
            "Localidad",
            model
                .tables("servicios")
                .first()
                .header
                .first()
        )
        assertEquals(
            6,
            model
                .tables("servicios")
                .first()
                .rows.size
        )
    }

    @Test
    fun `con cinco localidades o menos las tablas quedan con las localidades en columnas`() {
        val model = assemble(withLocalities(3)).model
        val economy = model.tables("economia").first()
        assertEquals(5, economy.header.size)
        assertEquals("Actividad", economy.header.first())
        assertEquals(
            "Problemática (problemas del agua)",
            economy.rows
                .last()
                .first()
                .text
        )
    }

    @Test
    fun `con mas de cinco localidades las organizaciones salen por tipo y los programas con X`() {
        val model = assemble(withLocalities(5)).model
        val tables = model.tables("organizaciones")
        val byType = tables.first()
        assertEquals("Localidad", byType.header.first())
        assertTrue("Organización comunal" in byType.header, "${byType.header}")
        assertEquals(6, byType.rows.size)
        val column = byType.header.indexOf("Organización comunal")
        assertEquals("1", byType.rows.single { it[0].text == "Caserío Ejemplo 2" }[column].text)
        val programs = tables[1]
        assertEquals(listOf("Localidad", "Programa social del Estado"), programs.header)
        assertEquals("X", programs.rows.single { it[0].text == "Caserío Ejemplo Alto" }[1].text)
        assertEquals("", programs.rows.single { it[0].text == "Caserío Ejemplo 2" }[1].text)
    }

    @Test
    fun `los impactos se agrupan por tipo de actividad con una columna por tipo de impacto`() {
        val source = T1Source.source()
        source.add(
            "potential_impact",
            T1Source.id(990),
            null,
            "project" to T1Source.PROJECT,
            "activity_type" to "PERFORACION",
            "impact_type" to "SOCIAL",
            "impact_nature" to "INDIRECTO",
            "impact_sign" to "POSITIVO",
            "description" to "Empleo local."
        )
        val table = assemble(source).model.tables("impactos").single()
        assertEquals(listOf("Tipo de actividad", "Impacto ambiental", "Impacto social"), table.header)
        val row = table.rows.single()
        assertEquals("Perforación", row[0].text)
        assertEquals("• Polvo en la vía. (directo, negativo)", row[1].text)
        assertEquals("• Empleo local. (indirecto, positivo)", row[2].text)
    }

    @Test
    fun `religion y proceso electoral llevan su marca y EXTERNO los quita`() {
        val model = assemble().model
        assertTrue(
            model.blocks("proceso_electoral").filterIsInstance<Block.Paragraph>().any { it.mark == Mark.restricted("locality_profile.electoral_process") }
        )
        assertTrue(model.blocks("costumbres").filterIsInstance<Block.Paragraph>().any { it.mark == Mark.restricted("locality_profile.religion") })
        val external = ReportDisclosure.apply(model, Disclosure.EXTERNO)
        assertTrue(external.blocks("proceso_electoral").filterIsInstance<Block.Paragraph>().none { "movimientos" in it.markdown })
        assertTrue(external.blocks("costumbres").filterIsInstance<Block.Paragraph>().none { "Católica" in it.markdown })
        assertTrue(
            external
                .tables("costumbres")
                .single()
                .rows
                .any { it[1].text == "Fiesta patronal" }
        )
    }

    @Test
    fun `una localidad sin nombre sale Sin dato y las secciones restringidas no quedan en un titulo solo`() {
        val source = T1Source.source()
        // una localidad del AIDS sin nombre ni perfil: ni «null» ni secciones vacias
        val nameless = T1Source.id(900)
        source.add("locality", nameless, null)
        source.add(
            "area_of_influence_member",
            T1Source.id(901),
            null,
            "area_of_influence" to T1Source.AIDS,
            "locality" to nameless,
            "member_condition" to "INCLUIDO"
        )
        source.add(
            "locality_profile",
            T1Source.id(902),
            null,
            "locality" to nameless,
            "study" to T1Source.STUDY,
            "electoral_process" to "Votan por listas.",
            "migration" to "Ninguna."
        )
        val model = assemble(source).model
        val text =
            model.sections
                .flatMap { it.blocks }
                .filterIsInstance<Block.Paragraph>()
                .joinToString("\n") { it.markdown }
        assertTrue("null" !in text, text)
        assertTrue(model.blocks("proceso_electoral").filterIsInstance<Block.Paragraph>().any { it.markdown.startsWith("**${DiagnosisScene.SIN_DATO}:**") })

        // sin ningun dato restringido: «Sin dato.» sin marca
        val bare = T1Source.source()
        bare.rows["locality_profile"]!!.replaceAll { it.copy(attributes = it.attributes - "electoral_process" - "religion") }
        bare.rows.remove("festivity")
        val none = assemble(bare).model
        assertTrue(none.blocks("proceso_electoral").any { it == Block.Paragraph("Sin dato.") })
        assertTrue(none.blocks("costumbres").any { it == Block.Paragraph("Sin dato.") })

        // EXTERNO: queda una linea sin marca bajo cada titulo
        val external = ReportDisclosure.apply(assemble(T1Source.source().also { it.rows.remove("festivity") }).model, Disclosure.EXTERNO)
        assertTrue(external.blocks("proceso_electoral").filterIsInstance<Block.Paragraph>().any { it.markdown == "Información reservada en esta versión." })
        assertTrue(external.blocks("costumbres").filterIsInstance<Block.Paragraph>().any { it.markdown == "Información reservada en esta versión." })
    }

    @Test
    fun `INTERNO no muestra el aviso de contenido reservado y EXTERNO si, sin el texto restringido`() {
        val model = assemble().model
        val internal =
            ReportDisclosure
                .apply(model, Disclosure.INTERNO)
                .blocks("proceso_electoral")
                .filterIsInstance<Block.Paragraph>()
                .map { it.markdown }
        assertTrue(internal.any { "movimientos" in it })
        assertTrue(internal.none { "Información reservada" in it })
        val external =
            ReportDisclosure
                .apply(model, Disclosure.EXTERNO)
                .blocks("proceso_electoral")
                .filterIsInstance<Block.Paragraph>()
                .map { it.markdown }
        assertTrue(external.any { "Información reservada en esta versión." in it })
        assertTrue(external.none { "movimientos" in it })
    }

    // todo el texto visible de una sección: párrafos y notas al pie, títulos, tablas (título, cabecera, celdas y fuente),
    // mapas, fotos y gráficos
    private fun ReportModel.text(key: String): String =
        blocks(key).joinToString("\n") { block ->
            when (block) {
                is Block.Paragraph -> block.markdown + " " + block.footnotes.joinToString(" ")
                is Block.Heading -> block.text
                is Block.Table ->
                    listOf(block.title, block.source.orEmpty()).joinToString(" ") + " " + block.header.joinToString(" ") + " " +
                        block.rows.flatten().joinToString(" ") { it.text }
                is Block.MapFigure -> block.title + " " + block.source.orEmpty()
                is Block.Photos -> block.title + " " + block.source.orEmpty()
                is Block.LineChart -> block.title + " " + block.source.orEmpty() + " " + block.series.joinToString(" ") { it.name }
                is Block.ScatterChart -> block.title + " " + block.source.orEmpty() + " " + block.points.joinToString(" ") { it.label }
                else -> ""
            }
        }

    // revisión final de 8.2c, Critical 1: la narrativa humana de una sección solo INTERNO nunca sale en EXTERNO
    @Test
    fun `barrida de EXTERNO con narrativa humana en cada seccion quita solo la de las secciones solo INTERNO`() {
        val narrative = type.sections.associate { it.key to "Narrativa distintiva de ${it.key} QX7." }
        val model = assemble(content = narrative).model
        val internal = ReportDisclosure.apply(model, Disclosure.INTERNO)
        val external = ReportDisclosure.apply(model, Disclosure.EXTERNO)
        val internalOnly = setOf("proceso_electoral", "costumbres")
        for ((key, text) in narrative) {
            assertTrue(text in internal.text(key), "$key: falta en INTERNO")
            if (key in internalOnly) {
                assertTrue(external.sections.none { text in external.text(it.key) }, "$key: salió en EXTERNO")
            } else {
                assertTrue(text in external.text(key), "$key: falta en EXTERNO")
            }
        }
    }

    @Test
    fun `una narrativa solo INTERNO sin datos deja en EXTERNO el aviso de reservado y no Sin dato`() {
        val bare = T1Source.source()
        bare.rows["locality_profile"]!!.replaceAll { it.copy(attributes = it.attributes - "electoral_process" - "religion") }
        bare.rows.remove("festivity")
        val model = assemble(bare, mapOf("proceso_electoral" to "Opiniones QX7.", "costumbres" to "Creencias QX7.")).model
        val external = ReportDisclosure.apply(model, Disclosure.EXTERNO)
        for (key in listOf("proceso_electoral", "costumbres")) {
            val paragraphs = external.blocks(key).filterIsInstance<Block.Paragraph>().map { it.markdown }
            assertTrue("Información reservada en esta versión." in paragraphs, "$key: $paragraphs")
            assertTrue("Sin dato." !in paragraphs, "$key: $paragraphs")
            assertTrue(paragraphs.none { "QX7" in it }, "$key: $paragraphs")
        }
        val internal = ReportDisclosure.apply(model, Disclosure.INTERNO).blocks("proceso_electoral").filterIsInstance<Block.Paragraph>()
        assertTrue(internal.any { it.markdown == "Opiniones QX7." } && internal.none { "reservada" in it.markdown }, "$internal")
    }

    @Test
    fun `sin localidades una narrativa solo INTERNO tambien deja el aviso de reservado en EXTERNO`() {
        val source = T1Source.source()
        source.rows.remove("area_of_influence_member")
        val model = assemble(source, mapOf("proceso_electoral" to "Opiniones QX7.")).model
        val external = ReportDisclosure.apply(model, Disclosure.EXTERNO).blocks("proceso_electoral")
        assertEquals(listOf<Block>(Block.Paragraph("Información reservada en esta versión.", Mark.EXTERNAL_ONLY)), external)
    }

    // revisión final de 8.2c, Minor 5: la persona detrás de cada mención (también la titular de un cargo o un grupo de interés)
    @Test
    fun `el ensamblado da la persona detras de cada mencion`() {
        val position = T1Source.id(81)
        val content = "Con [[person:${T1Source.PERSON}]], [[position:$position]] y [[stakeholder:${T1Source.LEADER}]]."
        val holders = assemble(content = mapOf("introduccion" to content)).holders
        val person = UUID.fromString(T1Source.PERSON)
        assertEquals(person, holders["person:${T1Source.PERSON}"])
        assertEquals(person, holders["position:$position"])
        assertEquals(person, holders["stakeholder:${T1Source.LEADER}"])
    }

    @Test
    fun `la guia de perfiles avisa que su narrativa sale en EXTERNO y las solo INTERNO lo dicen`() {
        val guides = type.sections.associate { it.key to it.guide }
        assertTrue("EXTERNO" in guides.getValue("perfiles") && "nombres" in guides.getValue("perfiles"), guides.getValue("perfiles"))
        assertEquals(setOf("proceso_electoral", "costumbres"), guides.filterValues { "solo INTERNO" in it }.keys)
    }

    @Test
    fun `los grupos de interes van por ambito y tipo con el actor por cargo, y los perfiles con su nota de entrevista`() {
        val model = assemble().model
        val profiles = model.blocks("perfiles")
        val identification = profiles.filterIsInstance<Block.Table>()
        assertEquals(listOf("Autoridad local", "Presidente, Junta Directiva Comunal Ejemplo"), identification[0].rows.single().map { it.text })
        assertEquals(listOf("Organización social distrital", "Municipalidad Distrital Ejemplo"), identification[1].rows.single().map { it.text })
        assertTrue(Block.Heading(4, "Municipalidad Distrital Ejemplo") in profiles, "$profiles")
        assertTrue(profiles.filterIsInstance<Block.Paragraph>().any { "Estado de la entrevista: Se negó." in it.footnotes })
        val classification = model.tables("clasificacion")
        assertEquals(
            listOf("Estratégico", "[[person:${T1Source.PERSON}]]", "Presidente", "Autoridad local", "A favor"),
            classification[0].rows.single().map { it.text }
        )
        assertTrue((model.blocks("grupos_politicos").single() as Block.Paragraph).markdown.contains("Municipalidad Distrital Ejemplo"))
        assertEquals(
            "Agua",
            model
                .tables("temas_clave")
                .single()
                .rows
                .single()[0]
                .text
        )
        // la lista de cada objeto va en su sección (7.1.1 a 7.1.3); la de 7.1 solo lleva su narrativa general
        assertTrue(model.blocks("percepciones").isEmpty())
        val project = model.blocks("percepciones_proyecto")
        assertTrue((project.single() as Block.Paragraph).markdown.contains("«Queremos agua limpia» — Dirigente comunal"), "$project")
        assertEquals(
            listOf("7.1.1 Percepciones sobre la minería", "7.1.2 Percepciones sobre el proyecto", "7.1.3 Percepciones sobre la empresa"),
            model.sections.filter { it.key.startsWith("percepciones_") }.map { it.title }
        )
        assertTrue((model.blocks("percepciones_empresa").single() as Block.Paragraph).markdown.contains("Sin percepciones registradas"))
    }

    // #105: ámbito (6.1 a 6.3) con su tabla, tipo, localidad de la organización y cargo; la narrativa abre la sección
    @Test
    fun `los perfiles van por ambito con su tabla, tipo, localidad y cargo, y la narrativa antes`() {
        val source = T1Source.source()
        // organizaciones con localidad Alta (UPPER) y Baja (LOWER) y una sin localidad
        val upper = T1Source.id(400)
        val lower = T1Source.id(401)
        val none = T1Source.id(402)
        source.add("organization", upper, null, "name" to "Comité Alto Ejemplo", "locality" to T1Source.UPPER)
        source.add("organization", lower, null, "name" to "Comité Bajo Ejemplo", "locality" to T1Source.LOWER)
        source.add("organization", none, null, "name" to "Comité Sin Localidad Ejemplo")

        fun actor(
            n: Int,
            organization: String,
            title: String?,
            scope: String,
            type: String
        ) {
            val person = T1Source.id(410 + n)
            if (title != null) {
                source.add("person", person, null, "given_names" to "Persona", "surnames" to "Ejemplo $n")
                source.add(
                    "position",
                    T1Source.id(430 + n),
                    null,
                    "person" to person,
                    "organization" to organization,
                    "position_title" to title,
                    "position_status" to "VIGENTE"
                )
            }
            source.add(
                "stakeholder",
                T1Source.id(450 + n),
                null,
                "project" to T1Source.PROJECT,
                "stakeholder_scope" to scope,
                "stakeholder_type" to type,
                *(if (title != null) arrayOf("person" to person) else arrayOf("organization" to organization))
            )
        }
        // desordenados a propósito
        actor(1, none, "Vocal", "AIDS", "ORGANIZACION COMUNAL")
        actor(2, lower, "Tesorero", "AIDS", "ORGANIZACION COMUNAL")
        actor(3, upper, "Secretario", "AIDS", "ORGANIZACION COMUNAL")
        actor(4, upper, "Fiscal", "AIDS", "ORGANIZACION COMUNAL")
        actor(5, upper, "Alcalde", "AIIS", "AUTORIDAD LOCAL")
        actor(6, lower, "Regidor", "REGIONAL", "AUTORIDAD LOCAL")
        val narrative = "Narrativa de apertura de los perfiles QX7."
        val model = assemble(source, mapOf("perfiles" to narrative)).model
        val blocks = model.blocks("perfiles")
        // la narrativa antes de todo, y el nivel 2 de los ámbitos numerados
        assertEquals(narrative, (blocks.first() as Block.Paragraph).markdown)
        val headings = blocks.filterIsInstance<Block.Heading>()
        assertEquals(
            listOf(2 to "6.1 Área de influencia directa social", 2 to "6.2 Área de influencia indirecta social", 2 to "6.3 Ámbito regional y nacional"),
            headings.filter { it.level == 2 }.map { it.level to it.text }
        )
        // la tabla de cada ámbito va justo tras su encabezado
        for (heading in headings.filter { it.level == 2 }) {
            val table = blocks[blocks.indexOf(heading) + 1] as Block.Table
            assertTrue(table.title.startsWith("Grupos de interés del"), table.title)
        }
        // en AIDS, el tipo abre con su subtítulo de nivel 3 y los perfiles van por localidad (Alta, Baja, sin localidad) y cargo
        val aids = blocks.subList(blocks.indexOf(headings.first()), blocks.indexOf(headings.first { it.text.startsWith("6.2") }))
        val titles = aids.filterIsInstance<Block.Heading>().filter { it.level == 4 }.map { it.text }
        assertEquals(
            listOf(
                "Fiscal, Comité Alto Ejemplo",
                "Secretario, Comité Alto Ejemplo",
                "Tesorero, Comité Bajo Ejemplo",
                "Vocal, Comité Sin Localidad Ejemplo"
            ),
            titles.filter { it.contains("Comité") }
        )
        assertTrue(aids.any { it is Block.Heading && it.level == 3 && it.text == "Organización comunal" }, "$aids")
        // EXTERNO: ningún nombre de persona, ni en los párrafos ni en los encabezados ni en las celdas
        val external = ReportDisclosure.apply(model, Disclosure.EXTERNO).blocks("perfiles")
        val shown =
            external.joinToString("\n") { block ->
                when (block) {
                    is Block.Paragraph -> block.markdown
                    is Block.Heading -> block.text
                    is Block.Table -> (listOf(block.title) + block.header + block.rows.flatten().map { it.text }).joinToString(" | ")
                    else -> ""
                }
            }
        assertTrue("Persona" !in shown && "Ejemplo 1" !in shown, shown)
    }

    // I3 y M2 de la revisión de #105 a #111: los tipos de actor en el orden del enum stakeholder_type (el de DOC-01), los
    // desconocidos al final por nombre, y el número del ámbito fijo aunque falte el AIDS
    @Test
    fun `los tipos de actor van en el orden del enum y el AIIS es 6-2 aunque no haya AIDS`() {
        val source = T1Source.source()
        source.rows.remove("stakeholder")
        listOf("IGLESIA", "TIPO NUEVO", "AUTORIDAD REGIONAL", "ORGANIZACION COMUNAL", "AUTORIDAD LOCAL", "LIDER POLITICO").forEachIndexed { i, type ->
            val organization = T1Source.id(700 + i)
            source.add("organization", organization, null, "name" to "Organización Ejemplo $i")
            source.add(
                "stakeholder",
                T1Source.id(720 + i),
                null,
                "project" to T1Source.PROJECT,
                "stakeholder_scope" to "AIIS",
                "stakeholder_type" to type,
                "organization" to organization
            )
        }
        val blocks = assemble(source).model.blocks("perfiles")
        val headings = blocks.filterIsInstance<Block.Heading>()
        assertEquals(listOf("6.2 Área de influencia indirecta social"), headings.filter { it.level == 2 }.map { it.text })
        assertEquals(
            listOf("AUTORIDAD LOCAL", "ORGANIZACION COMUNAL", "LIDER POLITICO", "AUTORIDAD REGIONAL", "IGLESIA", "TIPO NUEVO").map { ReportLabels.option(it) },
            headings.filter { it.level == 3 }.map { it.text }
        )
        val table = blocks.filterIsInstance<Block.Table>().first()
        assertEquals(
            "Autoridad local",
            table.rows
                .first()
                .first()
                .text
        )
        // OTRO está en el enum (al final); los desconocidos y «Sin dato» van después, por nombre en español
        assertEquals(
            listOf("AUTORIDAD LOCAL", "ORGANIZACION COMUNAL", "OTRO", "ÁNIMAS", "Sin dato", "ZETA"),
            listOf("ZETA", "OTRO", "Sin dato", "ORGANIZACION COMUNAL", "ÁNIMAS", "AUTORIDAD LOCAL").sortedWith(DiagnosisStakeholders.typeOrder())
        )
    }

    // #107: los ámbitos van en orden fijo dentro de cada objeto y la narrativa del objeto cierra tras su lista
    private fun perception(
        source: FakeReportSource,
        n: Int,
        subject: String,
        scope: String?,
        statement: String,
        role: String? = null
    ) {
        source.add(
            "perception",
            T1Source.id(n),
            null,
            "study" to T1Source.STUDY,
            "subject" to subject,
            "polarity" to "NEUTRAL",
            "perception_scope" to scope,
            "statement" to statement,
            "quote" to role?.let { "Cita $statement" },
            "attributed_role" to role
        )
    }

    private fun scopesOf(blocks: List<Block>): List<String> =
        (blocks.first() as Block.Paragraph)
            .markdown
            .lines()
            .filter { it.startsWith("**") }
            .map { it.trim('*') }

    @Test
    fun `los ambitos de las percepciones van AIDS, AIIS, regional y nacional, luego otros por nombre y sin ambito`() {
        val source = T1Source.source()
        source.rows.remove("perception")
        listOf("MINERIA", "PROYECTO", "EMPRESA").forEachIndexed { i, subject ->
            // datos desordenados a propósito, distintos de un objeto a otro
            val scopes = if (i == 1) listOf("NACIONAL", "AIIS", "ZONAL", "REGIONAL", "AIDS", null) else listOf("REGIONAL", "AIIS", "AIDS", "NACIONAL", "ANDINO")
            scopes.forEachIndexed { j, scope -> perception(source, 500 + i * 10 + j, subject, scope, "Dato $subject $j") }
        }
        val model = assemble(source).model
        val titles = listOf(DiagnosisScene.AIDS_TITLE, DiagnosisScene.AIIS_TITLE, "Ámbito regional", "Ámbito nacional")
        assertEquals(titles + "ANDINO", scopesOf(model.blocks("percepciones_mineria")))
        assertEquals(titles + listOf("ZONAL", "Sin ámbito"), scopesOf(model.blocks("percepciones_proyecto")))
        assertEquals(titles + "ANDINO", scopesOf(model.blocks("percepciones_empresa")))
    }

    @Test
    fun `la narrativa de cada objeto sale tras su lista, en INTERNO y EXTERNO con citas por cargo`() {
        val source = T1Source.source()
        perception(source, 600, "EMPRESA", "AIDS", "La empresa cumple.", "Dirigente comunal")
        val content =
            mapOf(
                "percepciones" to "General QX1.",
                "percepciones_mineria" to "Análisis minería QX2.",
                "percepciones_proyecto" to "Análisis proyecto QX3.",
                "percepciones_empresa" to "Análisis empresa QX4."
            )
        val model = assemble(source, content).model
        for (key in listOf("percepciones_mineria", "percepciones_proyecto", "percepciones_empresa")) {
            val blocks = model.blocks(key)
            assertEquals(2, blocks.size, key)
            assertTrue("QX" in (blocks[1] as Block.Paragraph).markdown && "Sin percepciones" !in (blocks[1] as Block.Paragraph).markdown, key)
        }
        assertEquals("General QX1.", (model.blocks("percepciones").single() as Block.Paragraph).markdown)
        for (level in listOf(Disclosure.INTERNO, Disclosure.EXTERNO)) {
            val shown = ReportDisclosure.apply(model, level)
            val company = shown.text("percepciones_empresa")
            assertTrue("— Dirigente comunal" in company && company.indexOf("La empresa cumple.") < company.indexOf("Análisis empresa QX4."), "$level: $company")
            assertTrue(listOf("QX2", "QX3", "QX4", "QX1").all { q -> shown.sections.any { q in shown.text(it.key) } }, "$level")
            assertTrue("Persona Ejemplo" !in company, "$level: nombre en las percepciones")
        }
    }

    @Test
    fun `un entregable sin las secciones de percepciones por objeto ensambla igual, sin esas narrativas`() {
        val model = assemble(content = mapOf("percepciones" to "General QX1.")).model
        assertEquals("General QX1.", (model.blocks("percepciones").single() as Block.Paragraph).markdown)
        // sin narrativa, cada objeto queda con su lista sola
        assertEquals(1, model.blocks("percepciones_proyecto").size)
        assertEquals(1, model.blocks("percepciones_mineria").size)
    }

    private fun alignedAnswer(
        source: FakeReportSource,
        n: Int,
        question: String,
        answer: String?,
        approved: Boolean = true
    ) = source.add(
        "aligned_answer",
        T1Source.id(n),
        null,
        "project" to T1Source.PROJECT,
        "question" to question,
        "answer" to answer,
        "approved" to approved
    )

    @Test
    fun `9_1_1 va tras la comunicacion con sus tres secciones y la priorizacion pasa a 9_1_1_4`() {
        val keys = type.sections.map { it.key }
        val at = keys.indexOf("comunicacion")
        assertEquals(
            listOf("comunicacion", "contactos_regulares", "reuniones_informativas", "reuniones_politicas", "priorizacion", "mensajes"),
            keys.subList(
                at,
                at + 6
            )
        )
        val titles = type.sections.associate { it.key to it.title }
        assertEquals("9.1.1 Metodología de interacción", titles["contactos_regulares"])
        assertEquals("9.1.1.2 Reuniones informativas", titles["reuniones_informativas"])
        assertEquals("9.1.1.3 Reuniones con grupos políticos", titles["reuniones_politicas"])
        assertEquals("9.1.1.4 Priorización de la información", titles["priorizacion"])
        val model = assemble().model
        assertEquals(
            listOf(3, 4, 4, 4),
            listOf("contactos_regulares", "reuniones_informativas", "reuniones_politicas", "priorizacion").map { k ->
                model.sections
                    .single {
                        it.key ==
                            k
                    }.level
            }
        )
        assertEquals(Block.Heading(4, "9.1.1.1 Contactos regulares"), model.blocks("contactos_regulares").first())
    }

    @Test
    fun `las respuestas alineadas salen tras la narrativa de las reuniones informativas, en INTERNO y EXTERNO`() {
        val source = T1Source.source()
        alignedAnswer(source, 700, "¿Habrá relocalización?", "No se prevé relocalizar a nadie.")
        alignedAnswer(source, 701, "¿Contratarán gente local?", "Sí, según la política de empleo local.")
        alignedAnswer(source, 702, "¿Pagarán por las tierras?", "Borrador sin aprobar.", approved = false)
        val model = assemble(source, mapOf("reuniones_informativas" to "Protocolo QX7.")).model
        for (level in listOf(Disclosure.INTERNO, Disclosure.EXTERNO)) {
            val text = ReportDisclosure.apply(model, level).text("reuniones_informativas")
            assertTrue(text.indexOf("Protocolo QX7.") in 0 until text.indexOf("¿Contratarán gente local?"), "$level: $text")
            assertTrue("Pagarán" !in text && "Borrador sin aprobar" !in text, "$level: respuesta no aprobada")
            assertTrue("No se prevé relocalizar a nadie." in text && text.indexOf("¿Contratarán") < text.indexOf("¿Habrá"), "$level: $text")
        }
    }

    @Test
    fun `sin respuestas alineadas aprobadas las reuniones informativas lo dicen en cursiva`() {
        val source = T1Source.source()
        alignedAnswer(source, 710, "¿Y la compensación?", "Sin aprobar.", approved = false)
        val blocks = assemble(source).model.blocks("reuniones_informativas")
        assertEquals("_Sin respuestas alineadas registradas._", (blocks.single() as Block.Paragraph).markdown)
    }

    @Test
    fun `un entregable sin las secciones de metodologia de interaccion ensambla igual, sin esas narrativas`() {
        val model = assemble(content = mapOf("comunicacion" to "Comunicación QX8.")).model
        assertTrue(model.blocks("reuniones_politicas").isEmpty())
        assertEquals(1, model.blocks("contactos_regulares").size)
        assertEquals(52, model.sections.size)
    }

    // Review Focus 1
    @Test
    fun `EXTERNO no lleva religion, proceso electoral, razones ni postura de una persona`() {
        val external = ReportDisclosure.apply(assemble().model, Disclosure.EXTERNO)
        // el texto fijo del capítulo 6 define las posturas en general (#109): no es la postura de nadie
        val text =
            external.sections.flatMap { if (it.key == "grupos_interes") it.blocks.drop(1) else it.blocks }.joinToString("\n") { block ->
                when (block) {
                    is Block.Paragraph -> block.markdown + " " + block.footnotes.joinToString(" ")
                    is Block.Table -> block.title + " " + block.rows.flatten().joinToString(" ") { it.text }
                    is Block.Heading -> block.text
                    else -> ""
                }
            }
        for (forbidden in listOf(
            "Católica",
            "movimientos locales",
            "Espera empleo local",
            "Teme por el agua",
            "Persona Ejemplo 1",
            "EN CONTRA",
            "A FAVOR",
            "A favor",
            "En contra"
        )) {
            assertTrue(forbidden !in text, "$forbidden salió en EXTERNO")
        }
        val row = external.tables("clasificacion")[0].rows.single()
        assertEquals("", row[1].text)
        assertEquals("Presidente", row[2].text)
        assertEquals("", row[4].text)
        val names = external.tables("clasificacion").flatMap { t -> t.rows.map { it[it.size - 4].text } }
        assertTrue(names.all { it.isEmpty() || it.startsWith("GI-") || it == "Municipalidad Distrital Ejemplo" }, "$names")
        val internal = ReportDisclosure.apply(assemble().model, Disclosure.INTERNO)
        assertTrue(
            "Persona Ejemplo 1" in
                internal
                    .tables("clasificacion")[0]
                    .rows
                    .single()[1]
                    .text
        )
    }

    @Test
    fun `un grupo de interes sin ambito sale bajo Sin ambito en las tablas y los temas sin dato dicen Sin dato`() {
        val source = T1Source.source()
        source.add(
            "stakeholder",
            T1Source.id(300),
            null,
            "project" to T1Source.PROJECT,
            "organization" to T1Source.MUNICIPALITY,
            "influence_group" to "ESTRATEGICO"
        )
        source.add("key_theme", T1Source.id(301), null, "project" to T1Source.PROJECT, "study" to T1Source.STUDY, "name" to "Tierra")
        val model = assemble(source).model
        assertTrue(model.tables("perfiles").any { it.title == "Grupos de interés sin ámbito" })
        assertTrue(model.tables("clasificacion").any { it.title == "Clasificación de los grupos de interés sin ámbito" })
        assertTrue(model.blocks("grupos_estrategicos").filterIsInstance<Block.Paragraph>().any { "Grupos de interés sin ámbito" in it.markdown })
        val tierra =
            model
                .tables("temas_clave")
                .single()
                .rows
                .first { it[0].text == "Tierra" }
        assertEquals(listOf("Sin dato", "Sin dato", "Sin dato"), tierra.drop(1).map { it.text })
    }

    // la Tabla 9.5 la calcula ReportSources con el lector de la plataforma; aquí, una fila fija
    private class MonitoredSource : FakeReportSource() {
        override suspend fun monitoring(
            project: UUID,
            start: LocalDate,
            end: LocalDate
        ): List<MonitoringRow> =
            listOf(
                MonitoringRow(
                    1,
                    "KPI-ENG-01",
                    "id-KPI-ENG-01",
                    "Interacciones del periodo",
                    "Actas",
                    "T1",
                    true,
                    12,
                    10,
                    "CATALOG",
                    Official("m1", "2026-10-01T00:00:00Z", 11)
                )
            )
    }

    @Test
    fun `el capitulo 9 lleva la Tabla 9_5 con la cifra oficial, la tendencia, la matriz 5 x 5 y la gestion del riesgo`() {
        val model = assemble(T1Source.source(MonitoredSource())).model
        assertEquals(
            listOf("1", "KPI-ENG-01 Interacciones del periodo", "Actas", "12.00", "10.00", "11.00 (cierre del 01/10/2026)"),
            model
                .tables("monitoreo")
                .single()
                .rows
                .single()
                .map { it.text }
        )
        val evaluation = model.blocks("evaluacion_grupos")
        val months = evaluation.filterIsInstance<Block.Table>().single()
        assertEquals(listOf("2026-09", "2", "3.00", "0", "100", "100"), months.rows.last().map { it.text })
        val chart = evaluation.filterIsInstance<Block.LineChart>().single()
        assertEquals(6, chart.categories.size)
        assertEquals(
            "3.00",
            chart.series
                .single()
                .values
                .last()
        )
        val matrix = model.tables("riesgos").single { it.figure }
        // fila del impacto 4 (la segunda desde arriba), probabilidad 4: el riesgo RS-1, crítico con 16
        assertEquals("1", matrix.rows[1][4].text)
        assertEquals("FF7C80", matrix.rows[1][4].shade)
        assertEquals("C6E0B4", matrix.rows[4][1].shade)
        val management =
            model
                .tables("gestion_riesgos")
                .single()
                .rows
                .single()
        assertEquals("RS-1 Conflicto por el agua", management[0].text)
        assertTrue("Monitoreo participativo del agua." in management.last().text)
        assertTrue(
            model
                .tables("seguimiento_compromisos")
                .first()
                .rows
                .any { it.map { c -> c.text } == listOf("Cumplido", "1") }
        )
        assertEquals(
            listOf("Muy alta", "Estratégico", "A favor", "AIDS", "Autoridad local", "Deciden en la asamblea."),
            model
                .tables("priorizacion")
                .single()
                .rows
                .single()
                .map { it.text }
        )
        assertTrue((model.blocks("seguimiento_compromisos").last() as Block.Paragraph).markdown.contains("50,000.00 PEN"))
        assertTrue(model.tables("necesidades").any { table -> table.rows.any { it[0].text == "Relacionista comunitario" && it[1].text == "2" } })
    }

    // #109: cada sección con método abre con su texto fijo, en INTERNO y en EXTERNO, y el capítulo 6 define cada postura
    @Test
    fun `los capitulos 6, 8 y 9 abren con su texto fijo de metodo en INTERNO y EXTERNO`() {
        val model = assemble(T1Source.source(MonitoredSource())).model
        val expected =
            mapOf(
                "temas_clave" to "Un tema clave es",
                "impactos" to "La gestión de las percepciones de impactos",
                "evaluacion_grupos" to "La evaluación de los grupos de interés mide",
                "seguimiento_compromisos" to "La matriz de compromisos registra",
                "riesgos" to "Un riesgo social es",
                "grupos_interes" to "Un grupo de interés es"
            )
        listOf(Disclosure.INTERNO, Disclosure.EXTERNO).forEach { level ->
            val shown = ReportDisclosure.apply(model, level)
            expected.forEach { (key, phrase) ->
                val first =
                    shown.sections
                        .single { it.key == key }
                        .blocks
                        .first()
                assertTrue(first is Block.Paragraph && first.markdown.startsWith(phrase), "$level $key: $first")
            }
            val risks = shown.sections.single { it.key == "riesgos" }.blocks
            val heading = risks.indexOf(Block.Heading(3, "9.3.2.1 Evaluación del riesgo"))
            assertTrue((risks[heading + 1] as Block.Paragraph).markdown.startsWith("El nivel de un riesgo resulta de"), "$level")
            val stances =
                (
                    shown.sections
                        .single { it.key == "grupos_interes" }
                        .blocks
                        .first() as Block.Paragraph
                ).markdown
            listOf("**A favor**", "**Neutral**", "**En contra pero abierto a escuchar**", "**En contra**:").forEach {
                assertTrue(it in stances, "$level $it")
            }
        }
    }

    // Review Focus 5
    @Test
    fun `sin estudio, sin AIIS, sin perfiles y sin indicadores el T1 se arma con sin dato`() {
        val source = T1Source.source()
        source.rows.getValue("area_of_influence").removeIf { it.id == T1Source.AIIS }
        listOf("locality_profile", "stakeholder_assessment", "perception", "key_theme").forEach { source.rows.remove(it) }
        val full = T1Source.input()
        val input =
            AssemblyInput(
                full.deliverable.copy(attributes = full.deliverable.attributes - "study"),
                full.sections,
                full.templateId,
                full.templateSha256,
                full.draftRevision,
                full.issueRevision,
                full.today
            )
        val assembled = runBlocking { type.assemble(input, source) }
        val model = assembled.model
        assertEquals(52, model.sections.size)
        assertTrue((model.blocks("objetivos").first() as Block.Paragraph).markdown.contains("no registra"))
        assertTrue((model.blocks("aiis").first() as Block.Paragraph).markdown.contains("no tiene un área de influencia indirecta social aprobada"))
        assertTrue((model.blocks("monitoreo").single() as Block.Paragraph).markdown.contains("no están disponibles"))
        assertTrue(model.blocks("evaluacion_grupos").last() is Block.Paragraph)
        assertEquals(2, model.blocks("evaluacion_grupos").size)
        assertEquals(listOf(UUID.fromString(T1Source.AIDS)), assembled.maps.single { it.key == DiagnosisStudy.AREAS_MAP }.areas)
    }

    // #104: una regla con el campo vacío aplica a todos, un rol sale con su rótulo y un tope sin monto no lleva moneda
    @Test
    fun `los campos vacios de una regla dicen Todos, el rol sale con su rotulo y el tope sin monto no lleva moneda`() {
        val source = T1Source.source()
        val rule = source.rows.getValue("prioritization_rule").single()
        source.rows.getValue("prioritization_rule")[0] =
            SourceRow(rule.id, rule.state, rule.attributes - "stakeholder_scope" - "stakeholder_type" - "stance")
        val campaign = source.rows.getValue("exploration_campaign").single()
        source.rows.getValue("exploration_campaign")[0] = SourceRow(campaign.id, campaign.state, campaign.attributes - "social_support_cap")
        val assignment = source.rows.getValue("project_assignment").first()
        source.rows.getValue("project_assignment")[0] =
            SourceRow(assignment.id, assignment.state, assignment.attributes + ("role_name" to "COMMITMENT_MANAGER"))
        val model = assemble(source).model
        assertEquals(
            listOf("Muy alta", "Estratégico", "Todos", "Todos", "Todos", "Deciden en la asamblea."),
            model
                .tables("priorizacion")
                .single()
                .rows
                .single()
                .map { it.text }
        )
        assertTrue((model.blocks("seguimiento_compromisos").last() as Block.Paragraph).markdown.contains("Campaña 2026: Sin dato\n"), "sin moneda")
        assertTrue(model.tables("necesidades").any { table -> table.rows.any { it[0].text == "Gestor de compromisos" } })
    }

    @Test
    fun `un telefono en un texto libre del capitulo 9 avisa POSSIBLE_PERSONAL_DATA con la clave de la seccion`() {
        val source = T1Source.source()
        val measure = source.rows.getValue("mitigation_measure").single()
        source.rows.getValue("mitigation_measure")[0] = SourceRow(measure.id, measure.state, measure.attributes + ("description" to "Llamar al 987654321."))
        val message = source.rows.getValue("key_message").single()
        source.rows.getValue("key_message")[0] = SourceRow(message.id, message.state, message.attributes + ("message" to "Escribir al 12345678."))
        val assembled = assemble(source)
        val warnings = PersonalDataScan.scan(assembled.texts, assembled.personNames)
        assertTrue(ReportWarning(PersonalDataScan.CODE, "gestion_riesgos") in warnings, "$warnings")
        assertTrue(ReportWarning(PersonalDataScan.CODE, "mensajes") in warnings, "$warnings")
    }

    // #108: el Anexo 3 trae el sistema de variables (las variables, sus componentes y sus 15 indicadores) y el gráfico de
    // influencia nativo con cada grupo evaluado por su código y las líneas de los umbrales del proyecto
    @Test
    fun `el anexo 3 lleva el sistema de variables y la dispersion de interes y poder por codigo con los umbrales`() {
        val source = T1Source.source()
        val project = source.rows.getValue("project").single()
        source.rows.getValue("project")[0] = SourceRow(project.id, project.state, project.attributes + ("interest_threshold" to "3.50"))
        source.add("stakeholder", T1Source.id(90), null, "project" to T1Source.PROJECT, "stakeholder_scope" to "AIIS", "stakeholder_type" to "OTRO")
        val model = assemble(source).model
        val blocks = model.blocks("anexo_metodologia")
        val variables = model.tables("anexo_metodologia").single { it.title == "Sistema de variables" }
        assertTrue(variables.figure)
        assertEquals(listOf("Variable", "Componente", "Indicador"), variables.header)
        assertEquals(InfluenceClassifier.INDICATORS.size, variables.rows.size)
        assertEquals(listOf("Interés", "Percepción de afectación", "Afectación del medio ambiente"), variables.rows[0].map { it.text })
        assertEquals(6 to 3, variables.rows[0][0].rowSpan to variables.rows[0][1].rowSpan)
        assertEquals(listOf("Poder", "Control de recursos", "Habilidad de acción legal"), variables.rows[6].map { it.text })
        assertEquals(listOf("Participación en otros mecanismos de presión"), variables.rows.last().map { it.text })
        val chart = blocks.filterIsInstance<Block.ScatterChart>().single()
        assertEquals("Gráfico de influencia", chart.title)
        assertEquals("Interés" to "Poder", chart.xAxis to chart.yAxis)
        assertEquals("1" to "5", chart.minimum to chart.maximum)
        assertEquals("3.5" to "3", chart.xThreshold to chart.yThreshold)
        assertEquals(
            listOf(
                ChartPoint(RegisterSheets.code(T1Source.LEADER), "4.2", "3.8"),
                ChartPoint(RegisterSheets.code(T1Source.DISTRICT), "2", "4")
            ),
            chart.points
        )
        // la tabla de cuadrantes sigue, después del gráfico
        val quadrant = model.tables("anexo_metodologia").single { it.title == "Cuadrantes de influencia" }
        assertTrue(blocks.indexOf(chart) < blocks.indexOf(quadrant))
        assertEquals(RegisterSheets.code(T1Source.LEADER), quadrant.rows[0][2].text)
    }

    // los rótulos del sistema de variables son los del modelo (sin «Componente de») y cubren todo InfluenceClassifier
    @Test
    fun `el sistema de variables rotula cada componente e indicador como model json`() {
        val objects = JsonMapper.builder().build().readTree(File("model/model.json"))["objects"]
        val fields =
            objects
                .values()
                .single { it["name"].asString() == "stakeholder_assessment" }["fields"]
                .values()
                .associate { it["name"].asString() to it["label"].asString() }
        val names = (InfluenceClassifier.INTEREST + InfluenceClassifier.POWER).flatMap { (component, indicators) -> listOf(component) + indicators }
        assertEquals(names.toSet(), DiagnosisAnnexes.LABELS.keys)
        for (name in names) {
            val expected = fields.getValue(name).removePrefix("Componente de ").replaceFirstChar { it.uppercase() }
            assertEquals(expected, DiagnosisAnnexes.LABELS[name], name)
        }
    }

    @Test
    fun `los anexos llevan el mapa horizontal, los instrumentos y el cuadrante de influencia por codigo`() {
        val assembled = assemble()
        val model = assembled.model
        assertEquals(
            listOf(Block.SectionBreak(true), Block.MapFigure(DiagnosisAnnexes.ANNEX_MAP, "Concesiones del proyecto y su entorno")),
            model.blocks("anexo_concesiones")
        )
        assertEquals(Block.SectionBreak(false), model.blocks("anexo_instrumentos").first())
        val quadrant = model.tables("anexo_metodologia").single { it.title == "Cuadrantes de influencia" }
        assertEquals(RegisterSheets.code(T1Source.LEADER), quadrant.rows[0][2].text)
        assertEquals(RegisterSheets.code(T1Source.DISTRICT), quadrant.rows[0][1].text)
        assertEquals("—", quadrant.rows[1][1].text)
        val annex = assembled.maps.single { it.key == DiagnosisAnnexes.ANNEX_MAP }
        assertTrue(annex.landscape)
        assertEquals(MapFrameKind.INTEREST_AND_CONCESSIONS, annex.frame)
        assertEquals(listOf(UUID.fromString(T1Source.CONCESSION)), annex.concessions)
        assertEquals(listOf(DiagnosisStudy.AREAS_MAP, DiagnosisAnnexes.ANNEX_MAP), assembled.maps.map { it.key })
        assertTrue(model.sections.all { section -> section.key in setOf("portada", "indice") || section.chapter != null })
    }

    // I2 de la revisión de #105 a #111: las secciones asistidas sin bloques propios mandan a AI-04 los de su contexto, y el
    // documento no los imprime dos veces
    @Test
    fun `AI-04 de percepciones, areas y 9-1-1 recibe los datos de su contexto sin cambiar lo impreso`() {
        val model = assemble().model

        fun ai(key: String): String = SectionText.render(DeliverableSections.aiBlocks(model, type, key), model.people, emptyMap()) { _, _ -> false }
        assertTrue(model.blocks("percepciones").none { it is Block.Table || (it is Block.Paragraph && "trae trabajo" in it.markdown) })
        val perceptions = ai("percepciones")
        assertTrue("La minería trae trabajo." in perceptions && "El proyecto afectará el agua." in perceptions, perceptions)
        assertTrue("Datos de 7.1.1" in perceptions, perceptions)
        assertTrue(model.blocks("areas").isEmpty())
        val areas = ai("areas")
        assertTrue("Impactos reales o percibidos de la exploración." in areas, areas)
        assertTrue("Interacciones del periodo por tipo" in ai("contactos_regulares"), ai("contactos_regulares"))
        val political = ai("reuniones_politicas")
        assertTrue("Interacciones del periodo por tipo" in political && "Datos de 7.2.2 Grupos políticos" in political, political)
        assertEquals(
            type.aiContext.keys,
            type.aiContext.keys
                .filter { key -> type.sections.single { it.key == key }.assisted }
                .toSet()
        )
        assertTrue(
            type.aiContext.values
                .flatten()
                .all { key -> type.sections.any { it.key == key } }
        )
    }

    // M1 de la revisión de #105 a #111: T1 quita el título de la portada, el índice y los perfiles por el modelo
    @Test
    fun `la portada, el indice y los perfiles no imprimen su titulo y las demas si`() {
        val model = assemble().model
        assertEquals(listOf("portada", "indice", "perfiles"), model.sections.filter { it.showTitle == false }.map { it.key })
        assertTrue(model.sections.filter { it.showTitle != false }.all { it.showTitle == null })
    }

    // M7 de la revisión de #105 a #111: la palabra entera, sin distinguir mayúsculas ni tildes
    @Test
    fun `el nombre que ya empieza con proyecto exige la palabra entera`() {
        assertTrue(DiagnosisStudy.startsWith("Proyecto Wayra Ejemplo", "proyecto"))
        assertTrue(DiagnosisStudy.startsWith("  PROYECTO", "proyecto"))
        assertFalse(DiagnosisStudy.startsWith("Proyectos Andinos Ejemplo SAC", "proyecto"))
        assertTrue(DiagnosisStudy.startsWith("Criterios: impactos", "criterios"))
        assertFalse(DiagnosisStudy.startsWith("Criteriosamente se eligió", "criterios"))
    }
}
