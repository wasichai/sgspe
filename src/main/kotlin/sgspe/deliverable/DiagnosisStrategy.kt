package sgspe.deliverable

import sgspe.deliverable.DiagnosisScene.Companion.SIN_DATO
import sgspe.privacy.Block
import sgspe.privacy.Cell
import sgspe.privacy.ChartSeries
import sgspe.privacy.Mark
import java.time.YearMonth
import java.util.Locale

// T1, capítulo 9 (deliverables.md §5, filas 9 a 9.4): el plan y sus ejes, las interacciones del periodo, la priorización
// (Tabla 9.1), los mensajes (9.2), las alianzas (9.3), los impactos (9.4), la Tabla 9.5 con el lector de la plataforma y la
// cifra oficial, la evaluación de los grupos de interés con su tendencia, el resumen de compromisos con los lineamientos de la
// campaña (la matriz completa sigue siendo el XLSX de F3), los riesgos con la matriz 5 × 5 y su gestión, y las necesidades
// internas. lo que falta sale «Sin dato»
object DiagnosisStrategy {
    private val PRIORITIES = listOf("MUY ALTA", "ALTA", "MEDIA")
    private const val MONTHS = 6

    suspend fun strategies(scene: DiagnosisScene): List<Block> {
        val plans = scene.source.all("engagement_plan", scene.byProject).sortedBy { it.text("name").orEmpty() }
        if (plans.isEmpty()) return listOf(Block.Paragraph("_El proyecto no tiene plan de relacionamiento._"))
        return plans.flatMap { plan ->
            scene.keep("estrategias", plan.text("strategic_objective"), plan.text("action_axes"))
            listOfNotNull(
                Block.Heading(3, DiagnosisScene.or(plan, "name")),
                plan.text("strategic_objective")?.let { Block.Paragraph("**Meta del periodo.** $it") },
                plan.text("action_axes")?.let { Block.Paragraph("**Ejes de acción.**\n\n$it") }
            )
        }
    }

    suspend fun communication(scene: DiagnosisScene): List<Block> {
        val held = scene.source.all("engagement", scene.byProject).filter { scene.inPeriod(it.text("held_at") ?: it.text("scheduled_at")) }
        if (held.isEmpty()) return listOf(Block.Paragraph("_Sin interacciones en el periodo._"))
        val rows = held.groupBy { it.text("engagement_type") ?: SIN_DATO }.toSortedMap().map { (type, list) -> listOf(Cell(type), Cell(list.size.toString())) }
        return listOf(Block.Table("Interacciones del periodo por tipo", listOf("Tipo", "Cantidad"), rows))
    }

    // Tabla 9.1 con la prioridad combinada
    suspend fun prioritization(scene: DiagnosisScene): List<Block> {
        val grouped = scene.source.all("prioritization_rule", scene.byProject).groupBy { it.text("information_priority") ?: SIN_DATO }
        if (grouped.isEmpty()) return listOf(Block.Paragraph("_Sin reglas de priorización._"))
        val order = PRIORITIES.filter { it in grouped } + (grouped.keys - PRIORITIES.toSet()).sorted()
        val rows =
            order.flatMap { priority ->
                val list = grouped.getValue(priority)
                list.mapIndexed { i, rule ->
                    val cells =
                        listOf("influence_group", "stance", "stakeholder_scope", "stakeholder_type").map {
                            // la postura es un dato restringido (RQ-SEC-010): EXTERNO la deja en blanco
                            if (it ==
                                "stance"
                            ) {
                                Cell(DiagnosisScene.or(rule, it), mark = Mark.restricted("stakeholder_assessment.stance"))
                            } else {
                                Cell(DiagnosisScene.or(rule, it))
                            }
                        } +
                            Cell(rule.text("rationale").orEmpty())
                    if (i == 0) listOf(Cell(priority, rowSpan = list.size)) + cells else cells
                }
            }
        return listOf(
            Block.Table(
                "Priorización de la información",
                listOf("Prioridad", "Grupo de influencia", "Postura", "Ámbito", "Tipo de actor", "Justificación"),
                rows,
                wide = true
            )
        )
    }

    suspend fun messages(scene: DiagnosisScene): List<Block> =
        table(
            scene,
            "key_message",
            "Mensajes clave",
            listOf(
                "Justificación" to "justification",
                "Mensaje" to "message",
                "Acciones de soporte" to "support_actions",
                "Público" to "target_audience"
            )
        )

