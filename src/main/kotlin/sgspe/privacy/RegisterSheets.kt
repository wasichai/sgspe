package sgspe.privacy

import org.apache.poi.ss.usermodel.Row
import org.apache.poi.xssf.streaming.SXSSFWorkbook
import sgspe.common.Positions
import sgspe.common.Values
import sgspe.common.safeCell
import java.io.OutputStream
import java.math.BigDecimal

// un grupo de interés como lo leyó quien exporta: solo los atributos que su rol puede leer
class StakeholderLine(
    val id: String,
    val state: String?,
    val attributes: Map<String, Any?>
)

// lo que las hojas necesitan además del grupo de interés, ya leído como quien exporta. `readable` son los objeto.campo que
// puede leer (Readability); los mapas solo traen lo que puede leer, así que un nombre que falta sale «no disponible»
class RegisterContext(
    val readable: Set<String>,
    val projects: Map<String, String>,
    val people: Map<String, String>,
    val organizations: Map<String, String>,
    val localities: Map<String, String>,
    val positions: Map<String, List<String>>
) {
    fun canRead(path: String): Boolean = path in readable
}

// una hoja: su encabezado y sus filas. los valores son texto, números o null
class XlsxSheet(
    val name: String,
    val header: List<String>,
    val rows: Sequence<List<Any?>>
)

// las hojas del registro de grupos de interés según el nivel de divulgación (deliverables.md §9.2 y §10): INTERNO nombra a
// las personas y trae al responsable; EXTERNO las cita por código y cargo y omite al responsable; PUBLICO solo cuenta. nada
// de esto lee datos: recibe lo que quien exporta pudo leer. en ningún nivel hay una columna del documento de identidad, de
// teléfonos ni de correos de personas (security.md §11.4)
object RegisterSheets {
    private const val STAKEHOLDER = "stakeholder"
    private const val UNEVALUATED = "SIN EVALUAR"
    private const val NO_SCOPE = "SIN AMBITO"

    // los campos de los grupos de interés que el registro escribe; por cada uno, su lectura se comprueba antes
    private val FIELDS =
        listOf(
            "stakeholder_type",
            "stakeholder_scope",
            "relationship_status",
            "interview_status",
            "influence_group",
            "interest_index",
            "power_index",
            "stance",
            "information_priority",
            "trust_score",
            "last_assessment_date",
            "last_contact_date",
            "owner_user_email"
        )

    // lo que se comprueba que quien exporta pueda leer, para el contexto: los campos del grupo de interés, los nombres de
    // personas y organizaciones, los cargos, el código del proyecto y el nombre de la localidad
    val REQUIRES: List<String> =
        FIELDS.map { "$STAKEHOLDER.$it" } +
            listOf(
                "person.given_names",
                "person.surnames",
                "organization.name",
                "${Positions.OBJECT}.${Positions.STATUS}",
                "${Positions.OBJECT}.position_title",
                "project.code",
                "locality.name"
            )

    // cómo se cita a un grupo de interés sin su nombre: estable, sin datos personales
    fun code(id: String): String = "GI-${id.take(8).uppercase()}"

    private val HEADER =
        listOf(
            "Proyecto",
            "Código",
            "Grupo de interés",
            "Tipo de actor",
            "Cargos vigentes",
            "Tipo de grupo de interés",
            "Ámbito",
            "Localidad",
            "Estado",
            "Estado de la relación",
            "Estado de entrevista",
            "Grupo de influencia",
            "Índice de interés",
            "Índice de poder",
            "Postura",
            "Prioridad informativa",
            "Confianza",
            "Última evaluación",
            "Último contacto"
        )

    fun register(
        lines: List<StakeholderLine>,
        context: RegisterContext,
        disclosure: Disclosure
    ): List<XlsxSheet> {
        require(disclosure != Disclosure.PUBLICO) { "PUBLICO no lleva el registro: solo agregados" }
        val header = HEADER + if (disclosure == Disclosure.INTERNO) listOf("Responsable") else emptyList()
        return listOf(XlsxSheet("Registro", header, lines.asSequence().map { row(it, context, disclosure) }))
    }

