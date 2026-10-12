package sgspe.territory

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

// el recuadro de un mapa en EPSG:4326, ya normalizado por Layers.normalizeBbox ("minX,minY,maxX,maxY"); null si no vino o si
// cubre todo el globo
data class LayerBox(
    val minX: Double,
    val minY: Double,
    val maxX: Double,
    val maxY: Double
) {
    companion object {
        fun of(normalized: String?): LayerBox? {
            val parts = normalized?.split(",")?.map { it.toDouble() } ?: return null
            return LayerBox(parts[0], parts[1], parts[2], parts[3])
        }
    }
}

// un registro ubicado por la compuerta: su id, por dónde se ubicó (PlacedSql.OWN o el nombre de la relación), el id del
// destino y el GeoJSON EPSG:4326 de lo que se dibuja
data class PlacedRow(
    val id: UUID,
    val kind: String,
    val target: UUID?,
    val geometry: String
)

// el SQL de una fuente de registros ubicados (patrón b de gis.md §5.2, por SpatialGate). cada registro sale con su geometría
// propia o, sin ella, con el punto del primer destino de sus relaciones que tenga geometría (gis.md §2.4). un polígono se
// dibuja por un punto sobre su superficie, como las celdas de concentration. filtra por periodo y proyecto en la tabla del
// objeto, recorta por el recuadro lo que se dibuja y corta en `limit` filas ordenadas por id. solo da ids y geometrías: los
// atributos se leen después por Records, como quien llama. los literales sin enlazar ('own' y los nombres de relación) salen
// del catálogo, nunca del cliente. puro
object PlacedSql {
    const val OWN = "own"
    private const val PROJECT = "project"

    // lo que nombra la consulta de una fuente: el objeto como principal (sus geometrías, relaciones, fechas y, si se acota por
    // proyecto, el campo project o los de sus padres), los destinos de sus relaciones y, con proyecto, los padres de un hijo
    fun reads(
        source: PlacedSource,
        projects: List<UUID>,
        period: Period?
    ): List<GateRead> {
        val fields = linkedSetOf<String>()
        source.geometry?.let(fields::add)
        fields += source.relations
        if (period != null) fields += source.period?.fields.orEmpty()
        val parents = ManagementLayerCatalog.parents(source.objectName)
        if (projects.isNotEmpty()) fields += if (parents.isEmpty()) listOf(PROJECT) else parents.map { it.first }
        val reads = mutableListOf(GateRead(source.objectName, fields, principal = true))
        for (relation in source.relations) {
            val target = Placements.BY_RELATION.getValue(relation)
            reads += GateRead(target.objectName, target.geometries.toSet())
        }
        if (projects.isNotEmpty()) parents.forEach { (_, parent) -> reads += GateRead(parent, setOf(PROJECT)) }
        return reads
    }

