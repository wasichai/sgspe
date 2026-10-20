package sgspe.deliverable

import sgspe.deliverable.DiagnosisScene.Companion.SIN_DATO
import sgspe.privacy.Block
import sgspe.privacy.Cell
import sgspe.privacy.Mark
import sgspe.privacy.RegisterSheets
import java.text.Collator
import java.util.Locale

// T1, capítulos 6 a 8 (deliverables.md §5): los grupos de interés por ámbito y tipo (Tablas 6.1 a 6.3, el actor por cargo y
// organización), sus perfiles por cargo (las razones de postura, Restricted), las percepciones por tema con sus citas por
// cargo, la clasificación por interés y poder (Tablas 7.1 a 7.3: el nombre como mención y la postura marcada, decisión 14 del
// plan 8.2c), los grupos de influencia por código GI- y los temas clave (Tabla 8.1)
object DiagnosisStakeholders {
    // los ámbitos de las tablas: AIDS, AIIS y el regional y nacional juntos
    private val NO_SCOPE: List<String> = emptyList()
    private val SCOPES =
        listOf(
            listOf("AIDS") to "área de influencia directa social",
            listOf("AIIS") to "área de influencia indirecta social",
            listOf("REGIONAL", "NACIONAL") to "ámbito regional y nacional",
            NO_SCOPE to "grupos de interés sin ámbito"
        )

    // las etiquetas del catálogo stakeholder_scope para los ámbitos de las percepciones
    private val SCOPE_LABELS =
        mapOf(
            "AIDS" to DiagnosisScene.AIDS_TITLE,
            "AIIS" to DiagnosisScene.AIIS_TITLE,
            "REGIONAL" to "Ámbito regional",
            "NACIONAL" to "Ámbito nacional"
        )
    private const val NO_SCOPE_LABEL = "Sin ámbito"
    private val GROUPS = listOf("ESTRATEGICO", "POLITICO", "PARA MONITOREO", "VULNERABLE")
    private val SCOPE_ORDER = listOf("AIDS", "AIIS", "REGIONAL", "NACIONAL")

    // la sección de percepciones de cada objeto y el valor de `perception.subject` que lista
    private val PERCEPTION_SECTIONS =
        mapOf("percepciones_mineria" to "MINERIA", "percepciones_proyecto" to "PROYECTO", "percepciones_empresa" to "EMPRESA")
    private const val INTERVIEWED = "ENTREVISTADO"

    // el ámbito vacío (NO_SCOPE) toma a los que no tienen ámbito o lo tienen fuera del catálogo: ninguno desaparece
    private val KNOWN = SCOPES.flatMap { it.first }.toSet()

    private fun inScope(
        row: SourceRow,
        scopes: List<String>
    ): Boolean = if (scopes.isEmpty()) row.text("stakeholder_scope") !in KNOWN else row.text("stakeholder_scope") in scopes

    // el capítulo abre con su texto; las tablas de cada ámbito (Tablas 6.1 a 6.3) van con sus perfiles, en `profiles`
    fun identification(): List<Block> = listOf(Block.Paragraph(DiagnosisTexts.text("capitulo-6")))

    // un grupo de interés con lo que ordena sus perfiles: la localidad de su organización o, sin ella, la suya (los sin
    // localidad, al final) y el cargo. guarda el cargo vigente para el perfil: una lectura menos por grupo
    private class Ordered(
        val row: SourceRow,
        val position: SourceRow?,
        val locality: String?,
        val title: String
    )

    // como se ordena en español (es-PE): «Ánimas» antes que «Zapata»
    private val SPANISH: Collator = Collator.getInstance(Locale.forLanguageTag("es-PE"))

    private suspend fun ordered(
        scene: DiagnosisScene,
        rows: List<SourceRow>
    ): List<Ordered> =
        rows
            .map { row ->
                val position = row.ref("person")?.let { scene.people.position(it) }
                val organization = (position?.ref("organization") ?: row.ref("organization"))?.let { scene.source.one("organization", it) }
                val locality = (organization?.ref("locality") ?: row.ref("locality"))?.let { scene.source.one("locality", it)?.text("name") }
                Ordered(row, position, locality, position?.text("position_title").orEmpty())
            }.sortedWith(
                compareBy<Ordered> { it.locality == null }
                    .thenBy(SPANISH) { it.locality.orEmpty() }
                    .thenBy(SPANISH) { it.title }
                    .thenBy { it.row.id }
            )

