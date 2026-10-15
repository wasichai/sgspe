package sgspe.deliverable

import sgspe.deliverable.DiagnosisScene.Companion.SIN_DATO
import sgspe.privacy.Block
import sgspe.privacy.Cell
import java.util.UUID

// T1, portada, índice y capítulos 1 a 4 (deliverables.md §5): la introducción con el proyecto, sus campañas y sus partes; los
// objetivos, la metodología, el alcance, el diseño y el trabajo de campo del estudio (Tabla 3.1 con celdas combinadas por
// ámbito); los criterios y los miembros de las áreas aprobadas, y el mapa de áreas al final del capítulo 4 (§3.8)
object DiagnosisStudy {
    const val AREAS_MAP = "areas"
    private const val INTERVIEW = "ENTREVISTA"
    private const val OBSERVATION = "FICHA DE OBSERVACION"

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
        return listOf(Block.Paragraph(lines.joinToString("\n\n")))
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
                .filter { it.text("engagement_type") in setOf(INTERVIEW, OBSERVATION) }
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

    // los miembros de un área aprobada con su condición, sus criterios, su justificación y si se estudiaron
    suspend fun members(
        scene: DiagnosisScene,
        area: SourceRow?,
        key: String,
        title: String
    ): List<Block> {
        if (area == null) return listOf(Block.Paragraph("_El proyecto no tiene un área de influencia indirecta social aprobada._"))
        val rows =
            scene.members(area).map { member ->
                scene.keep(key, member.text("rationale"))
                listOf(
                    Cell(memberName(scene, member)),
                    Cell(DiagnosisScene.or(member, "member_condition")),
                    Cell(member.text("applied_criteria").orEmpty()),
                    Cell(member.text("rationale").orEmpty()),
                    Cell(ReportFormat.yesNo(member.attributes["studied_in_diagnosis"]))
                )
            }
        return listOfNotNull(
            area.text("criteria")?.let { Block.Paragraph("**Criterios:** $it") },
            rows.takeIf { it.isNotEmpty() }?.let {
                Block.Table(title, listOf("Miembro", "Condición", "Criterios aplicados", "Justificación", "Estudiado en el diagnóstico"), it)
            }
        )
    }

    suspend fun aids(scene: DiagnosisScene): List<Block> = members(scene, scene.aids, "aids", "Miembros del área de influencia directa social")

    suspend fun aiis(scene: DiagnosisScene): List<Block> =
        members(scene, scene.aiis, "aiis", "Miembros del área de influencia indirecta social") +
            Block.MapFigure(AREAS_MAP, "Áreas de influencia social del proyecto")

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
