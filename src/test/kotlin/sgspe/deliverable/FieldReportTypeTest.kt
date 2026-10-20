package sgspe.deliverable

import kotlinx.coroutines.runBlocking
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import sgspe.privacy.Block
import sgspe.privacy.CanonicalJson
import sgspe.privacy.Disclosure
import sgspe.privacy.Fact
import sgspe.privacy.Mark
import sgspe.privacy.PersonalDataScan
import sgspe.privacy.PhotoRef
import sgspe.privacy.ReportDisclosure
import java.io.ByteArrayInputStream
import java.time.LocalDate
import java.util.UUID

// especificación de F8 §3.6 y §5 («Tipos»): T2 ensamblado con una fuente falsa (sin base) y leído de vuelta con POI. todo
// ficticio (RQ-SEC-018)
class FieldReportTypeTest {
    private val type = FieldReportType()

    private fun id(n: Int): String = "00000000-0000-0000-0000-%012d".format(n)

    private val project = id(1)
    private val community = id(2)
    private val person = id(3)
    private val contribution = id(10)
    private val delivery = id(11)
    private val consented = id(20)
    private val withoutConsent = id(21)
    private val receiptImage = id(22)

    // las filas por objeto; `related` por (objeto, id, relación). internos: DeckPlannerTest arma el mismo T2
    internal inner class FakeSource : ReportSource {
        val rows = mutableMapOf<String, MutableList<SourceRow>>()
        val links = mutableMapOf<Triple<String, String, String>, List<SourceRow>>()
        val read = mutableSetOf<String>()

        fun add(
            objectName: String,
            id: String,
            state: String? = null,
            vararg attributes: Pair<String, Any?>
        ) {
            rows.getOrPut(objectName) { mutableListOf() } += SourceRow(id, state, attributes.toMap())
        }

        override suspend fun one(
            objectName: String,
            id: UUID
        ): SourceRow? = rows[objectName]?.firstOrNull { it.id == id.toString() }.also { read += objectName }

        override suspend fun all(
            objectName: String,
            filters: Map<String, String>
        ): List<SourceRow> = rows[objectName].orEmpty().filter { row -> filters.all { (k, v) -> row.text(k) == v } }.also { read += objectName }

        override suspend fun related(
            objectName: String,
            id: UUID,
            relationship: String,
            target: String
        ): List<SourceRow> = links[Triple(objectName, id.toString(), relationship)].orEmpty().also { read += target }

        override suspend fun concessionTerritories(concessions: List<UUID>): List<Map<String, Any?>> =
            listOf(mapOf("concession" to mapOf("code" to "CM-01"), "object" to "community", "name" to "Comunidad Ejemplo", "areaHa" to "12.5"))

        // T2 no agrupa por microcuenca
        override suspend fun microbasins(localities: List<UUID>): Map<UUID, String> = emptyMap()
    }