    suspend fun alliances(scene: DiagnosisScene): List<Block> {
        val rows = scene.source.all("strategic_alliance", scene.byProject)
        if (rows.isEmpty()) return listOf(Block.Paragraph("_Sin alianzas registradas._"))
        return listOf(
            Block.Table(
                "Alianzas estratégicas",
                listOf("Institución", "Razones", "Acciones posibles", "Formalizada"),
                rows.map {
                    listOf(
                        Cell(scene.people.organizationName(it.ref("organization")) ?: SIN_DATO),
                        Cell(it.text("reasons").orEmpty()),
                        Cell(it.text("possible_actions").orEmpty()),
                        Cell(ReportFormat.yesNo(it.attributes["formalized"]))
                    )
                }
            )
        )
    }

    suspend fun impacts(scene: DiagnosisScene): List<Block> =
        table(
            scene,
            "potential_impact",
            "Impactos potenciales por tipo de actividad",
            listOf(
                "Tipo de actividad" to "activity_type",
                "Tipo de impacto" to "impact_type",
                "Naturaleza" to "impact_nature",
                "Signo" to "impact_sign",
                "Descripción" to "description"
            ),
            wide = true
        )

    // Tabla 9.5: valor del periodo, meta y cifra oficial (con su fecha de cierre) o «—»
    suspend fun monitoring(scene: DiagnosisScene): List<Block> {
        val rows =
            scene.source.monitoring(scene.projectId, scene.start, scene.end)
                ?: return listOf(Block.Paragraph("_Los indicadores no están disponibles en este despliegue._"))
        return listOf(
            Block.Table(
                "Monitoreo de indicadores",
                listOf("N°", "Indicador", "Medio de verificación", "Valor del periodo", "Meta", "Cifra oficial"),
                rows.map { r ->
                    listOf(
                        Cell(r.number.toString()),
                        Cell("${r.code} ${r.label}"),
                        Cell(r.verificationMeans),
                        Cell(if (r.available && r.value != null) ReportFormat.decimal(r.value) else "No disponible"),
                        Cell(r.target?.let { ReportFormat.decimal(it) } ?: "Sin meta"),
                        Cell(
                            r.official?.let { o -> "${o.value?.let { ReportFormat.decimal(it) } ?: "Sin valor"} (cierre del ${ReportFormat.date(o.closedAt)})" }
                                ?: "—"
                        )
                    )
                },
                wide = true
            )
        )
    }

    // 9.3.1.1: por mes, los últimos seis hasta el corte, con la tendencia de la confianza
    suspend fun evaluation(scene: DiagnosisScene): List<Block> {
        val months = (MONTHS - 1 downTo 0).map { YearMonth.from(scene.cutoff).minusMonths(it.toLong()) }
        val assessments =
            scene
                .stakeholders()
                .flatMap { scene.source.all("stakeholder_assessment", mapOf("stakeholder" to it.id)) }
                .filter { scene.upToCutoff(it.text("assessment_date")) }
        if (assessments.isEmpty()) return listOf(Block.Paragraph("_Sin evaluaciones de grupos de interés hasta el corte._"))
        val byMonth = assessments.groupBy { scene.day(it.text("assessment_date"))?.let(YearMonth::from) }
        val rows =
            months.map { month ->
                val list = byMonth[month].orEmpty()
                listOf(
                    Cell(month.toString()),
                    Cell(list.size.toString()),
                    Cell(average(list) ?: SIN_DATO),
                    Cell(share(list, "stance_changed") ?: SIN_DATO),
                    Cell(share(list, "communication_sufficient") ?: SIN_DATO),
                    Cell(share(list, "information_adequate") ?: SIN_DATO)
                )
            }
        return listOf(
            Block.Table(
                "Evaluación de los grupos de interés por mes",
                listOf("Mes", "Evaluaciones", "Confianza promedio", "Cambio de postura (%)", "Comunicación suficiente (%)", "Información adecuada (%)"),
                rows,
                wide = true
            ),
            Block.LineChart(
                "Confianza promedio de los grupos de interés",
                months.map { it.toString() },
                listOf(ChartSeries("Confianza promedio (1 a 5)", months.map { average(byMonth[it].orEmpty()) }))
            )
        )
    }

