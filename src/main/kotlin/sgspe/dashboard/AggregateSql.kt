package sgspe.dashboard

import io.r2dbc.spi.Readable
import sgspe.common.Attention
import sgspe.common.LIMA
import sgspe.territory.GateRead
import sgspe.territory.GateTables
import sgspe.territory.SpatialGate
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.YearMonth
import java.time.ZoneOffset
import java.util.UUID

// una consulta agregada de los KPI por la compuerta de sgspe.territory: lo que nombra (GateRead), su SQL y cómo leer cada
// fila. `key` la distingue en la caché de DashboardData
class AggregateQuery<T : Any>(
    val key: String,
    val reads: List<GateRead>,
    val sql: GateTables.((Any) -> String) -> String,
    val map: (Readable) -> T
)

// un objeto en una consulta agregada: sus campos legibles por quien lee se declaran y salen como columna; los demás salen
// como NULL, lo mismo que hace RecordService al quitarlos de los atributos (nunca un no disponible que el camino de Records
// no daba). el proyecto, la campaña y el responsable se filtran en la base y quien arma la consulta exige que se lean
class AggregateSource(
    val objectName: String,
    val readable: Set<String>,
    val alias: String = "x"
) {
    fun read(): GateRead = GateRead(objectName, readable)

    // la columna del campo o null si no se lee
    fun column(
        t: GateTables,
        field: String
    ): String? = if (field in readable) "$alias.${t.column(objectName, field)}" else null

    fun system(
        t: GateTables,
        field: String
    ): String = "$alias.${t.column(objectName, field)}"
}

// el filtro que comparten las consultas agregadas: los proyectos, los filtros de territorio (todos se cumplen) y, con el ámbito
// AIDS o AIIS, el territorio del ámbito de todos los proyectos (las tiles) y el de cada uno (su fila de byProject, como
// DashboardData.with con ese proyecto solo)
class AggregateFilter(
    val projects: List<UUID>,
    val territories: List<DashboardData.Territory>,
    val scope: ScopeTerritories? = null
)

// el ámbito AIDS o AIIS: el territorio de todos los proyectos (las tiles, DashboardData.scopeTerritory) y el de cada uno (su
// fila de byProject, que antes salía de DashboardData.with con ese proyecto solo)
class ScopeTerritories(
    val all: DashboardData.Territory,
    val byProject: Map<UUID, DashboardData.Territory>
)

// el SQL de los conteos de los KPI del tablero executive (plan 8.2a-bis), puro: cada objeto como la subconsulta de la
// compuerta (organización y alcances de lectura), los proyectos y el territorio en el WHERE y un GROUP BY por proyecto
object AggregateSql {
    const val TILE = "tile"
    const val OWN = "own"

    // el registro cae en el territorio: su localidad entre las del territorio o su comunidad entre las suyas
    // (DashboardData.inTerritory). una columna ilegible no casa, como un atributo ausente en Kotlin; sin localidades ni
    // comunidades, nada casa
    internal fun inTerritory(
        locality: String?,
        community: String?,
        territory: DashboardData.Territory,
        bind: (Any) -> String
    ): String {
        val terms =
            listOfNotNull(
                locality?.takeIf { territory.localities.isNotEmpty() }?.let { "$it = ANY(${bind(uuids(territory.localities))})" },
                community?.takeIf { territory.communities.isNotEmpty() }?.let { "$it = ANY(${bind(uuids(territory.communities))})" }
            )
        return if (terms.isEmpty()) "FALSE" else "COALESCE(${terms.joinToString(" OR ")}, FALSE)"
    }

    private fun uuids(ids: Set<String>): Array<UUID> = ids.map(UUID::fromString).sorted().toTypedArray()

    // el WHERE común y las expresiones de tile y own
    internal class Where(
        val condition: String,
        val tile: String,
        val own: String
    )

    internal fun where(
        t: GateTables,
        bind: (Any) -> String,
        source: AggregateSource,
        filter: AggregateFilter
    ): Where {
        val project = source.column(t, "project") ?: error("${source.objectName}.project se exige legible antes de armar la consulta")
        val locality = source.column(t, "locality")
        val community = source.column(t, "community")
        val territories = filter.territories.map { inTerritory(locality, community, it, bind) }
        val scope = filter.scope
        val tile = scope?.let { inTerritory(locality, community, it.all, bind) } ?: "TRUE"
        val own =
            scope?.let { s ->
                s.byProject.entries
                    .sortedBy { it.key }
                    .joinToString(" OR ") { (p, territory) -> "($project = ${bind(p)} AND ${inTerritory(locality, community, territory, bind)})" }
                    .ifEmpty { "FALSE" }
            } ?: "TRUE"
        val terms = listOf("$project = ANY(${bind(filter.projects.toTypedArray())})") + territories + "(($tile) OR ($own))"
        return Where(terms.joinToString(" AND "), tile, own)
    }