    internal fun source(): FakeSource {
        val s = FakeSource()
        s.add("project", project, "active", "code" to "PEA-1", "name" to "Proyecto Ejemplo A", "working_crs" to "EPSG 32717")
        s.add(
            "community",
            community,
            null,
            "name" to "Comunidad Ejemplo",
            "registered_area_ha" to "1500",
            "calculated_area_ha" to "1480.25",
            "recognized_as_indigenous" to true
        )
        s.add("organization", id(4), null, "name" to "Asociación Ejemplo", "address" to "Av. Ejemplo 123, Cajamarca")
        s.add("person", person, null, "given_names" to "Persona", "surnames" to "Ejemplo 1")
        s.add("position", id(5), null, "person" to person, "organization" to id(4), "position_title" to "Presidente", "position_status" to "VIGENTE")
        s.add("stakeholder", id(6), null, "project" to project, "person" to person, "relationship_status" to "RELACION DETERIORADA")
        s.add(
            "incident",
            id(7),
            null,
            "project" to project,
            "incident_type" to "EXPULSION",
            "occurred_at" to "2026-08-20T10:00:00Z",
            "description" to "Ingreso no autorizado."
        )
        s.add(
            "engagement_plan",
            id(8),
            null,
            "project" to project,
            "name" to "Plan 2026",
            "strategic_objective" to "Reconstruir la relación.",
            "action_axes" to "1. Mapeo de actores."
        )
        s.add(
            "engagement",
            id(9),
            "cancelled",
            "project" to project,
            "community" to community,
            "engagement_type" to "REUNION",
            "title" to "Reunión en faena",
            "scheduled_at" to "2026-09-10T15:00:00Z",
            "cancel_reason" to "Lluvia"
        )
        s.add(
            "community_network",
            id(12),
            null,
            "project" to project,
            "community" to community,
            "name" to "Red del Caserío Ejemplo",
            "contact_status" to "PENETRADA"
        )
        s.add("network_node", id(13), null, "community_network" to id(12), "person" to person, "node_role" to "LIDER")
        s.add(
            "social_contribution",
            contribution,
            "delivered",
            "project" to project,
            "code" to "AP-1",
            "declared_purpose" to "Faena comunal",
            "conditions" to "Ninguna",
            "currency" to "PEN",
            "payer_organization" to id(4)
        )
        s.add("delivery", delivery, null, "social_contribution" to contribution, "delivery_date" to "2026-09-15", "place" to "Vivienda del lote 7")
        s.add(
            "contribution_item",
            id(14),
            null,
            "social_contribution" to contribution,
            "item_kind" to "BIEN",
            "description" to "Herramientas",
            "line_amount" to "500"
        )
        s.add(
            "receipt",
            id(15),
            null,
            "social_contribution" to contribution,
            "receipt_type" to "BOLETA DE VENTA",
            "receipt_number" to "B001-123",
            "issuer_name" to "Ferretería Ejemplo",
            "amount" to "500",
            "currency" to "PEN"
        )
        s.add("community_sector", id(16), null, "community" to community, "name" to "Sector Ejemplo Alto", "delegates_count" to 2)
        s.add("mining_concession", id(17), null, "code" to "CM-01", "name" to "Concesión Ejemplo", "holder_organization" to id(4))

        fun file(n: Int) = mapOf("id" to id(100 + n), "sha256" to "%064d".format(n))
        s.links[Triple("delivery", delivery, "evidence_delivery")] =
            listOf(
                SourceRow(consented, null, mapOf("evidence_type" to "FOTO", "file" to file(1), "people_identifiable" to true, "photo_consent_given" to true)),
                SourceRow(withoutConsent, null, mapOf("evidence_type" to "FOTO", "file" to file(2), "people_identifiable" to true)),
                SourceRow(receiptImage, null, mapOf("evidence_type" to "COMPROBANTE", "file" to file(3)))
            )
        s.links[Triple("project", project, "project_concession")] = s.rows.getValue("mining_concession")
        // las menciones de la narrativa que no salen en otra tabla, y una persona del proyecto que el informe no cita
        s.add("person", id(40), null, "given_names" to "Persona", "surnames" to "Ejemplo 2")
        s.add("stakeholder", id(41), null, "project" to project, "person" to id(40))
        s.add("stakeholder", id(42), null, "project" to project, "organization" to id(4))
        s.add("person", id(44), null, "given_names" to "Persona", "surnames" to "Ejemplo 3")
        s.add("position", id(43), null, "person" to id(44), "organization" to id(4), "position_title" to "Tesorero", "position_status" to "CONCLUIDO")
        s.add("person", id(45), null, "given_names" to "Persona", "surnames" to "Ejemplo 4")
        s.add("stakeholder", id(46), null, "project" to project, "person" to id(45))
        return s
    }

