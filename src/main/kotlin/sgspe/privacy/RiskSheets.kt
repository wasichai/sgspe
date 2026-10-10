package sgspe.privacy

import org.springframework.http.HttpStatus
import sgspe.common.Finding
import sgspe.common.Values
import sgspe.common.toCodedException

// un riesgo de la matriz como lo leyó quien exporta, con lo que la matriz junta de sus hijos: sus grupos de interés (id y nombre
// si se puede mostrar; null si no se leen), su última revisión no residual y sus medidas contadas (null si no se leen)
class RiskLine(
    val id: String,
    val state: String?,
    val attributes: Map<String, Any?>,
    val stakeholders: List<Pair<String, String?>>?,
    val lastDate: String?,
    val lastTrend: String?,
    val measures: MeasureCount?
)

// las medidas de un riesgo: totales, completadas y atrasadas (sin completar y con planned_date anterior a hoy)
data class MeasureCount(
    val total: Int,
    val completed: Int,
    val late: Int
)

// lo que las hojas necesitan además de los riesgos, ya leído como quien exporta: los objeto.campo que lee, los nombres de los
// registros vinculados (localidad, comunidad, área de influencia, conflicto, tema, incidente), y el código de cada riesgo por id
class RiskContext(
    val readable: Set<String>,
    val names: Map<String, String> = emptyMap(),
    val codes: Map<String, String> = emptyMap()
) {
    fun canRead(path: String): Boolean = path in readable
}

// las hojas de risk-register según el nivel de divulgación (deliverables.md §10, ASM-101): INTERNO con «Matriz» (los campos de
// DOC-01 §9.3.2), «Revisiones», «Medidas» e «Impactos»; EXTERNO sin texto libre ni correos, con los grupos de interés por código
// GI- y las medidas contadas en la «Matriz»; PUBLICO solo cuenta riesgos activos sobre la grilla del catálogo con SmallCells
// (ASM-088). nada de esto lee datos: recibe lo que quien exporta pudo leer
object RiskSheets {
    const val RISK = "social_risk"
    const val ASSESSMENT = "risk_assessment"
    const val MEASURE = "mitigation_measure"
    const val IMPACT = "potential_impact"
    const val CLOSED = "closed"
    const val NO_DATA = "SIN DATO"
    const val NO_LEVEL = "SIN NIVEL"
    const val UNDECIDED = "sin decidir"

    // los niveles de riesgo y los estados que calculan review_status (privacy solo depende de common; RiskSheetsTest comprueba que
    // son los de sgspe.risk.Risks)
    val LEVELS: List<String> = listOf("BAJO", "MEDIO", "ALTO", "CRITICO")
    val REVIEWED: Set<String> = setOf("assessed", "mitigating", "monitoring")

    // el texto libre (LONG_TEXT) y los correos: nunca en EXTERNO (deliverables.md §10)
    val FREE_TEXT: Map<String, Set<String>> =
        mapOf(
            RISK to setOf("risk_source", "treatment_rationale", "current_controls", "monitoring_sources", "close_reason"),
            ASSESSMENT to setOf("notes"),
            MEASURE to setOf("description"),
            IMPACT to setOf("description")
        )
    val EMAILS: Set<String> = setOf("owner_user_email", "assessor_user_email")

    val RISK_FIELDS: List<String> =
        listOf(
            "code",
            "title",
            "risk_source",
            "category",
            "impact_type",
            "impact_nature",
            "probability",
            "impact",
            "score",
            "risk_level",
            "treatment_decision",
            "treatment_rationale",
            "current_controls",
            "controls_adequate",
            "monitoring_sources",
            "residual_probability",
            "residual_impact",
            "residual_score",
            "residual_level",
            "next_review_date",
            "review_status",
            "owner_user_email",
            "close_reason",
            "locality",
            "community",
            "area_of_influence",
            "local_conflict",
            "key_theme",
            "origin_incident"
        )
    val ASSESSMENT_FIELDS: List<String> =
        listOf("assessment_date", "probability", "impact", "score", "risk_level", "residual", "trend", "notes", "assessor_user_email")
    val MEASURE_FIELDS: List<String> = listOf("description", "owner_user_email", "planned_date", "completed_date", "completed")
    val IMPACT_FIELDS: List<String> = listOf("activity_type", "description", "impact_type", "impact_nature", "impact_sign")

    // el objeto y el campo de cada vínculo que la matriz nombra
    val LINKED: Map<String, Pair<String, String>> =
        mapOf(
            "locality" to ("locality" to "name"),
            "community" to ("community" to "name"),
            "area_of_influence" to ("area_of_influence" to "area_type"),
            "local_conflict" to ("local_conflict" to "title"),
            "key_theme" to ("key_theme" to "name"),
            "origin_incident" to ("incident" to "code")
        )

