package sgspe.territory

import io.r2dbc.spi.R2dbcException
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import java.math.BigDecimal
import java.util.Optional

// reason: el motivo en español (null si es válida); detail: el texto crudo de ST_IsValidReason, en inglés y con
// coordenadas, para quien quiera mostrarlo como detalle
data class Validity(
    val valid: Boolean,
    val reason: String?,
    val detail: String? = null
)

// una geometría que PostGIS no puede procesar: GeoJSON mal formado o coordenadas fuera del dominio del CRS («Point outside
// of projection domain», «Invalid coordinate»). quien importa la convierte en una fila inválida, no en un 500
class InvalidGeometryException(
    val detail: String,
    cause: Throwable? = null
) : RuntimeException("geometría no procesable: $detail", cause)

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
    ): Map<String, Any?> {
        requireCatalogued(srid)
        return geometry("SELECT ST_AsGeoJSON(ST_Force2D(ST_Transform(ST_SetSRID(ST_GeomFromGeoJSON(:g), :srid), 4326)), 9) AS out", geojson, srid)
    }

    suspend fun pointToWgs84(
        x: BigDecimal,
        y: BigDecimal,
        srid: Int
    ): Map<String, Any?> {
        requireCatalogued(srid)
        return read(
            db
                .sql("SELECT ST_AsGeoJSON(ST_Transform(ST_SetSRID(ST_MakePoint(:x, :y), :srid), 4326), 9) AS out")
                .bind("x", x.toDouble())
                .bind("y", y.toDouble())
                .bind("srid", srid)
        )
    }

    // una geometría vacía no es válida para SGSPE aunque PostGIS la dé por válida; el motivo sale en español y el texto
    // de ST_IsValidReason queda en `detail`
    suspend fun validity(geojson: Map<String, Any?>): Validity =
        guarded {
            db
                .sql(
                    "SELECT ST_IsEmpty(g) AS empty, ST_IsValid(g) AS valid, ST_IsValidReason(g) AS reason " +
                        "FROM (SELECT ST_SetSRID(ST_GeomFromGeoJSON(:g), 4326) AS g) s"
                ).bind("g", json(geojson))
                .map { row, _ ->
                    val empty = row.get("empty", java.lang.Boolean::class.java)?.booleanValue() == true
                    val valid = row.get("valid", java.lang.Boolean::class.java)?.booleanValue() == true
                    val raw = row.get("reason", String::class.java)
                    when {
                        empty -> Validity(false, EMPTY, null)
                        valid -> Validity(true, null, null)
                        else -> Validity(false, reasonLabel(raw), raw)
                    }
                }.one()
                .awaitSingleOrNull() ?: Validity(false, EMPTY, null)
        }

    // al tipo del campo destino (gis.md §4.1): un MultiPolygon de una sola parte hacia POLYGON pasa a Polygon, y un
    // Polygon hacia MULTIPOLYGON se envuelve; lo mismo con puntos y líneas. null si no se puede sin perder partes
    suspend fun asType(
        geojson: Map<String, Any?>,
        target: String
    ): Map<String, Any?>? {
        val type = geojson["type"]?.toString()?.lowercase() ?: return null
        val wanted = target.lowercase()
        if (type == wanted) return geojson
        if (wanted.startsWith("multi") && type == wanted.removePrefix("multi")) {
            return geometry("SELECT ST_AsGeoJSON(ST_Multi(ST_SetSRID(ST_GeomFromGeoJSON(:g), :srid)), 9) AS out", geojson, Crs.WGS84)
        }
        if (!wanted.startsWith("multi") && type == "multi$wanted") {
            val parts =
                guarded {
                    db
                        .sql("SELECT ST_NumGeometries(ST_GeomFromGeoJSON(:g)) AS n")
                        .bind("g", json(geojson))
                        .map { row, _ -> (row.get("n") as Number?)?.toInt() ?: 0 }
                        .one()
                        .awaitSingleOrNull() ?: 0
                }
            if (parts != 1) return null
            return geometry("SELECT ST_AsGeoJSON(ST_GeometryN(ST_SetSRID(ST_GeomFromGeoJSON(:g), :srid), 1), 9) AS out", geojson, Crs.WGS84)
        }
        return null
    }

    // área plana en ha, redondeada a dos decimales (RQ-GIS-009, ASM-057); null para puntos y líneas. `srid` debe ser una
    // zona de trabajo (32717 a 32719): nunca se mide un área sobre grados
    suspend fun areaHa(
        geojson: Map<String, Any?>,
        srid: Int
    ): BigDecimal? {
        requireWorkingZone(srid)
        return guarded {
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
        }
    }

    // el centroide en grados: (longitud, latitud); null si la geometría está vacía
    suspend fun centroid(geojson: Map<String, Any?>): Pair<Double, Double>? =
        guarded {
            db
                .sql("SELECT ST_X(c) AS x, ST_Y(c) AS y FROM (SELECT ST_Centroid(ST_SetSRID(ST_GeomFromGeoJSON(:g), 4326)) AS c) s")
                .bind("g", json(geojson))
                .map { row, _ ->
                    val x = row.get("x") as? Number
                    val y = row.get("y") as? Number
                    Optional.ofNullable(if (x != null && y != null) x.toDouble() to y.toDouble() else null)
                }.one()
                .awaitSingleOrNull()
                ?.orElse(null)
        }

    // la zona UTM sur WGS 84 del centroide (ASM-064): 32717, 32718 o 32719 en el Perú. fuera de esas zonas o al norte
    // del ecuador, null: no se calcula con una zona inesperada (gis.md §3.3)
    suspend fun centroidZone(geojson: Map<String, Any?>): Int? = centroid(geojson)?.let { (x, y) -> zoneOf(x, y) }

    // distancia de Hausdorff en metros entre dos geometrías EPSG:4326, medida en `srid` (una zona de trabajo): para
    // dos puntos es su distancia; para líneas y polígonos, el mayor desplazamiento (gis.md §4.4)
    suspend fun distanceM(
        a: Map<String, Any?>,
        b: Map<String, Any?>,
        srid: Int
    ): BigDecimal {
        requireWorkingZone(srid)
        return guarded {
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
        }
    }

    // el punto (o el centroide) en una zona de trabajo, para el reporte de diferencias; null si la geometría está vacía
    suspend fun utm(
        geojson: Map<String, Any?>,
        srid: Int
    ): UtmPoint? {
        requireWorkingZone(srid)
        return guarded {
            db
                .sql(
                    "SELECT round(ST_X(p)::numeric, 3) AS e, round(ST_Y(p)::numeric, 3) AS n " +
                        "FROM (SELECT ST_Transform(ST_Centroid(ST_SetSRID(ST_GeomFromGeoJSON(:g), 4326)), :srid) AS p) s"
                ).bind("g", json(geojson))
                .bind("srid", srid)
                .map { row, _ ->
                    val e = row.get("e", BigDecimal::class.java)
                    val n = row.get("n", BigDecimal::class.java)
                    Optional.ofNullable(if (e != null && n != null) UtmPoint(e, n) else null)
                }.one()
                .awaitSingleOrNull()
                ?.orElse(null)
        }
    }

    private suspend fun geometry(
        sql: String,
        geojson: Map<String, Any?>,
        srid: Int
    ): Map<String, Any?> = read(db.sql(sql).bind("g", json(geojson)).bind("srid", srid))

    // un fallo de PostGIS por la geometría misma pasa a InvalidGeometryException; los de conexión y de SQL se propagan
    private suspend fun <T> guarded(block: suspend () -> T): T =
        try {
            block()
        } catch (e: Exception) {
            val failure = geometryFailure(e) ?: throw e
            throw InvalidGeometryException(failure.message ?: "error de PostGIS", e)
        }

    @Suppress("UNCHECKED_CAST")
    private suspend fun read(spec: DatabaseClient.GenericExecuteSpec): Map<String, Any?> {
        val out =
            guarded {
                spec
                    .map { row, _ -> Optional.ofNullable(row.get("out", String::class.java)) }
                    .one()
                    .awaitSingleOrNull()
                    ?.orElse(null)
            } ?: error("PostGIS no devolvió la geometría")
        return JSON.readValue(out, Map::class.java) as Map<String, Any?>
    }

    private fun json(geojson: Map<String, Any?>): String = JSON.writeValueAsString(geojson)

    companion object {
        private val JSON: JsonMapper = JsonMapper.builder().build()
        private const val EMPTY = "geometría vacía"

        // origen de una transformación: cualquier SRID del catálogo (4326, UTM o PSAD56)
        fun requireCatalogued(srid: Int) = require(Crs.isCatalogued(srid)) { "SRID $srid fuera del catálogo de CRS" }

        // medición: solo una zona de trabajo WGS 84 UTM, nunca grados ni PSAD56 (solo de entrada)
        fun requireWorkingZone(srid: Int) = require(Crs.isWorkingZone(srid)) { "SRID $srid no es una zona de trabajo (32717 a 32719)" }

        // el error de R2DBC que causó `e`, si PostGIS rechazó la geometría: clase SQLSTATE 22 (dato), XX (interno, como
        // «Point outside of projection domain» o «invalid GeoJSON representation») o P0 (RAISE); null si es otra cosa
        // (conexión, SQL mal escrito, permisos)
        fun geometryFailure(e: Throwable): R2dbcException? {
            val r2dbc =
                generateSequence(e) { it.cause?.takeIf { next -> next !== it } }
                    .filterIsInstance<R2dbcException>()
                    .firstOrNull() ?: return null
            val state = r2dbc.sqlState ?: return null
            return r2dbc.takeIf { state.startsWith("22") || state.startsWith("XX") || state.startsWith("P0") }
        }

        // el motivo de ST_IsValidReason en español, sin coordenadas
        fun reasonLabel(raw: String?): String {
            val text = raw?.lowercase().orEmpty()
            return when {
                "ring self-intersection" in text -> "anillo autointersecado"
                "self-intersection" in text -> "autointersección"
                "too few points" in text -> "pocos puntos"
                "do not form a closed" in text -> "anillo sin cerrar"
                "hole lies outside shell" in text -> "hueco fuera del contorno"
                "holes are nested" in text -> "huecos anidados"
                "nested shells" in text -> "contornos anidados"
                "duplicate rings" in text -> "anillos duplicados"
                "interior is disconnected" in text -> "interior desconectado"
                else -> "no cumple las reglas de validez OGC"
            }
        }

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