    internal fun input(): AssemblyInput =
        AssemblyInput(
            deliverable =
                SourceRow(
                    id(30),
                    "draft",
                    mapOf(
                        "project" to project,
                        "community" to community,
                        "document_code" to "ENT-001",
                        "title" to "Informe de campo y avance",
                        "period_start_date" to "2026-09-01",
                        "period_end_date" to "2026-09-30",
                        "prepared_by_organization" to id(4)
                    )
                ),
            sections =
                listOf(
                    SourceRow(
                        id(31),
                        null,
                        mapOf(
                            "section_key" to "conclusiones",
                            "content" to "1. Se identificó la red del caserío.",
                            "content_origin" to "ASISTIDO POR IA"
                        )
                    ),
                    SourceRow(id(32), null, mapOf("section_key" to "antecedentes", "content" to "El equipo llamó al 987654321.", "content_origin" to "HUMANO")),
                    SourceRow(
                        id(33),
                        null,
                        mapOf(
                            "section_key" to "estrategias",
                            "content" to "Con [[person:${id(40)}]], [[stakeholder:${id(42)}]] y [[position:${id(43)}]].",
                            "content_origin" to "HUMANO"
                        )
                    )
                ),
            templateId = null,
            templateSha256 = "0".repeat(64),
            draftRevision = "A",
            issueRevision = "0",
            today = LocalDate.parse("2026-10-15")
        )

    @Test
    fun `las secciones del tipo en su orden, con sus datos y su texto`() {
        val source = source()
        val assembled = runBlocking { type.assemble(input(), source) }
        val model = assembled.model
        assertEquals(type.sections.map { it.key }, model.sections.map { it.key })
        assertEquals("Setiembre, 2026", model.meta.dateLabel)
        assertEquals("Asociación Ejemplo", model.meta.preparedBy)
        assertEquals(listOf("conclusiones"), model.aiAssistedSections)
        val conclusions = model.sections.single { it.key == "conclusiones" }.blocks
        assertEquals(Block.Paragraph("1. Se identificó la red del caserío."), conclusions.single())
        val activities =
            model.sections
                .single { it.key == "actividades" }
                .blocks
                .filterIsInstance<Block.Table>()
                .single()
        assertEquals("Cancelada: Lluvia", activities.rows.single()[3].text)
        val receipts =
            model.sections
                .single { it.key == "apoyo" }
                .blocks
                .filterIsInstance<Block.Table>()
                .single { it.title == "Comprobantes del aporte" }
        assertEquals(listOf(null, Mark.REGISTRY, Mark.HOLDER, null, null), receipts.rows.single().map { it.mark })
        assertEquals(setOf(consented, withoutConsent, receiptImage), model.photos.keys)
        assertEquals(PhotoRef.RECEIPT, model.photos.getValue(receiptImage).kind)
        assertEquals(listOf("portada", "predio"), assembled.maps.map { it.key })
        assertEquals("EPSG 32717", assembled.maps.first().workingCrs)
        assertEquals(MapFrameKind.COMMUNITY_AND_PROJECT to MapFrameKind.COMMUNITY, assembled.maps[0].frame to assembled.maps[1].frame)
        assertEquals("Asociación Ejemplo", assembled.maps.first().author)
        val target = model.people.getValue("person:$person")
        assertEquals("Persona Ejemplo 1", target.name)
        assertEquals("Presidente", target.position)
        assertTrue(target.stakeholderCode!!.startsWith("GI-"))
        assertTrue("Persona Ejemplo 1" in assembled.personNames)
        assertTrue("El equipo llamó al 987654321." in assembled.texts.getValue("antecedentes"))
    }