    val REQUIRES: List<String> =
        RISK_FIELDS.map { "$RISK.$it" } + ASSESSMENT_FIELDS.map { "$ASSESSMENT.$it" } + MEASURE_FIELDS.map { "$MEASURE.$it" } +
            IMPACT_FIELDS.map { "$IMPACT.$it" } + LINKED.values.map { (o, f) -> "$o.$f" } +
            listOf("organization.name", "person.given_names", "person.surnames")

    // si `path` (objeto.campo) sale en el nivel
    fun allowed(
        path: String,
        disclosure: Disclosure
    ): Boolean {
        if (disclosure == Disclosure.INTERNO) return true
        val objectName = path.substringBefore('.')
        val field = path.substringAfter('.')
        return field !in FREE_TEXT[objectName].orEmpty() && field !in EMAILS
    }

    private fun cell(
        context: RiskContext,
        objectName: String,
        attributes: Map<String, Any?>,
        field: String
    ): Any? {
        if (!context.canRead("$objectName.$field")) return NOT_AVAILABLE
        val value = attributes[field]
        return when {
            value is Boolean -> if (value) "Sí" else "No"
            value is Number -> Values.decimal(value)
            else -> Values.text(value).orEmpty()
        }
    }

    // «Matriz»: un riesgo por fila. la situación de revisión solo en los estados que la calculan; los vínculos por su nombre; en
    // EXTERNO, las medidas contadas en lugar de su hoja
    fun matrix(
        lines: List<RiskLine>,
        context: RiskContext,
        disclosure: Disclosure
    ): XlsxSheet {
        require(disclosure != Disclosure.PUBLICO) { "PUBLICO no lleva la matriz: solo agregados" }
        val internal = disclosure == Disclosure.INTERNO
        val columns =
            listOf(
                "code" to "Código",
                "title" to "Título",
                "risk_source" to "Fuente",
                "category" to "Categoría",
                "impact_type" to "Tipo de impacto",
                "impact_nature" to "Naturaleza del impacto",
                "probability" to "Probabilidad",
                "impact" to "Impacto",
                "score" to "Puntaje",
                "risk_level" to "Nivel",
                "treatment_decision" to "Decisión de tratamiento",
                "treatment_rationale" to "Razón de la decisión",
                "current_controls" to "Acciones actuales",
                "controls_adequate" to "Acciones adecuadas",
                "monitoring_sources" to "Fuentes de monitoreo",
                "residual_probability" to "Probabilidad residual",
                "residual_impact" to "Impacto residual",
                "residual_score" to "Puntaje residual",
                "residual_level" to "Nivel residual",
                "next_review_date" to "Próxima revisión",
                "review_status" to "Situación de revisión",
                "owner_user_email" to "Responsable",
                "locality" to "Localidad",
                "community" to "Comunidad",
                "area_of_influence" to "Área de influencia",
                "local_conflict" to "Conflicto local",
                "key_theme" to "Tema clave",
                "origin_incident" to "Incidente de origen",
                "close_reason" to "Motivo de cierre"
            ).filter { allowed("$RISK.${it.first}", disclosure) }
        val header =
            listOf("Estado") + columns.map { it.second } + listOf("Tendencia", "Última revisión", "Grupos de interés") +
                if (internal) emptyList() else listOf("Medidas", "Medidas completadas", "Medidas atrasadas")
        val rows =
            lines.asSequence().map { line ->
                val values =
                    columns.map { (field, _) ->
                        when {
                            field in LINKED -> linked(line, field, context)
                            field == "review_status" && line.state !in REVIEWED -> ""
                            else -> cell(context, RISK, line.attributes, field)
                        }
                    }
                val stakeholders: Any =
                    line.stakeholders?.joinToString("; ") { (id, name) -> if (internal && name != null) name else RegisterSheets.code(id) } ?: NOT_AVAILABLE
                val counts =
                    if (internal) {
                        emptyList()
                    } else {
                        line.measures?.let { listOf(it.total, it.completed, it.late) } ?: listOf(NOT_AVAILABLE, NOT_AVAILABLE, NOT_AVAILABLE)
                    }
                listOf<Any?>(line.state.orEmpty()) + values + listOf(line.lastTrend.orEmpty(), line.lastDate.orEmpty(), stakeholders) + counts
            }
        return XlsxSheet("Matriz", header, rows)
    }

    private fun linked(
        line: RiskLine,
        field: String,
        context: RiskContext
    ): Any {
        val (objectName, nameField) = LINKED.getValue(field)
        if (!context.canRead("$RISK.$field") || !context.canRead("$objectName.$nameField")) return NOT_AVAILABLE
        val id = Values.text(line.attributes[field]) ?: return ""
        return context.names[id] ?: NOT_AVAILABLE
    }

