package sgspe.territory

import io.r2dbc.spi.Readable
import java.util.UUID

// una fila de un agregado por localidad: la localidad, su nombre y tipo, la geometría que dio su punto (location o boundary),
// el GeoJSON del punto y las cifras de la fila
data class LocalityRow(
    val zone: UUID,
    val name: String?,
    val localityType: String?,
    val field: String,
    val geometry: String,
    val values: Map<String, Any?>
)

// los agregados por localidad de las capas de gestión (patrón b de gis.md §5.2, por SpatialGate): `inner` cuenta o suma en la
// tabla del objeto y agrupa por su localidad (zone), sin nombrar nunca un registro. aquí se le pone el punto de la localidad
// (su ubicación o un punto sobre su límite), se recorta por el recuadro y se cortan las localidades en `limit`, así que las
// cifras de cada localidad quedan enteras. una localidad sin geometría no sale. puro
object LocalityAggregateSql {
    const val STAKEHOLDER = "stakeholder"
    const val STANCE = "stance"

    // una postura vacía (CON-006): cuenta aparte, nunca se pierde
    const val NO_STANCE = "SIN DATO"

    // lo que se lee de la localidad: nombre, tipo y sus dos geometrías
    val LOCALITY_READ = GateRead(TerritoryNames.LOCALITY, setOf("name", "locality_type", "location", "boundary"), principal = true)

    fun wrap(
        t: GateTables,
        bind: (Any) -> String,
        inner: String,
        columns: List<String>,
        box: LayerBox?,
        limit: Int
    ): String {
        val l = TerritoryNames.LOCALITY
        val location = "l.${t.column(l, "location")}"
        val boundary = "l.${t.column(l, "boundary")}"
        val zones =
            "SELECT l.${t.column(l, SpatialGate.ID)} AS zone, l.${t.column(l, "name")}::text AS name, " +
                "l.${t.column(l, "locality_type")}::text AS locality_type, " +
                "CASE WHEN $location IS NOT NULL THEN 'location' ELSE 'boundary' END AS field, " +
                "COALESCE(ST_Transform($location, 4326), ST_PointOnSurface(ST_Transform($boundary, 4326))) AS g " +
                "FROM ${t.table(l)} l WHERE l.${t.column(l, SpatialGate.ID)} IN (SELECT zone FROM k)"
        val filters = listOfNotNull("z.g IS NOT NULL", box?.let { PlacedSql.envelope("z.g", bind, it) })
        return "WITH k AS ($inner), z AS ($zones) " +
            "SELECT y.zone AS zone, y.name AS name, y.locality_type AS locality_type, y.field AS field, ST_AsGeoJSON(y.g, 9) AS geometry, " +
            columns.joinToString(", ") { "k.$it AS $it" } +
            " FROM (SELECT * FROM z WHERE ${filters.joinToString(" AND ")} ORDER BY z.zone LIMIT ${bind(limit)}) y " +
            "JOIN k ON k.zone = y.zone ORDER BY y.zone, k.${columns.first()} NULLS LAST"
    }

    // LYR-17: grupos de interés por localidad y postura; sin `byStance` (quien llama no lee la postura), solo el total
    fun stakeholders(
        t: GateTables,
        bind: (Any) -> String,
        projects: List<UUID>,
        byStance: Boolean,
        box: LayerBox?,
        limit: Int
    ): String {
        val s = STAKEHOLDER
        val locality = "s.${t.column(s, TerritoryNames.LOCALITY)}"
        val stance = if (byStance) "s.${t.column(s, STANCE)}::text" else "NULL::text"
        val project = if (projects.isEmpty()) "" else " AND s.${t.column(s, "project")} = ANY(${bind(projects.toTypedArray())})"
        val inner = "SELECT $locality AS zone, $stance AS stance, count(*) AS n FROM ${t.table(s)} s WHERE $locality IS NOT NULL$project GROUP BY 1, 2"
        return wrap(t, bind, inner, listOf(STANCE, "n"), box, limit)
    }

    fun row(
        readable: Readable,
        values: (Readable) -> Map<String, Any?>
    ): LocalityRow =
        LocalityRow(
            readable.get("zone", UUID::class.java)!!,
            readable.get("name", String::class.java),
            readable.get("locality_type", String::class.java),
            readable.get("field", String::class.java)!!,
            readable.get("geometry", String::class.java)!!,
            values(readable)
        )
}