    suspend fun commitments(scene: DiagnosisScene): List<Block> {
        val rows = scene.source.all("commitment", scene.byProject)
        val byState = rows.groupBy { it.state ?: SIN_DATO }.toSortedMap().map { (state, list) -> listOf(Cell(state), Cell(list.size.toString())) }
        val byDeadline =
            rows.groupBy { it.text("deadline_status") ?: SIN_DATO }.toSortedMap().map { (status, list) ->
                listOf(Cell(status), Cell(list.size.toString()))
            }
        val p = scene.project
        val campaigns = scene.source.all("exploration_campaign", scene.byProject)
        val lines =
            listOf(
                "- Empleo local solo del área de influencia directa: ${ReportFormat.yesNo(p.attributes["policy_local_jobs_direct_area_only"])}",
                "- Empleo local: entre ${DiagnosisScene.or(p, "local_jobs_min")} y ${DiagnosisScene.or(p, "local_jobs_max")} puestos",
                "- Sin trabajo comunal pagado: ${ReportFormat.yesNo(p.attributes["policy_no_paid_labor"])}",
                "- Donaciones no monetarias: ${ReportFormat.yesNo(p.attributes["policy_non_monetary_donations"])}",
                "- Sinergias con acuerdo: ${ReportFormat.yesNo(p.attributes["policy_synergy_requires_agreement"])}"
            ) +
                campaigns.map { c ->
                    "- Tope de apoyo social de ${DiagnosisScene.or(
                        c,
                        "name"
                    )}: ${ReportFormat.decimal(c.attributes["social_support_cap"]).ifEmpty { SIN_DATO }} ${c.text("currency").orEmpty()}".trim()
                }
        return listOfNotNull(
            byState.takeIf { it.isNotEmpty() }?.let { Block.Table("Compromisos por estado", listOf("Estado", "Cantidad"), it) },
            byDeadline.takeIf { it.isNotEmpty() }?.let { Block.Table("Compromisos por plazo", listOf("Plazo", "Cantidad"), it) },
            Block.Paragraph("**Lineamientos de apoyo de la campaña.**\n\n${lines.joinToString("\n")}\n\nLa matriz completa de compromisos se exporta en XLSX.")
        )
    }

    // 9.3.2 y 9.3.2.1: los riesgos y la matriz de probabilidad e impacto con la última revisión no residual hasta el corte
    suspend fun risks(scene: DiagnosisScene): List<Block> {
        val risks = scene.source.all("social_risk", scene.byProject).sortedBy { it.text("code").orEmpty() }
        risks.forEach { scene.keep("riesgos", it.text("title"), it.text("risk_source")) }
        val counts = Array(5) { IntArray(5) }
        for (risk in risks) {
            val latest =
                scene.source
                    .all("risk_assessment", mapOf("social_risk" to risk.id))
                    .filter { it.attributes["residual"] != true && scene.upToCutoff(it.text("assessment_date")) }
                    .maxByOrNull { it.text("assessment_date").orEmpty() } ?: continue
            val probability = (latest.attributes["probability"] as? Number)?.toInt() ?: continue
            val impact = (latest.attributes["impact"] as? Number)?.toInt() ?: continue
            if (probability in 1..5 && impact in 1..5) counts[impact - 1][probability - 1]++
        }
        val matrix =
            (5 downTo 1).map { impact ->
                listOf(Cell("$impact")) +
                    (1..5).map { probability -> Cell(counts[impact - 1][probability - 1].toString(), shade = shade(probability * impact, scene.project)) }
            }
        return listOfNotNull(
            risks.takeIf { it.isNotEmpty() }?.let { list ->
                Block.Table(
                    "Riesgos sociales identificados",
                    listOf("Código", "Riesgo", "Fuente", "Categoría"),
                    list.map {
                        listOf(
                            Cell(DiagnosisScene.or(it, "code")),
                            Cell(DiagnosisScene.or(it, "title")),
                            Cell(it.text("risk_source").orEmpty()),
                            Cell(DiagnosisScene.or(it, "category"))
                        )
                    }
                )
            },
            Block.Heading(3, "9.3.2.1 Evaluación del riesgo"),
            Block.Table("Matriz de probabilidad e impacto", listOf("Impacto / Probabilidad", "1", "2", "3", "4", "5"), matrix, figure = true)
        )
    }

    suspend fun riskManagement(scene: DiagnosisScene): List<Block> {
        val risks = scene.source.all("social_risk", scene.byProject).sortedBy { it.text("code").orEmpty() }
        if (risks.isEmpty()) return listOf(Block.Paragraph("_Sin riesgos sociales registrados._"))
        val rows =
            risks.map { risk ->
                val measures =
                    scene.source.all("mitigation_measure", mapOf("social_risk" to risk.id)).joinToString("; ") {
                        DiagnosisScene.or(it, "description") + if (it.attributes["completed"] == true) " (cumplida)" else ""
                    }
                scene.keep("gestion_riesgos", risk.text("treatment_rationale"), risk.text("current_controls"))
                listOf(
                    Cell("${DiagnosisScene.or(risk, "code")} ${DiagnosisScene.or(risk, "title")}"),
                    Cell(DiagnosisScene.or(risk, "treatment_decision")),
                    Cell(risk.text("treatment_rationale").orEmpty()),
                    Cell(risk.text("current_controls").orEmpty()),
                    Cell(ReportFormat.yesNo(risk.attributes["controls_adequate"])),
                    Cell(risk.text("monitoring_sources").orEmpty()),
                    Cell(DiagnosisScene.or(risk, "residual_level")),
                    Cell(measures.ifEmpty { SIN_DATO })
                )
            }
        return listOf(
            Block.Table(
                "Gestión de los riesgos sociales",
                listOf("Riesgo", "Decisión", "Justificación", "Controles", "¿Adecuados?", "Fuentes de monitoreo", "Nivel residual", "Medidas"),
                rows,
                wide = true
            )
        )
    }

    suspend fun needs(scene: DiagnosisScene): List<Block> {
        val activities =
            scene.source
                .all("engagement_plan", scene.byProject)
                .flatMap { scene.source.all("planned_activity", mapOf("engagement_plan" to it.id)) }
                .sortedBy { it.text("planned_date").orEmpty() }
        val team =
            scene.source
                .all("project_assignment", scene.byProject)
                .filter { it.attributes["active"] != false }
                .groupBy { it.text("role_name") ?: SIN_DATO }
                .toSortedMap()
        val contractors =
            scene.source
                .all("project_party", scene.byProject)
                .filter { it.text("party_role") == "CONTRATISTA" }
                .mapNotNull { scene.people.organizationName(it.ref("organization")) }
        return listOfNotNull(
            Block.Heading(3, "9.4.1 Alineamiento interno"),
            activities.takeIf { it.isNotEmpty() }?.let { list ->
                Block.Table(
                    "Plan de trabajo",
                    listOf("Actividad", "Fecha", "Cumplida"),
                    list.map {
                        listOf(
                            Cell(DiagnosisScene.or(it, "name")),
                            Cell(
                                ReportFormat.date(it.text("planned_date")).ifEmpty {
                                    SIN_DATO
                                }
                            ),
                            Cell(ReportFormat.yesNo(it.attributes["completed"]))
                        )
                    }
                )
            },
            Block.Heading(3, "9.4.2 Equipo de relaciones comunitarias"),
            team.takeIf { it.isNotEmpty() }?.let { roles ->
                Block.Table(
                    "Equipo del proyecto",
                    listOf("Rol", "Integrantes activos"),
                    roles.map { (role, list) ->
                        listOf(Cell(role), Cell(list.size.toString()))
                    }
                )
            },
            Block.Paragraph("**Contratistas:** ${contractors.joinToString(", ").ifEmpty { SIN_DATO }}")
        )
    }

    // el color de una celda de la matriz por su puntaje, con los umbrales del proyecto (5, 10 y 15 por defecto, ASM-004)
    fun shade(
        score: Int,
        project: SourceRow
    ): String {
        fun threshold(
            field: String,
            default: Int
        ): Int = (project.attributes[field] as? Number)?.toInt() ?: default
        return when {
            score >= threshold("risk_critical_min_score", 15) -> "FF7C80"
            score >= threshold("risk_high_min_score", 10) -> "F4B183"
            score >= threshold("risk_medium_min_score", 5) -> "FFE699"
            else -> "C6E0B4"
        }
    }

    private fun average(rows: List<SourceRow>): String? =
        rows
            .mapNotNull { (it.attributes["trust_score"] as? Number)?.toDouble() }
            .takeIf { it.isNotEmpty() }
            ?.average()
            ?.let { "%.2f".format(Locale.US, it) }

    private fun share(
        rows: List<SourceRow>,
        field: String
    ): String? =
        rows.mapNotNull { it.attributes[field] as? Boolean }.takeIf { it.isNotEmpty() }?.let {
            "%.0f".format(
                Locale.US,
                it.count { b -> b } * 100.0 / it.size
            )
        }

    private suspend fun table(
        scene: DiagnosisScene,
        objectName: String,
        title: String,
        columns: List<Pair<String, String>>,
        wide: Boolean = false
    ): List<Block> {
        val rows = scene.source.all(objectName, scene.byProject)
        if (rows.isEmpty()) return listOf(Block.Paragraph("_Sin registros._"))
        return listOf(
            Block.Table(
                title,
                columns.map { it.first },
                rows.map { row ->
                    columns.map { (_, field) -> Cell(row.text(field).orEmpty()) }
                },
                wide = wide
            )
        )
    }
}
