package sgspe.deliverable

import sgspe.common.Values
import sgspe.deliverable.DiagnosisScene.Companion.SIN_DATO
import sgspe.privacy.Block
import sgspe.privacy.Cell
import sgspe.privacy.ChartPoint
import sgspe.privacy.RegisterSheets
import sgspe.stakeholder.InfluenceClassifier
import java.math.BigDecimal
import java.util.UUID

// T1, anexos (deliverables.md §5, filas 10.1 a 10.3 y Gráfico 10.3): el mapa de las concesiones del proyecto con las diez capas
// de la leyenda del Anexo 1 en una sección horizontal, los temas e instrumentos (Tabla 10.1) y la metodología de interés y
// poder con el sistema de variables, el gráfico de influencia (una dispersión nativa interés × poder por código GI-, con las
// líneas de los umbrales) y el cuadrante 2 × 2 con los mismos códigos (§7.9)
object DiagnosisAnnexes {
    const val ANNEX_MAP = "anexo-1"
    private val DEFAULT_THRESHOLD = BigDecimal("3")
    private val MIN_INDEX = BigDecimal.ONE
    private val MAX_INDEX = BigDecimal("5")

    // los rótulos de los componentes y los indicadores de InfluenceClassifier en el informe: los de model.json
    // (stakeholder_assessment), sin «Componente de» en los componentes
    val LABELS: Map<String, String> =
        mapOf(
            "interest_affectation" to "Percepción de afectación",
            "interest_opportunity" to "Percepción de oportunidad",
            "power_resource_control" to "Control de recursos",
            "power_action_capacity" to "Capacidad de acción",
            "power_network_mobilization" to "Movilización de redes",
            "affect_environment" to "Afectación del medio ambiente",
            "affect_livelihood" to "Afecta económicamente mi vida",
            "affect_property_status" to "Afectación de la propiedad o la posición",
            "opportunity_work" to "Oportunidad de trabajo u oportunidad personal",
            "opportunity_development" to "Oportunidad de desarrollo u oportunidad colectiva",
            "opportunity_benefits" to "Oportunidad de beneficios económicos o políticos",
            "power_legal_action" to "Habilidad de acción legal",
            "power_credibility" to "Credibilidad",
            "power_permit_control" to "Control de permisos",
            "power_action_scope" to "Ámbito de acción",
            "power_organization_level" to "Nivel de organización",
            "power_convening_capacity" to "Capacidad de convocatoria",
            "power_network_membership" to "Pertenencia a redes",
            "power_group_mobilization" to "Participación en acciones grupales de movilización",
            "power_other_pressure" to "Participación en otros mecanismos de presión"
        )

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
                        Block.Table("Temas e instrumentos", listOf("Tema", "Tipo de instrumento", "Ámbito de aplicación"), it, key = "temas_e_instrumentos")
                    )
                }
                    ?: listOf(Block.Paragraph("_El estudio no registra instrumentos._"))
            )
    }

    // el texto fijo, el sistema de variables, los umbrales del proyecto, la dispersión y el cuadrante: poder alto arriba, interés
    // alto a la derecha. un grupo de interés sin sus dos índices (sin evaluar) no sale en ninguno de los dos
    suspend fun methodology(scene: DiagnosisScene): List<Block> {
        val interest = Values.decimal(scene.project.attributes["interest_threshold"]) ?: DEFAULT_THRESHOLD
        val power = Values.decimal(scene.project.attributes["power_threshold"]) ?: DEFAULT_THRESHOLD
        val quadrants = linkedMapOf<Pair<Boolean, Boolean>, MutableList<String>>()
        val points = mutableListOf<ChartPoint>()
        for (row in scene.stakeholders()) {
            val i = Values.decimal(row.attributes["interest_index"]) ?: continue
            val p = Values.decimal(row.attributes["power_index"]) ?: continue
            val code = RegisterSheets.code(row.id)
            quadrants.getOrPut((p >= power) to (i >= interest)) { mutableListOf() } += code
            points += ChartPoint(code, plain(i), plain(p))
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
            variables(),
            Block.Paragraph("**Umbral de interés:** ${ReportFormat.decimal(interest)}; **umbral de poder:** ${ReportFormat.decimal(power)}."),
            Block.ScatterChart(
                "Gráfico de influencia",
                "Interés",
                "Poder",
                points.sortedBy { it.label },
                plain(MIN_INDEX),
                plain(MAX_INDEX),
                plain(interest),
                plain(power),
                key = "influencia"
            ),
            Block.Table(
                "Cuadrantes de influencia",
                listOf("Poder / Interés", "Interés bajo", "Interés alto"),
                rows,
                figure = true,
                key = "cuadrantes_de_influencia"
            )
        )
    }

    // el sistema de variables (ASM-031, CON-010): cada variable con sus componentes y cada componente con sus indicadores, en el
    // orden de InfluenceClassifier, que es el que calcula los índices. los pesos son iguales mientras GAP-002 no fije otros,
    // así que la tabla no lleva columna de pesos
    fun variables(): Block.Table {
        val rows = mutableListOf<List<Cell>>()
        for ((variable, components) in listOf("Interés" to InfluenceClassifier.INTEREST, "Poder" to InfluenceClassifier.POWER)) {
            var firstOfVariable = true
            for ((component, indicators) in components) {
                indicators.forEachIndexed { index, indicator ->
                    rows +=
                        listOfNotNull(
                            Cell(variable, rowSpan = components.values.sumOf { it.size }).takeIf { firstOfVariable },
                            Cell(LABELS.getValue(component), rowSpan = indicators.size).takeIf { index == 0 },
                            Cell(LABELS.getValue(indicator))
                        )
                    firstOfVariable = false
                }
            }
        }
        return Block.Table("Sistema de variables", listOf("Variable", "Componente", "Indicador"), rows, figure = true, key = "sistema_de_variables")
    }

    // un decimal sin ceros de más, como texto («4.2», «3»): los valores del gráfico
    private fun plain(value: BigDecimal): String = value.stripTrailingZeros().toPlainString()

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
