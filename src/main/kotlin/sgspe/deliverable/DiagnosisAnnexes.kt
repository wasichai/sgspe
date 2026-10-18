package sgspe.deliverable

import sgspe.common.Values
import sgspe.deliverable.DiagnosisScene.Companion.SIN_DATO
import sgspe.privacy.Block
import sgspe.privacy.Cell
import sgspe.privacy.RegisterSheets
import java.math.BigDecimal
import java.util.UUID

// T1, anexos (deliverables.md §5, filas 10.1 a 10.3 y Gráfico 10.3): el mapa de las concesiones del proyecto con las diez capas
// de la leyenda del Anexo 1 en una sección horizontal, los temas e instrumentos (Tabla 10.1) y la metodología de interés y
// poder con el gráfico de influencia (cuadrante 2 × 2 por código GI-, un gráfico nativo hecho de una tabla, §7.9)
object DiagnosisAnnexes {
    const val ANNEX_MAP = "anexo-1"
    private val DEFAULT_THRESHOLD = BigDecimal("3")

    fun annexes(): List<Block> =
        listOf(
            Block.Paragraph("Recursos del diagnóstico: el mapa de las concesiones del proyecto, los temas e instrumentos y la metodología de interés y poder.")
        )

    fun concessions(): List<Block> = listOf(Block.SectionBreak(true), Block.MapFigure(ANNEX_MAP, "Concesiones del proyecto y su entorno"))

    suspend fun instruments(scene: DiagnosisScene): List<Block> {
        val study = scene.study ?: return listOf(Block.SectionBreak(false), Block.Paragraph("_El entregable no tiene estudio._"))
        val rows =
            scene.source.all("study_instrument", mapOf("study" to study.id)).map { row ->
                listOf(
                    Cell(row.ref("topic")?.let { scene.source.one("topic", it)?.text("name") } ?: SIN_DATO),
                    Cell(DiagnosisScene.option(row, "instrument_type")),
                    Cell(DiagnosisScene.option(row, "application_scope"))
                )
            }
        return listOf(Block.SectionBreak(false)) +
            (
                rows.takeIf { it.isNotEmpty() }?.let {
                    listOf(
                        Block.Table("Temas e instrumentos", listOf("Tema", "Tipo de instrumento", "Ámbito de aplicación"), it)
                    )
                }
                    ?: listOf(Block.Paragraph("_El estudio no registra instrumentos._"))
            )
    }

    // el texto fijo, los umbrales del proyecto y el cuadrante: poder alto arriba, interés alto a la derecha
    suspend fun methodology(scene: DiagnosisScene): List<Block> {
        val interest = Values.decimal(scene.project.attributes["interest_threshold"]) ?: DEFAULT_THRESHOLD
        val power = Values.decimal(scene.project.attributes["power_threshold"]) ?: DEFAULT_THRESHOLD
        val quadrants = linkedMapOf<Pair<Boolean, Boolean>, MutableList<String>>()
        for (row in scene.stakeholders()) {
            val i = Values.decimal(row.attributes["interest_index"]) ?: continue
            val p = Values.decimal(row.attributes["power_index"]) ?: continue
            quadrants.getOrPut((p >= power) to (i >= interest)) { mutableListOf() } += RegisterSheets.code(row.id)
        }

        fun cell(
            highPower: Boolean,
            highInterest: Boolean,
            shade: String
        ): Cell = Cell(quadrants[highPower to highInterest]?.sorted()?.joinToString(", ") ?: "—", shade = shade)
        val rows =
            listOf(
                listOf(Cell("Poder alto"), cell(true, false, "FFE699"), cell(true, true, "F4B183")),
                listOf(Cell("Poder bajo"), cell(false, false, "E2EFDA"), cell(false, true, "BDD7EE"))
            )
        return listOf(
            Block.Paragraph(DiagnosisTexts.text("anexo-3")),
            Block.Paragraph("**Umbral de interés:** ${ReportFormat.decimal(interest)}; **umbral de poder:** ${ReportFormat.decimal(power)}."),
            Block.Table("Gráfico de influencia", listOf("Poder / Interés", "Interés bajo", "Interés alto"), rows, figure = true)
        )
    }

    // el Anexo 1 (§3.7): sondajes, centros poblados, capital provincial, vías, área de interés, concesiones, territorio comunal,
    // predios y límites, horizontal, con las concesiones del proyecto en línea
    suspend fun annexMap(scene: DiagnosisScene): MapRequest? {
        val crs = scene.project.text("working_crs") ?: return null
        val concessions = scene.source.related(Deliverables.PROJECT, scene.projectId, "project_concession", "mining_concession").map { UUID.fromString(it.id) }
        return MapRequest(
            ANNEX_MAP,
            scene.projectId,
            crs,
            null,
            listOf("LYR-10", "LYR-02", "LYR-03", "LYR-04", "LYR-09", "LYR-07", "LYR-05", "LYR-14", "LYR-15", "LYR-01"),
            MapFrameKind.INTEREST_AND_CONCESSIONS,
            concessions = concessions,
            landscape = true,
            author = scene.author
        )
    }
}
