package sgspe.territory

import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import java.math.BigDecimal
import java.util.Optional

data class Validity(
    val valid: Boolean,
    val reason: String?
)

data class UtmPoint(
    val easting: BigDecimal,
    val northing: BigDecimal
)

// las cuentas geométricas de sgspe.territory con PostGIS, sobre GeoJSON y sin tocar ninguna tabla: transformar desde el
// CRS de origen, validar, normalizar el tipo, medir áreas en ha y distancias en metros en un CRS UTM (gis.md §3 y §4). el
// almacenamiento sigue siendo de wasichai-gis (SRID 4326, 2D); nunca se calcula un área sobre grados
@Component
class GeoSql(
    private val db: DatabaseClient
) {
    // GeoJSON en `srid` -> GeoJSON EPSG:4326, en 2D
    suspend fun toWgs84(
        geojson: Map<String, Any?>,
        srid: Int
    ): Map<String, Any?> = geometry("SELECT ST_AsGeoJSON(ST_Force2D(ST_Transform(ST_SetSRID(ST_GeomFromGeoJSON(:g), :srid), 4326)), 9) AS out", geojson, srid)

    suspend fun pointToWgs84(
        x: BigDecimal,
        y: BigDecimal,
        srid: Int
    ): Map<String, Any?> =
        read(
            db
                .sql("SELECT ST_AsGeoJSON(ST_Transform(ST_SetSRID(ST_MakePoint(:x, :y), :srid), 4326), 9) AS out")
                .bind("x", x.toDouble())
                .bind("y", y.toDouble())
                .bind("srid", srid)
        )

    suspend fun validity(geojson: Map<String, Any?>): Validity =
        db
            .sql("SELECT ST_IsValid(g) AS valid, ST_IsValidReason(g) AS reason FROM (SELECT ST_SetSRID(ST_GeomFromGeoJSON(:g), 4326) AS g) s")
            .bind("g", json(geojson))
            .map { row, _ ->
                val valid = row.get("valid", java.lang.Boolean::class.java)?.booleanValue() == true
                Validity(valid, if (valid) null else row.get("reason", String::class.java))
            }.one()
            .awaitSingleOrNull() ?: Validity(false, "geometría vacía")

    // al tipo del campo destino (gis.md §4.1): un MultiPolygon de una sola parte hacia POLYGON pasa a Polygon, y un
    // Polygon hacia MULTIPOLYGON se envuelve; lo mismo con puntos y líneas. null si no se puede sin perder partes
    suspend fun asType(
        geojson: Map<String, Any?>,
        target: String
    ): Map<String, Any?>? {
        val type = geojson["type"]?.toString() ?: return null
        if (type.equals(target, ignoreCase = true)) return geojson
        val multi = target.startsWith("Multi")
        if (multi && type.equals(target.removePrefix("Multi"), ignoreCase = true)) {
            return geometry("SELECT ST_AsGeoJSON(ST_Multi(ST_SetSRID(ST_GeomFromGeoJSON(:g), :srid)), 9) AS out", geojson, Crs.WGS84)
        }
        if (!multi && type.equals("Multi$target", ignoreCase = true)) {
            val parts =
                db
                    .sql("SELECT ST_NumGeometries(ST_GeomFromGeoJSON(:g)) AS n")
                    .bind("g", json(geojson))
                    .map { row, _ -> (row.get("n") as Number?)?.toInt() ?: 0 }
                    .one()
                    .awaitSingleOrNull() ?: 0
            if (parts != 1) return null
            return geometry("SELECT ST_AsGeoJSON(ST_GeometryN(ST_SetSRID(ST_GeomFromGeoJSON(:g), :srid), 1), 9) AS out", geojson, Crs.WGS84)
        }
        return null
    }

    // área plana en ha, redondeada a dos decimales (RQ-GIS-009, ASM-057); null para puntos y líneas
    suspend fun areaHa(
        geojson: Map<String, Any?>,
        srid: Int
    ): BigDecimal? =
        db
            .sql(
                "SELECT CASE WHEN ST_Dimension(g) = 2 THEN round((ST_Area(ST_Transform(g, :srid)) / 10000)::numeric, 2) END AS ha " +
                    "FROM (SELECT ST_SetSRID(ST_GeomFromGeoJSON(:g), 4326) AS g) s"
            ).bind("g", json(geojson))
            .bind("srid", srid)
            .map { row, _ -> Optional.ofNullable(row.get("ha", BigDecimal::class.java)) }
            .one()
            .awaitSingleOrNull()
            ?.orElse(null)

    // el centroide en grados: (longitud, latitud)
    suspend fun centroid(geojson: Map<String, Any?>): Pair<Double, Double>? =
        db
            .sql("SELECT ST_X(c) AS x, ST_Y(c) AS y FROM (SELECT ST_Centroid(ST_SetSRID(ST_GeomFromGeoJSON(:g), 4326)) AS c) s")
            .bind("g", json(geojson))
            .map { row, _ -> (row.get("x") as Number).toDouble() to (row.get("y") as Number).toDouble() }
            .one()
            .awaitSingleOrNull()

    // la zona UTM sur WGS 84 del centroide (ASM-064): 32717, 32718 o 32719 en el Perú. fuera de esas zonas o al norte
    // del ecuador, null: no se calcula con una zona inesperada (gis.md §3.3)
    suspend fun centroidZone(geojson: Map<String, Any?>): Int? = centroid(geojson)?.let { (x, y) -> zoneOf(x, y) }

    // distancia de Hausdorff en metros entre dos geometrías EPSG:4326, medida en `srid`: para dos puntos es su
    // distancia; para líneas y polígonos, el mayor desplazamiento (gis.md §4.4)
    suspend fun distanceM(
        a: Map<String, Any?>,
        b: Map<String, Any?>,
        srid: Int
    ): BigDecimal =
        db
            .sql(
                "SELECT round(ST_HausdorffDistance(ST_Transform(ST_SetSRID(ST_GeomFromGeoJSON(:a), 4326), :srid), " +
                    "ST_Transform(ST_SetSRID(ST_GeomFromGeoJSON(:b), 4326), :srid))::numeric, 3) AS d"
            ).bind("a", json(a))
            .bind("b", json(b))
            .bind("srid", srid)
            .map { row, _ -> Optional.ofNullable(row.get("d", BigDecimal::class.java)) }
            .one()
            .awaitSingleOrNull()
            ?.orElse(null) ?: BigDecimal.ZERO

    // el punto (o el centroide) en un CRS UTM, para el reporte de diferencias
    suspend fun utm(
        geojson: Map<String, Any?>,
        srid: Int
    ): UtmPoint? =
        db
            .sql(
                "SELECT round(ST_X(p)::numeric, 3) AS e, round(ST_Y(p)::numeric, 3) AS n " +
                    "FROM (SELECT ST_Transform(ST_Centroid(ST_SetSRID(ST_GeomFromGeoJSON(:g), 4326)), :srid) AS p) s"
            ).bind("g", json(geojson))
            .bind("srid", srid)
            .map { row, _ -> UtmPoint(row.get("e", BigDecimal::class.java)!!, row.get("n", BigDecimal::class.java)!!) }
            .one()
            .awaitSingleOrNull()

    private suspend fun geometry(
        sql: String,
        geojson: Map<String, Any?>,
        srid: Int
    ): Map<String, Any?> = read(db.sql(sql).bind("g", json(geojson)).bind("srid", srid))

    @Suppress("UNCHECKED_CAST")
    private suspend fun read(spec: DatabaseClient.GenericExecuteSpec): Map<String, Any?> {
        val out =
            spec
                .map { row, _ -> Optional.ofNullable(row.get("out", String::class.java)) }
                .one()
                .awaitSingleOrNull()
                ?.orElse(null) ?: error("PostGIS no devolvió la geometría")
        return JSON.readValue(out, Map::class.java) as Map<String, Any?>
    }

    private fun json(geojson: Map<String, Any?>): String = JSON.writeValueAsString(geojson)

    companion object {
        private val JSON: JsonMapper = JsonMapper.builder().build()

        // pura: la zona de un punto en grados, en el hemisferio sur y en las zonas de trabajo
        fun zoneOf(
            longitude: Double,
            latitude: Double
        ): Int? {
            if (latitude > 0) return null
            val zone = kotlin.math.floor((longitude + 180) / 6).toInt() + 1
            return (32700 + zone).takeIf { it in Crs.WORKING_ZONES }
        }
    }
}