    @Test
    fun `el modelo va y vuelve y su DOCX EXTERNO no nombra a la persona, el lugar de la entrega ni lleva el comprobante`() {
        val model = runBlocking { type.assemble(input(), source()) }.model
        val back = CanonicalJson.read(CanonicalJson.bytes(model))
        assertEquals(model, back)
        val external = ReportDisclosure.apply(back, Disclosure.EXTERNO)
        assertEquals(setOf(consented), external.photos.keys)
        val text = docxText(external)
        assertFalse("Persona Ejemplo 1" in text, text)
        assertFalse("B001-123" in text || "Ferretería Ejemplo" in text, text)
        assertTrue("Presidente, Asociación Ejemplo" in text, text)
        assertTrue("Tabla 1." in text, text)
        // el lugar puede ser una vivienda (deliverables.md §9.1): solo en INTERNO; la fecha de la entrega queda
        assertFalse("Vivienda del lote 7" in text, text)
        assertTrue("Entrega:" in text, text)
        assertTrue("Vivienda del lote 7" in docxText(ReportDisclosure.apply(back, Disclosure.INTERNO)))
    }

    private fun docxText(leveled: sgspe.privacy.ReportModel): String {
        val bytes = DocxWriter(DeliverableProperties.Photos()).write(DocxWriter.Input(leveled, BaseTemplate.bytes, true, "Asociación Ejemplo", emptyMap()))
        return XWPFDocument(ByteArrayInputStream(bytes)).use { doc ->
            (doc.paragraphs.map { it.text } + doc.tables.flatMap { t -> t.rows.flatMap { r -> r.tableCells.map { it.text } } }).joinToString("\n")
        }
    }

    @Test
    fun `T2 exige comunidad, periodo y el working_crs del proyecto`() {
        val missing = runBlocking { type.missingParameters(mapOf("project" to project), mapOf("name" to "Proyecto Ejemplo A")) }
        assertEquals(listOf("community", "period_start_date", "period_end_date", "project.working_crs"), missing)
        assertEquals(emptyList<String>(), runBlocking { type.missingParameters(input().deliverable.attributes, mapOf("working_crs" to "EPSG 32717")) })
    }

    @Test
    fun `solo lee objetos que declara`() {
        val source = source()
        runBlocking { type.assemble(input(), source) }
        assertEquals(emptySet<String>(), source.read - type.reads.keys)
    }

    @Test
    fun `las menciones de la narrativa se resuelven por nivel y el aviso busca a todas las personas del proyecto`() {
        val assembled = runBlocking { type.assemble(input(), source()) }

        fun text(level: Disclosure) =
            ReportDisclosure
                .apply(assembled.model, level)
                .sections
                .single { it.key == "estrategias" }
                .blocks
                .filterIsInstance<Block.Paragraph>()
                .joinToString { it.markdown }
        assertTrue("Con Persona Ejemplo 2, Asociación Ejemplo y Persona Ejemplo 3." in text(Disclosure.INTERNO), text(Disclosure.INTERNO))
        val external = text(Disclosure.EXTERNO)
        assertFalse("Persona Ejemplo" in external, external)
        assertTrue("GI-" in external && "Tesorero, Asociación Ejemplo" in external, external)
        assertTrue("Persona Ejemplo 4" in assembled.personNames)
    }

    @Test
    fun `el pie de una foto con el nombre de una persona del proyecto da el aviso de datos personales`() {
        val source = source()
        val row = source.links.getValue(Triple("delivery", delivery, "evidence_delivery"))
        source.links[Triple("delivery", delivery, "evidence_delivery")] =
            row.map { if (it.id == consented) SourceRow(it.id, null, it.attributes + ("title" to "Entrega a Persona Ejemplo 1")) else it }
        val assembled = runBlocking { type.assemble(input(), source) }
        val warnings = PersonalDataScan.scan(assembled.texts, assembled.personNames)
        assertTrue(warnings.any { it.section == "apoyo" }, warnings.toString())
    }

