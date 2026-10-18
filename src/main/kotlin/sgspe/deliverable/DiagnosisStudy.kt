package sgspe.deliverable

import sgspe.deliverable.DiagnosisScene.Companion.SIN_DATO
import sgspe.privacy.Block
import sgspe.privacy.Cell
import sgspe.privacy.Mark
import java.util.UUID

// T1, portada, índice y capítulos 1 a 4 (deliverables.md §5): la introducción con el proyecto, sus campañas y sus partes; los
// objetivos, la metodología, el alcance, el diseño y el trabajo de campo del estudio (Tabla 3.1 con celdas combinadas por
// ámbito); los criterios y los miembros de las áreas aprobadas, y el mapa de áreas al final del capítulo 4 (§3.8)
object DiagnosisStudy {
    const val AREAS_MAP = "areas"
    private const val INTERVIEW = "ENTREVISTA"
    private const val OBSERVATION = "FICHA DE OBSERVACION"

    // solo lo que ocurrió: las interacciones realizadas o con acta o cerradas (WF-C) y las visitas concluidas, total o
    // parcialmente (WF-L); lo planificado, programado, postergado, en curso o cancelado no cuenta
    private val HELD = setOf("held", "minutes_drafted", "minutes_in_review", "minutes_approved", "closed")
    private val DONE_VISIT = setOf("completed", "partial")

    // la ubicación de la introducción, del distrito a la región (el departamento)
    private val LEVELS = listOf("DISTRITO" to "distrito de", "PROVINCIA" to "provincia de", "DEPARTAMENTO" to "región")
    private const val STANCES = "**Apoyos y oposición:**"
    private const val AIIS_MEMBERS = "Miembros del área de influencia indirecta social"

    // las posturas del enum stance: A FAVOR es apoyo, las dos EN CONTRA son oposición (RN-STK-007) y lo demás es neutral
    private val SUPPORT = setOf("A FAVOR")
    private val OPPOSITION = setOf("EN CONTRA", "EN CONTRA PERO ABIERTO A ESCUCHAR")

    fun cover(scene: DiagnosisScene): List<Block> = listOf(Block.Paragraph("Diagnóstico social del proyecto ${scene.project.text("name")}"))

    fun index(): List<Block> =
        listOf(
            Block.Toc("Índice"),
            Block.CaptionList("Lista de tablas", CaptionNumbering.TABLE),
            Block.CaptionList("Lista de figuras", CaptionNumbering.FIGURE)
        )