    fun sql(
        t: GateTables,
        bind: (Any) -> String,
        source: PlacedSource,
        projects: List<UUID>,
        period: Period?,
        box: LayerBox?,
        limit: Int
    ): String {
        val o = source.objectName
        // (tipo, id del destino, geometría en 4326), en el orden en que se prueban
        val candidates = mutableListOf<Triple<String, String, String>>()
        val joins = StringBuilder()
        source.geometry?.let { candidates += Triple(OWN, "NULL::uuid", "ST_Transform(${nonEmpty("e.${t.column(o, it)}")}, 4326)") }
        source.relations.forEachIndexed { i, relation ->
            val target = Placements.BY_RELATION.getValue(relation)
            val n = target.objectName
            val alias = "r$i"
            joins.append(" LEFT JOIN ${t.table(n)} $alias ON $alias.${t.column(n, SpatialGate.ID)} = e.${t.column(o, relation)}")
            val shapes =
                target.points.map { "ST_Transform(${nonEmpty("$alias.${t.column(n, it)}")}, 4326)" } +
                    target.surfaces.map { "ST_PointOnSurface(ST_Transform(${nonEmpty("$alias.${t.column(n, it)}")}, 4326))" }
            candidates += Triple(relation, "$alias.${t.column(n, SpatialGate.ID)}", "COALESCE(${shapes.joinToString(", ")})")
        }
        require(candidates.isNotEmpty()) { "la fuente $o no tiene nada que dibujar" }
        val kind = candidates.joinToString(" ", "CASE ", " END") { (k, _, g) -> "WHEN $g IS NOT NULL THEN '$k'" }
        val target = candidates.joinToString(" ", "CASE ", " END") { (_, id, g) -> "WHEN $g IS NOT NULL THEN $id" }
        val geometry = "COALESCE(${candidates.joinToString(", ") { it.third }})"
        val filters = mutableListOf<String>()
        if (period != null) source.period?.let { filters += periodCondition(t, bind, o, it, period) }
        if (projects.isNotEmpty()) filters += projectCondition(t, bind, o, projects)
        val where = if (filters.isEmpty()) "" else " WHERE ${filters.joinToString(" AND ")}"
        val inner = "SELECT e.${t.column(o, SpatialGate.ID)} AS id, $kind AS kind, $target AS target, $geometry AS g FROM ${t.table(o)} e$joins$where"
        val outer = listOfNotNull("x.g IS NOT NULL", box?.let { envelope("x.g", bind, it) })
        return "SELECT x.id AS id, x.kind AS kind, x.target AS target, ST_AsGeoJSON(x.g, 9) AS geometry FROM ($inner) x " +
            "WHERE ${outer.joinToString(" AND ")} ORDER BY x.id LIMIT ${bind(limit)}"
    }

    // una geometría vacía (EMPTY no es NULL) cuenta como ausente, así que el registro cae al destino siguiente en vez de
    // dibujarse en un punto vacío
    internal fun nonEmpty(column: String): String = "CASE WHEN ST_IsEmpty($column) THEN NULL ELSE $column END"

    // la condición del periodo sobre la fecha de la fuente (decisión 7 del plan)
    internal fun periodCondition(
        t: GateTables,
        bind: (Any) -> String,
        o: String,
        rule: LayerPeriod,
        period: Period
    ): String =
        when (rule) {
            is LayerPeriod.Moment -> {
                val column = "e.${t.column(o, rule.field)}"
                "$column >= ${bind(utc(period.from()))} AND $column < ${bind(utc(period.until()))}"
            }
            is LayerPeriod.Day -> {
                val column = "e.${t.column(o, rule.field)}"
                "$column >= ${bind(period.start)} AND $column <= ${bind(period.end)}"
            }
            is LayerPeriod.Span -> {
                val start = "e.${t.column(o, rule.start)}"
                val end = "e.${t.column(o, rule.end)}"
                "$start <= ${bind(period.end)} AND ($end IS NULL OR $end >= ${bind(period.start)})"
            }
        }

    // una raíz por su campo project; un hijo por cualquiera de sus padres, leídos de la tabla del padre con su alcance
    internal fun projectCondition(
        t: GateTables,
        bind: (Any) -> String,
        o: String,
        projects: List<UUID>
    ): String {
        val parents = ManagementLayerCatalog.parents(o)
        if (parents.isEmpty()) return "e.${t.column(o, PROJECT)} = ANY(${bind(projects.toTypedArray())})"
        return parents.joinToString(" OR ", "(", ")") { (field, parent) ->
            "e.${t.column(o, field)} IN (SELECT p.${t.column(parent, SpatialGate.ID)} FROM ${t.table(parent)} p " +
                "WHERE p.${t.column(parent, PROJECT)} = ANY(${bind(projects.toTypedArray())}))"
        }
    }

    // lo que se dibuja cae en el recuadro (EPSG:4326)
    internal fun envelope(
        geometry: String,
        bind: (Any) -> String,
        box: LayerBox
    ): String = "ST_Intersects($geometry, ST_MakeEnvelope(${bind(box.minX)}, ${bind(box.minY)}, ${bind(box.maxX)}, ${bind(box.maxY)}, 4326))"

    private fun utc(instant: Instant): OffsetDateTime = OffsetDateTime.ofInstant(instant, ZoneOffset.UTC)
}