    // ámbito → tipo (por el valor crudo, en el orden del enum stakeholder_type de model.json, que sigue a DOC-01; los que no
    // están en el enum y SIN_DATO, al final por nombre) → localidad → cargo
    private suspend fun byType(
        scene: DiagnosisScene,
        rows: List<SourceRow>
    ): Map<String, List<Ordered>> {
        val grouped = ordered(scene, rows).groupBy { it.row.text("stakeholder_type") ?: SIN_DATO }
        return grouped.keys.sortedWith(typeOrder()).associateWith { grouped.getValue(it) }
    }

    // el orden de los tipos de actor: el índice en el enum del modelo y, para los desconocidos, al final por nombre
    fun typeOrder(order: List<String> = ReportLabels.enumValues("stakeholder_type")): Comparator<String> =
        compareBy<String> { order.indexOf(it).let { i -> if (i < 0) Int.MAX_VALUE else i } }.thenBy(SPANISH) { it }

    private suspend fun scopeTable(
        scene: DiagnosisScene,
        scopes: List<String>,
        label: String,
        byType: Map<String, List<Ordered>>
    ): Block.Table {
        val cells =
            byType.flatMap { (type, list) ->
                list.mapIndexed { i, entry ->
                    val actor = Cell(scene.people.actor(entry.row))
                    if (i == 0) listOf(Cell(ReportLabels.option(type) ?: type, rowSpan = list.size), actor) else listOf(actor)
                }
            }
        return Block.Table(
            if (scopes.isEmpty()) "Grupos de interés sin ámbito" else "Grupos de interés del $label",
            listOf("Tipo de actor", "Actor"),
            cells,
            key = "grupos_de_interes_${scopeKey(scopes)}"
        )
    }

    // la clave de un ámbito de SCOPES en las claves de sus tablas (ADR-0031): aids, aiis, regional_nacional o sin_ambito
    private fun scopeKey(scopes: List<String>): String = if (scopes.isEmpty()) "sin_ambito" else scopes.joinToString("_").lowercase()

    // 6.1 AIDS, 6.2 AIIS y 6.3 regional y nacional (y los sin ámbito al final): cada ámbito abre con su tabla y sigue con un
    // subtítulo por tipo de actor; los perfiles de un tipo van por localidad y cargo. La sección `perfiles` no imprime su título
    // (`ReportSection.showTitle`): el ámbito es de nivel 2, el tipo de nivel 3 y el perfil, titulado por su cargo, de nivel 4
    suspend fun profiles(scene: DiagnosisScene): List<Block> {
        val stakeholders = scene.stakeholders()
        // el número es la posición fija del ámbito (6.1 AIDS, 6.2 AIIS, 6.3 regional y nacional): no se corre si falta uno
        return SCOPES.withIndex().flatMap { (index, scope) ->
            val (scopes, label) = scope
            val rows = stakeholders.filter { inScope(it, scopes) }
            if (rows.isEmpty()) {
                emptyList()
            } else {
                val types = byType(scene, rows)
                val title = if (scopes.isEmpty()) "Grupos de interés sin ámbito" else label.replaceFirstChar { it.uppercase() }
                listOf<Block>(Block.Heading(2, "6.${index + 1} $title"), scopeTable(scene, scopes, label, types)) +
                    types.flatMap { (type, list) ->
                        listOf<Block>(Block.Heading(3, ReportLabels.option(type) ?: type)) + list.flatMap { profile(scene, it) }
                    }
            }
        }
    }

