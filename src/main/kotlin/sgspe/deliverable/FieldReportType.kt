package sgspe.deliverable

import org.springframework.stereotype.Component
import sgspe.privacy.Block
import sgspe.privacy.Cell
import sgspe.privacy.Mark
import sgspe.privacy.MentionTarget
import sgspe.privacy.Mentions
import sgspe.privacy.PhotoRef
import sgspe.privacy.RegisterSheets
import sgspe.privacy.ReportMeta
import sgspe.privacy.ReportModel
import sgspe.privacy.ReportSection
import java.time.LocalDate
import java.util.UUID

// T2 · Informe de campo y avance (especificación de F8 §3.6, deliverables.md §6, DOC-02): sin índice, con número de página.
// exige project, community, el periodo y el working_crs del proyecto (§2.2.13). lee como la plataforma, solo lo que declara
// en `reads` (ReportSourcesTest). las personas van como menciones [[person:<id>]] o [[stakeholder:<id>]] y sus datos de cita
// en ReportModel.people; los números de comprobante y el emisor llevan su marca (EXTERNO los quita); las fotos de la entrega y
// la del comprobante van por evidencia con su consentimiento. el texto de cada sección se copia al final de sus bloques. los
// mapas (portada y predio) se piden a ReportMaps; en 8.2b los dibuja un doble y 8.2c trae GeoServer
@Component
class FieldReportType : DeliverableType {
    override val name: String = NAME
    override val version: Int = 1

    override val sections: List<SectionSpec> =
        listOf(
            SectionSpec("portada", "Portada", 1, CALCULATED, true, "Título con el proyecto, mes y año del fin del periodo y mapa de ubicación.", false),
            SectionSpec(
                "antecedentes",
                "Antecedentes",
                2,
                MIXED,
                true,
                "Historia de la relación: ingresos, expulsiones o retenciones, respuesta del equipo social y estado de la relación.",
                true
            ),
            SectionSpec("objetivos", "Objetivo estratégico y objetivos tácticos", 3, CALCULATED, true, "Del plan de relacionamiento del proyecto.", false),
            SectionSpec("estrategias", "Estrategias de intervención", 4, MIXED, true, "Ejes de acción y su justificación, en lista numerada.", true),
            SectionSpec("actividades", "Actividades del periodo", 5, MIXED, true, "Viajes y reuniones del periodo, también las canceladas y su motivo.", true),
            SectionSpec("redes", "Identificación de redes comunitarias", 6, MIXED, true, "Redes de la comunidad, sus integrantes y su papel.", false),
            SectionSpec(
                "relaciones",
                "Construcción de relaciones y penetración en redes",
                7,
                MIXED,
                true,
                "Contactos cara a cara, cargos vigentes o previstos y organizaciones con las que se puede dialogar.",
                true
            ),
            SectionSpec("apoyo", "Apoyo entregado", 8, MIXED, false, "Pedido, ocasión, propósito, condiciones, ítems y entrega con su acta.", false),
            SectionSpec(
                "predio",
                "Identificación del predio",
                9,
                CALCULATED,
                true,
                "Concesiones y su relación con el territorio comunal y los caseríos; datos registrales y sectores.",
                false
            ),
            SectionSpec("conclusiones", "Conclusiones", 10, NARRATIVE, true, "Redes identificadas, contactos logrados y pendientes, en lista numerada.", true),
            SectionSpec("recomendaciones", "Recomendaciones", 11, NARRATIVE, true, "Con vínculos a riesgos sociales y acuerdos, en lista numerada.", true)
        )

