package sgspe.deliverable

import sgspe.deliverable.DiagnosisScene.Companion.SIN_DATO
import sgspe.privacy.Block
import sgspe.privacy.Cell
import sgspe.privacy.Fact
import sgspe.privacy.FactRow
import sgspe.privacy.Mark
import java.util.UUID

// T1, capítulo 5 (deliverables.md §5, filas 5.1.1 a 5.2.6): por tema, con un título de nivel 3 por ámbito (decisión 12 del plan
// 8.2c). los perfiles son los del estudio del entregable; lo que falta sale «Sin dato». el proceso electoral y la religión
// llevan su marca Restricted: solo INTERNO (§3.8, RQ-SEC-010)
object DiagnosisContext {
    private const val WIDE = 4
    private const val LOCALITY = "locality"

    // la página horizontal: más de WIDE columnas de localidades, tipos o programas además de la de los rótulos. la tabla
    // transpuesta (una fila por localidad) pasa una columna antes: sus columnas son temas de texto libre, no marcas ni conteos
    private fun wide(columns: Int): Boolean = columns > WIDE + 1

    private fun wideTransposed(columns: Int): Boolean = columns > WIDE

    // con más localidades que esto, las tablas del AIIS pasan a una fila por localidad (o una matriz resumida)
    private const val TRANSPOSE = 5
    const val RESERVED = "Información reservada en esta versión."

