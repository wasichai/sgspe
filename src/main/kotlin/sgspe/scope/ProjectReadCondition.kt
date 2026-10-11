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

// un salto de la cadena de un hijo hasta su raíz: la tabla física del objeto padre y su columna hacia el salto siguiente (en
// la raíz, `project`), ya entrecomilladas. null en `column`: la definición no la trae, y la condición falla cerrada
data class Hop(
    val table: String,
    val column: String?
)

// un padre posible de un hijo (ProjectScope.CHILDREN): la columna del hijo que lo nombra, ya entrecomillada, y los saltos
// hasta la columna `project` de la raíz (uno en el hijo de una raíz, dos en un nieto como program_measurement)
data class ParentPath(
    val column: String?,
    val hops: List<Hop>
) {
    val complete: Boolean get() = column != null && hops.isNotEmpty() && hops.all { it.column != null }
}

// lo que la condición necesita del objeto leído, ya entrecomillado. `scope`: su columna de alcance (`id` en project,
// `project` en las raíces y en project_assignment; los hijos usan `parents`); `optional`: si esa relación es opcional en el
// modelo (ModelCatalog.isRequiredRelation), en una raíz o en un hijo de un único padre; `member`: member_user_id, solo en
// project_assignment; `parents`: solo en los hijos, uno por padre posible. null en una columna: la definición no la trae, y
// la condición falla cerrada
data class ReadColumns(
    val scope: String?,
    val assignments: AssignmentColumns?,
    val optional: Boolean = false,
    val member: String? = null,
    val parents: List<ParentPath> = emptyList()
)

// lo que decide qué registros de proyecto lee quien llama, puro (security.md §9.7, ASM-106, ASM-114): SQL sobre la tabla de
// project_assignment, sin lecturas desde Kotlin (leerla con Records dentro del alcance lo dispararía sobre sí mismo). la
// columna del objeto leído va sin alias, porque el SQL del store de wasichai no usa alias; las subconsultas califican siempre
// las suyas, con un alias por salto. los valores van siempre enlazados (bind), nunca concatenados. solo ProjectReadScope la
// usa (ArchitectureTest)
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
            // todos sus padres completos; la forma «sin padre, del tenant» solo vale con un único padre
            ReadGroup.CHILD ->
                columns.assignments != null && columns.parents.isNotEmpty() && columns.parents.all { it.complete } &&
                    (!columns.optional || columns.parents.size == 1)
        }

    // null: no restringe (un maestro del tenant). NOTHING: un objeto sin clasificar o sin las columnas. si no, la condición
    // del grupo: el proyecto entre mis proyectos; mis asignaciones y las de mis equipos; la raíz de mis proyectos; el hijo
    // cuyo padre presente llega a mis proyectos por su cadena (con varios padres, la disyunción: un registro sin ninguno no
    // cumple ningún término); con la relación opcional de una raíz o de un único padre, también el registro sin ella (del
    // tenant, §9.2 punto 4)
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
                val terms = columns.parents.map { path -> "${path.column} IN (${parentIds(path.hops, 1, organizationId, bind, mine)})" }
                val any = if (terms.size == 1) terms.single() else terms.joinToString(" OR ") { "($it)" }
                if (columns.optional) "${columns.parents.single().column} IS NULL OR $any" else any
            }
            else -> NOTHING
        }
    }

    // los ids de los registros del salto `depth` (alias t<depth>) cuya columna hacia el salto siguiente (en la raíz, `project`)
    // está en la subconsulta de ese salto (en la raíz, «mis proyectos»): una subconsulta anidada por salto. la organización se
    // enlaza antes que lo de adentro, así los parámetros quedan en el orden en que se leen
    private fun parentIds(
        hops: List<Hop>,
        depth: Int,
        organizationId: UUID,
        bind: (Any) -> String,
        mine: () -> String
    ): String {
        val hop = hops.first()
        val alias = "t$depth"
        val organization = bind(organizationId)
        val inner = if (hops.size == 1) mine() else parentIds(hops.drop(1), depth + 1, organizationId, bind, mine)
        return "SELECT $alias.$ID_COLUMN FROM ${hop.table} $alias WHERE $alias.organization_id = $organization AND $alias.${hop.column} IN ($inner)"
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
