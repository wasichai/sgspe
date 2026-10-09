package sgspe.privacy

import org.springframework.http.HttpStatus
import sgspe.common.Finding
import sgspe.common.Values
import sgspe.common.toCodedException

// un compromiso de la matriz como lo leyó quien exporta, con lo que la matriz junta de sus hijos (DOC-01 §9.3.1.2, RF-COM-001)
class MatrixLine(
    val id: String,
    val state: String?,
    val attributes: Map<String, Any?>,
    // los solicitantes (commitment_stakeholder): su id de grupo de interés y su nombre si se puede mostrar
    val requesters: List<Pair<String, String?>>,
    val milestones: List<String>,
    val reschedules: List<String>,
    val actions: Int
)

// una entrega de la matriz, con los tipos de sus evidencias vinculadas
class MatrixDelivery(
    val commitmentCode: String,
    val attributes: Map<String, Any?>,
    val evidenceTypes: List<String>
)

// las hojas de commitment-matrix según el nivel de divulgación (deliverables.md §9 y §10): INTERNO nombra a las organizaciones y
// personas que quien exporta puede leer y trae al responsable y los campos restringidos que puede leer (beneficiaries_detail,
// recipients, conformity_by); EXTERNO cita a los solicitantes por código GI- (seudónimo, ruling P29) y omite al responsable y
// los campos restringidos; PUBLICO solo cuenta, sobre la grilla del catálogo y con SmallCells (ASM-088). en ningún nivel sale el
// documento de identidad de los solicitantes: la matriz no tiene esa columna (RQ-REP-022, CON-014)
object MatrixSheets {
    const val COMMITMENT = "commitment"
    const val DELIVERY = "delivery"
    private const val NO_THEME = "SIN TEMA"
    private const val NO_DATA = "SIN DATO"

    // los campos de commitment y de delivery que la matriz escribe: su lectura se comprueba antes (Readability)
    val COMMITMENT_FIELDS =
        listOf(
            "code",
            "title",
            "description",
            "objective",
            "resource_type",
            "quantity",
            "unit",
            "execution_time",
            "commitment_theme",
            "formality",
            "beneficiaries_count",
            "beneficiaries_female_count",
            "beneficiaries_male_count",
            "beneficiaries_detail",
            "request_medium",
            "agreed_date",
            "due_date",
            "amount",
            "currency",
            "executed_amount",
            "delay_risk_reported",
            "delay_risk_note",
            "deadline_status",
            "closed_date",
            "owner_user_email"
        )
    val DELIVERY_FIELDS = listOf("delivery_date", "place", "delivered_items", "delivered_amount", "conformity_given", "conformity_by", "recipients")

    // restringidos (security.md §7.9): solo en INTERNO y solo si quien exporta los lee
    val RESTRICTED = setOf("$COMMITMENT.beneficiaries_detail", "$DELIVERY.conformity_by", "$DELIVERY.recipients")

    val REQUIRES: List<String> =
        COMMITMENT_FIELDS.map { "$COMMITMENT.$it" } + DELIVERY_FIELDS.map { "$DELIVERY.$it" } +
            listOf("project.code", "organization.name", "person.given_names", "person.surnames", "evidence.evidence_type")

    fun matrix(
        lines: List<MatrixLine>,
        projects: Map<String, String>,
        readable: Set<String>,
        disclosure: Disclosure
    ): XlsxSheet {
        require(disclosure != Disclosure.PUBLICO) { "PUBLICO no lleva la matriz: solo agregados" }
        val internal = disclosure == Disclosure.INTERNO
        val header =
            listOf(
                "Proyecto",
                "Código",
                "Título",
                "Descripción",
                "Objetivo",
                "Tipo de recurso",
                "Cantidad",
                "Unidad",
                "Tiempo de ejecución",
                "Tema",
                "Formalidad",
                "Solicitantes",
                "Beneficiarios",
                "Beneficiarias",
                "Beneficiarios varones"
            ) + (if (internal) listOf("Detalle de beneficiarios") else emptyList()) +
                listOf(
                    "Medio de solicitud",
                    "Fecha de acuerdo",
                    "Hitos",
                    "Vencimiento",
                    "Reprogramaciones",
                    "Monto",
                    "Moneda",
                    "Ejecutado",
                    "Estado",
                    "Acciones",
                    "Riesgo de retraso",
                    "Detalle del riesgo",
                    "Situación de plazo",
                    "Cierre"
                ) + if (internal) listOf("Responsable") else emptyList()
        val rows =
            lines.asSequence().map { line ->
                val a = line.attributes

                fun text(name: String): Any = if ("$COMMITMENT.$name" in readable) Values.text(a[name]).orEmpty() else NOT_AVAILABLE

                fun number(name: String): Any = if ("$COMMITMENT.$name" in readable) Values.decimal(a[name]) ?: "" else NOT_AVAILABLE
                val requesters =
                    line.requesters.joinToString("; ") { (id, name) -> if (internal && name != null) name else RegisterSheets.code(id) }
                listOf(
                    Values
                        .text(a["project"])
                        ?.takeIf { "project.code" in readable }
                        ?.let { projects[it] }
                        .orEmpty(),
                    text("code"),
                    text("title"),
                    text("description"),
                    text("objective"),
                    text("resource_type"),
                    number("quantity"),
                    text("unit"),
                    text("execution_time"),
                    text("commitment_theme"),
                    text("formality"),
                    requesters,
                    number("beneficiaries_count"),
                    number("beneficiaries_female_count"),
                    number("beneficiaries_male_count")
                ) + (if (internal) listOf(text("beneficiaries_detail")) else emptyList()) +
                    listOf(
                        text("request_medium"),
                        text("agreed_date"),
                        line.milestones.joinToString("; "),
                        text("due_date"),
                        line.reschedules.joinToString("; "),
                        number("amount"),
                        text("currency"),
                        number("executed_amount"),
                        line.state.orEmpty(),
                        line.actions,
                        text("delay_risk_reported"),
                        text("delay_risk_note"),
                        text("deadline_status"),
                        text("closed_date")
                    ) + if (internal) listOf(text("owner_user_email")) else emptyList()
            }
        return XlsxSheet("Matriz", header, rows)
    }