    // «Revisiones»: todas, de cada riesgo por fecha, con quien revisó en INTERNO
    fun assessments(
        rows: List<Map<String, Any?>>,
        context: RiskContext,
        disclosure: Disclosure
    ): XlsxSheet {
        val fields = ASSESSMENT_FIELDS.filter { allowed("$ASSESSMENT.$it", disclosure) }
        val labels =
            mapOf(
                "assessment_date" to "Fecha",
                "probability" to "Probabilidad",
                "impact" to "Impacto",
                "score" to "Puntaje",
                "risk_level" to "Nivel",
                "residual" to "Residual",
                "trend" to "Tendencia",
                "notes" to "Notas",
                "assessor_user_email" to "Revisó"
            )
        return XlsxSheet(
            "Revisiones",
            listOf("Riesgo") + fields.map { labels.getValue(it) },
            rows.asSequence().map { a -> listOf<Any?>(riskCode(a, context)) + fields.map { cell(context, ASSESSMENT, a, it) } }
        )
    }

    // «Medidas» (solo INTERNO)
    fun measures(
        rows: List<Map<String, Any?>>,
        context: RiskContext
    ): XlsxSheet {
        val labels = listOf("Descripción", "Responsable", "Fecha planificada", "Fecha de cumplimiento", "Completada")
        return XlsxSheet(
            "Medidas",
            listOf("Riesgo") + labels,
            rows.asSequence().map { m -> listOf<Any?>(riskCode(m, context)) + MEASURE_FIELDS.map { cell(context, MEASURE, m, it) } }
        )
    }

    // «Impactos»: en EXTERNO, actividad, tipo, naturaleza, signo y riesgo (sin la descripción)
    fun impacts(
        rows: List<Map<String, Any?>>,
        context: RiskContext,
        disclosure: Disclosure
    ): XlsxSheet {
        val fields = IMPACT_FIELDS.filter { allowed("$IMPACT.$it", disclosure) }
        val labels =
            mapOf(
                "activity_type" to "Actividad",
                "description" to "Descripción",
                "impact_type" to "Tipo",
                "impact_nature" to "Naturaleza",
                "impact_sign" to "Signo"
            )
        return XlsxSheet(
            "Impactos",
            listOf("Riesgo") + fields.map { labels.getValue(it) },
            rows.asSequence().map { i -> listOf<Any?>(riskCode(i, context)) + fields.map { cell(context, IMPACT, i, it) } }
        )
    }

    private fun riskCode(
        attributes: Map<String, Any?>,
        context: RiskContext
    ): String {
        val id = Values.text(attributes[RISK]) ?: return ""
        return if (context.canRead("$RISK.code")) context.codes[id] ?: "" else NOT_AVAILABLE
    }

    // PUBLICO: riesgos activos por nivel y categoría, y por decisión de tratamiento (con «sin decidir») y nivel, sobre la grilla
    // completa con sus ceros y SmallCells en cada tabla; sin totales. un catálogo vacío rechaza la salida
    fun aggregates(
        active: List<Map<String, Any?>>,
        readable: Set<String>,
        categories: List<String>,
        treatments: List<String>
    ): List<XlsxSheet> {
        if (categories.isEmpty() || treatments.isEmpty()) {
            throw Finding(
                "CATALOGUE_UNAVAILABLE",
                "Un catálogo de los riesgos no está disponible: una salida PUBLICO no puede armar su grilla",
                "category",
                status = HttpStatus.CONFLICT
            ).toCodedException()
        }

        fun value(
            attributes: Map<String, Any?>,
            field: String,
            known: List<String>,
            empty: String
        ): String {
            if ("$RISK.$field" !in readable) return NOT_AVAILABLE
            val found = Values.text(attributes[field]) ?: return empty
            return if (found in known) found else NOT_AVAILABLE
        }
        val levelAxis = LEVELS + NO_LEVEL + NOT_AVAILABLE
        val categoryAxis = categories + NO_DATA + NOT_AVAILABLE
        val treatmentAxis = treatments + UNDECIDED + NOT_AVAILABLE
        val byCategory = active.groupingBy { value(it, "risk_level", LEVELS, NO_LEVEL) to value(it, "category", categories, NO_DATA) }.eachCount()
        val byTreatment = active.groupingBy { value(it, "treatment_decision", treatments, UNDECIDED) to value(it, "risk_level", LEVELS, NO_LEVEL) }.eachCount()
        val first = SmallCells.apply(levelAxis.flatMap { l -> categoryAxis.map { c -> (l to c) to (byCategory[l to c] ?: 0) } })
        val second = SmallCells.apply(treatmentAxis.flatMap { t -> levelAxis.map { l -> (t to l) to (byTreatment[t to l] ?: 0) } })
        return listOf(
            XlsxSheet("Por nivel y categoría", listOf("Nivel", "Categoría", "Riesgos"), first.asSequence().map { (k, n) -> listOf(k.first, k.second, n) }),
            XlsxSheet(
                "Por tratamiento y nivel",
                listOf("Decisión de tratamiento", "Nivel", "Riesgos"),
                second.asSequence().map { (k, n) ->
                    listOf(k.first, k.second, n)
                }
            )
        )
    }
}