    override val reads: Map<String, Set<String>> =
        mapOf(
            Deliverables.PROJECT to setOf("code", "name", "working_crs"),
            "organization" to setOf("name", "address"),
            "incident" to setOf("code", "incident_type", "occurred_at", "place", "description", "immediate_response", "consequences", "project", "community"),
            "stakeholder" to setOf("relationship_status", "person", "organization", "project", "locality"),
            "person" to setOf("given_names", "surnames", "residence_locality"),
            "locality" to setOf("name"),
            "position" to setOf("position_title", "position_status", "person", "organization"),
            "engagement_plan" to setOf("name", "strategic_objective", "tactical_objectives", "action_axes", "project"),
            "field_visit" to setOf("title", "itinerary", "outcome_summary", "actual_start_date", "planned_start_date", "project"),
            "engagement" to setOf("engagement_type", "title", "results", "scheduled_at", "held_at", "cancel_reason", "project", "community", "field_visit"),
            "community_network" to setOf("name", "network_type", "contact_status", "description", "project", "community"),
            "network_node" to setOf("node_role", "community_network", "person", "organization"),
            "social_contribution" to
                setOf(
                    "code",
                    "contribution_type",
                    "occasion",
                    "declared_purpose",
                    "conditions",
                    "currency",
                    "project",
                    "payer_organization",
                    "recipient_organization"
                ),
            "contribution_item" to setOf("description", "item_kind", "line_amount", "social_contribution"),
            "receipt" to setOf("receipt_type", "receipt_number", "issuer_name", "issue_date", "amount", "currency", "social_contribution"),
            "delivery" to setOf("delivery_date", "place", "social_contribution"),
            "evidence" to setOf("evidence_type", "title", "file", "people_identifiable", "photo_consent_given"),
            "mining_concession" to setOf("code", "name", "registered_area_ha", "holder_organization"),
            "community" to
                setOf(
                    "name",
                    "recognized_as_indigenous",
                    "indigenous_people",
                    "registered_area_ha",
                    "calculated_area_ha",
                    "property_record",
                    "parent_record",
                    "independized",
                    "legal_entity_record",
                    "title_reference",
                    "title_issuer_as_written",
                    "title_issuer_normalized",
                    "registry_records_unverified"
                ),
            "community_sector" to setOf("name", "delegates_count", "community")
        )

    override suspend fun missingParameters(
        deliverable: Map<String, Any?>,
        project: Map<String, Any?>
    ): List<String> =
        listOfNotNull(
            "community".takeIf { deliverable["community"] == null },
            Deliverables.PERIOD_START.takeIf { deliverable[Deliverables.PERIOD_START] == null },
            Deliverables.PERIOD_END.takeIf { deliverable[Deliverables.PERIOD_END] == null },
            "project.working_crs".takeIf { project["working_crs"] == null }
        )

    override suspend fun assemble(
        input: AssemblyInput,
        source: ReportSource
    ): Assembled {
        val d = input.deliverable
        val projectId = d.ref(Deliverables.PROJECT)!!
        val communityId = d.ref("community")!!
        val project = source.one(Deliverables.PROJECT, projectId)!!
        val community = source.one("community", communityId)!!
        val start = LocalDate.parse(d.text(Deliverables.PERIOD_START)!!.take(10))
        val end = LocalDate.parse(d.text(Deliverables.PERIOD_END)!!.take(10))
        val cutoff = d.text(Deliverables.CUTOFF)?.let { LocalDate.parse(it.take(10)) } ?: end
        val scene = Scene(source, projectId, communityId, start, end, cutoff)
        val texts = input.sections.associate { (it.text(Deliverables.SECTION_KEY) ?: "") to it.text(Deliverables.CONTENT) }
        val built =
            sections.map { spec ->
                val blocks =
                    when (spec.key) {
                        "portada" -> cover(project)
                        "antecedentes" -> scene.background()
                        "objetivos" -> scene.objectives()
                        "estrategias" -> scene.strategies()
                        "actividades" -> scene.activities()
                        "redes" -> scene.networks()
                        "relaciones" -> scene.relations()
                        "apoyo" -> scene.support()
                        "predio" -> scene.land(community)
                        else -> emptyList()
                    }
                val narrative = texts[spec.key]?.let { listOf(Block.Paragraph(it)) }.orEmpty()
                ReportSection(spec.key, spec.title, spec.kind, blocks + narrative)
            }
        input.sections
            .flatMap { Mentions.keys(it.text(Deliverables.CONTENT).orEmpty()) }
            .distinct()
            .forEach { scene.mention(it) }
        scene.projectNames()
        val preparedFor = d.ref("prepared_for_organization")?.let { source.one("organization", it) }
        val preparedBy = d.ref("prepared_by_organization")?.let { source.one("organization", it) }
        val meta =
            ReportMeta(
                type = NAME,
                typeVersion = version,
                code = d.text(Deliverables.CODE)!!,
                title = d.text(Deliverables.TITLE)!!,
                draftRevision = input.draftRevision,
                issueRevision = input.issueRevision,
                project = project.text("name")!!,
                periodStart = start.toString(),
                periodEnd = end.toString(),
                cutoff = cutoff.toString(),
                scope = community.text("name"),
                preparedFor = preparedFor?.text("name"),
                preparedBy = preparedBy?.text("name"),
                authorAddress = preparedBy?.text("address"),
                dateLabel = ReportFormat.monthYear(end),
                templateId = input.templateId,
                templateSha256 = input.templateSha256,
                coverMap = COVER_MAP
            )
        val crs = project.text("working_crs")!!
        val maps =
            listOf(
                MapRequest(COVER_MAP, projectId, crs, communityId, listOf("LYR-05", "LYR-07", "LYR-02", "LYR-04", "LYR-01", "LYR-08")),
                MapRequest(LAND_MAP, projectId, crs, communityId, listOf("LYR-05", "LYR-07", "LYR-02", "LYR-04", "LYR-01", "LYR-06"))
            )
        val sectionTexts =
            built.associate { section ->
                section.key to
                    (texts[section.key]?.let { listOf(it) }.orEmpty() + scene.recordTexts[section.key].orEmpty())
            }
        val model =
            ReportModel(
                meta = meta,
                sections = built,
                people = scene.people,
                photos = scene.photos,
                aiAssistedSections =
                    input.sections
                        .filter { it.text(Deliverables.CONTENT_ORIGIN) == Deliverables.AI_ASSISTED }
                        .mapNotNull { it.text(Deliverables.SECTION_KEY) }
                        .sorted()
            )
        return Assembled(model, maps, scene.personNames, sectionTexts)
    }