    fun deliveries(
        deliveries: List<MatrixDelivery>,
        readable: Set<String>,
        disclosure: Disclosure
    ): XlsxSheet {
        val internal = disclosure == Disclosure.INTERNO
        val header =
            listOf("Compromiso", "Fecha", "Lugar", "Qué se entregó", "Monto entregado", "Conformidad") +
                (if (internal) listOf("Quiénes dieron la conformidad", "Receptores") else emptyList()) +
                listOf("Actas de entrega", "Fotos", "Otras evidencias")
        val rows =
            deliveries.asSequence().map { d ->
                val a = d.attributes

                fun text(name: String): Any = if ("$DELIVERY.$name" in readable) Values.text(a[name]).orEmpty() else NOT_AVAILABLE
                val types = d.evidenceTypes
                listOf(
                    d.commitmentCode,
                    text("delivery_date"),
                    text("place"),
                    text("delivered_items"),
                    if ("$DELIVERY.delivered_amount" in readable) Values.decimal(a["delivered_amount"]) ?: "" else NOT_AVAILABLE,
                    if ("$DELIVERY.conformity_given" in readable) (if (Values.bool(a["conformity_given"]) == true) "Sí" else "No") else NOT_AVAILABLE
                ) + (if (internal) listOf(text("conformity_by"), text("recipients")) else emptyList()) +
                    listOf(types.count { it == "ACTA DE ENTREGA" }, types.count { it == "FOTO" }, types.count { it != "ACTA DE ENTREGA" && it != "FOTO" })
            }
        return XlsxSheet("Entregas", header, rows)
    }

    // PUBLICO: compromisos por estado y tema y por tipo de recurso, sobre la grilla del catálogo (los estados de WF-D) con sus
    // ceros, y con SmallCells en cada tabla; sin totales. un catálogo vacío rechaza la salida (409 CATALOGUE_UNAVAILABLE)
    fun aggregates(
        lines: List<MatrixLine>,
        readable: Set<String>,
        states: List<String>,
        themes: List<String>,
        resourceTypes: List<String>
    ): List<XlsxSheet> {
        fun axis(
            field: String,
            known: List<String>,
            empty: String
        ): List<String> {
            if ("$COMMITMENT.$field" !in readable) return listOf(NOT_AVAILABLE)
            if (known.isEmpty()) {
                throw Finding(
                    "CATALOGUE_UNAVAILABLE",
                    "El catálogo de $field no está disponible: una salida PUBLICO no puede armar su grilla",
                    field,
                    status = HttpStatus.CONFLICT
                ).toCodedException()
            }
            return known + empty + NOT_AVAILABLE
        }

        fun value(
            line: MatrixLine,
            field: String,
            known: List<String>,
            empty: String
        ): String {
            if ("$COMMITMENT.$field" !in readable) return NOT_AVAILABLE
            val found = Values.text(line.attributes[field]) ?: return empty
            return if (found in known) found else NOT_AVAILABLE
        }
        if (states.isEmpty()) {
            throw Finding("CATALOGUE_UNAVAILABLE", "Los estados de WF-D no están disponibles", "state", status = HttpStatus.CONFLICT).toCodedException()
        }
        val themeAxis = axis("commitment_theme", themes, NO_THEME)
        val stateOf = lines.map { if (it.state in states) it.state!! else NOT_AVAILABLE }
        val themeOf = lines.map { value(it, "commitment_theme", themes, NO_THEME) }
        val counts = stateOf.zip(themeOf).groupingBy { it }.eachCount()
        val byState = SmallCells.apply((states + NOT_AVAILABLE).flatMap { s -> themeAxis.map { t -> (s to t) to (counts[s to t] ?: 0) } })
        val resourceAxis = axis("resource_type", resourceTypes, NO_DATA)
        val resourceCounts = lines.map { value(it, "resource_type", resourceTypes, NO_DATA) }.groupingBy { it }.eachCount()
        val byResource = SmallCells.apply(resourceAxis.map { it to (resourceCounts[it] ?: 0) })
        return listOf(
            XlsxSheet(
                "Por estado y tema",
                listOf("Estado", "Tema", "Compromisos"),
                byState.asSequence().map { (key, count) ->
                    listOf(key.first, key.second, count)
                }
            ),
            XlsxSheet("Por tipo de recurso", listOf("Tipo de recurso", "Compromisos"), byResource.asSequence().map { (key, count) -> listOf(key, count) })
        )
    }
}