    private fun row(
        line: StakeholderLine,
        context: RegisterContext,
        disclosure: Disclosure
    ): List<Any?> {
        val a = line.attributes
        val personId = Values.text(a["person"])
        val organizationId = Values.text(a["organization"])
        val code = code(line.id)

        fun text(name: String): String = if (context.canRead("$STAKEHOLDER.$name")) Values.text(a[name]).orEmpty() else NOT_AVAILABLE

        fun number(name: String): Any = if (context.canRead("$STAKEHOLDER.$name")) Values.decimal(a[name]) ?: "" else NOT_AVAILABLE

        // la organización se nombra si se puede leer; la persona, por código en EXTERNO y por nombre en INTERNO solo si se
        // puede leer. quien arma el contexto ya no trae lo ilegible; aquí se vuelve a comprobar porque es la frontera de lo
        // que sale en el archivo
        fun named(
            path: String,
            names: Map<String, String>,
            id: String?
        ): String = id?.takeIf { context.canRead(path) }?.let { names[it] } ?: NOT_AVAILABLE

        val name =
            when {
                organizationId != null -> named("organization.name", context.organizations, organizationId)
                disclosure == Disclosure.EXTERNO -> code
                personId != null -> named("person.given_names", context.people, personId)
                else -> NOT_AVAILABLE
            }
        val kind =
            when {
                organizationId != null -> "ORGANIZACION"
                personId != null -> "PERSONA"
                else -> NOT_AVAILABLE
            }
        val values =
            listOf(
                Values
                    .text(a["project"])
                    ?.takeIf { context.canRead("project.code") }
                    ?.let { context.projects[it] }
                    .orEmpty(),
                code,
                name,
                kind,
                personId
                    ?.takeIf { context.canRead("${Positions.OBJECT}.position_title") }
                    ?.let { context.positions[it] }
                    .orEmpty()
                    .joinToString("; "),
                text("stakeholder_type"),
                text("stakeholder_scope"),
                Values
                    .text(a["locality"])
                    ?.takeIf { context.canRead("locality.name") }
                    ?.let { context.localities[it] }
                    .orEmpty(),
                line.state.orEmpty(),
                text("relationship_status"),
                text("interview_status"),
                text("influence_group"),
                number("interest_index"),
                number("power_index"),
                text("stance"),
                text("information_priority"),
                number("trust_score"),
                text("last_assessment_date"),
                text("last_contact_date")
            )
        return if (disclosure == Disclosure.INTERNO) values + text("owner_user_email") else values
    }

    // PUBLICO: solo conteos por grupo de influencia y postura, y por ámbito (deliverables.md §9.1). lo que quien exporta no
    // puede leer se agrupa como «no disponible», nunca como si no hubiera valor
    fun aggregates(
        lines: List<StakeholderLine>,
        context: RegisterContext
    ): List<XlsxSheet> {
        fun value(
            line: StakeholderLine,
            name: String,
            empty: String
        ): String = if (context.canRead("$STAKEHOLDER.$name")) Values.text(line.attributes[name]) ?: empty else NOT_AVAILABLE

        val groups =
            lines
                .groupingBy { value(it, "influence_group", UNEVALUATED) to value(it, "stance", UNEVALUATED) }
                .eachCount()
                .toSortedMap(compareBy({ it.first }, { it.second }))
        val scopes =
            lines
                .groupingBy { value(it, "stakeholder_scope", NO_SCOPE) }
                .eachCount()
                .toSortedMap()
        return listOf(
            XlsxSheet(
                "Agregados",
                listOf("Grupo de influencia", "Postura", "Grupos de interés"),
                groups.entries.asSequence().map { (key, count) -> listOf(key.first, key.second, count) }
            ),
            XlsxSheet("Por ámbito", listOf("Ámbito", "Grupos de interés"), scopes.entries.asSequence().map { (scope, count) -> listOf(scope, count) })
        )
    }
}

// escribe las hojas con POI en modo de flujo (SXSSF): las filas ya escritas salen de la memoria. todo texto pasa por
// safeCell, porque el nombre de una persona o de una organización lo teclea un usuario o viene de un archivo importado y una
// hoja de cálculo lo tomaría por fórmula (security.md §11.4, RQ-SEC-020); los números van como números
object Xlsx {
    private const val WINDOW = 100

    fun write(
        sheets: List<XlsxSheet>,
        out: OutputStream
    ) {
        val workbook = SXSSFWorkbook(WINDOW)
        try {
            for (sheet in sheets) {
                val target = workbook.createSheet(sheet.name)
                put(target.createRow(0), sheet.header)
                sheet.rows.forEachIndexed { index, values -> put(target.createRow(index + 1), values) }
            }
            workbook.write(out)
        } finally {
            // los archivos temporales de SXSSF
            workbook.dispose()
            workbook.close()
        }
    }

    private fun put(
        row: Row,
        values: List<Any?>
    ) {
        values.forEachIndexed { column, value ->
            val cell = row.createCell(column)
            when (value) {
                null -> cell.setBlank()
                is BigDecimal -> cell.setCellValue(value.toDouble())
                is Number -> cell.setCellValue(value.toDouble())
                else -> cell.setCellValue(safeCell(value.toString()))
            }
        }
    }
}
