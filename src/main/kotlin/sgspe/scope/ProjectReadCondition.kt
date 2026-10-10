package sgspe.scope

import java.time.LocalDate
import java.util.UUID

// la tabla física de project_assignment del tenant y sus columnas, ya entrecomilladas (WasichaiSchemas.dataTable y
// SqlIdentifier.quote): lo que arma «mis proyectos»
data class AssignmentColumns(
    val table: String,
    val project: String,
    val member: String,
    val active: String,
    val startDate: String,
    val endDate: String
)

// la tabla física del padre de un hijo y su columna `project`, ya entrecomilladas
data class ParentColumns(
    val table: String,
    val project: String
)

// lo que la condición necesita del objeto leído, ya entrecomillado. `scope`: su columna de alcance (`id` en project,
// `project` en las raíces y en project_assignment, la del padre en los hijos); `optional`: si esa relación es opcional en el
// modelo (ModelCatalog.isRequiredRelation); `member`: member_user_id, solo en project_assignment; `parent`: solo en los
// hijos. null en una columna: la definición no la trae, y la condición falla cerrada
data class ReadColumns(
    val scope: String?,
    val assignments: AssignmentColumns?,
    val optional: Boolean = false,
    val member: String? = null,
    val parent: ParentColumns? = null
)

// lo que decide qué registros de proyecto lee quien llama, puro (security.md §9.7, ASM-106): SQL sobre la tabla de
// project_assignment, sin lecturas desde Kotlin (leerla con Records dentro del alcance lo dispararía sobre sí mismo). la
// columna del objeto leído va sin alias, porque el SQL del store de wasichai no usa alias; las subconsultas califican siempre
// las suyas. los valores van siempre enlazados (bind), nunca concatenados. solo ProjectReadScope la usa (ArchitectureTest)
object ProjectReadCondition {
    // la condición que no admite ningún registro: falla cerrada
    const val NOTHING = "false"

    // la columna física del id de un registro de wasichai
    const val ID_COLUMN = "\"id\""

    // si `columns` trae todo lo que la condición del grupo necesita
    fun complete(
        group: ReadGroup,
        columns: ReadColumns
    ): Boolean =
        when (group) {
            ReadGroup.TENANT -> true
            ReadGroup.UNCLASSIFIED -> false
            ReadGroup.PROJECT -> columns.assignments != null
            ReadGroup.ASSIGNMENT -> columns.assignments != null && columns.scope != null && columns.member != null
            ReadGroup.ROOT -> columns.assignments != null && columns.scope != null
            ReadGroup.CHILD -> columns.assignments != null && columns.scope != null && columns.parent != null
        }

    // null: no restringe (un maestro del tenant). NOTHING: un objeto sin clasificar o sin las columnas. si no, la condición
    // del grupo: el proyecto entre mis proyectos; mis asignaciones y las de mis equipos; la raíz de mis proyectos; el hijo
    // cuyo padre es de mis proyectos; con la relación opcional, también el registro sin ella (del tenant, §9.2 punto 4)
    fun condition(
        group: ReadGroup,
        columns: ReadColumns,
        organizationId: UUID,
        user: UUID,
        today: LocalDate,
        bind: (Any) -> String
    ): String? {
        if (group == ReadGroup.TENANT) return null
        if (!complete(group, columns)) return NOTHING
        val assignments = columns.assignments!!
        val mine = { mine(assignments, organizationId, user, today, bind) }
        return when (group) {
            ReadGroup.PROJECT -> "$ID_COLUMN IN (${mine()})"
            ReadGroup.ASSIGNMENT -> "${columns.member} = ${bind(user)} OR ${columns.scope} IN (${mine()})"
            ReadGroup.ROOT -> orUnset(columns, "${columns.scope} IN (${mine()})")
            ReadGroup.CHILD -> {
                val parent = columns.parent!!
                orUnset(
                    columns,
                    "${columns.scope} IN (SELECT t.$ID_COLUMN FROM ${parent.table} t WHERE t.organization_id = ${bind(organizationId)} " +
                        "AND t.${parent.project} IN (${mine()}))"
                )
            }
            else -> NOTHING
        }
    }

    // «mis proyectos»: los de las asignaciones vigentes de quien llama, con la vigencia de Validity.inForce (active y, si tiene
    // fechas, hoy en Lima entre ellas, con los extremos incluidos)
    private fun mine(
        a: AssignmentColumns,
        organizationId: UUID,
        user: UUID,
        today: LocalDate,
        bind: (Any) -> String
    ): String =
        "SELECT a.${a.project} FROM ${a.table} a WHERE a.organization_id = ${bind(organizationId)} AND a.${a.member} = ${bind(user)} " +
            "AND a.${a.active} IS TRUE AND (a.${a.startDate} IS NULL OR a.${a.startDate} <= ${bind(today)}) " +
            "AND (a.${a.endDate} IS NULL OR a.${a.endDate} >= ${bind(today)})"

    private fun orUnset(
        columns: ReadColumns,
        condition: String
    ): String = if (columns.optional) "${columns.scope} IS NULL OR $condition" else condition
}