    suspend fun introduction(scene: DiagnosisScene): List<Block> {
        val s = scene.source
        val campaigns = s.all("exploration_campaign", scene.byProject).sortedBy { it.text("planned_start_date").orEmpty() }
        val parties = s.all("project_party", scene.byProject)

        suspend fun named(role: String): List<String> =
            parties
                .filter {
                    it.text("party_role") == role
                }.mapNotNull { scene.people.organizationName(it.ref("organization")) }
        val clients = named("CLIENTE")
        val consultants = named("CONSULTORA")
        scene.keep("introduccion", scene.project.text("description"))
        val lines =
            listOfNotNull(
                scene.project.text("description")?.let { "**Proyecto.** $it" },
                "**Ubicación:** ${location(scene)}",
                "**Fase de exploración:** ${DiagnosisScene.or(scene.project, "exploration_phase")}",
                campaigns.takeIf { it.isNotEmpty() }?.joinToString("; ", prefix = "**Campañas previstas:** ") { c ->
                    "${DiagnosisScene.or(
                        c,
                        "name"
                    )} (${DiagnosisScene.or(
                        c,
                        "activity_type"
                    )}, del ${ReportFormat.date(c.text("planned_start_date"))} al ${ReportFormat.date(c.text("planned_end_date"))})"
                },
                clients.takeIf { it.isNotEmpty() }?.let { "**Empresa a cargo:** ${it.joinToString(", ")}" },
                consultants.takeIf { it.isNotEmpty() }?.let { "**Consultora:** ${it.joinToString(", ")}" },
                "La ubicación de las concesiones del proyecto está en el Anexo 1."
            )
        return listOf(Block.Paragraph(lines.joinToString("\n\n"))) + stances(scene)
    }

    // la ubicación (#80): los distritos de las localidades del AIDS y sus provincias y departamentos por parent_unit, cada nivel
    // con sus nombres distintos unidos con «y»; «Sin dato» si ninguna localidad tiene unidad
    private suspend fun location(scene: DiagnosisScene): String {
        val byLevel = linkedMapOf<String, MutableSet<String>>()
        val seen = mutableSetOf<UUID>()
        for (locality in scene.localities(scene.aids)) {
            var unit = locality.ref("administrative_unit")
            while (unit != null && seen.add(unit)) {
                val row = scene.source.one("administrative_unit", unit) ?: break
                row.text("level")?.let { level -> row.text("name")?.let { byLevel.getOrPut(level) { sortedSetOf() } += it } }
                unit = row.ref("parent_unit")
            }
        }
        val parts =
            LEVELS.mapNotNull { (level, label) -> byLevel[level]?.takeIf { it.isNotEmpty() }?.let { "$label ${joined(it.toList())}" } }
        return parts.joinToString(", ").ifEmpty { SIN_DATO }
    }

    // «A», «A y B», «A, B y C»
    private fun joined(names: List<String>): String = if (names.size < 2) names.joinToString() else names.dropLast(1).joinToString(", ") + " y " + names.last()

    // los apoyos y la oposición (#80, RN-STK-007): la última evaluación de cada grupo de interés hasta el corte, sin nombres. la
    // postura es restringida y EXTERNO la vacía en las Tablas 7.1 a 7.3 (DiagnosisStakeholders.classification): aquí la línea
    // de conteos lleva la misma marca, y EXTERNO dice que se reservan
    private suspend fun stances(scene: DiagnosisScene): List<Block> {
        val stances = scene.stakeholders().mapNotNull { scene.latestAssessment(it)?.text("stance") }
        val support = stances.count { it in SUPPORT }
        val opposition = stances.count { it in OPPOSITION }
        val neutral = stances.size - support - opposition
        val groups = if (support == 1) "grupo de interés" else "grupos de interés"
        val neutrals = if (neutral == 1) "neutral" else "neutrales"
        return listOf(
            Block.Paragraph(
                "$STANCES $support $groups a favor, $opposition en oposición y $neutral $neutrals, según la última evaluación al corte.",
                Mark.restricted("stakeholder_assessment.stance")
            ),
            Block.Paragraph("$STANCES se reservan en la versión externa.", Mark.EXTERNAL_ONLY)
        )
    }

    fun objectives(scene: DiagnosisScene): List<Block> {
        scene.keep("objetivos", scene.study?.text("objectives"))
        return listOf(Block.Paragraph(scene.study?.text("objectives") ?: "_El estudio no registra sus objetivos._"))
    }

    fun methodology(scene: DiagnosisScene): List<Block> {
        scene.keep("metodologia", scene.study?.text("methodology"))
        return listOf(Block.Paragraph(scene.study?.text("methodology") ?: "_El estudio no registra su metodología._"))
    }

    suspend fun scope(scene: DiagnosisScene): List<Block> {
        val aids = scene.localities(scene.aids).mapNotNull { it.text("name") }
        val aiis = scene.localities(scene.aiis).mapNotNull { it.text("name") }
        scene.keep("alcance", scene.study?.text("scope_description"))
        return listOfNotNull(
            scene.study?.text("scope_description")?.let { Block.Paragraph(it) },
            Block.Paragraph(
                "**Localidades del AIDS:** ${aids.joinToString(", ").ifEmpty { SIN_DATO }}\n\n" +
                    "**Localidades del AIIS:** ${aiis.joinToString(", ").ifEmpty { SIN_DATO }}"
            )
        )
    }

    suspend fun design(scene: DiagnosisScene): List<Block> {
        val study = scene.study ?: return listOf(Block.Paragraph("_El entregable no tiene estudio._"))
        val instruments = scene.source.all("study_instrument", mapOf("study" to study.id))
        if (instruments.isEmpty()) return listOf(Block.Paragraph("_El estudio no registra instrumentos._"))
        val rows =
            instruments.map { row ->
                listOf(
                    Cell(row.ref("topic")?.let { scene.source.one("topic", it)?.text("name") } ?: SIN_DATO),
                    Cell(DiagnosisScene.or(row, "instrument_type")),
                    Cell(DiagnosisScene.or(row, "application_scope")),
                    Cell(row.text("description").orEmpty())
                )
            }
        return listOf(Block.Table("Temas e instrumentos del estudio", listOf("Tema", "Instrumento", "Ámbito de aplicación", "Descripción"), rows))
    }

    // Tabla 3.1: entrevistas y fichas de observación del estudio por ámbito y localidad, con el ámbito combinado
    suspend fun fieldwork(scene: DiagnosisScene): List<Block> {
        val study = scene.study
        val engagements =
            study
                ?.let { scene.source.all("engagement", mapOf("study" to it.id)) }
                .orEmpty()
                .filter { it.state in HELD && it.text("engagement_type") in setOf(INTERVIEW, OBSERVATION) }
        val aids = scene.localities(scene.aids).map { it.id }.toSet()
        val aiis = scene.localities(scene.aiis).map { it.id }.toSet()
        val groups = linkedMapOf<String, MutableList<Pair<String, List<SourceRow>>>>()
        for ((locality, rows) in engagements.groupBy { it.ref("locality")?.toString() }) {
            val scope =
                when (locality) {
                    in aids -> DiagnosisScene.AIDS
                    in aiis -> DiagnosisScene.AIIS
                    else -> "Sin ámbito"
                }
            val name = locality?.let { scene.source.one("locality", UUID.fromString(it))?.text("name") } ?: "Sin localidad"
            groups.getOrPut(scope) { mutableListOf() } += name to rows
        }
        val order = listOf(DiagnosisScene.AIDS, DiagnosisScene.AIIS, "Sin ámbito")
        val tableRows =
            order.filter { it in groups }.flatMap { scope ->
                val items = groups.getValue(scope).sortedBy { it.first }
                items.mapIndexed { i, (name, rows) ->
                    val counts =
                        listOf(
                            Cell(name),
                            Cell(rows.count { it.text("engagement_type") == INTERVIEW }.toString()),
                            Cell(rows.count { it.text("engagement_type") == OBSERVATION }.toString())
                        )
                    if (i == 0) listOf(Cell(scope, rowSpan = items.size)) + counts else counts
                }
            }
        val start = scene.day(study?.text("fieldwork_start_date"))
        val end = scene.day(study?.text("fieldwork_end_date"))
        val visits =
            scene.source.all("field_visit", scene.byProject).count { visit ->
                if (visit.state !in DONE_VISIT) return@count false
                val day = scene.day(visit.text("actual_start_date") ?: visit.text("planned_start_date"))
                day != null && start != null && end != null && !day.isBefore(start) && !day.isAfter(end)
            }
        val dates =
            "**Trabajo de campo:** del ${ReportFormat.date(study?.text("fieldwork_start_date")).ifEmpty { SIN_DATO }} " +
                "al ${ReportFormat.date(study?.text("fieldwork_end_date")).ifEmpty { SIN_DATO }}\n\n**Visitas de campo en esas fechas:** $visits"
        return listOfNotNull(
            Block.Paragraph(dates),
            tableRows.takeIf { it.isNotEmpty() }?.let {
                Block.Table(
                    "Entrevistas y fichas de observación por ámbito y localidad",
                    listOf("Ámbito", "Localidad", "Entrevistas", "Fichas de observación"),
                    it
                )
            }
        )
    }

    fun areasIntro(scene: DiagnosisScene): List<Block> {
        scene.keep("areas", scene.aids.text("criteria"))
        return listOf(Block.Paragraph(scene.aids.text("criteria") ?: "_El área no registra su criterio general._"))
    }

    // los miembros de un área aprobada con su condición, sus criterios, su justificación y si se estudiaron; con `grouped`, una
    // tabla por título (en ese orden) y las vacías fuera
    suspend fun members(
        scene: DiagnosisScene,
        area: SourceRow?,
        key: String,
        title: String,
        grouped: (suspend (List<SourceRow>) -> List<Pair<String, List<SourceRow>>>)? = null
    ): List<Block> {
        if (area == null) return listOf(Block.Paragraph("_El proyecto no tiene un área de influencia indirecta social aprobada._"))
        val all = scene.members(area)
        val groups = grouped?.invoke(all) ?: listOf(title to all)
        val tables =
            groups.filter { it.second.isNotEmpty() }.map { (caption, list) ->
                val rows =
                    list.map { member ->
                        scene.keep(key, member.text("rationale"))
                        listOf(
                            Cell(memberName(scene, member)),
                            Cell(DiagnosisScene.or(member, "member_condition")),
                            Cell(member.text("applied_criteria").orEmpty()),
                            Cell(member.text("rationale").orEmpty()),
                            Cell(ReportFormat.yesNo(member.attributes["studied_in_diagnosis"]))
                        )
                    }
                Block.Table(caption, listOf("Miembro", "Condición", "Criterios aplicados", "Justificación", "Estudiado en el diagnóstico"), rows)
            }
        return listOfNotNull(area.text("criteria")?.let { Block.Paragraph("**Criterios:** $it") }) + tables
    }

    suspend fun aids(scene: DiagnosisScene): List<Block> = members(scene, scene.aids, "aids", "Miembros del área de influencia directa social")

    // 4.2 por microcuenca (#80): una tabla por microcuenca, por nombre, y «Otros miembros del AIIS» con las comunidades, las
    // unidades administrativas y las localidades sin microcuenca; si ninguna localidad tiene microcuenca, la tabla única
    suspend fun aiis(scene: DiagnosisScene): List<Block> =
        members(scene, scene.aiis, "aiis", AIIS_MEMBERS) { all ->
            val basins = scene.source.microbasins(all.mapNotNull { it.ref("locality") }.distinct())
            if (basins.isEmpty()) {
                listOf(AIIS_MEMBERS to all)
            } else {
                val basinOf = { member: SourceRow -> member.ref("locality")?.let { basins[it] } }
                basins.values
                    .distinct()
                    .sorted()
                    .map { name -> "Miembros del AIIS en la $name" to all.filter { basinOf(it) == name } } +
                    ("Otros miembros del AIIS" to all.filter { basinOf(it) == null })
            }
        } + Block.MapFigure(AREAS_MAP, "Áreas de influencia social del proyecto")

    // el mapa de áreas (§3.7): AIDS y AIIS aprobadas con localidades, sondajes, territorio comunal y límites
    fun areasMap(scene: DiagnosisScene): MapRequest? =
        scene.project.text("working_crs")?.let { crs ->
            MapRequest(
                AREAS_MAP,
                scene.projectId,
                crs,
                null,
                listOf("LYR-13", "LYR-02", "LYR-10", "LYR-05", "LYR-01"),
                MapFrameKind.AREAS,
                areas = listOfNotNull(scene.aids.id, scene.aiis?.id).map(UUID::fromString),
                author = scene.author
            )
        }

    // una localidad con su reconocimiento si no está reconocida, una comunidad o una unidad administrativa
    private suspend fun memberName(
        scene: DiagnosisScene,
        member: SourceRow
    ): String {
        member.ref("locality")?.let { id ->
            val locality = scene.source.one("locality", id) ?: return SIN_DATO
            val status = locality.text("recognition_status")?.takeIf { it != "RECONOCIDO" }
            return listOfNotNull(locality.text("name"), status?.let { "($it)" }).joinToString(" ")
        }
        member.ref("community")?.let { return scene.source.one("community", it)?.text("name") ?: SIN_DATO }
        member.ref("administrative_unit")?.let { return scene.source.one("administrative_unit", it)?.text("name") ?: SIN_DATO }
        return SIN_DATO
    }
}