    private suspend fun profile(
        scene: DiagnosisScene,
        entry: Ordered
    ): List<Block> {
        val row = entry.row
        val position = entry.position
        val assessment = scene.latestAssessment(row)
        scene.keep(
            "perfiles",
            position?.text("career_notes"),
            position?.text("election_mechanism"),
            row.text("profile_summary"),
            assessment?.text("stance_reasons")
        )
        val facts =
            listOfNotNull(
                position?.text("career_notes")?.let { "**Trayectoria:** $it" },
                position?.text("election_mechanism")?.let { "**Elección:** $it" },
                position?.let {
                    "**Periodo:** del ${ReportFormat.date(
                        it.text("term_start_date")
                    ).ifEmpty { SIN_DATO }} al ${ReportFormat.date(it.text("term_end_date")).ifEmpty { SIN_DATO }}"
                },
                row.text("profile_summary")?.let { "**Funciones y coordinación:** $it" }
            )
        val notes =
            listOfNotNull(
                row.text("interview_status")?.takeIf { it != INTERVIEWED }?.let { "Estado de la entrevista: ${ReportLabels.option(it)}." },
                "El cargo está vacante.".takeIf { row.ref("person") != null && position == null }
            )
        return listOfNotNull(
            Block.Heading(4, scene.people.actor(row)),
            Block.Paragraph(facts.joinToString("\n\n").ifEmpty { "_Sin datos del perfil._" }, footnotes = notes),
            assessment?.let { a ->
                val reasons = a.text("stance_reasons")?.let { " **Razones:** $it" }.orEmpty()
                Block.Paragraph("**Postura:** ${DiagnosisScene.option(a, "stance")}.$reasons", Mark.restricted("stakeholder_assessment.stance"))
            }
        )
    }

    fun characterization(): List<Block> = listOf(Block.Paragraph(DiagnosisTexts.text("capitulo-7")))

    // 7.1.1 a 7.1.3: la lista de un objeto (minería, proyecto, empresa). La narrativa de cada objeto es su propia sección
    // (`percepciones_*`, a continuación de la lista) y la de `percepciones` queda como apertura general de 7.1
    suspend fun perceptions(
        scene: DiagnosisScene,
        key: String
    ): List<Block> {
        val subject = PERCEPTION_SECTIONS.getValue(key)
        val rows = scene.study?.let { scene.source.all("perception", mapOf("study" to it.id)) }.orEmpty()
        val found = rows.filter { it.text("subject") == subject }
        found.forEach { scene.keep(key, it.text("statement"), it.text("quote"), it.text("attributed_role")) }
        if (found.isEmpty()) return listOf(Block.Paragraph("_Sin percepciones registradas._"))
        // orden fijo de ámbitos (AIDS, AIIS, regional, nacional), luego los demás por nombre y al final los sin ámbito
        val byScope = found.groupBy { it.text("perception_scope") }
        val order: List<String?> =
            SCOPE_ORDER.filter { it in byScope } + (byScope.keys.filterNotNull() - SCOPE_ORDER.toSet()).sorted() +
                (if (null in byScope) listOf(null) else emptyList())
        val text =
            order.joinToString("\n\n") { scope ->
                "**${scope?.let { SCOPE_LABELS[it] ?: it } ?: NO_SCOPE_LABEL}**\n\n" +
                    byScope.getValue(scope).joinToString("\n") { p ->
                        val quote = p.text("quote")?.let { q -> " «$q»" + p.text("attributed_role")?.let { r -> " — $r" }.orEmpty() }.orEmpty()
                        "- (${p.text("polarity")?.lowercase() ?: "sin polaridad"}) ${DiagnosisScene.or(p, "statement")}$quote"
                    }
            }
        return listOf(Block.Paragraph(text))
    }

    suspend fun classification(scene: DiagnosisScene): List<Block> {
        val thresholds =
            "**Umbral de interés:** ${DiagnosisScene.or(scene.project, "interest_threshold")}; " +
                "**umbral de poder:** ${DiagnosisScene.or(scene.project, "power_threshold")}."
        val tables =
            SCOPES.mapNotNull { (scopes, label) ->
                val entries = scene.stakeholders().filter { inScope(it, scopes) }.map { it to scene.latestAssessment(it) }
                if (entries.isEmpty()) {
                    null
                } else {
                    val byGroup = entries.groupBy { (row, assessment) -> assessment?.text("influence_group") ?: row.text("influence_group") ?: SIN_DATO }
                    val order = GROUPS.filter { it in byGroup } + (byGroup.keys - GROUPS.toSet()).sorted()
                    val cells =
                        order.flatMap { group ->
                            val list = byGroup.getValue(group)
                            list.mapIndexed { i, (row, assessment) ->
                                val position = row.ref("person")?.let { scene.people.position(it) }
                                val values =
                                    listOf(
                                        nameCell(scene, row),
                                        Cell(position?.text("position_title") ?: SIN_DATO),
                                        Cell(DiagnosisScene.option(row, "stakeholder_type")),
                                        Cell(DiagnosisScene.option(assessment, "stance"), mark = Mark.restricted("stakeholder_assessment.stance"))
                                    )
                                if (i == 0) listOf(Cell(ReportLabels.option(group) ?: group, rowSpan = list.size)) + values else values
                            }
                        }
                    Block.Table(
                        if (scopes.isEmpty()) "Clasificación de los grupos de interés sin ámbito" else "Clasificación de los grupos de interés del $label",
                        listOf("Grupo de influencia", "Nombre", "Cargo", "Tipo de actor", "Postura"),
                        cells,
                        key = "clasificacion_${scopeKey(scopes)}"
                    )
                }
            }
        return listOf<Block>(Block.Paragraph(DiagnosisTexts.text("capitulo-7-2")), Block.Paragraph(thresholds)) + tables
    }

    // el nombre del grupo de interés: la persona (mención, solo INTERNO) con su marca de nombre, que EXTERNO vacía (Tablas 7.1 a
    // 7.3, deliverables.md §9.2: ahí el cargo y la organización van en sus propias columnas); una organización va tal cual
    private suspend fun nameCell(
        scene: DiagnosisScene,
        row: SourceRow
    ): Cell {
        val text = scene.people.stakeholder(row)
        return if (row.ref("person") != null) Cell(text, mark = Mark.restricted("person.name")) else Cell(text)
    }

    // 7.2.1 a 7.2.3: los grupos de interés de un grupo de influencia por ámbito, por código GI- y cargo (sin nombre)
    suspend fun group(
        scene: DiagnosisScene,
        group: String
    ): List<Block> {
        val members = scene.stakeholders().filter { (scene.latestAssessment(it)?.text("influence_group") ?: it.text("influence_group")) == group }
        if (members.isEmpty()) return listOf(Block.Paragraph("_Ningún grupo de interés en este grupo de influencia._"))
        return SCOPES.mapNotNull { (scopes, label) ->
            val list = members.filter { inScope(it, scopes) }
            if (list.isEmpty()) {
                null
            } else {
                val lines = list.map { "- ${RegisterSheets.code(it.id)} · ${scene.people.actor(it)}" }
                Block.Paragraph("**${label.replaceFirstChar { it.uppercase() }}**\n\n" + lines.joinToString("\n"))
            }
        }
    }

    suspend fun keyThemes(scene: DiagnosisScene): List<Block> {
        val filters = scene.byProject + (scene.study?.let { mapOf("study" to it.id) } ?: emptyMap())
        val rows = scene.source.all("key_theme", filters).sortedBy { it.text("name").orEmpty() }
        val intro = Block.Paragraph(DiagnosisTexts.text("capitulo-8"))
        if (rows.isEmpty()) return listOf(intro, Block.Paragraph("_Sin temas clave registrados._"))
        rows.forEach { scene.keep("temas_clave", it.text("description"), it.text("risks"), it.text("opportunities")) }
        return listOf(
            intro,
            Block.Table(
                "Temas clave",
                listOf("Tema", "Descripción", "Riesgos", "Oportunidades"),
                rows.map {
                    listOf(
                        Cell(DiagnosisScene.or(it, "name")),
                        Cell(DiagnosisScene.or(it, "description")),
                        Cell(DiagnosisScene.or(it, "risks")),
                        Cell(DiagnosisScene.or(it, "opportunities"))
                    )
                },
                key = "temas_clave"
            )
        )
    }
}