    private fun cover(project: SourceRow): List<Block> =
        listOf(
            Block.Paragraph("Informe de trabajo de campo y avance en el proyecto ${project.text("name")}"),
            Block.MapFigure(COVER_MAP, "Mapa de ubicación")
        )

    // lo que se lee una vez por ensamblado y lo que se acumula al leer: menciones, fotos, nombres y textos de registro
    private inner class Scene(
        val source: ReportSource,
        val project: UUID,
        val community: UUID,
        val start: LocalDate,
        val end: LocalDate,
        val cutoff: LocalDate
    ) {
        val people = linkedMapOf<String, MentionTarget>()
        val photos = linkedMapOf<String, PhotoRef>()
        val personNames = linkedSetOf<String>()
        val recordTexts = linkedMapOf<String, MutableList<String>>()
        private val byProject = mapOf(Deliverables.PROJECT to project.toString())

        private fun keep(
            section: String,
            vararg values: String?
        ) {
            recordTexts.getOrPut(section) { mutableListOf() } += values.filterNotNull()
        }

        private fun inPeriod(raw: String?): Boolean {
            val day = raw?.take(10)?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return false
            return !day.isBefore(start) && !day.isAfter(end)
        }

        private suspend fun organizationName(id: UUID?): String? = id?.let { source.one("organization", it)?.text("name") }

        // la mención de una persona: su nombre, su cargo vigente con su organización, su código GI- en el proyecto y su localidad
        suspend fun person(id: UUID): String {
            val key = "person:$id"
            if (key !in people) {
                val person = source.one("person", id)
                val name = listOfNotNull(person?.text("given_names"), person?.text("surnames")).joinToString(" ").ifEmpty { "Sin nombre" }
                personNames += name
                val position = source.all("position", mapOf("person" to id.toString())).firstOrNull { it.text("position_status") == "VIGENTE" }
                val stakeholder = source.all("stakeholder", byProject + ("person" to id.toString())).firstOrNull()
                people[key] =
                    MentionTarget(
                        name,
                        position?.text("position_title"),
                        organizationName(position?.ref("organization")),
                        stakeholder?.let { RegisterSheets.code(it.id) },
                        person?.ref("residence_locality")?.let { source.one("locality", it)?.text("name") }
                    )
            }
            return "[[$key]]"
        }

        // una mención escrita en la narrativa (ASM-137): [[person:<id>]], [[stakeholder:<id>]] o [[position:<id>]], resuelta con lo
        // que el tipo lee. una que no existe queda sin resolver y Mentions la imprime «una persona» en todo nivel
        suspend fun mention(key: String) {
            if (key in people) return
            val id = runCatching { UUID.fromString(key.substringAfter(':')) }.getOrNull() ?: return
            when (key.substringBefore(':')) {
                "person" -> person(id)
                "stakeholder" -> {
                    val row = source.one("stakeholder", id) ?: return
                    val code = RegisterSheets.code(row.id)
                    val holder = row.ref("person")?.let { holder -> person(holder).let { people["person:$holder"] } }
                    val organization = organizationName(row.ref("organization"))
                    people[key] =
                        holder?.copy(stakeholderCode = code)
                            ?: MentionTarget(
                                organization ?: code,
                                null,
                                organization,
                                code,
                                row.ref("locality")?.let { source.one("locality", it)?.text("name") }
                            )
                }
                "position" -> {
                    val row = source.one("position", id) ?: return
                    val holder = row.ref("person")?.let { holder -> person(holder).let { people["person:$holder"] } }
                    people[key] =
                        MentionTarget(
                            holder?.name ?: row.text("position_title") ?: "Sin nombre",
                            row.text("position_title"),
                            organizationName(row.ref("organization")),
                            holder?.stakeholderCode,
                            holder?.locality
                        )
                }
            }
        }

        // los nombres de las personas de los grupos de interés del proyecto, para el aviso de datos personales (ASM-137): se
        // buscan aunque el informe no las cite
        suspend fun projectNames() {
            for (row in source.all("stakeholder", byProject)) {
                val id = row.ref("person") ?: continue
                val person = source.one("person", id) ?: continue
                listOfNotNull(person.text("given_names"), person.text("surnames")).joinToString(" ").ifEmpty { null }?.let { personNames += it }
            }
        }

        // un grupo de interés: su persona (mención) o el nombre de su organización
        suspend fun stakeholder(row: SourceRow): String =
            row.ref("person")?.let { person(it) } ?: organizationName(row.ref("organization")) ?: RegisterSheets.code(row.id)

        suspend fun background(): List<Block> {
            val incidents =
                source
                    .all("incident", byProject)
                    .filter { it.text("incident_type") in BACKGROUND_INCIDENTS }
                    .filter { row -> row.text("occurred_at")?.take(10)?.let { !LocalDate.parse(it).isAfter(cutoff) } ?: false }
                    .sortedBy { it.text("occurred_at") }
            incidents.forEach { keep("antecedentes", it.text("description"), it.text("immediate_response"), it.text("consequences")) }
            val stakeholders = source.all("stakeholder", byProject).filter { it.text("relationship_status") != null }
            return listOfNotNull(
                incidents.takeIf { it.isNotEmpty() }?.let { rows ->
                    Block.Table(
                        "Incidentes de expulsión y retención hasta el corte",
                        listOf("Fecha", "Tipo", "Lugar", "Descripción", "Respuesta inmediata"),
                        rows.map {
                            listOf(
                                Cell(ReportFormat.date(it.text("occurred_at"))),
                                Cell(it.text("incident_type").orEmpty()),
                                Cell(it.text("place").orEmpty()),
                                Cell(it.text("description").orEmpty()),
                                Cell(it.text("immediate_response").orEmpty())
                            )
                        }
                    )
                },
                stakeholders.takeIf { it.isNotEmpty() }?.let { rows ->
                    Block.Table(
                        "Estado de la relación con los grupos de interés",
                        listOf("Grupo de interés", "Estado de la relación"),
                        rows.map { listOf(Cell(stakeholder(it)), Cell(it.text("relationship_status").orEmpty())) }
                    )
                }
            )
        }

        private suspend fun plans(): List<SourceRow> = source.all("engagement_plan", byProject).sortedBy { it.text("name") }

        suspend fun objectives(): List<Block> =
            plans().flatMap { plan ->
                keep("objetivos", plan.text("strategic_objective"), plan.text("tactical_objectives"))
                listOfNotNull(
                    plan.text("strategic_objective")?.let { Block.Paragraph("**Objetivo estratégico.** $it") },
                    plan.text("tactical_objectives")?.let { Block.Paragraph("**Objetivos tácticos.**\n\n$it") }
                )
            }

        suspend fun strategies(): List<Block> =
            plans().mapNotNull { plan ->
                keep("estrategias", plan.text("action_axes"))
                plan.text("action_axes")?.let { Block.Paragraph(it) }
            }

        suspend fun activities(): List<Block> {
            val visits = source.all("field_visit", byProject).filter { inPeriod(it.text("actual_start_date") ?: it.text("planned_start_date")) }
            val engagements =
                source
                    .all("engagement", byProject + ("community" to community.toString()))
                    .filter { inPeriod(it.text("held_at") ?: it.text("scheduled_at")) }
                    .sortedBy { it.text("held_at") ?: it.text("scheduled_at") }
            visits.forEach { keep("actividades", it.text("itinerary"), it.text("outcome_summary")) }
            engagements.forEach { keep("actividades", it.text("results")) }
            val title = "Actividades realizadas entre ${ReportFormat.month(start)} y ${ReportFormat.month(end)} de ${end.year}"
            return listOfNotNull(
                Block.Heading(2, title),
                visits.takeIf { it.isNotEmpty() }?.let { rows ->
                    Block.Table(
                        "Visitas de campo del periodo",
                        listOf("Inicio", "Visita", "Itinerario", "Resultado"),
                        rows.map {
                            listOf(
                                Cell(ReportFormat.date(it.text("actual_start_date") ?: it.text("planned_start_date"))),
                                Cell(it.text("title").orEmpty()),
                                Cell(it.text("itinerary").orEmpty()),
                                Cell(it.text("outcome_summary").orEmpty())
                            )
                        }
                    )
                },
                engagements.takeIf { it.isNotEmpty() }?.let { rows ->
                    Block.Table(
                        "Interacciones del periodo en la comunidad",
                        listOf("Fecha", "Tipo", "Interacción", "Estado"),
                        rows.map {
                            val state = if (it.state == CANCELLED) "Cancelada: ${it.text("cancel_reason").orEmpty()}" else it.state.orEmpty()
                            listOf(
                                Cell(ReportFormat.date(it.text("held_at") ?: it.text("scheduled_at"))),
                                Cell(it.text("engagement_type").orEmpty()),
                                Cell(it.text("title").orEmpty()),
                                Cell(state)
                            )
                        }
                    )
                }
            )
        }

        suspend fun networks(): List<Block> =
            source
                .all("community_network", byProject + ("community" to community.toString()))
                .sortedBy { it.text("name") }
                .flatMap { network ->
                    keep("redes", network.text("description"))
                    val nodes = source.all("network_node", mapOf("community_network" to network.id))
                    val rows =
                        nodes.map { node ->
                            val who = node.ref("person")?.let { person(it) } ?: organizationName(node.ref("organization")) ?: ""
                            listOf(Cell(who), Cell(node.text("node_role").orEmpty()))
                        }
                    listOfNotNull(
                        Block.Heading(2, network.text("name")!!),
                        Block.Paragraph(
                            listOfNotNull(
                                network.text("network_type"),
                                network.text("contact_status")?.let {
                                    "estado del contacto: $it"
                                }
                            ).joinToString("; ").ifEmpty { "Sin datos de la red." }
                        ),
                        rows.takeIf { it.isNotEmpty() }?.let {
                            Block.Table(
                                "Integrantes de ${network.text("name")}",
                                listOf("Integrante", "Papel en la red"),
                                it
                            )
                        }
                    )
                }

        suspend fun relations(): List<Block> {
            val contacts =
                source
                    .all("engagement", byProject + ("community" to community.toString()) + ("engagement_type" to FACE_TO_FACE))
                    .filter { inPeriod(it.text("held_at") ?: it.text("scheduled_at")) }
            val rows =
                contacts.map { contact ->
                    keep("relaciones", contact.text("results"))
                    val who = source.related("engagement", UUID.fromString(contact.id), "engagement_stakeholder", "stakeholder").map { stakeholder(it) }
                    listOf(
                        Cell(ReportFormat.date(contact.text("held_at") ?: contact.text("scheduled_at"))),
                        Cell(who.joinToString(", ")),
                        Cell(contact.text("results").orEmpty())
                    )
                }
            val mentioned = people.keys.filter { it.startsWith("person:") }.map { UUID.fromString(it.removePrefix("person:")) }
            val positions =
                mentioned.flatMap { id ->
                    source.all("position", mapOf("person" to id.toString())).filter { it.text("position_status") in OPEN_POSITIONS }.map { id to it }
                }
            return listOfNotNull(
                rows.takeIf { it.isNotEmpty() }?.let { Block.Table("Contactos cara a cara del periodo", listOf("Fecha", "Con", "Resultados"), it) },
                positions.takeIf { it.isNotEmpty() }?.let { list ->
                    Block.Table(
                        "Cargos vigentes y previstos",
                        listOf("Persona", "Cargo", "Organización", "Situación"),
                        list.map { (id, p) ->
                            listOf(
                                Cell("[[person:$id]]"),
                                Cell(p.text("position_title").orEmpty()),
                                Cell(organizationName(p.ref("organization")).orEmpty()),
                                Cell(p.text("position_status").orEmpty())
                            )
                        }
                    )
                }
            )
        }

        suspend fun support(): List<Block> {
            val delivered =
                source.all("social_contribution", byProject).filter { it.state in DELIVERED }.mapNotNull { contribution ->
                    source
                        .all("delivery", mapOf("social_contribution" to contribution.id))
                        .firstOrNull { inPeriod(it.text("delivery_date")) }
                        ?.let { contribution to it }
                }
            return delivered.flatMap { (contribution, delivery) ->
                keep("apoyo", contribution.text("declared_purpose"), contribution.text("conditions"))
                val payer = organizationName(contribution.ref("payer_organization"))
                val recipient = organizationName(contribution.ref("recipient_organization"))
                val items = source.all("contribution_item", mapOf("social_contribution" to contribution.id))
                val byKind = items.groupBy { it.text("item_kind").orEmpty() }
                val receipts = source.all("receipt", mapOf("social_contribution" to contribution.id))
                val evidence =
                    source.related("delivery", UUID.fromString(delivery.id), "evidence_delivery", "evidence").filter {
                        it.text("evidence_type") in
                            PICTURES
                    }
                evidence.forEach { row ->
                    val file = row.attributes["file"] as? Map<*, *>
                    if (file != null) {
                        photos[row.id] =
                            PhotoRef(
                                row.id,
                                file["id"].toString(),
                                file["sha256"].toString(),
                                row.text("title") ?: row.text("evidence_type")!!.lowercase(),
                                if (row.text("evidence_type") == PhotoRef.RECEIPT) PhotoRef.RECEIPT else PhotoRef.PHOTO,
                                row.attributes["people_identifiable"] as? Boolean,
                                row.attributes["photo_consent_given"] as? Boolean
                            )
                    }
                }
                listOfNotNull(
                    Block.Heading(2, "Aporte ${contribution.text("code")}"),
                    Block.Paragraph(
                        listOfNotNull(
                            contribution.text("occasion")?.let { "**Ocasión:** $it" },
                            contribution.text("declared_purpose")?.let { "**Propósito declarado:** $it" },
                            contribution.text("conditions")?.let { "**Condiciones:** $it" },
                            payer?.let { "**Pagador:** $it" },
                            recipient?.let { "**Receptor:** $it" },
                            "**Entrega:** ${ReportFormat.date(delivery.text("delivery_date"))}${delivery.text("place")?.let { ", $it" }.orEmpty()}"
                        ).joinToString("\n\n")
                    ),
                    byKind.takeIf { it.isNotEmpty() }?.let { groups ->
                        Block.Table(
                            "Ítems del aporte por tipo",
                            listOf("Tipo", "Ítems", "Monto (${contribution.text("currency").orEmpty()})"),
                            groups.map { (kind, rows) ->
                                listOf(
                                    Cell(kind),
                                    Cell(
                                        rows
                                            .mapNotNull {
                                                it.text("description")
                                            }.joinToString("; ")
                                    ),
                                    Cell(ReportFormat.decimal(ReportFormat.sum(rows.map { it.attributes["line_amount"] })))
                                )
                            }
                        )
                    },
                    receipts.takeIf { it.isNotEmpty() }?.let { rows ->
                        Block.Table(
                            "Comprobantes del aporte",
                            listOf("Tipo", "Número", "Emisor", "Fecha", "Monto"),
                            rows.map {
                                listOf(
                                    Cell(it.text("receipt_type").orEmpty()),
                                    Cell(it.text("receipt_number").orEmpty(), mark = Mark.REGISTRY),
                                    Cell(it.text("issuer_name").orEmpty(), mark = Mark.HOLDER),
                                    Cell(ReportFormat.date(it.text("issue_date"))),
                                    Cell("${ReportFormat.decimal(it.attributes["amount"])} ${it.text("currency").orEmpty()}".trim())
                                )
                            }
                        )
                    },
                    evidence.takeIf { it.isNotEmpty() }?.let { rows -> Block.Photos("Entrega del apoyo y comprobante", rows.map { it.id }) }
                )
            }
        }

        suspend fun land(community: SourceRow): List<Block> {
            val concessions = source.related(Deliverables.PROJECT, project, "project_concession", "mining_concession").sortedBy { it.text("code") }
            val crossings = source.concessionTerritories(concessions.map { UUID.fromString(it.id) })
            val registry =
                listOf(
                    "**Reconocida como pueblo indígena u originario:** ${ReportFormat.yesNo(community.attributes["recognized_as_indigenous"])}" +
                        community.text("indigenous_people")?.let { " ($it)" }.orEmpty(),
                    "**Área inscrita:** ${ReportFormat.decimal(community.attributes["registered_area_ha"]).ifEmpty { "sin dato" }} ha; " +
                        "**área calculada:** ${ReportFormat.decimal(community.attributes["calculated_area_ha"]).ifEmpty { "sin dato" }} ha",
                    "**Partida de la propiedad:** ${community.text(
                        "property_record"
                    ) ?: "sin dato"}; **partida matriz:** ${community.text("parent_record") ?: "sin dato"}; " +
                        "**independizada:** ${ReportFormat.yesNo(community.attributes["independized"])}",
                    "**Personería jurídica:** ${community.text("legal_entity_record") ?: "sin dato"}",
                    "**Título:** ${community.text(
                        "title_reference"
                    ) ?: "sin dato"}; **emisor tal como figura:** ${community.text("title_issuer_as_written") ?: "sin dato"}" +
                        community.text("title_issuer_normalized")?.let { " (normalizado: $it)" }.orEmpty()
                ) + listOfNotNull("_Las partidas no están verificadas._".takeIf { community.attributes["registry_records_unverified"] == true })
            val sectors = source.all("community_sector", mapOf("community" to community.id)).sortedBy { it.text("name") }
            return listOfNotNull(
                concessions.takeIf { it.isNotEmpty() }?.let { rows ->
                    Block.Table(
                        "Concesiones del proyecto",
                        listOf("Código", "Nombre", "Titular", "Área inscrita (ha)"),
                        rows.map {
                            listOf(
                                Cell(it.text("code").orEmpty()),
                                Cell(it.text("name").orEmpty()),
                                Cell(organizationName(it.ref("holder_organization")).orEmpty()),
                                Cell(ReportFormat.decimal(it.attributes["registered_area_ha"]))
                            )
                        }
                    )
                },
                crossings.takeIf { it.isNotEmpty() }?.let { rows ->
                    Block.Table(
                        "Concesiones sobre el territorio comunal, los sectores y los caseríos",
                        listOf("Concesión", "Territorio", "Nombre", "Área del cruce (ha)"),
                        rows.map {
                            listOf(
                                Cell(((it["concession"] as? Map<*, *>)?.get("code")).toString()),
                                Cell(TERRITORY_LABELS[it["object"]] ?: it["object"].toString()),
                                Cell(it["name"]?.toString().orEmpty()),
                                Cell(ReportFormat.decimal(it["areaHa"]))
                            )
                        }
                    )
                },
                Block.Heading(2, "Datos registrales de ${community.text("name")}"),
                Block.Paragraph(registry.joinToString("\n\n")),
                sectors.takeIf { it.isNotEmpty() }?.let { rows ->
                    Block.Table(
                        "Sectores comunales",
                        listOf("Sector", "Delegados ante la asamblea"),
                        rows.map { listOf(Cell(it.text("name").orEmpty()), Cell(it.text("delegates_count") ?: "sin dato")) }
                    )
                },
                Block.MapFigure(LAND_MAP, "Mapa del predio")
            )
        }
    }

    companion object {
        const val NAME = "INFORME DE CAMPO Y AVANCE"
        const val COVER_MAP = "portada"
        const val LAND_MAP = "predio"
        private const val CALCULATED = "CALCULADA"
        private const val MIXED = "MIXTA"
        private const val NARRATIVE = "NARRATIVA"
        private const val CANCELLED = "cancelled"
        private val DELIVERED = setOf("delivered", "closed")
        private const val FACE_TO_FACE = "CONTACTO CARA A CARA"
        private val BACKGROUND_INCIDENTS = setOf("EXPULSION", "RETENCION DE VEHICULO O EQUIPOS")
        private val OPEN_POSITIONS = setOf("VIGENTE", "PREVISTO")
        private val PICTURES = setOf(PhotoRef.PHOTO, PhotoRef.RECEIPT)
        private val TERRITORY_LABELS = mapOf("community" to "Territorio comunal", "community_sector" to "Sector comunal", "locality" to "Localidad")
    }
}