    // #88: «Con» lista a quienes asistieron (participantes con persona, grupo de interés u organización, o el grupo que
    // representan) además de los grupos vinculados; nunca participant_name, que es restringido
    @Test
    fun `los contactos cara a cara citan a los participantes que asistieron`() {
        val source = source()
        source.add(
            "engagement",
            id(60),
            "held",
            "project" to project,
            "community" to community,
            "engagement_type" to "CONTACTO CARA A CARA",
            "held_at" to "2026-09-12T15:00:00Z",
            "results" to "Acordaron una visita."
        )
        source.add("engagement_participant", id(61), null, "engagement" to id(60), "person" to person, "attended" to true)
        source.add("engagement_participant", id(62), null, "engagement" to id(60), "represented_group" to "Junta de usuarios", "attended" to true)
        source.add("engagement_participant", id(63), null, "engagement" to id(60), "person" to id(41), "attended" to false)
        source.add("engagement_participant", id(64), null, "engagement" to id(60), "participant_name" to "Nombre Restringido", "attended" to true)
        val model = runBlocking { type.assemble(input(), source) }.model
        val contacts =
            model.sections
                .single { it.key == "relaciones" }
                .blocks
                .filterIsInstance<Block.Table>()
                .single { it.title == "Contactos cara a cara del periodo" }
        assertEquals("[[person:$person]], Junta de usuarios, una persona", contacts.rows.single()[1].text)
    }

    // ADR-0031 (plan de presentaciones, paso 3): desde typeVersion 2 los hechos tipados salen de los mismos datos que su tabla y
    // cada tabla lleva su clave estable
    private fun sgspe.privacy.ReportModel.fact(key: String): Fact = facts.orEmpty().single { it.key == key }

    private fun sgspe.privacy.ReportModel.table(
        section: String,
        title: String
    ): Block.Table =
        sections
            .single { it.key == section }
            .blocks
            .filterIsInstance<Block.Table>()
            .single { it.title == title }

    @Test
    fun `T2 es typeVersion 2 y cada tabla lleva su clave estable`() {
        assertEquals(2, type.version)
        val source = source()
        source.add(
            "engagement",
            id(60),
            "held",
            "project" to project,
            "community" to community,
            "engagement_type" to "CONTACTO CARA A CARA",
            "held_at" to "2026-09-12T15:00:00Z"
        )
        source.add(
            "field_visit",
            id(65),
            "completed",
            "project" to project,
            "title" to "Visita de setiembre",
            "actual_start_date" to "2026-09-05"
        )
        val model = runBlocking { type.assemble(input(), source) }.model
        assertEquals(2, model.meta.typeVersion)
        val keys =
            model.sections
                .flatMap { it.blocks }
                .filterIsInstance<Block.Table>()
                .map { it.title to it.key }
        assertTrue(keys.all { (_, key) -> key != null && Regex("^[a-z0-9]+(_[a-z0-9]+)*$").matches(key) }, keys.toString())
        assertEquals(
            listOf(
                "incidentes",
                "estado_de_la_relacion",
                "visitas_de_campo",
                "interacciones_del_periodo",
                "integrantes_de_red",
                "contactos_cara_a_cara",
                "cargos",
                "items_del_aporte",
                "comprobantes",
                "concesiones",
                "cruces",
                "sectores_comunales"
            ),
            keys.map { it.second }
        )
        assertEquals(model, CanonicalJson.read(CanonicalJson.bytes(model)))
    }

    @Test
    fun `las actividades por tipo van sobre el catalogo completo con las filas de su tabla`() {
        val model = runBlocking { type.assemble(input(), source()) }.model
        val table = model.table("actividades", "Interacciones del periodo en la comunidad")
        val fact = model.fact("actividades_por_tipo")
        assertEquals(Fact.COUNT, fact.kind)
        assertTrue(fact.forPublic)
        assertEquals(
            ReportLabels.enumValues("engagement_type").map { listOf(ReportLabels.option(it)!!) } + listOf(listOf("Sin dato"), listOf("No disponible")),
            fact.rows.map { it.labels }
        )
        assertEquals(table.rows.size, fact.rows.sumOf { it.count!! })
        assertEquals(1, fact.rows.single { it.labels == listOf(table.rows.single()[1].text) }.count)
    }