    private fun flag(
        row: Readable,
        name: String
    ): Boolean = row.get(name) as Boolean? == true

    private fun count(row: Readable): Int = (row.get("n") as Number).toInt()

    private fun instant(at: Instant): OffsetDateTime = OffsetDateTime.ofInstant(at, ZoneOffset.UTC)

    private fun lima(day: LocalDate): OffsetDateTime = instant(day.atStartOfDay(LIMA).toInstant())

    // KPI-GRV-02 y las quejas de KPI-EXE-03: las QCS de los tres objetos, con la regla de conteo de §3.3 en la base. `months`
    // son los 12 meses de la serie (EngagementKpis.months); fuera de ellos el mes sale nulo
    fun attention(
        sources: Map<String, AggregateSource>,
        filter: AggregateFilter,
        months: List<YearMonth>,
        excludeConfidential: Boolean
    ): AggregateQuery<AttentionCount> {
        val pointers = Attention.OBJECTS.associateWith { "from_$it" }
        val sql: GateTables.((Any) -> String) -> String = { bind ->
            val branches =
                Attention.OBJECTS.map { type ->
                    val s = sources.getValue(type)
                    val w = where(this, bind, s, filter)
                    val links = Attention.RECLASSIFIED_FROM.getValue(type)
                    val from =
                        Attention.OBJECTS.joinToString(", ") { source ->
                            val field = links.entries.firstOrNull { it.value == source }?.key
                            (field?.let { s.column(this, it) } ?: "CAST(NULL AS uuid)") + " AS ${pointers.getValue(source)}"
                        }
                    val received = s.column(this, Attention.RECEIVED_AT)
                    val closedDate = s.column(this, "closed_date")
                    val state = s.system(this, SpatialGate.STATE)
                    val receivedMonth =
                        if (type == Attention.GRIEVANCE && received != null) {
                            "CASE WHEN $received >= ${bind(
                                lima(months.first().atDay(1))
                            )} AND $received < ${bind(lima(months.last().plusMonths(1).atDay(1)))} " +
                                "THEN to_char($received AT TIME ZONE ${bind(LIMA.id)}, 'YYYY-MM') END"
                        } else {
                            "CAST(NULL AS text)"
                        }
                    val closedMonth =
                        if (type == Attention.GRIEVANCE && closedDate != null) {
                            "CASE WHEN $state = ${bind(AttentionKpis.CLOSED)} AND $closedDate >= ${bind(months.first().atDay(1))} " +
                                "AND $closedDate <= ${bind(months.last().atEndOfMonth())} THEN to_char($closedDate, 'YYYY-MM') END"
                        } else {
                            "CAST(NULL AS text)"
                        }
                    val confidential =
                        s
                            .column(this, Attention.CONFIDENTIAL)
                            ?.takeIf { excludeConfidential }
                            ?.let { " AND $it IS NOT TRUE" }
                            .orEmpty()
                    "SELECT CAST(${bind(type)} AS text) AS type, ${s.system(this, SpatialGate.ID)} AS id, ${s.column(this, "project")} AS project, " +
                        "CAST($state AS text) AS state, ${w.tile} AS $TILE, ${w.own} AS $OWN, $from, $receivedMonth AS received, $closedMonth AS closed " +
                        "FROM ${table(s.objectName)} ${s.alias} WHERE ${w.condition}$confidential"
                }
            val pointed =
                Attention.OBJECTS.joinToString(" UNION ALL ") { source ->
                    val column = pointers.getValue(source)
                    "SELECT CAST(${bind(source)} AS text) AS type, $column AS id, project, $TILE, $OWN FROM q WHERE $column IS NOT NULL"
                }
            // los originales apuntados desde la lista de las tiles (g) y desde la de su mismo proyecto (o), sin repetir, por
            // hash join: nunca una subconsulta por fila
            "WITH q AS (${branches.joinToString(" UNION ALL ")}), pointed AS ($pointed), marked AS (" +
                "SELECT q.project, q.type, q.state, q.$TILE, q.$OWN, g.id IS NOT NULL AS gone, o.id IS NOT NULL AS gone_own, q.received, q.closed " +
                "FROM q LEFT JOIN (SELECT DISTINCT type, id FROM pointed WHERE $TILE) g ON g.type = q.type AND g.id = q.id " +
                "LEFT JOIN (SELECT DISTINCT type, id, project FROM pointed WHERE $OWN) o ON o.type = q.type AND o.id = q.id AND o.project = q.project) " +
                "SELECT project, type, state, $TILE, $OWN, gone, gone_own, received, closed, count(*) AS n FROM marked " +
                "GROUP BY project, type, state, $TILE, $OWN, gone, gone_own, received, closed"
        }
        return AggregateQuery(
            "attention:$excludeConfidential",
            Attention.OBJECTS.map { sources.getValue(it).read() },
            sql
        ) { row ->
            AttentionCount(
                row.get("project", UUID::class.java)!!.toString(),
                row.get("type", String::class.java)!!,
                row.get("state", String::class.java),
                flag(row, TILE),
                flag(row, OWN),
                flag(row, "gone"),
                flag(row, "gone_own"),
                row.get("received", String::class.java)?.let(YearMonth::parse),
                row.get("closed", String::class.java)?.let(YearMonth::parse),
                count(row)
            )
        }
    }

    // KPI-ENG-01 y la serie de KPI-EXE-03: las interacciones realizadas (EngagementWriteGuard.HELD_STATES) con held_at entre
    // el primer día del periodo o de la serie (el que vaya antes) y el fin del periodo, por proyecto, tipo, mes de Lima y si
    // caen en el periodo
    fun held(
        source: AggregateSource,
        filter: AggregateFilter,
        states: Set<String>,
        periodStart: LocalDate,
        periodEnd: LocalDate,
        windowStart: LocalDate
    ): AggregateQuery<HeldCount> {
        val sql: GateTables.((Any) -> String) -> String = { bind ->
            val w = where(this, bind, source, filter)
            val heldAt = source.column(this, "held_at") ?: error("engagement.held_at se exige legible")
            val until = bind(lima(periodEnd.plusDays(1)))
            "SELECT ${source.column(this, "project")} AS project, CAST(${source.column(this, "engagement_type") ?: "NULL"} AS text) AS type, " +
                "to_char($heldAt AT TIME ZONE ${bind(LIMA.id)}, 'YYYY-MM') AS month, " +
                "($heldAt >= ${bind(lima(periodStart))} AND $heldAt < $until) AS in_period, ${w.tile} AS $TILE, ${w.own} AS $OWN, count(*) AS n " +
                "FROM ${table(source.objectName)} ${source.alias} WHERE ${w.condition} " +
                "AND ${source.system(this, SpatialGate.STATE)} = ANY(${bind(states.sorted().toTypedArray())}) " +
                "AND $heldAt >= ${bind(lima(minOf(periodStart, windowStart)))} AND $heldAt < $until " +
                "GROUP BY 1, 2, 3, 4, 5, 6"
        }
        return AggregateQuery("held:$periodStart:$periodEnd", listOf(source.read()), sql) { row ->
            HeldCount(
                row.get("project", UUID::class.java)!!.toString(),
                row.get("type", String::class.java),
                YearMonth.parse(row.get("month", String::class.java)!!),
                flag(row, "in_period"),
                flag(row, TILE),
                flag(row, OWN),
                count(row)
            )
        }
    }

    // un conteo por proyecto y por `keys` (campos del objeto, legibles o NULL, como texto salvo los DATE) de los registros que
    // pasan el filtro y `conditions`
    fun grouped(
        key: String,
        source: AggregateSource,
        filter: AggregateFilter,
        keys: List<Pair<String, String>>,
        conditions: GateTables.((Any) -> String) -> List<String>
    ): AggregateQuery<GroupCount> {
        val sql: GateTables.((Any) -> String) -> String = { bind ->
            val w = where(this, bind, source, filter)
            val columns =
                keys.mapIndexed { i, (field, type) ->
                    val column = if (field == SpatialGate.STATE) source.system(this, field) else source.column(this, field) ?: "NULL"
                    "CAST($column AS $type) AS k$i"
                }
            val extra = conditions(bind).joinToString("") { " AND $it" }
            val groups = (1..keys.size + 3).joinToString(", ")
            "SELECT ${source.column(
                this,
                "project"
            )} AS project, ${(columns + listOf("${w.tile} AS $TILE", "${w.own} AS $OWN")).joinToString(", ")}, count(*) AS n " +
                "FROM ${table(source.objectName)} ${source.alias} WHERE ${w.condition}$extra GROUP BY $groups"
        }
        return AggregateQuery(key, listOf(source.read()), sql) { row ->
            GroupCount(
                row.get("project", UUID::class.java)!!.toString(),
                keys.indices.map { row.get("k$it") },
                flag(row, TILE),
                flag(row, OWN),
                count(row)
            )
        }
    }
}
