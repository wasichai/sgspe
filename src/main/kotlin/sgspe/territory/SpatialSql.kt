package sgspe.territory

// los objetos que nombran las consultas espaciales
object TerritoryNames {
    const val PROJECT = "project"
    const val COMPONENT = "project_component"
    const val PARCEL = "land_parcel"
    const val RIGHT = "land_right"
    const val COMMUNITY = "community"
    const val SECTOR = "community_sector"
    const val LOCALITY = "locality"
    const val UNIT = "administrative_unit"
    const val AREA = "area_of_influence"
    const val MEMBER = "area_of_influence_member"
    const val FEATURE = "environmental_feature"
    const val CONCESSION = "mining_concession"

    // las geometrías de un componente, en el orden en que una cuenta toma la primera que tenga: la huella de una plataforma, el
    // trazo de un acceso, el punto de un sondaje
    val COMPONENT_GEOMETRIES = listOf("footprint", "trace", "location")
}

// los fragmentos de SQL de las medidas (gis.md §3.3 y §3.4): las geometrías se guardan en el SRID de su campo y se miden en un
// CRS UTM: el working_crs del proyecto o, en los registros del tenant, la zona UTM del centroide (ASM-064). `srid` es el
// parámetro ya enlazado (":g3"); las expresiones son columnas ya entrecomilladas de GateTables
object SpatialSql {
    // grados del margen del prefiltro en 4326: un grado de longitud mide al menos unos 105 km en el Perú (hasta 18.4° S), así
    // que contar 100 km por grado deja la envolvente un poco más grande de lo necesario, nunca más chica
    const val METERS_PER_DEGREE = 100_000.0

    fun degrees(meters: Number): Double = meters.toDouble() / METERS_PER_DEGREE

    // la primera geometría con valor, todas llevadas a `srid` (del campo con el que se cruza)
    fun firstOf(
        geometries: List<String>,
        srid: Int
    ): String = "COALESCE(${geometries.joinToString(", ") { "ST_Transform($it, $srid)" }})"

    // la longitud en metros de la parte de `a` dentro de `b`
    fun lengthM(
        a: String,
        b: String,
        srid: String
    ): String = "round(ST_Length(ST_Intersection(ST_Transform($a, $srid), ST_Transform($b, $srid)))::numeric, 2)"

    // el área en ha de la parte de `a` dentro de `b`, con cuatro decimales (una plataforma ocupa centésimas de hectárea)
    fun areaHa(
        a: String,
        b: String,
        srid: String
    ): String = "round((ST_Area(ST_Intersection(ST_Transform($a, $srid), ST_Transform($b, $srid))) / 10000)::numeric, 4)"

    fun distanceM(
        a: String,
        b: String,
        srid: String
    ): String = "round(ST_Distance(ST_Transform($a, $srid), ST_Transform($b, $srid))::numeric, 2)"

    // la zona UTM sur del centroide (gis.md §3.3): 32700 + zona; null al norte del ecuador
    fun centroidZone(geometry: String): String {
        val centroid = "ST_Centroid(ST_Transform($geometry, 4326))"
        return "(CASE WHEN ST_Y($centroid) < 0 THEN 32700 + floor((ST_X($centroid) + 180) / 6)::int + 1 END)"
    }

    // el área plana en ha de un polígono del tenant en la zona de su centroide, con dos decimales; null para puntos y líneas o
    // fuera de 17S a 19S (no se calcula con una zona inesperada)
    fun zoneAreaHa(geometry: String): String {
        val zone = centroidZone(geometry)
        return "(CASE WHEN ST_Dimension($geometry) = 2 AND $zone IN (32717, 32718, 32719) " +
            "THEN round((ST_Area(ST_Transform($geometry, $zone)) / 10000)::numeric, 2) END)"
    }
}

// lo que junta una consulta de una o varias partes de la compuerta: las filas legibles y lo que faltó leer. la respuesta
// dice available: false con missingFields si algo faltó: la lista trae lo que sí se leyó, nunca un cero (RQ-DSH-032)
class SpatialParts<T> {
    val rows = mutableListOf<T>()
    private val absent = linkedSetOf<String>()

    fun add(outcome: GateOutcome<T>): List<T> =
        when (outcome) {
            is GateOutcome.Rows -> outcome.rows.also { rows += it }
            is GateOutcome.Unavailable -> emptyList<T>().also { absent += outcome.missingFields }
        }

    fun missing(vararg fields: String) {
        absent += fields
    }

    fun response(
        key: String,
        items: List<Any?>,
        truncated: Boolean? = null
    ): Map<String, Any?> {
        val out = linkedMapOf<String, Any?>("available" to absent.isEmpty())
        if (absent.isNotEmpty()) out["missingFields"] = absent.toList()
        if (truncated != null) out["truncated"] = truncated
        out[key] = items
        return out
    }
}
