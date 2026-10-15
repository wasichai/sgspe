package sgspe.deliverable

import sgspe.deliverable.DiagnosisScene.Companion.SIN_DATO
import sgspe.privacy.Block
import sgspe.privacy.Cell
import sgspe.privacy.Mark
import java.util.UUID

// T1, capítulo 5 (deliverables.md §5, filas 5.1.1 a 5.2.6): por tema, con un título de nivel 3 por ámbito (decisión 12 del plan
// 8.2c). los perfiles son los del estudio del entregable; lo que falta sale «Sin dato». el proceso electoral y la religión
// llevan su marca Restricted: solo INTERNO (§3.8, RQ-SEC-010)
object DiagnosisContext {
    private const val WIDE = 4

    suspend fun history(scene: DiagnosisScene): List<Block> {
        val conflicts = linked(scene, "local_conflict", "local_conflict_locality")
        return scene.scopes().flatMap { (title, localities) ->
            listOf<Block>(Block.Heading(3, title)) +
                localities.map { locality ->
                    scene.keep("historia", locality.text("history"))
                    val found = conflicts[locality.id].orEmpty()
                    val text =
                        listOfNotNull(
                            "**${DiagnosisScene.or(locality, "name")}** (${DiagnosisScene.or(locality, "recognition_status")}).",
                            locality.text("history"),
                            found.takeIf { it.isNotEmpty() }?.joinToString("; ", prefix = "Conflictos: ", postfix = ".") { c ->
                                "${DiagnosisScene.or(c, "title")} (${DiagnosisScene.or(c, "conflict_type")}, ${DiagnosisScene.or(c, "conflict_status")}" +
                                    c.text("disputed_resource")?.let { ", por $it" }.orEmpty() + ")"
                            }
                        ).joinToString(" ")
                    Block.Paragraph(text)
                }
        }
    }

    suspend fun population(scene: DiagnosisScene): List<Block> =
        scene.scopes().flatMap { (title, localities) ->
            val profiles = localities.map { it to scene.profile(it) }
            val rows =
                profiles.map { (locality, profile) ->
                    scene.keep("poblacion", profile?.text("migration"))
                    listOf(
                        Cell(DiagnosisScene.or(locality, "name")),
                        Cell(value(profile, "families_count")),
                        Cell(value(profile, "population_count")),
                        Cell(value(profile, "members_per_family")),
                        Cell(ReportFormat.date(profile?.text("data_date")).ifEmpty { SIN_DATO }),
                        Cell(value(profile, "data_source")),
                        Cell(value(profile, "reliability"))
                    )
                }
            val migration = profiles.mapNotNull { (locality, profile) -> profile?.text("migration")?.let { "**${locality.text("name")}:** $it" } }
            listOfNotNull(
                Block.Heading(3, title),
                Block.Table(
                    "Familias y población: $title",
                    listOf("Localidad", "Familias", "Población", "Miembros por familia", "Fecha del dato", "Fuente", "Confiabilidad"),
                    rows
                ),
                migration.takeIf { it.isNotEmpty() }?.let { Block.Paragraph("**Migración.**\n\n" + it.joinToString("\n\n")) }
            )
        }

    suspend fun economy(scene: DiagnosisScene): List<Block> =
        columns(
            scene,
            "economia",
            "Actividad",
            "Actividades económicas",
            listOf(
                "Cultivos" to "crops",
                "Ganadería" to "livestock",
                "Otras actividades" to "economic_activities"
            )
        )

    suspend fun services(scene: DiagnosisScene): List<Block> =
        columns(
            scene,
            "servicios",
            "Servicio",
            "Servicios básicos",
            listOf(
                "Fuente de agua" to "water_source",
                "Tratamiento del agua" to "water_treatment",
                "Frecuencia del agua" to "water_frequency",
                "Pago por el agua" to "water_fee",
                "Antigüedad del sistema (años)" to "water_system_age_years",
                "Problemas del agua" to "water_problems",
                "Electricidad" to "electricity",
                "Saneamiento" to "sanitation"
            )
        )

