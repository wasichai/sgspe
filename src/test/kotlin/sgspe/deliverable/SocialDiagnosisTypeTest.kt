package sgspe.deliverable

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import sgspe.dashboard.MonitoringRow
import sgspe.dashboard.Official
import sgspe.privacy.Block
import sgspe.privacy.Disclosure
import sgspe.privacy.Mark
import sgspe.privacy.ReportDisclosure
import sgspe.privacy.ReportModel
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
    fun `declara las 46 secciones en orden con sus capitulos y las asistidas por IA de la especificacion`() {
        val keys = type.sections.map { it.key }
        assertEquals(46, keys.size)
        assertEquals(keys.size, keys.toSet().size)
        assertEquals(listOf("portada", "indice", "introduccion"), keys.take(3))
        assertEquals(
            setOf(
                "introduccion",
                "areas",
                "contexto",
                "percepciones",
                "grupos_estrategicos",
                "grupos_politicos",
                "grupos_monitoreo",
                "estrategias",
                "comunicacion",
                "necesidades"
            ),
            type.sections
                .filter { it.assisted }
                .map { it.key }
                .toSet()
        )
        assertEquals((1..46).toList(), type.sections.map { it.order })
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
        assertTrue(aids.rows.any { it[0].text == "Caserío Ejemplo Alto" && it[1].text == "INCLUIDO" }, "${aids.rows}")
        assertTrue(aids.rows.any { it[0].text == "Comunidad Ejemplo" })
        assertTrue(
            model
                .tables("aiis")
                .single()
                .rows
                .any { it[0].text == "Caserío Ejemplo Bajo (EN SEPARACION)" }
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
        assertTrue(history.filterIsInstance<Block.Paragraph>().any { "Linderos con el caserío vecino" in it.markdown && "Caserío Ejemplo Bajo" in it.markdown })
        val population = model.tables("poblacion").first()
        assertEquals(listOf("Caserío Ejemplo Alto", "120", "480", "4.00", "10/08/2026", "Trabajo de campo", "ALTA"), population.rows.single().map { it.text })
        val services = model.tables("servicios")
        assertEquals(listOf("Servicio", "Caserío Ejemplo Alto"), services[0].header)
        assertEquals(listOf("Fuente de agua", "Manantial"), services[0].rows.first().map { it.text })
        assertEquals(listOf("Fuente de agua", DiagnosisScene.SIN_DATO), services[1].rows.first().map { it.text })
        val matrix = model.tables("organizaciones").first()
        assertTrue(matrix.rows.any { row -> row.map { it.text } == listOf("Junta Directiva Comunal Ejemplo", "ORGANIZACION COMUNAL", "X") }, "${matrix.rows}")
        assertTrue(matrix.rows.any { row -> row.map { it.text } == listOf("Programa social del Estado", "Programa del Estado", "X") }, "${matrix.rows}")
        val roads = model.tables("vias").first()
        assertEquals(listOf("Caserío Ejemplo Alto", "Trocha", "Caserío Ejemplo Bajo", "Regular", "Combi", "40 min"), roads.rows.single().map { it.text })
        assertTrue(model.tables("educacion_salud").any { table -> table.rows.any { it[1].text == "I. E. N° 101" } })
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

    @Test
    fun `los grupos de interes van por ambito y tipo con el actor por cargo, y los perfiles con su nota de entrevista`() {
        val model = assemble().model
        val identification = model.tables("grupos_interes")
        assertEquals(listOf("AUTORIDAD LOCAL", "Presidente, Junta Directiva Comunal Ejemplo"), identification[0].rows.single().map { it.text })
        assertEquals(listOf("ORGANIZACION SOCIAL DISTRITAL", "Municipalidad Distrital Ejemplo"), identification[1].rows.single().map { it.text })
        val profiles = model.blocks("perfiles")
        assertTrue(Block.Heading(3, "Municipalidad Distrital Ejemplo") in profiles, "$profiles")
        assertTrue(profiles.filterIsInstance<Block.Paragraph>().any { "Estado de la entrevista: SE NEGO." in it.footnotes })
        val classification = model.tables("clasificacion")
        assertEquals(
            listOf("ESTRATEGICO", "[[person:${T1Source.PERSON}]]", "Presidente", "AUTORIDAD LOCAL", "A FAVOR"),
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
        val perceptions = model.blocks("percepciones")
        assertEquals(Block.Heading(3, "7.1.1 Percepciones sobre la minería"), perceptions[0])
        assertTrue((perceptions[3] as Block.Paragraph).markdown.contains("«Queremos agua limpia» — Dirigente comunal"), "${perceptions[3]}")
    }

    // Review Focus 1
    @Test
    fun `EXTERNO no lleva religion, proceso electoral, razones ni postura de una persona`() {
        val external = ReportDisclosure.apply(assemble().model, Disclosure.EXTERNO)
        val text =
            external.sections.flatMap { it.blocks }.joinToString("\n") { block ->
                when (block) {
                    is Block.Paragraph -> block.markdown + " " + block.footnotes.joinToString(" ")
                    is Block.Table -> block.title + " " + block.rows.flatten().joinToString(" ") { it.text }
                    is Block.Heading -> block.text
                    else -> ""
                }
            }
        for (forbidden in listOf("Católica", "movimientos locales", "Espera empleo local", "Teme por el agua", "Persona Ejemplo 1", "EN CONTRA", "A FAVOR")) {
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
        assertTrue(model.tables("grupos_interes").any { it.title == "Grupos de interés sin ámbito" })
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
                MonitoringRow(1, "KPI-ENG-01", "Interacciones del periodo", "Actas", "T1", true, 12, 10, "CATALOG", Official("m1", "2026-10-01T00:00:00Z", 11))
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
                .any { it.map { c -> c.text } == listOf("fulfilled", "1") }
        )
        assertEquals(
            listOf("MUY ALTA", "ESTRATEGICO", "A FAVOR", "AIDS", "AUTORIDAD LOCAL", "Deciden en la asamblea."),
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
        assertEquals(46, model.sections.size)
        assertTrue((model.blocks("objetivos").first() as Block.Paragraph).markdown.contains("no registra"))
        assertTrue((model.blocks("aiis").first() as Block.Paragraph).markdown.contains("no tiene un área de influencia indirecta social aprobada"))
        assertTrue((model.blocks("monitoreo").single() as Block.Paragraph).markdown.contains("no están disponibles"))
        assertTrue(model.blocks("evaluacion_grupos").single() is Block.Paragraph)
        assertEquals(listOf(UUID.fromString(T1Source.AIDS)), assembled.maps.single().areas)
    }
}