    suspend fun history(scene: DiagnosisScene): List<Block> {
        val conflicts = linked(scene, "local_conflict", "local_conflict_locality")
        return scene.scopes().flatMap { (title, localities) ->
            listOf<Block>(Block.Heading(3, title)) +
                localities.flatMap { locality ->
                    scene.keep("historia", locality.text("history"))
                    val found = conflicts[locality.id].orEmpty()
                    val text =
                        listOfNotNull(
                            "Reconocimiento: ${DiagnosisScene.option(locality, "recognition_status")}.",
                            locality.text("history"),
                            found.takeIf { it.isNotEmpty() }?.joinToString("; ", prefix = "Conflictos: ", postfix = ".") { c ->
                                "${DiagnosisScene.or(
                                    c,
                                    "title"
                                )} (${DiagnosisScene.option(c, "conflict_type")}, ${DiagnosisScene.option(c, "conflict_status")}" +
                                    c.text("disputed_resource")?.let { ", por $it" }.orEmpty() + ")"
                            }
                        ).joinToString(" ")
                    // un subtítulo de nivel 4 por localidad, que entra en el índice
                    listOf(Block.Heading(4, DiagnosisScene.or(locality, "name")), Block.Paragraph(text))
                }
        }
    }

    // Tabla 5.2 por ámbito; los hechos `familias` y `poblacion` llevan las mismas celdas por ámbito y localidad (lo que falta,
    // sin valor: nunca cero)
    suspend fun population(scene: DiagnosisScene): List<Block> {
        val families = mutableListOf<FactRow>()
        val inhabitants = mutableListOf<FactRow>()
        val blocks =
            scene.scopes().flatMap { (title, localities) ->
                val profiles = localities.map { it to scene.profile(it) }
                val rows =
                    profiles.map { (locality, profile) ->
                        scene.keep("poblacion", profile?.text("migration"))
                        val name = DiagnosisScene.or(locality, "name")
                        val familiesCount = value(profile, "families_count")
                        val populationCount = value(profile, "population_count")
                        families += FactRow(listOf(title, name), value = ReportFacts.value(familiesCount), unit = "familias")
                        inhabitants += FactRow(listOf(title, name), value = ReportFacts.value(populationCount), unit = "habitantes")
                        listOf(
                            Cell(name),
                            Cell(familiesCount),
                            Cell(populationCount),
                            Cell(value(profile, "members_per_family")),
                            Cell(ReportFormat.date(profile?.text("data_date")).ifEmpty { SIN_DATO }),
                            Cell(value(profile, "data_source")),
                            Cell(DiagnosisScene.option(profile, "reliability"))
                        )
                    }
                val migration =
                    profiles.mapNotNull { (locality, profile) ->
                        profile?.text("migration")?.let { "**${DiagnosisScene.or(locality, "name")}:** $it" }
                    }
                listOfNotNull(
                    Block.Heading(3, title),
                    Block.Table(
                        "Familias y población: $title",
                        listOf("Localidad", "Familias", "Población", "Miembros por familia", "Fecha del dato", "Fuente", "Confiabilidad"),
                        rows,
                        key = "poblacion_${DiagnosisScene.scopeKey(title)}"
                    ),
                    migration.takeIf { it.isNotEmpty() }?.let { Block.Paragraph("**Migración.**\n\n" + it.joinToString("\n\n")) }
                )
            }
        if (families.isNotEmpty()) {
            val dimensions = listOf("Ámbito", "Localidad")
            scene.fact(Fact("familias", Fact.AMOUNT, "Familias por localidad", dimensions, "Familias", families, unit = "familias", forPublic = true))
            scene.fact(Fact("poblacion", Fact.AMOUNT, "Población por localidad", dimensions, "Población", inhabitants, unit = "habitantes", forPublic = true))
        }
        return blocks
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
                "Otras actividades" to "economic_activities",
                // el modelo no tiene una problemática general de la localidad: la más cercana es la del agua
                "Problemática (problemas del agua)" to "water_problems"
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
                        it,
                        key = "instituciones_educativas_${DiagnosisScene.scopeKey(title)}"
                    )
                },
                facilities.takeIf { it.isNotEmpty() }?.let {
                    Block.Table(
                        "Establecimientos de salud: $title",
                        listOf("Localidad", "Establecimiento", "Tipo", "Estado"),
                        it,
                        key = "establecimientos_de_salud_${DiagnosisScene.scopeKey(title)}"
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
                    }?.let {
                        Block.Table(
                            "Vías de acceso: $title",
                            listOf("Localidad", "Tipo de vía", "Conecta con", "Estado", "Transporte", "Tiempo"),
                            it,
                            key = "vias_${DiagnosisScene.scopeKey(title)}"
                        )
                    },
                Block.Table("Telefonía: $title", listOf("Localidad", "Cobertura"), phones, key = "telefonia_${DiagnosisScene.scopeKey(title)}")
            )
        }

    // Tabla 5.10: con cinco localidades o menos, organizaciones y programas en filas y las localidades en columnas, con X;
    // con más, una matriz por localidad (filas) con el número de organizaciones por tipo y otra con X por programa del Estado
    suspend fun organizations(scene: DiagnosisScene): List<Block> {
        val programs = linked(scene, "state_program", "state_program_locality")
        return scene.scopes().flatMap { (title, localities) ->
            val ids = localities.map { it.id }
            val organizations =
                localities
                    .flatMap { locality -> scene.source.all("organization", mapOf("locality" to locality.id)) }
                    .distinctBy { it.id }
                    .sortedBy { it.text("name") }
            val present =
                programs
                    .filterKeys { it in ids }
                    .values
                    .flatten()
                    .distinctBy { it.id }
                    .sortedBy { it.text("name") }
            val tables =
                if (localities.size > TRANSPOSE) {
                    organizationMatrices(title, localities, organizations, present, programs)
                } else {
                    val organizationRows =
                        organizations.map { org ->
                            listOf(Cell(DiagnosisScene.or(org, "name")), Cell(DiagnosisScene.option(org, "organization_type"))) +
                                ids.map { Cell(if (org.text("locality") == it) "X" else "") }
                        }
                    val programRows =
                        present.map { program ->
                            listOf(Cell(DiagnosisScene.or(program, "name")), Cell("Programa del Estado")) +
                                ids.map { id -> Cell(if (programs[id].orEmpty().any { it.id == program.id }) "X" else "") }
                        }
                    val rows = organizationRows + programRows
                    listOfNotNull(
                        rows.takeIf { it.isNotEmpty() }?.let {
                            Block.Table(
                                "Organizaciones e instituciones locales: $title",
                                listOf("Organización o programa", "Tipo") + localities.map { l -> DiagnosisScene.or(l, "name") },
                                it,
                                wide = wide(localities.size + 1),
                                key = "organizaciones_${DiagnosisScene.scopeKey(title)}"
                            )
                        }
                    )
                }
            // un ámbito sin organizaciones ni programas no queda en un título solo (#83)
            listOf<Block>(Block.Heading(3, title)) + tables.ifEmpty { listOf(Block.Paragraph("$SIN_DATO.")) }
        }
    }

    // localidad × tipo de organización (el número de organizaciones) y localidad × programa del Estado (X); agrupa por el valor crudo
    private fun organizationMatrices(
        title: String,
        localities: List<SourceRow>,
        organizations: List<SourceRow>,
        present: List<SourceRow>,
        programs: Map<String, List<SourceRow>>
    ): List<Block> {
        val types = organizations.map { it.text("organization_type") }.distinct().sortedBy { ReportLabels.option(it) ?: SIN_DATO }
        val byType =
            Block
                .Table(
                    "Organizaciones por tipo y localidad: $title",
                    listOf("Localidad") + types.map { ReportLabels.option(it) ?: SIN_DATO },
                    localities.map { locality ->
                        val here = organizations.filter { it.text("locality") == locality.id }
                        listOf(Cell(DiagnosisScene.or(locality, "name"))) +
                            types.map { type ->
                                Cell(
                                    here
                                        .count { it.text("organization_type") == type }
                                        .takeIf { it > 0 }
                                        ?.toString()
                                        .orEmpty()
                                )
                            }
                    },
                    wide = wide(types.size + 1),
                    key = "organizaciones_por_tipo_${DiagnosisScene.scopeKey(title)}"
                ).takeIf { organizations.isNotEmpty() }
        val byProgram =
            Block
                .Table(
                    "Programas del Estado por localidad: $title",
                    listOf("Localidad") + present.map { DiagnosisScene.or(it, "name") },
                    localities.map { locality ->
                        listOf(Cell(DiagnosisScene.or(locality, "name"))) +
                            present.map { program -> Cell(if (programs[locality.id].orEmpty().any { it.id == program.id }) "X" else "") }
                    },
                    wide = wide(present.size + 1),
                    key = "programas_del_estado_${DiagnosisScene.scopeKey(title)}"
                ).takeIf { present.isNotEmpty() }
        return listOfNotNull(byType, byProgram)
    }

    suspend fun electoral(scene: DiagnosisScene): List<Block> =
        scene.scopes().flatMap { (title, localities) ->
            val restricted =
                localities.mapNotNull { locality ->
                    scene.profile(locality)?.text("electoral_process")?.let {
                        scene.keep("proceso_electoral", it)
                        Block.Paragraph("**${DiagnosisScene.or(locality, "name")}:** $it", Mark.restricted("locality_profile.electoral_process"))
                    }
                }
            // una línea sin marca para que la sección no quede en un título solo (EXTERNO quita los párrafos marcados)
            listOf<Block>(Block.Heading(3, title)) + restricted + reservedNote(restricted.isEmpty() && !scene.hasNarrative("proceso_electoral"))
        }

    suspend fun customs(scene: DiagnosisScene): List<Block> =
        scene.scopes().flatMap { (title, localities) ->
            val religion =
                localities.mapNotNull { locality ->
                    scene.profile(locality)?.text("religion")?.let {
                        scene.keep("costumbres", it)
                        Block.Paragraph("**Religión en ${DiagnosisScene.or(locality, "name")}:** $it", Mark.restricted("locality_profile.religion"))
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
            val table =
                festivities.takeIf { it.isNotEmpty() }?.let {
                    Block.Table(
                        "Costumbres y fechas festivas: $title",
                        listOf("Localidad", "Festividad", "Fecha", "Turnos de coorganización"),
                        it,
                        key = "festividades_${DiagnosisScene.scopeKey(title)}"
                    )
                }
            // sin tabla y sin marca visible, una línea fija evita el título solo en EXTERNO
            val note = if (table == null) listOf(reservedNote(religion.isEmpty() && !scene.hasNarrative("costumbres"))) else emptyList()
            listOf<Block>(Block.Heading(3, title)) + religion + listOfNotNull(table) + note
        }

    // sin datos ni narrativa, «Sin dato.»; con datos restringidos o la narrativa (solo INTERNO), el aviso que solo muestra EXTERNO
    private fun reservedNote(noData: Boolean): Block.Paragraph = if (noData) Block.Paragraph("$SIN_DATO.") else Block.Paragraph(RESERVED, Mark.EXTERNAL_ONLY)

    // una tabla por ámbito con los temas en filas y las localidades en columnas; con más de cinco localidades, una fila por
    // localidad y los temas en columnas (Tablas 5.2, 5.3 y 5.8)
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
            val table =
                if (localities.size > TRANSPOSE) {
                    // una fila por localidad, los temas en columnas
                    Block.Table(
                        "$title: $scope",
                        listOf("Localidad") + rows.map { it.first },
                        localities.mapIndexed { i, locality ->
                            listOf(Cell(DiagnosisScene.or(locality, "name"))) + rows.map { (_, field) -> Cell(value(profiles[i], field)) }
                        },
                        wide = wideTransposed(rows.size + 1),
                        key = "${key}_${DiagnosisScene.scopeKey(scope)}"
                    )
                } else {
                    Block.Table(
                        "$title: $scope",
                        listOf(first) + localities.map { DiagnosisScene.or(it, "name") },
                        rows.map { (label, field) -> listOf(Cell(label)) + profiles.map { Cell(value(it, field)) } },
                        wide = wide(localities.size + 1),
                        key = "${key}_${DiagnosisScene.scopeKey(scope)}"
                    )
                }
            listOf(Block.Heading(3, scope), table)
        }

    // los registros de `objectName` por localidad de los ámbitos, desde el lado de la localidad de la relación muchos a muchos:
    // una lectura por localidad, no todos los del tenant con una lectura por registro (#83). cada lista va por id, como antes
    private suspend fun linked(
        scene: DiagnosisScene,
        objectName: String,
        relationship: String
    ): Map<String, List<SourceRow>> =
        scene
            .scopes()
            .flatMap { it.second }
            .map { it.id }
            .distinct()
            .associateWith { locality -> scene.source.related(LOCALITY, UUID.fromString(locality), relationship, objectName).sortedBy { it.id } }

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