    @Test
    fun `las redes y sus integrantes se cuentan como en sus tablas`() {
        val source = source()
        source.add("community_network", id(70), null, "project" to project, "community" to community, "name" to "Red sin integrantes")
        source.add("network_node", id(71), null, "community_network" to id(12), "organization" to id(4), "node_role" to "MIEMBRO")
        val model = runBlocking { type.assemble(input(), source) }.model
        val fact = model.fact("redes_y_nodos")
        assertEquals(Fact.COUNT, fact.kind)
        assertTrue(fact.forPublic)
        assertEquals(listOf(listOf("Redes") to 2, listOf("Integrantes") to 2), fact.rows.map { it.labels to it.count })
        assertEquals(2, model.table("redes", "Integrantes de Red del Caserío Ejemplo").rows.size)
    }

    @Test
    fun `los items del aporte por tipo y el monto total salen de la tabla del aporte`() {
        val source = source()
        source.add(
            "contribution_item",
            id(18),
            null,
            "social_contribution" to contribution,
            "item_kind" to "BIEN",
            "description" to "Clavos",
            "line_amount" to "20.5"
        )
        source.add(
            "contribution_item",
            id(19),
            null,
            "social_contribution" to contribution,
            "item_kind" to "SERVICIO",
            "description" to "Flete",
            "line_amount" to "1000"
        )
        val model = runBlocking { type.assemble(input(), source) }.model
        val table = model.table("apoyo", "Ítems del aporte por tipo")
        val items = model.fact("items_del_aporte")
        assertEquals(Fact.AMOUNT, items.kind)
        assertFalse(items.forPublic)
        assertEquals(listOf("Aporte", "Tipo"), items.dimensions)
        assertEquals(table.rows.map { listOf("AP-1", it[0].text) to it[2].text }, items.rows.map { it.labels to it.value })
        assertEquals(listOf(listOf("AP-1", "BIEN") to "520.50", listOf("AP-1", "SERVICIO") to "1,000.00"), items.rows.map { it.labels to it.value })
        assertTrue(items.rows.all { it.unit == "PEN" })
        val total = model.fact("monto_del_aporte")
        assertTrue(total.forPublic)
        assertEquals(listOf(listOf("AP-1") to "1,520.50"), total.rows.map { it.labels to it.value })
        assertEquals("PEN", total.unit)
        // en PUBLICO el monto total sale y los ítems no
        val public =
            ReportDisclosure
                .apply(model, Disclosure.PUBLICO)
                .facts
                .orEmpty()
                .map { it.key }
        assertTrue("monto_del_aporte" in public && "items_del_aporte" !in public, public.toString())
    }

    @Test
    fun `las concesiones y los cruces dan su area en hectareas como en sus tablas`() {
        val source = source()
        source.rows.getValue("mining_concession").replaceAll { it.copy(attributes = it.attributes + ("registered_area_ha" to "1234.5")) }
        source.links[Triple("project", project, "project_concession")] = source.rows.getValue("mining_concession")
        val model = runBlocking { type.assemble(input(), source) }.model
        val concessions = model.fact("concesiones")
        assertEquals(Fact.AMOUNT, concessions.kind)
        assertTrue(concessions.forPublic)
        assertEquals("ha", concessions.unit)
        assertEquals(listOf(listOf("CM-01", "Concesión Ejemplo") to "1,234.50"), concessions.rows.map { it.labels to it.value })
        assertEquals(
            "1,234.50",
            model
                .table("predio", "Concesiones del proyecto")
                .rows
                .single()[3]
                .text
        )
        val crossings = model.fact("cruces")
        assertFalse(crossings.forPublic)
        assertEquals(listOf(listOf("CM-01", "Territorio comunal", "Comunidad Ejemplo") to "12.50"), crossings.rows.map { it.labels to it.value })
        assertEquals(
            "12.50",
            model
                .table("predio", "Concesiones sobre el territorio comunal, los sectores y los caseríos")
                .rows
                .single()[3]
                .text
        )
    }
}