    suspend fun educationHealth(scene: DiagnosisScene): List<Block> =
        scene.scopes().flatMap { (title, localities) ->
            val schools =
                localities.flatMap { locality ->
                    scene.source.all("school", mapOf("locality" to locality.id)).map { row ->
                        listOf(
                            Cell(DiagnosisScene.or(locality, "name")),
                            Cell(DiagnosisScene.or(row, "name")),
                            Cell(DiagnosisScene.or(row, "education_level")),
                            Cell(DiagnosisScene.or(row, "school_status"))
                        )
                    }
                }
            val facilities =
                localities.flatMap { locality ->
                    scene.source.all("health_facility", mapOf("locality" to locality.id)).map { row ->
                        listOf(
                            Cell(DiagnosisScene.or(locality, "name")),
                            Cell(DiagnosisScene.or(row, "name")),
                            Cell(DiagnosisScene.or(row, "facility_type")),
                            Cell(DiagnosisScene.or(row, "facility_status"))
                        )
                    }
                }
            listOfNotNull(
                Block.Heading(3, title),
                schools.takeIf { it.isNotEmpty() }?.let {
                    Block.Table(
                        "Instituciones educativas: $title",
                        listOf("Localidad", "Institución", "Nivel", "Estado"),
                        it
                    )
                },
                facilities.takeIf { it.isNotEmpty() }?.let {
                    Block.Table(
                        "Establecimientos de salud: $title",
                        listOf("Localidad", "Establecimiento", "Tipo", "Estado"),
                        it
                    )
                }
            )
        }

    // Tabla 5.5: las vías con la localidad de origen combinada; la telefonía por localidad aparte
    suspend fun roads(scene: DiagnosisScene): List<Block> =
        scene.scopes().flatMap { (title, localities) ->
            val rows =
                localities.flatMap { locality ->
                    val roads = scene.source.all("road_access", mapOf("locality" to locality.id))
                    roads.mapIndexed { i, road ->
                        val cells =
                            listOf(
                                Cell(DiagnosisScene.or(road, "road_type")),
                                Cell(road.ref("connects_to")?.let { scene.source.one("locality", it)?.text("name") } ?: SIN_DATO),
                                Cell(DiagnosisScene.or(road, "road_condition")),
                                Cell(DiagnosisScene.or(road, "transport_means")),
                                Cell(DiagnosisScene.or(road, "travel_time"))
                            )
                        if (i == 0) listOf(Cell(DiagnosisScene.or(locality, "name"), rowSpan = roads.size)) + cells else cells
                    }
                }
            val phones = localities.map { listOf(Cell(DiagnosisScene.or(it, "name")), Cell(value(scene.profile(it), "phone_coverage"))) }
            listOfNotNull(
                Block.Heading(3, title),
                rows
                    .takeIf {
                        it.isNotEmpty()
                    }?.let { Block.Table("Vías de acceso: $title", listOf("Localidad", "Tipo de vía", "Conecta con", "Estado", "Transporte", "Tiempo"), it) },
                Block.Table("Telefonía: $title", listOf("Localidad", "Cobertura"), phones)
            )
        }

    // Tabla 5.10: organizaciones por localidad y programas del Estado presentes, con X
    suspend fun organizations(scene: DiagnosisScene): List<Block> {
        val programs = linked(scene, "state_program", "state_program_locality")
        return scene.scopes().flatMap { (title, localities) ->
            val ids = localities.map { it.id }
            val organizations =
                localities
                    .flatMap { locality -> scene.source.all("organization", mapOf("locality" to locality.id)) }
                    .distinctBy { it.id }
                    .sortedBy { it.text("name") }
            val organizationRows =
                organizations.map { org ->
                    listOf(Cell(DiagnosisScene.or(org, "name")), Cell(DiagnosisScene.or(org, "organization_type"))) +
                        ids.map { Cell(if (org.text("locality") == it) "X" else "") }
                }
            val present =
                programs
                    .filterKeys { it in ids }
                    .values
                    .flatten()
                    .distinctBy { it.id }
                    .sortedBy { it.text("name") }
            val programRows =
                present.map { program ->
                    listOf(Cell(DiagnosisScene.or(program, "name")), Cell("Programa del Estado")) +
                        ids.map { id -> Cell(if (programs[id].orEmpty().any { it.id == program.id }) "X" else "") }
                }
            val rows = organizationRows + programRows
            listOfNotNull(
                Block.Heading(3, title),
                rows.takeIf { it.isNotEmpty() }?.let {
                    Block.Table(
                        "Organizaciones e instituciones locales: $title",
                        listOf("Organización o programa", "Tipo") + localities.map { l -> DiagnosisScene.or(l, "name") },
                        it,
                        wide = localities.size > WIDE
                    )
                }
            )
        }
    }

