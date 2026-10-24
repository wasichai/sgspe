package sgspe.deliverable

import sgspe.deliverable.DiagnosisScene.Companion.SIN_DATO
import sgspe.privacy.Block
import sgspe.privacy.Cell
import sgspe.privacy.ChartSeries
import sgspe.privacy.Fact
import sgspe.privacy.FactRow
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
    private const val TODOS = "Todos"
    private const val TRUST = "Confianza promedio (1 a 5)"

    // las preguntas de sí o no de la evaluación por mes: el campo, la clave de su hecho y su rótulo (la columna «<rótulo> (%)»)
    private val SHARES =
        listOf(
            "stance_changed" to ("cambio_de_postura" to "Cambio de postura"),
            "communication_sufficient" to ("comunicacion_suficiente" to "Comunicación suficiente"),
            "information_adequate" to ("informacion_adecuada" to "Información adecuada")
        )

    suspend fun strategies(scene: DiagnosisScene): List<Block> {
        val plans = scene.source.all("engagement_plan", scene.byProject).sortedBy { it.text("name").orEmpty() }
        if (plans.isEmpty()) return listOf(Block.Paragraph("_El proyecto no tiene plan de relacionamiento._"))
        // la meta sale una vez: con un solo plan, la narrativa de la sección es su meta del periodo y el campo no se repite; con
        // varios, la narrativa no dice de cuál habla y cada plan conserva la suya
        val narrated = plans.size == 1 && scene.hasNarrative("estrategias")
        return plans.flatMap { plan ->
            scene.keep("estrategias", plan.text("name"), plan.text("strategic_objective"), plan.text("action_axes"))
            listOfNotNull(
                Block.Heading(3, DiagnosisScene.or(plan, "name")),
                plan.text("strategic_objective")?.takeUnless { narrated }?.let { Block.Paragraph("**Meta del periodo.** $it") },
                plan.text("action_axes")?.let { Block.Paragraph("**Ejes de acción.**\n\n$it") }
            )
        }
    }

    // las interacciones del periodo por tipo; el hecho `interacciones_por_tipo` cuenta la misma agrupación sobre el catálogo
    // engagement_type, con sus ceros (también sin interacciones)
    suspend fun communication(scene: DiagnosisScene): List<Block> {
        val held = scene.source.all("engagement", scene.byProject).filter { scene.inPeriod(it.text("held_at") ?: it.text("scheduled_at")) }
        val byType = held.groupBy { it.text("engagement_type") ?: SIN_DATO }.toSortedMap()
        ReportFacts.grid(ReportLabels.enumValues("engagement_type"), byType.mapValues { it.value.size }, ReportFacts::option)?.let { rows ->
            scene.fact(
                Fact(
                    "interacciones_por_tipo",
                    Fact.COUNT,
                    "Interacciones del periodo por tipo",
                    listOf("Tipo"),
                    "Interacciones",
                    rows,
                    period = ReportFacts.period(scene.start, scene.end),
                    forPublic = true
                )
            )
        }
        if (held.isEmpty()) return listOf(Block.Paragraph("_Sin interacciones en el periodo._"))
        val rows =
            byType.map { (type, list) ->
                listOf(Cell(ReportLabels.option(type) ?: type), Cell(list.size.toString()))
            }
        return listOf(Block.Table("Interacciones del periodo por tipo", listOf("Tipo", "Cantidad"), rows, key = "interacciones_por_tipo"))
    }

    // 9.1.1.2: las respuestas alineadas aprobadas del proyecto (pregunta y respuesta; un informe no publica las que el equipo no
    // aprobó), tras la narrativa del protocolo (#106)
    suspend fun alignedAnswers(scene: DiagnosisScene): List<Block> {
        val rows =
            scene.source
                .all("aligned_answer", scene.byProject)
                .filter { it.attributes["approved"] == true }
                .sortedBy { it.text("question").orEmpty() }
        if (rows.isEmpty()) return listOf(Block.Paragraph("_Sin respuestas alineadas registradas._"))
        rows.forEach { scene.keep("reuniones_informativas", it.text("question"), it.text("answer")) }
        val list = rows.joinToString("\n") { "- **${DiagnosisScene.or(it, "question")}** ${DiagnosisScene.or(it, "answer")}" }
        return listOf(Block.Paragraph("**Respuestas alineadas**\n\n$list"))
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
                    scene.keep("priorizacion", rule.text("rationale"))
                    val cells =
                        listOf("influence_group", "stance", "stakeholder_scope", "stakeholder_type").map { ruleCell(rule, it) } +
                            Cell(rule.text("rationale").orEmpty())
                    if (i == 0) listOf(Cell(ReportLabels.option(priority) ?: priority, rowSpan = list.size)) + cells else cells
                }
            }
        return listOf(
            Block.Table(
                "Priorización de la información",
                listOf("Prioridad", "Grupo de influencia", "Postura", "Ámbito", "Tipo de actor", "Justificación"),
                rows,
                wide = true,
                key = "priorizacion"
            )
        )
    }

    // la postura es un criterio de la regla pero un dato restringido (RQ-SEC-010): EXTERNO la deja en blanco (falla cerrado)
    private fun ruleCell(
        rule: SourceRow,
        field: String
    ): Cell =
        if (field ==
            "stance"
        ) {
            Cell(ruleOption(rule, field), mark = Mark.restricted("prioritization_rule.stance"))
        } else {
            Cell(ruleOption(rule, field))
        }

    // una regla con el campo vacío aplica a todos
    private fun ruleOption(
        rule: SourceRow,
        field: String
    ): String = ReportLabels.option(rule.text(field)) ?: TODOS

    suspend fun messages(scene: DiagnosisScene): List<Block> =
        table(
            scene,
            "mensajes",
            "key_message",
            "mensajes_clave",
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
        rows.forEach { scene.keep("alianzas", it.text("reasons"), it.text("possible_actions")) }
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
                },
                key = "alianzas"
            )
        )
    }

    // Tabla 9.4: una fila por tipo de actividad, con una columna por tipo de impacto (ambiental, social) y, en cada celda, las
    // descripciones con su naturaleza y su signo en texto
    suspend fun impacts(scene: DiagnosisScene): List<Block> {
        val intro = Block.Paragraph(DiagnosisTexts.text("capitulo-9-3"))
        val rows = scene.source.all("potential_impact", scene.byProject)
        if (rows.isEmpty()) return listOf(intro, Block.Paragraph("_Sin registros._"))
        rows.forEach { row ->
            listOf("activity_type", "impact_type", "impact_nature", "impact_sign", "description").forEach { scene.keep("impactos", row.text(it)) }
        }
        val types = rows.map { it.text("impact_type") }.distinct().sortedBy { listOf("AMBIENTAL", "SOCIAL").indexOf(it).let { i -> if (i < 0) 9 else i } }
        val activities = rows.map { it.text("activity_type") }.distinct().sortedBy { ReportLabels.option(it) ?: SIN_DATO }

        fun described(row: SourceRow): String {
            val traits = listOfNotNull(ReportLabels.option(row.text("impact_nature")), ReportLabels.option(row.text("impact_sign"))).map { it.lowercase() }
            val text = row.text("description") ?: SIN_DATO
            return if (traits.isEmpty()) "• $text" else "• $text (${traits.joinToString(", ")})"
        }
        val table =
            Block.Table(
                "Impactos potenciales por tipo de actividad",
                listOf("Tipo de actividad") + types.map { "Impacto " + (ReportLabels.option(it) ?: SIN_DATO).lowercase() },
                activities.map { activity ->
                    listOf(Cell(ReportLabels.option(activity) ?: SIN_DATO)) +
                        types.map { type ->
                            Cell(rows.filter { it.text("activity_type") == activity && it.text("impact_type") == type }.joinToString("\n") { described(it) })
                        }
                },
                wide = true,
                key = "impactos"
            )
        return listOf(intro, table)
    }

    // Tabla 9.5: valor del periodo, meta y cifra oficial (con su fecha de cierre) o «—». el hecho `monitoreo` lleva el mismo
    // valor y la misma meta por fila, sin valor si no está disponible; sin la tabla en el despliegue no hay hecho
    suspend fun monitoring(scene: DiagnosisScene): List<Block> {
        val rows =
            scene.source.monitoring(scene.projectId, scene.start, scene.end)
                ?: return listOf(Block.Paragraph("_Los indicadores no están disponibles en este despliegue._"))
        val values = rows.map { r -> if (r.available && r.value != null) ReportFormat.decimal(r.value) else null }
        val targets = rows.map { r -> r.target?.let { ReportFormat.decimal(it) } }
        scene.fact(
            Fact(
                "monitoreo",
                Fact.VALUE,
                "Monitoreo de indicadores",
                listOf("Indicador"),
                "Valor del periodo",
                rows.mapIndexed { i, r -> FactRow(listOf("${r.code} ${r.label}"), value = values[i], target = targets[i]) },
                period = ReportFacts.period(scene.start, scene.end)
            )
        )
        return listOf(
            Block.Table(
                "Monitoreo de indicadores",
                listOf("N°", "Indicador", "Medio de verificación", "Valor del periodo", "Meta", "Cifra oficial"),
                rows.mapIndexed { i, r ->
                    listOf(
                        Cell(r.number.toString()),
                        Cell("${r.code} ${r.label}"),
                        Cell(r.verificationMeans),
                        Cell(values[i] ?: "No disponible"),
                        Cell(targets[i] ?: "Sin meta"),
                        Cell(
                            r.official?.let { o -> "${o.value?.let { ReportFormat.decimal(it) } ?: "Sin valor"} (cierre del ${ReportFormat.date(o.closedAt)})" }
                                ?: "—"
                        )
                    )
                },
                wide = true,
                key = "monitoreo"
            )
        )
    }

    // 9.3.1.1: por mes, los últimos seis hasta el corte, con la tendencia de la confianza
    suspend fun evaluation(scene: DiagnosisScene): List<Block> {
        val months = (MONTHS - 1 downTo 0).map { YearMonth.from(scene.cutoff).minusMonths(it.toLong()) }
        val assessments = scene.stakeholders().flatMap { scene.assessments(it) }
        val intro = Block.Paragraph(DiagnosisTexts.text("evaluacion-grupos"))
        if (assessments.isEmpty()) return listOf(intro, Block.Paragraph("_Sin evaluaciones de grupos de interés hasta el corte._"))
        val byMonth = assessments.groupBy { scene.day(it.text("assessment_date"))?.let(YearMonth::from) }
        // por mes, los puntajes de confianza y las respuestas de cada pregunta: la tabla, el gráfico y los hechos salen de aquí
        val scores = months.map { scores(byMonth[it].orEmpty()) }
        val answers = SHARES.map { (field, _) -> months.map { answers(byMonth[it].orEmpty(), field) } }
        val rows =
            months.mapIndexed { i, month ->
                listOf(
                    Cell(month.toString()),
                    Cell(byMonth[month].orEmpty().size.toString()),
                    Cell(average(scores[i]) ?: SIN_DATO)
                ) + answers.map { Cell(share(it[i]) ?: SIN_DATO) }
            }
        val labels = months.map { listOf(it.toString()) }
        scene.fact(
            Fact(
                "confianza_por_mes",
                Fact.MEAN,
                "Confianza promedio de los grupos de interés por mes",
                listOf("Mes"),
                TRUST,
                labels.mapIndexed { i, label -> FactRow(label, value = average(scores[i]), n = scores[i].size) },
                forPublic = true
            )
        )
        SHARES.forEachIndexed { s, (_, fact) ->
            val (key, title) = fact
            scene.fact(
                Fact(
                    key,
                    Fact.VALUE,
                    "$title por mes",
                    listOf("Mes"),
                    "$title (%)",
                    labels.mapIndexed { i, label -> FactRow(label, value = share(answers[s][i]), n = answers[s][i].size) },
                    unit = "%",
                    forPublic = true
                )
            )
        }
        return listOf(
            intro,
            Block.Table(
                "Evaluación de los grupos de interés por mes",
                listOf("Mes", "Evaluaciones", "Confianza promedio") + SHARES.map { "${it.second.second} (%)" },
                rows,
                wide = true,
                key = "evaluacion_por_mes"
            ),
            Block.LineChart(
                "Confianza promedio de los grupos de interés",
                months.map { it.toString() },
                listOf(ChartSeries(TRUST, scores.map { average(it) })),
                key = "confianza"
            )
        )
    }

    // los compromisos por estado y por plazo; los hechos `compromisos_por_estado` (los estados de WF-D) y `compromisos_por_plazo`
    // (el catálogo deadline_status) cuentan las mismas agrupaciones sobre la grilla completa, con sus ceros
    suspend fun commitments(scene: DiagnosisScene): List<Block> {
        val rows = scene.source.all("commitment", scene.byProject)
        val states = rows.groupBy { it.state ?: SIN_DATO }.toSortedMap().mapValues { it.value.size }
        val deadlines = rows.groupBy { it.text("deadline_status") ?: SIN_DATO }.toSortedMap().mapValues { it.value.size }
        val byState =
            states.map { (state, count) ->
                listOf(Cell(ReportLabels.state("commitment", state) ?: state), Cell(count.toString()))
            }
        val byDeadline =
            deadlines.map { (status, count) ->
                listOf(Cell(ReportLabels.option(status) ?: status), Cell(count.toString()))
            }
        // un compromiso sin estado no está en el workflow: va a «No disponible», como en las exportaciones (MatrixSheets)
        ReportFacts.grid(ReportLabels.states("commitment"), states, { ReportLabels.state("commitment", it) ?: it }, empty = null)?.let {
            scene.fact(Fact("compromisos_por_estado", Fact.COUNT, "Compromisos por estado", listOf("Estado"), "Compromisos", it, forPublic = true))
        }
        ReportFacts.grid(ReportLabels.enumValues("deadline_status"), deadlines, ReportFacts::option)?.let {
            scene.fact(Fact("compromisos_por_plazo", Fact.COUNT, "Compromisos por plazo", listOf("Plazo"), "Compromisos", it, forPublic = true))
        }
        val p = scene.project
        val campaigns = scene.source.all("exploration_campaign", scene.byProject)
        campaigns.forEach { scene.keep("seguimiento_compromisos", it.text("name")) }
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
                    )}: ${supportCap(c)}"
                }
        return listOfNotNull(
            Block.Paragraph(DiagnosisTexts.text("seguimiento-compromisos")),
            byState.takeIf { it.isNotEmpty() }?.let { Block.Table("Compromisos por estado", listOf("Estado", "Cantidad"), it, key = "compromisos_por_estado") },
            byDeadline.takeIf { it.isNotEmpty() }?.let { Block.Table("Compromisos por plazo", listOf("Plazo", "Cantidad"), it, key = "compromisos_por_plazo") },
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
                    .maxWithOrNull(compareBy<SourceRow>({ it.text("assessment_date").orEmpty() }, { it.id })) ?: continue
            val probability = (latest.attributes["probability"] as? Number)?.toInt() ?: continue
            val impact = (latest.attributes["impact"] as? Number)?.toInt() ?: continue
            if (probability in 1..5 && impact in 1..5) counts[impact - 1][probability - 1]++
        }

        fun cell(
            probability: Int,
            impact: Int
        ): Int = counts[impact - 1][probability - 1]
        val matrix =
            (5 downTo 1).map { impact ->
                listOf(Cell("$impact")) +
                    (1..5).map { probability -> Cell(cell(probability, impact).toString(), shade = shade(probability * impact, scene.project)) }
            }
        // el hecho `riesgos_matriz`: las 25 celdas de la misma matriz, en su orden (impacto de 5 a 1, probabilidad de 1 a 5)
        val grid =
            (5 downTo 1).flatMap { impact ->
                (1..5).map { probability -> FactRow(listOf("$probability", "$impact"), count = cell(probability, impact)) }
            }
        scene.fact(
            Fact(
                "riesgos_matriz",
                Fact.COUNT,
                "Riesgos por probabilidad e impacto",
                listOf("Probabilidad", "Impacto"),
                "Riesgos",
                grid,
                period = ReportFacts.cutoff(scene.cutoff),
                forPublic = true
            )
        )
        return listOfNotNull(
            Block.Paragraph(DiagnosisTexts.text("riesgos")),
            risks.takeIf { it.isNotEmpty() }?.let { list ->
                Block.Table(
                    "Riesgos sociales identificados",
                    listOf("Código", "Riesgo", "Fuente", "Categoría"),
                    list.map {
                        listOf(
                            Cell(DiagnosisScene.or(it, "code")),
                            Cell(DiagnosisScene.or(it, "title")),
                            Cell(it.text("risk_source").orEmpty()),
                            Cell(DiagnosisScene.option(it, "category"))
                        )
                    },
                    key = "riesgos"
                )
            },
            Block.Heading(3, "9.3.2.1 Evaluación del riesgo"),
            Block.Paragraph(DiagnosisTexts.text("evaluacion-riesgo")),
            Block.Table(
                "Matriz de probabilidad e impacto",
                listOf("Impacto / Probabilidad", "1", "2", "3", "4", "5"),
                matrix,
                figure = true,
                key = "matriz_de_riesgos"
            )
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
                scene.keep("gestion_riesgos", risk.text("treatment_rationale"), risk.text("current_controls"), risk.text("monitoring_sources"), measures)
                listOf(
                    Cell("${DiagnosisScene.or(risk, "code")} ${DiagnosisScene.or(risk, "title")}"),
                    Cell(DiagnosisScene.option(risk, "treatment_decision")),
                    Cell(risk.text("treatment_rationale").orEmpty()),
                    Cell(risk.text("current_controls").orEmpty()),
                    Cell(ReportFormat.yesNo(risk.attributes["controls_adequate"])),
                    Cell(risk.text("monitoring_sources").orEmpty()),
                    Cell(DiagnosisScene.option(risk, "residual_level")),
                    Cell(measures.ifEmpty { SIN_DATO })
                )
            }
        return listOf(
            Block.Table(
                "Gestión de los riesgos sociales",
                listOf("Riesgo", "Decisión", "Justificación", "Controles", "¿Adecuados?", "Fuentes de monitoreo", "Nivel residual", "Medidas"),
                rows,
                wide = true,
                key = "gestion_de_riesgos"
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
        activities.forEach { scene.keep("necesidades", it.text("name")) }
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
                    },
                    key = "plan_de_trabajo"
                )
            },
            Block.Heading(3, "9.4.2 Equipo de relaciones comunitarias"),
            team.takeIf { it.isNotEmpty() }?.let { roles ->
                Block.Table(
                    "Equipo del proyecto",
                    listOf("Rol", "Integrantes activos"),
                    roles.map { (role, list) ->
                        listOf(Cell(ReportLabels.role(role) ?: role), Cell(list.size.toString()))
                    },
                    key = "equipo"
                )
            },
            Block.Paragraph("**Contratistas:** ${contractors.joinToString(", ").ifEmpty { SIN_DATO }}")
        )
    }

    // el tope de apoyo social de una campaña: el monto con su moneda, o «Sin dato» a secas si no hay monto
    private fun supportCap(campaign: SourceRow): String {
        val amount = ReportFormat.decimal(campaign.attributes["social_support_cap"])
        return if (amount.isEmpty()) SIN_DATO else "$amount ${campaign.text("currency").orEmpty()}".trim()
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

    // los puntajes de confianza de las evaluaciones que lo tienen: su número es el `n` del promedio
    private fun scores(rows: List<SourceRow>): List<Double> = rows.mapNotNull { (it.attributes["trust_score"] as? Number)?.toDouble() }

    private fun average(scores: List<Double>): String? = scores.takeIf { it.isNotEmpty() }?.average()?.let { "%.2f".format(Locale.US, it) }

    // las respuestas (sí o no) de las evaluaciones que responden: su número es el denominador de la proporción
    private fun answers(
        rows: List<SourceRow>,
        field: String
    ): List<Boolean> = rows.mapNotNull { it.attributes[field] as? Boolean }

    private fun share(answers: List<Boolean>): String? =
        answers.takeIf { it.isNotEmpty() }?.let {
            "%.0f".format(
                Locale.US,
                it.count { b -> b } * 100.0 / it.size
            )
        }

    private suspend fun table(
        scene: DiagnosisScene,
        section: String,
        objectName: String,
        key: String,
        title: String,
        columns: List<Pair<String, String>>,
        labeled: Set<String> = emptySet(),
        wide: Boolean = false
    ): List<Block> {
        val rows = scene.source.all(objectName, scene.byProject)
        if (rows.isEmpty()) return listOf(Block.Paragraph("_Sin registros._"))
        rows.forEach { row -> columns.forEach { (_, field) -> scene.keep(section, row.text(field)) } }
        return listOf(
            Block.Table(
                title,
                columns.map { it.first },
                rows.map { row ->
                    columns.map { (_, field) -> Cell((if (field in labeled) ReportLabels.option(row.text(field)) else row.text(field)).orEmpty()) }
                },
                wide = wide,
                key = key
            )
        )
    }
}