    suspend fun electoral(scene: DiagnosisScene): List<Block> =
        scene.scopes().flatMap { (title, localities) ->
            listOf<Block>(Block.Heading(3, title)) +
                localities.mapNotNull { locality ->
                    scene.profile(locality)?.text("electoral_process")?.let {
                        scene.keep("proceso_electoral", it)
                        Block.Paragraph("**${locality.text("name")}:** $it", Mark.restricted("locality_profile.electoral_process"))
                    }
                }
        }

    suspend fun customs(scene: DiagnosisScene): List<Block> =
        scene.scopes().flatMap { (title, localities) ->
            val religion =
                localities.mapNotNull { locality ->
                    scene.profile(locality)?.text("religion")?.let {
                        scene.keep("costumbres", it)
                        Block.Paragraph("**Religión en ${locality.text("name")}:** $it", Mark.restricted("locality_profile.religion"))
                    }
                }
            val festivities =
                localities.flatMap { locality ->
                    scene.source.all("festivity", mapOf("locality" to locality.id)).map { row ->
                        listOf(
                            Cell(DiagnosisScene.or(locality, "name")),
                            Cell(DiagnosisScene.or(row, "name")),
                            Cell(DiagnosisScene.or(row, "date_text")),
                            Cell(row.text("co_organization_turns").orEmpty())
                        )
                    }
                }
            listOf<Block>(Block.Heading(3, title)) + religion +
                listOfNotNull(
                    festivities.takeIf { it.isNotEmpty() }?.let {
                        Block.Table("Costumbres y fechas festivas: $title", listOf("Localidad", "Festividad", "Fecha", "Turnos de coorganización"), it)
                    }
                )
        }

    // una tabla por ámbito con los temas en filas y las localidades en columnas (Tablas 5.2, 5.3 y 5.8)
    private suspend fun columns(
        scene: DiagnosisScene,
        key: String,
        first: String,
        title: String,
        rows: List<Pair<String, String>>
    ): List<Block> =
        scene.scopes().flatMap { (scope, localities) ->
            val profiles = localities.map { scene.profile(it) }
            profiles.forEach { profile -> rows.forEach { (_, field) -> scene.keep(key, profile?.text(field)) } }
            listOf(
                Block.Heading(3, scope),
                Block.Table(
                    "$title: $scope",
                    listOf(first) + localities.map { DiagnosisScene.or(it, "name") },
                    rows.map { (label, field) -> listOf(Cell(label)) + profiles.map { Cell(value(it, field)) } },
                    wide = localities.size > WIDE
                )
            )
        }

    // los registros de `objectName` (del tenant) por localidad, desde su lado de la relación muchos a muchos
    private suspend fun linked(
        scene: DiagnosisScene,
        objectName: String,
        relationship: String
    ): Map<String, List<SourceRow>> {
        val out = linkedMapOf<String, MutableList<SourceRow>>()
        for (row in scene.source.all(objectName)) {
            for (locality in scene.source.related(objectName, UUID.fromString(row.id), relationship, "locality")) {
                out.getOrPut(locality.id) { mutableListOf() } += row
            }
        }
        return out
    }

    private fun value(
        row: SourceRow?,
        field: String
    ): String =
        row
            ?.attributes
            ?.get(field)
            ?.toString()
            ?.trim()
            ?.ifEmpty { null } ?: SIN_DATO
}
